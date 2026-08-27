package org.dependencyskills.codex.harvester

import org.treesitter.TSLanguage
import org.treesitter.TreeSitterJava
import org.treesitter.TreeSitterKotlin

/**
 * A language this harvester can read, and the grammar vocabulary it reads it with.
 *
 * [docFormat] is carried per language rather than derived from it, because the two are not the
 * same fact and the store keeps them apart for a reason. Swift documents with `///` line
 * comments, so a run of consecutive lines is one doc comment and a block-comment extractor
 * pointed at it finds nothing — and reports that nothing as an absence. Recording the format
 * the entry was actually read under is what lets a later reader tell the two apart.
 */
internal enum class SourceLanguage(
    val lang: String,
    val docFormat: String,
    val extension: String,
    /** The grammar's node type for a documentation block comment. */
    val docCommentType: String,
    /**
     * Every node type that is a comment, documentation or not. Needed because a comment sitting
     * among a declaration's modifiers would otherwise land in the middle of its signature.
     */
    val commentTypes: Set<String>,
    /** Node types worth an entry: something a caller could reach and name. */
    val declarations: Set<String>,
    /** Declarations that also qualify what is nested inside them. */
    val containers: Set<String>,
    /** The node holding the file's package, and the node inside it holding the dotted name. */
    val packageNode: String,
    val packageNameNodes: Set<String>,
) {
    Kotlin(
        lang = "kotlin",
        docFormat = "kdoc",
        extension = ".kt",
        docCommentType = "multiline_comment",
        commentTypes = setOf("multiline_comment", "line_comment", "comment"),
        declarations = setOf(
            "function_declaration", "class_declaration", "object_declaration",
            "property_declaration", "type_alias", "enum_entry",
        ),
        containers = setOf("class_declaration", "object_declaration"),
        packageNode = "package_header",
        packageNameNodes = setOf("identifier"),
    ),
    Java(
        lang = "java",
        docFormat = "javadoc",
        extension = ".java",
        docCommentType = "block_comment",
        commentTypes = setOf("block_comment", "line_comment", "comment"),
        declarations = setOf(
            "class_declaration", "interface_declaration", "enum_declaration",
            "record_declaration", "annotation_type_declaration",
            "method_declaration", "constructor_declaration", "field_declaration",
        ),
        containers = setOf(
            "class_declaration", "interface_declaration", "enum_declaration",
            "record_declaration", "annotation_type_declaration",
        ),
        packageNode = "package_declaration",
        packageNameNodes = setOf("scoped_identifier", "identifier"),
    );

    /** A fresh grammar handle. Not shared: a `TSParser` is not safe across threads. */
    fun grammar(): TSLanguage = when (this) {
        Kotlin -> TreeSitterKotlin()
        Java -> TreeSitterJava()
    }

    companion object {
        /** The language of a path inside an archive, or null if this harvester does not read it. */
        fun of(path: String): SourceLanguage? = entries.firstOrNull { path.endsWith(it.extension) }
    }
}
