package org.dependencyskills.codex.harvester

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.TraceSignatureVisitor

/**
 * Renders a compiled declaration back into something a developer can read and call.
 *
 * **The signature is the whole deliverable here.** A degraded entry has no prose — it is a symbol
 * and this string — so a caller needs it character-for-character correct in order to invoke what
 * it names. That is also why it can never be paraphrased: RAD-0062 established that rewriting,
 * which is the control used on prose everywhere else in this codex, is simply unavailable for a
 * signature. It is stored verbatim or the entry is not stored.
 *
 * **Only names and types.** No parameter names, no annotation values, no constant values, no
 * debug attributes. Parameter names are not in a class file unless it was compiled with
 * `-parameters`, and the archive is read with debug information skipped, so there is nothing here
 * that could carry a sentence from the library's author beyond the identifiers themselves — which
 * is a channel this project has measured and screens separately.
 */
internal object BytecodeSignatures {

    /** `public interface Codec<T> extends Closeable`, generics included when the class carries them. */
    fun ofClass(node: ClassNode): String {
        val kind = when {
            node.access and Opcodes.ACC_ANNOTATION != 0 -> "@interface"
            node.access and Opcodes.ACC_INTERFACE != 0 -> "interface"
            node.access and Opcodes.ACC_ENUM != 0 -> "enum"
            node.access and Opcodes.ACC_RECORD != 0 -> "record"
            else -> "class"
        }
        val name = simpleName(node)
        val generic = node.signature?.let { signature ->
            // The class access is passed so the visitor knows whether `extends` or `implements`
            // is the right word for each bound it meets.
            runCatching {
                TraceSignatureVisitor(node.access).also { SignatureReader(signature).accept(it) }.declaration
            }.getOrNull()
        }
        if (generic != null) return "${modifiers(node.access)}$kind $name$generic".trim()

        // No signature attribute: not generic, so the descriptor names are the whole truth.
        val parts = StringBuilder("${modifiers(node.access)}$kind $name")
        node.superName
            ?.takeIf { it != "java/lang/Object" && node.access and Opcodes.ACC_INTERFACE == 0 }
            ?.let { parts.append(" extends ").append(dotted(it)) }
        node.interfaces.orEmpty().takeIf { it.isNotEmpty() }?.let {
            parts.append(if (node.access and Opcodes.ACC_INTERFACE != 0) " extends " else " implements ")
            parts.append(it.joinToString(", ", transform = ::dotted))
        }
        return parts.toString().trim()
    }

    /**
     * `public Instant parse(String)` — types only, deliberately.
     *
     * A constructor is rendered under the class's simple name rather than as `<init>`, because
     * that is what a caller writes and what the source symbol says.
     */
    fun ofMethod(node: ClassNode, method: MethodNode): String {
        val name = if (method.name == "<init>") simpleName(node) else method.name
        val generic = method.signature?.let { signature ->
            runCatching {
                val visitor = TraceSignatureVisitor(method.access)
                SignatureReader(signature).accept(visitor)
                val returns = visitor.returnType?.takeIf { method.name != "<init>" }
                val throws = visitor.exceptions?.takeIf { it.isNotBlank() }.orEmpty()
                buildString {
                    append(modifiers(method.access))
                    if (returns != null) append(returns).append(' ')
                    append(name).append(visitor.declaration).append(throws)
                }
            }.getOrNull()
        }
        if (generic != null) return generic.trim()

        val arguments = Type.getArgumentTypes(method.desc).joinToString(", ") { it.className }
        val returns = if (method.name == "<init>") "" else Type.getReturnType(method.desc).className + " "
        val throws = method.exceptions.orEmpty()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = " throws ", transform = ::dotted)
            .orEmpty()
        return "${modifiers(method.access)}$returns$name($arguments)$throws".trim()
    }

    /**
     * `public static final String NAME`.
     *
     * **The constant's value is never rendered**, even though a class file carries it for a static
     * final field. That is attacker-controlled text sitting one string away from a signature, and
     * a degraded entry's signature is returned verbatim to a model.
     */
    fun ofField(field: FieldNode): String {
        val type = field.signature?.let { signature ->
            runCatching {
                TraceSignatureVisitor(field.access).also { SignatureReader(signature).acceptType(it) }.declaration
            }.getOrNull()
        } ?: Type.getType(field.desc).className
        return "${modifiers(field.access)}$type ${field.name}".trim()
    }

    /** Source order, and only the modifiers a caller needs in order to use the thing. */
    private fun modifiers(access: Int): String = buildString {
        if (access and Opcodes.ACC_PUBLIC != 0) append("public ")
        if (access and Opcodes.ACC_PROTECTED != 0) append("protected ")
        if (access and Opcodes.ACC_STATIC != 0) append("static ")
        if (access and Opcodes.ACC_FINAL != 0) append("final ")
        if (access and Opcodes.ACC_ABSTRACT != 0 && access and Opcodes.ACC_INTERFACE == 0) append("abstract ")
    }

    private fun dotted(internalName: String) = internalName.replace('/', '.')

    /**
     * The name as written in source.
     *
     * From the `InnerClasses` attribute when the class is nested, and never by cutting at the last
     * `$`: gson's `$Gson$Types` is a top-level class whose simple name really does contain two of
     * them, and cutting would render it as `Types` — a name that does not exist and cannot be
     * used to call anything.
     */
    private fun simpleName(node: ClassNode): String =
        node.innerClasses.orEmpty().firstOrNull { it.name == node.name }?.innerName
            ?: node.name.substringAfterLast('/')
}
