#!/usr/bin/env python3
"""
Build a synthetic capability corpus for the retrieval-at-scale eval (Layer 1).

The four test0 targets (retry, cache, debounce, parse) are buried among ADVERSARIAL
near-neighbours (same semantic cluster) plus broad cross-domain noise. The queries are
the caller's *need* in plain words — never the opaque symbol name — so the test is
whether the semantic face retrieves the right entry among look-alikes.

Emits corpus.json (entries) and queries.json (query + ground-truth target symbol).
"""
import json, os

# domain -> list of (Name, capability, triggers, category, notfor). '*' marks a target.
DOMAINS = {
    "resilience": [
        ("Policy*", "apply a reusable retry-with-backoff policy around a call that may fail transiently", "retry, backoff, transient failure, flaky call", "resilience", "rate limiting"),
        ("Governor", "rate-limit calls to a fixed number per second, pacing successful calls", "rate limit, throttle, pace, quota", "resilience", "retrying failures"),
        ("Breaker", "open a circuit after repeated failures so calls to a failing service fail fast", "circuit breaker, fail fast, trip, half-open", "resilience", "retrying"),
        ("Deadline", "abort a call that exceeds a time budget", "timeout, deadline, cancel, budget", "resilience", "retrying"),
        ("Bulkhead", "cap the number of concurrent calls so one dependency cannot exhaust the pool", "bulkhead, concurrency limit, isolate", "resilience", "retrying"),
        ("Hedge", "send a second attempt when the first is slow and take whichever returns first", "hedge, tail latency, speculative", "resilience", "retrying failures"),
    ],
    "caching": [
        ("BoundedStore*", "an in-memory cache bounded to a maximum size, evicting the least-recently-used entry when full", "cache, LRU, bounded, eviction, memoize", "caching", "time-based expiry"),
        ("Cellar", "an in-memory cache whose entries expire a fixed time after they are written", "expiry, TTL, time-based, stale", "caching", "size-bounded eviction"),
        ("WriteThrough", "a cache that writes every update through to a backing store", "write-through, backing store, persist", "caching", "bounded eviction"),
        ("Memoizer", "remember the result of a pure function keyed by its arguments", "memoize, pure function, recompute", "caching", "eviction policy"),
        ("Pool", "reuse a fixed set of expensive objects across callers", "object pool, reuse, borrow, lease", "caching", "caching values"),
    ],
    "async": [
        ("Coalescer*", "coalesce rapid repeated calls, running only the most recent action after a quiet period", "debounce, coalesce, quiet period, latest-wins", "async", "running at a fixed rate"),
        ("Metronome", "run at most one action per fixed interval, dropping calls that arrive in between", "throttle, fixed interval, sample, rate", "async", "waiting for a quiet period"),
        ("Batcher", "collect items and flush them together when a size or time threshold is reached", "batch, buffer, flush, accumulate", "async", "latest-wins"),
        ("Scheduler", "run an action after a delay or on a repeating schedule", "schedule, delay, timer, cron", "async", "coalescing"),
        ("Latch", "run an action exactly once and ignore later triggers", "once, idempotent, single-shot", "async", "repeated calls"),
    ],
    "text": [
        ("RowReader*", "parse delimited text into rows of fields, respecting quoted fields that may contain the delimiter", "parse, delimited, CSV, TSV, quoted fields", "text", "naive splitting"),
        ("Shredder", "split delimited text into fields, treating every delimiter as a field separator", "split, tokenize, columns, separator", "text", "quoted fields"),
        ("Templater", "substitute named placeholders in a template string with values", "template, interpolate, placeholder, mustache", "text", "parsing"),
        ("Slugger", "turn a string into a URL-safe slug", "slug, url-safe, normalize, kebab", "text", "parsing"),
        ("Wrapper", "wrap long text to a maximum line width", "word wrap, line width, fill", "text", "parsing"),
    ],
    # --- broad noise (also seeds cross-cluster adversaries: Semaphored~Bulkhead, Validator~accumulate) ---
    "serialization": [
        ("JsonCodec", "serialize objects to and from JSON", "json, serialize, encode, decode", "serialization", ""),
        ("YamlCodec", "serialize objects to and from YAML", "yaml, config, encode", "serialization", ""),
        ("TomlCodec", "serialize objects to and from TOML", "toml, config", "serialization", ""),
        ("ProtoCodec", "serialize objects to and from protocol buffers", "protobuf, binary, schema", "serialization", ""),
    ],
    "http": [
        ("HttpGetter", "perform an HTTP GET request and return the response body", "http, get, fetch, request", "http", ""),
        ("HttpPoster", "perform an HTTP POST request with a body", "http, post, upload", "http", ""),
        ("SocketLink", "open a websocket connection for bidirectional messages", "websocket, realtime, duplex", "http", ""),
        ("Downloader", "download a file to disk, reporting progress", "download, file, progress, stream", "http", ""),
    ],
    "observability": [
        ("Journal", "record structured application log events", "log, structured, event", "observability", ""),
        ("Tracer", "record distributed tracing spans across services", "trace, span, distributed", "observability", ""),
        ("Meter", "record counters and gauges as metrics", "metrics, counter, gauge", "observability", ""),
        ("Auditor", "append immutable audit records of sensitive actions", "audit, immutable, compliance", "observability", ""),
    ],
    "validation": [
        ("Validator", "check a value against constraints, accumulating all errors", "validate, constraints, accumulate errors", "validation", ""),
        ("Sanitizer", "strip unsafe or unwanted content from input", "sanitize, clean, escape", "validation", ""),
        ("Coercer", "coerce a string into a typed value with a fallback", "coerce, parse, convert, default", "validation", ""),
    ],
    "collections": [
        ("Grouper", "group items in a sequence by a derived key", "group by, partition, index", "collections", ""),
        ("Windower", "produce sliding windows over a sequence", "window, sliding, chunk", "collections", ""),
        ("Deduper", "remove duplicate items while preserving order", "dedupe, distinct, unique", "collections", ""),
        ("Sorter", "sort items by several keys with mixed directions", "sort, order, comparator", "collections", ""),
    ],
    "datetime": [
        ("Ticker", "read the current instant in a given time zone", "clock, now, instant, timezone", "datetime", ""),
        ("Span", "parse and format human-readable durations", "duration, elapsed, format", "datetime", ""),
        ("Almanac", "compute business days and holidays between dates", "business day, holiday, calendar", "datetime", ""),
    ],
    "crypto": [
        ("Digester", "compute a cryptographic hash of bytes", "hash, digest, sha, checksum", "crypto", ""),
        ("Notary", "sign a message and verify signatures", "sign, verify, signature", "crypto", ""),
        ("Cipher", "encrypt and decrypt with a symmetric key", "encrypt, decrypt, aes, symmetric", "crypto", ""),
    ],
    "io": [
        ("Sentinel", "watch a directory for file changes", "watch, filesystem, notify", "io", ""),
        ("Scratch", "create and clean up temporary files and directories", "temp file, scratch, cleanup", "io", ""),
        ("Piper", "copy bytes between streams with buffering", "stream, copy, buffer", "io", ""),
    ],
    "concurrency": [
        ("Guarded", "guard a mutable value behind a mutex", "mutex, lock, guard", "concurrency", ""),
        ("Semaphored", "limit the number of coroutines entering a section at once", "semaphore, concurrency limit, permit", "concurrency", "retrying"),
        ("Gathered", "await several async results and collect them together", "await all, gather, join", "concurrency", ""),
    ],
    "math": [
        ("Stats", "compute mean, variance and percentiles of numbers", "statistics, mean, percentile", "math", ""),
        ("Lerp", "interpolate between values linearly or with splines", "interpolate, lerp, spline", "math", ""),
        ("Dice", "generate seeded pseudo-random values", "random, seed, prng", "math", ""),
    ],
    "strings": [
        ("Fuzzy", "score how closely two strings match approximately", "fuzzy, similarity, levenshtein", "strings", ""),
        ("Differ", "compute a line diff between two texts", "diff, compare, patch", "strings", ""),
        ("Caser", "convert identifiers between camel, snake and kebab case", "case, camel, snake, kebab", "strings", ""),
    ],
}

# Caller's need in PLAIN words that deliberately avoid the entry's own trigger vocabulary
# (no "retry", "cache", "debounce", "parse", "LRU"...), so lexical overlap is minimal and
# the test is genuine semantic retrieval — RAD-0011's "capability in the caller's words".
# Ground-truth target sits among in-corpus adversarial neighbours (named in the comment).
QUERIES = [
    # vs Governor / Breaker / Deadline
    ("the request occasionally fails for no lasting reason; attempt it a few more times with growing pauses between tries, instead of giving up on the first error", "org.corpus.Policy"),
    # vs Cellar / Memoizer
    ("hold recent lookup answers in memory but keep a lid on how many I store, discarding the least-recently-touched when it fills up", "org.corpus.BoundedStore"),
    # vs Metronome / Batcher
    ("the input box fires on every keystroke; only react once the person pauses for a moment, using just what they last typed", "org.corpus.Coalescer"),
    # vs Shredder
    ("I have spreadsheet-style lines separated by tabs, except a cell can be wrapped in quotes and then may contain tabs that must not break the line", "org.corpus.RowReader"),
    # vs Policy / Deadline
    ("when a downstream service is clearly down, stop calling it for a while and fail immediately instead of piling up requests", "org.corpus.Breaker"),
    # vs Policy / Breaker
    ("give up on an operation that takes longer than a fixed time budget and move on", "org.corpus.Deadline"),
    # vs BoundedStore / caching
    ("this pure computation is expensive; keep its output for a given set of arguments so the same work is never done twice", "org.corpus.Memoizer"),
    # vs Coalescer / Metronome
    ("gather items and send them onward together once I've collected enough or a little time has passed", "org.corpus.Batcher"),
]


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    entries = []
    for dom, items in DOMAINS.items():
        for name, cap, trig, cat, notfor in items:
            target = name.endswith("*")
            sym = name.rstrip("*")
            entries.append(dict(
                symbol=f"org.corpus.{sym}", capability=cap, triggers=trig,
                category=cat, notfor=notfor, target=target,
            ))
    with open(os.path.join(here, "corpus.json"), "w") as f:
        json.dump(entries, f, indent=2)
    with open(os.path.join(here, "queries.json"), "w") as f:
        json.dump([dict(query=q, target=t) for q, t in QUERIES], f, indent=2)
    print(f"corpus: {len(entries)} entries across {len(DOMAINS)} domains; {len(QUERIES)} queries")


if __name__ == "__main__":
    main()
