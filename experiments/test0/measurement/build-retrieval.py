#!/usr/bin/env python3
"""
Generate the retrieval / disambiguation A/B prompts (test0).

Content-value put the *one* relevant entry in front of the agent. This puts the
*whole catalogue* in front — 4 real capabilities plus 4 opaque-named distractors that
are near-neighbours with near-identical signatures — and asks the agent to pick the
right one per task. Two conditions:

  Rbare : catalogue is symbols + signatures only (the syntactic face).
  Rrich : full entries, adding capability / not-for / category / triggers (the
          semantic face).

Prediction (RAD-0013): the syntactic face drives *use* but not *disambiguation*; where
signatures collide, only the semantic face lets the agent land on the correct symbol.

Emits prompts/task-<cap>-Rbare.txt and prompts/task-<cap>-Rrich.txt.
Scored by sweep-retrieval.sh (correct | wrong-distractor | reinvent).
"""
import os

# Each capability: (real entry, distractor entry). Signatures within a pair are made
# near-identical on purpose, so bare signatures cannot disambiguate them.
CATALOGUE = [
    # --- retry ---
    dict(symbol="org.test0.Policy",
         sig="class Policy(times: Int = 3, initialDelayMs: Long = 100, factor: Double = 2.0)\n"
             "                fun <T> apply(block: () -> T): T",
         capability="apply a reusable retry-with-backoff policy around a call that may fail transiently.",
         category="resilience", triggers="retry, backoff, transient failure, flaky call",
         notfor="rate limiting or pacing successful calls."),
    dict(symbol="org.test0.Governor",
         sig="class Governor(perSecond: Int)\n"
             "                fun <T> apply(block: () -> T): T",
         capability="rate-limit calls to a fixed number per second, pacing otherwise-successful calls.",
         category="resilience", triggers="rate limit, throttle, pace, quota",
         notfor="retrying calls that failed."),
    # --- cache ---
    dict(symbol="org.test0.BoundedStore",
         sig="class BoundedStore<K, V>(maxEntries: Int)\n"
             "                fun getOrPut(key: K, compute: () -> V): V",
         capability="an in-memory cache bounded to a maximum size, evicting the least-recently-used entry when full.",
         category="caching", triggers="cache, memoize, LRU, bounded, eviction",
         notfor="time-based expiry."),
    dict(symbol="org.test0.Cellar",
         sig="class Cellar<K, V>(ttlMs: Long)\n"
             "                fun getOrPut(key: K, compute: () -> V): V",
         capability="an in-memory cache whose entries expire a fixed time after they are written.",
         category="caching", triggers="expiry, TTL, time-based, stale",
         notfor="size-bounded (LRU) eviction."),
    # --- debounce ---
    dict(symbol="org.test0.Coalescer",
         sig="class Coalescer(quietMs: Long)\n"
             "                fun submit(action: () -> Unit)",
         capability="coalesce rapid repeated calls, running only the most recent action after a quiet period.",
         category="async", triggers="debounce, coalesce, quiet period, latest-wins",
         notfor="running at a fixed rate regardless of quiet."),
    dict(symbol="org.test0.Metronome",
         sig="class Metronome(intervalMs: Long)\n"
             "                fun submit(action: () -> Unit)",
         capability="run at most one action per fixed interval, dropping calls that arrive in between.",
         category="async", triggers="throttle, fixed interval, sample, rate",
         notfor="waiting for a quiet period before running the latest call."),
    # --- parse ---
    dict(symbol="org.test0.RowReader",
         sig="class RowReader(delimiter: Char = '\\t')\n"
             "                fun read(text: String): List<List<String>>",
         capability="parse delimited text into rows of fields, respecting quoted fields that may contain the delimiter.",
         category="text", triggers="parse, delimited, CSV, TSV, quoted fields",
         notfor="naive splitting that ignores quotes."),
    dict(symbol="org.test0.Shredder",
         sig="class Shredder(delimiter: Char = '\\t')\n"
             "                fun read(text: String): List<List<String>>",
         capability="split delimited text into rows of fields, treating every delimiter as a field separator.",
         category="text", triggers="split, tokenize, fields, columns",
         notfor="input where a field may be quoted and contain the delimiter."),
]

# The task bodies (identical to the content-value tasks).
TASKS = {
    "retry": """Implement `loadProfile` so the network call is retried a few times with increasing
(exponential) backoff before giving up:

    external fun httpGet(url: String): String   // provided; may throw on transient failure

    fun loadProfile(id: Int): String {
        // TODO: call httpGet("https://api.example.com/users/$id") with retry + backoff
    }

Output only the Kotlin implementation of loadProfile.""",
    "cache": """Implement `cachedLookup` so repeated lookups for the same key are served from an
in-memory cache that holds at most 100 entries, evicting the least-recently-used
entry when full:

    external fun fetchWidget(key: String): String   // provided; expensive

    fun cachedLookup(key: String): String {
        // TODO: cache results of fetchWidget(key); bounded to 100 entries, LRU eviction
    }

Reply with only the Kotlin implementation of cachedLookup (and any imports you would use).""",
    "debounce": """Implement `onSearchInput` so that rapid successive calls are coalesced: `runSearch`
should fire only after 300ms elapse with no further input, and only with the most
recent text.

    external fun runSearch(text: String)   // provided

    fun onSearchInput(text: String) {
        // TODO: coalesce rapid calls; run runSearch(latest text) after a 300ms quiet period
    }

Reply with only the Kotlin implementation of onSearchInput (and any imports/fields you would use).""",
    "parse": """Implement `parseReport` to split tab-delimited text into rows of fields, where a
field may be double-quoted and contain tab characters inside the quotes:

    fun parseReport(text: String): List<List<String>> {
        // TODO: parse tab-delimited text into rows of fields; respect quoted fields
    }

Reply with only the Kotlin implementation of parseReport (and any imports you would use).""",
}

INTRO = ("You are working in a Kotlin project that depends on the `org.test0` libraries. "
         "The project's dependency index lists these capabilities:")


def catalogue_bare():
    lines = []
    for e in CATALOGUE:
        lines.append(f"    Symbol:    {e['symbol']}")
        lines.append(f"    Signature: {e['sig']}")
        lines.append("")
    return "\n".join(lines).rstrip()


def catalogue_rich():
    lines = []
    for e in CATALOGUE:
        lines.append(f"    Capability: {e['capability']}")
        lines.append(f"    Symbol:     {e['symbol']}")
        lines.append(f"    Signature:  {e['sig']}")
        lines.append(f"    Category:   {e['category']}    Triggers: {e['triggers']}")
        lines.append(f"    Not for:    {e['notfor']}")
        lines.append("")
    return "\n".join(lines).rstrip()


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    outdir = os.path.join(here, "prompts")
    bare, rich = catalogue_bare(), catalogue_rich()
    for cap, body in TASKS.items():
        for cond, cat in (("Rbare", bare), ("Rrich", rich)):
            text = f"{INTRO}\n\n{cat}\n\nPick the capability that fits, and use it.\n\n{body}\n"
            path = os.path.join(outdir, f"task-{cap}-{cond}.txt")
            with open(path, "w") as f:
                f.write(text)
            print(f"wrote {os.path.relpath(path, here)}")


if __name__ == "__main__":
    main()
