package org.dependencyskills.codex.harvester

/**
 * Reduces a doc comment to the prose inside it, or null when there is not enough left to be
 * worth a retrieval key.
 *
 * This is the raw documentation the store holds: a key it searches on and never hands out. What
 * a reader is shown is the rewrite, which a later pass produces. So the job here is not to
 * present the comment well — it is to leave the words a query would match on and drop the
 * markup a query never contains.
 *
 * Markup is not one thing, and these are not one rule:
 *
 * - **Block code goes**, in whichever syntax delimits it — a markdown fence or a `<pre>` block.
 * - **Inline code keeps its content.** What sits in a backtick or a `<code>` is usually a symbol
 *   name, and a query does contain those.
 * - **A tag goes and the text it wrapped stays.** `<a href="…">common navigation patterns</a>`
 *   is one address and four words worth matching on.
 * - **An entity is decoded**, because `&lt;` is how a comment spells a character rather than a
 *   thing anyone types into a search box.
 *
 * ## Divergence from the corpus experiment
 *
 * This no longer matches `experiments/corpus/build.py`, which strips the fence, the leading star
 * and the block tags and stops there. Measured against that corpus of 684,392 declarations, the
 * rules here change **38.4%** of the documentation and remove **20.4%** of its words — 52.2% of
 * javadoc, where `<p>`, `<pre>` and `<code>` alone account for 890,000 occurrences.
 *
 * That is a deliberate break, and it means retrieval numbers measured through `corpus.db` were
 * measured against a different extraction than the one that ships. It is recorded here rather
 * than only in the commit because nothing about a stale number announces itself.
 */
internal fun cleanDoc(raw: String): String? {
    var text = LEADING_STAR.replace(raw.removePrefix("/**").removeSuffix("*/"), "")
    text = BLOCK_CODE.replace(text, " ")
    text = INLINE_TAG.replace(text, " ")
    text = HTML_TAG.replace(text, " ")
    text = LINK.replace(text, "$1")
    text = URL.replace(text, " ")
    val kept = text.lineSequence()
        .map { it.trim() }
        // A line that is only a `@param`/`@return` tag is structure, not description. The tag
        // vocabulary is the same in every library, so it matches every query equally and
        // separates nothing.
        .filter { it.isNotEmpty() && !TAG_ONLY.containsMatchIn(it) }
        .joinToString(" ")
    // Last, so a decoded `&#64;` cannot make a line look like a tag and a decoded `&lt;` cannot
    // make a word look like markup. By this point both passes have already run.
    val decoded = WHITESPACE.replace(ENTITY.replace(kept) { decode(it.groupValues[1]) }, " ")
    val prose = LOOSE_PUNCTUATION.replace(decoded, "").trim()
    return if (prose.split(' ').size >= MINIMUM_WORDS) prose else null
}

private val LEADING_STAR = Regex("^\\s*\\*", RegexOption.MULTILINE)

/** Block code, in both the syntaxes these conventions use. Whatever is inside it is code. */
private val BLOCK_CODE = Regex(
    "```.*?```|<pre\\b.*?</pre\\s*>",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)

/** Inline javadoc tags: `{@link}`, `{@code}`, `{@literal}` — markup, and their braces prove it. */
private val INLINE_TAG = Regex("\\{@\\w+[^}]*\\}")

/**
 * A closed vocabulary, not `<\\w+>`.
 *
 * The open form eats generics and placeholders. Over the corpus, angle-bracket tokens outside
 * this list are `<T>`, `<String>`, `<key>`, `<name>` — 2,777 occurrences that are prose, against
 * the 1.2 million that are markup. The last four are not HTML at all; they are the admonition
 * tags Android and Kotlin documentation write.
 */
private const val TAG_NAMES =
    "a|abbr|b|big|blockquote|br|caption|center|cite|code|dd|div|dl|dt|em|font|h1|h2|h3|h4|h5|h6|" +
        "hr|i|img|li|nobr|ol|p|pre|q|s|samp|small|span|strong|sub|sup|table|tbody|td|tfoot|th|" +
        "thead|tr|tt|u|ul|var|note|important|caution|warning"

private val HTML_TAG = Regex("</?(?:$TAG_NAMES)\\b[^>]*>", RegexOption.IGNORE_CASE)

/**
 * A markdown inline link, reduced to its text.
 *
 * The address never survives; the text usually should. Of 18,194 links in the corpus the two
 * commonest texts are the boilerplate footers "Report a problem" (3,829) and "MDN Reference"
 * (1,694), but the tail is ordinary noun phrases — "logs retention period", "monitoring filter"
 * — that a query would match. A KDoc `[Symbol]` reference carries no address and is left alone:
 * that text is a symbol name, which is the most matchable thing in the comment.
 */
private val LINK = Regex("!?\\[([^\\]\\n]*)\\]\\([^)\\s]*\\)")

/** An address in prose, and the autolink form that wraps one. Nobody queries a URL. */
private val URL = Regex("<(?:https?|ftp)://[^>\\s]*>|\\b(?:https?|ftp)://\\S*")

/**
 * The gap a removed tag leaves in front of punctuation: `<em>top</em>.` becomes `top .` unless
 * this closes it. The tokenizer would not care; the summariser reads this text and does.
 */
private val LOOSE_PUNCTUATION = Regex(" +(?=[,.;:!?)\\]])")

private val TAG_ONLY = Regex("^\\s*@\\w+")
private val WHITESPACE = Regex("\\s+")
private val ENTITY = Regex("&(#\\d{1,5}|#[xX][0-9a-fA-F]{1,4}|[a-zA-Z][a-zA-Z0-9]{1,10});")

/**
 * The named entities that actually appear, plus the punctuation ones that read badly left raw.
 *
 * `nbsp` becomes an ordinary space rather than U+00A0, because `\s` in this regex flavour does
 * not match the non-breaking one and it would survive into the key as a word boundary that is
 * not a word boundary.
 */
private val NAMED = mapOf(
    "lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "hellip" to "...", "mdash" to "-", "ndash" to "-", "lsquo" to "'", "rsquo" to "'",
    "ldquo" to "\"", "rdquo" to "\"", "copy" to "(c)", "reg" to "(r)", "trade" to "(tm)",
    "deg" to " degrees", "times" to "x", "middot" to ".", "bull" to ".",
    "le" to "<=", "ge" to ">=", "ne" to "!=",
)

/**
 * An entity's character, or the entity untouched when it is not one this knows.
 *
 * Leaving an unrecognised entity alone is the conservative half: a wrong expansion puts a word
 * in the key that the comment never said, and there is no later pass that would notice.
 */
private fun decode(name: String): String {
    val numeric = when {
        name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)
        name.startsWith("#") -> name.drop(1).toIntOrNull()
        else -> return NAMED[name.lowercase()] ?: "&$name;"
    }
    return if (numeric != null && numeric in 1..0x10FFFF) String(Character.toChars(numeric)) else ""
}

/**
 * Below this a comment is a label rather than a description — "The name." — and it
 * retrieves no better than the symbol it sits on, which the store already has.
 */
private const val MINIMUM_WORDS = 4
