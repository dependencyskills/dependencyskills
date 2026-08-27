package org.dependencyskills.codex.harvester

import org.treesitter.TSInputEncoding
import org.treesitter.TSNode
import org.treesitter.TSParser

/** One declaration and the documentation bound to it, before the store gives it an identity. */
internal data class Extracted(val symbol: String, val signature: String, val doc: String)

/** What one source file yielded, discards included so the caller can count them. */
internal data class FileYield(
    val extracted: List<Extracted>,
    val declarations: Int,
    val unclaimedDocs: Int,
    val tooShort: Int,
    val hadParseError: Boolean,
)

/**
 * Reads one source file. Holds a parser, so it is not safe across threads and is closed by the
 * caller when the archive is done with.
 */
internal class SourceExtractor(private val language: SourceLanguage) : AutoCloseable {

    // `apply` would resolve `language` to TSParser's own getLanguage(), not to this field.
    private val parser = TSParser().also { it.setLanguage(language.grammar()) }

    override fun close() = parser.close()

    fun read(source: String): FileYield {
        // Byte offsets from tree-sitter index the UTF-8 encoding, so every slice below is taken
        // from these bytes rather than from the string. Working in chars instead silently shifts
        // every offset in a file containing one non-ASCII character.
        val bytes = source.toByteArray(Charsets.UTF_8)
        val tree = parser.parseStringEncoding(null, source, TSInputEncoding.TSInputEncodingUTF8)
        return tree.use {
            val root = it.rootNode
            val comments = comments(root)
            val docs = docComments(comments, bytes)
            val declarations = declarations(root, bytes)
            bind(declarations, docs, comments, bytes, root.hasError())
        }
    }

    // -- walking ---------------------------------------------------------------------------

    private class Decl(val node: TSNode, val symbol: String)

    /**
     * Every named declaration, qualified by its package and its enclosing types.
     *
     * The qualifier is not decoration. Entries are content-addressed on `(symbol, signature,
     * doc)`, so two libraries shipping the same `run` collapse into one entry only if they really
     * are the same declaration — and a bare simple name says they are when they are not.
     */
    private fun declarations(root: TSNode, bytes: ByteArray): List<Decl> {
        val out = ArrayList<Decl>()
        // Explicit stack rather than recursion: a tree-sitter tree is as deep as the source
        // nests, and a long concatenation chain in a published file is deeper than it looks.
        val stack = ArrayDeque<Pair<TSNode, String>>()
        stack.addLast(root to packageOf(root, bytes))
        while (stack.isNotEmpty()) {
            val (node, prefix) = stack.removeLast()
            var childPrefix = prefix
            if (node.type in language.declarations) {
                val name = nameOf(node, bytes)
                if (name != null) {
                    val qualified = if (prefix.isEmpty()) name else "$prefix.$name"
                    out.add(Decl(node, qualified))
                    if (node.type in language.containers) childPrefix = qualified
                }
            }
            for (i in node.childCount - 1 downTo 0) stack.addLast(node.getChild(i) to childPrefix)
        }
        out.sortBy { it.node.startByte }
        return out
    }

    private fun packageOf(root: TSNode, bytes: ByteArray): String {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child.type != language.packageNode) continue
            for (j in 0 until child.childCount) {
                val name = child.getChild(j)
                if (name.type in language.packageNameNodes) return text(bytes, name)
            }
        }
        return ""
    }

    /**
     * A declaration's own name.
     *
     * Both grammars label it with a `name` field on most declarations, but not on all of them:
     * a Kotlin property hangs its name off a `variable_declaration` and a Java field off a
     * `variable_declarator`, one level down. Taking only direct children misses both, and a
     * harvester that quietly indexes no properties looks exactly like a library that declares
     * none.
     */
    private fun nameOf(node: TSNode, bytes: ByteArray): String? {
        val field = runCatching { node.getChildByFieldName("name") }.getOrNull()
        if (field != null && !field.isNull) return text(bytes, field)
        named(node, bytes)?.let { return it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child.type == "variable_declaration" || child.type == "variable_declarator") {
                named(child, bytes)?.let { return it }
            }
        }
        return null
    }

    private fun named(node: TSNode, bytes: ByteArray): String? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child.type in NAME_NODES) return text(bytes, child)
        }
        return null
    }

    /**
     * The declaration's own text, up to where its implementation starts: enough to call it,
     * without the body.
     */
    private fun signatureOf(node: TSNode, comments: List<Comment>, bytes: ByteArray): String {
        var end = node.endByte
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child.type in BODY_NODES || child.type.endsWith("_body")) {
                end = child.startByte
                break
            }
        }
        return withoutComments(bytes, node.startByte, end, comments)
            .replace(WHITESPACE, " ")
            .trim()
            .trimEnd(';', ',')
            .trim()
            .take(SIGNATURE_LIMIT)
    }

    // -- documentation ---------------------------------------------------------------------

    private class Doc(val start: Int, val end: Int, val body: String?)

    private class Comment(val type: String, val start: Int, val end: Int)

    /**
     * The declaration's text with any comment inside it removed.
     *
     * A published library comments its own modifiers — a `// see KT-41082` between two
     * annotations is ordinary — and taking the raw bytes puts that note in the middle of the
     * signature the store shows.
     */
    private fun withoutComments(
        bytes: ByteArray,
        from: Int,
        to: Int,
        comments: List<Comment>,
    ): String {
        val out = StringBuilder()
        var at = from
        for (comment in comments) {
            if (comment.end <= at) continue
            if (comment.start >= to) break
            if (comment.start > at) out.append(String(bytes, at, comment.start - at, Charsets.UTF_8))
            at = maxOf(at, comment.end)
        }
        if (at < to) out.append(String(bytes, at, to - at, Charsets.UTF_8))
        return out.toString()
    }

    /** Every comment in the file, documentation or not, in source order. */
    private fun comments(root: TSNode): List<Comment> {
        val out = ArrayList<Comment>()
        val stack = ArrayDeque<TSNode>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.type in language.commentTypes) {
                out.add(Comment(node.type, node.startByte, node.endByte))
            }
            for (i in node.childCount - 1 downTo 0) stack.addLast(node.getChild(i))
        }
        out.sortBy { it.start }
        return out
    }

    /**
     * Every documentation block comment in the file, in source order.
     *
     * They are collected from the whole tree rather than from a declaration's siblings, because
     * they are extra nodes and the grammar puts them wherever they fall: a KDoc between the last
     * import and the first function is parsed as part of the import list, not as a sibling of
     * the function it documents.
     */
    private fun docComments(comments: List<Comment>, bytes: ByteArray): List<Doc> =
        comments.filter { it.type == language.docCommentType }
            .mapNotNull { comment ->
                val raw = String(bytes, comment.start, comment.end - comment.start, Charsets.UTF_8)
                if (!raw.startsWith("/**") || raw == "/**/") null
                else Doc(comment.start, comment.end, cleanDoc(raw))
            }
            .sortedBy { it.end }

    /**
     * Binds each doc comment to the declaration it belongs to.
     *
     * The rule is that only whitespace may separate them. That is stricter than the proximity
     * rule the corpus experiment used — nearest comment ending within 400 bytes — and it is
     * stricter on purpose: a file licence header sits within 400 bytes of the first declaration
     * below it and binds to a symbol it does not describe, which happened 670 times in 681,000
     * rows. A header sits above the package statement, so under this rule the gap contains
     * `package`, and it binds to nothing.
     */
    private fun bind(
        declarations: List<Decl>,
        docs: List<Doc>,
        comments: List<Comment>,
        bytes: ByteArray,
        hadParseError: Boolean,
    ): FileYield {
        val claimed = BooleanArray(docs.size)
        var tooShort = 0
        val extracted = ArrayList<Extracted>(declarations.size)
        for (decl in declarations) {
            val i = lastEndingAtOrBefore(docs, decl.node.startByte)
            if (i < 0 || claimed[i]) continue
            val doc = docs[i]
            if (!onlyWhitespace(bytes, doc.end, decl.node.startByte)) continue
            claimed[i] = true
            if (doc.body == null) {
                tooShort++
                continue
            }
            extracted.add(Extracted(decl.symbol, signatureOf(decl.node, comments, bytes), doc.body))
        }
        return FileYield(
            extracted = extracted,
            declarations = declarations.size,
            unclaimedDocs = claimed.count { !it },
            tooShort = tooShort,
            hadParseError = hadParseError,
        )
    }

    /** Index of the last doc comment ending at or before [offset]; -1 if there is none. */
    private fun lastEndingAtOrBefore(docs: List<Doc>, offset: Int): Int {
        var lo = 0
        var hi = docs.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (docs[mid].end <= offset) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    private fun onlyWhitespace(bytes: ByteArray, from: Int, until: Int): Boolean {
        for (i in from until until) if (bytes[i] > 0x20 || bytes[i] < 0) return false
        return true
    }

    private fun text(bytes: ByteArray, node: TSNode) =
        String(bytes, node.startByte, node.endByte - node.startByte, Charsets.UTF_8)

    private companion object {
        val NAME_NODES = setOf("simple_identifier", "identifier", "type_identifier")

        /** Where a declaration stops being a signature and starts being an implementation. */
        val BODY_NODES = setOf("block", "=", "{", ";", "constructor_delegation_call")

        val WHITESPACE = Regex("\\s+")
        const val SIGNATURE_LIMIT = 400
    }
}
