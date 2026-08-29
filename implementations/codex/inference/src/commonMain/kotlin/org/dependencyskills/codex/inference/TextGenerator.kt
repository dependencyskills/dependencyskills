package org.dependencyskills.codex.inference

/**
 * A local text generator, loaded once and used many times.
 *
 * **Loaded once is the point.** The summariser is a batch job over a whole dependency graph — one
 * resolved graph is 14,899 documented declarations — and a generator that reloads its model per
 * call pays that cost every time. Measured, the difference is 25 minutes against 21 hours.
 *
 * Deliberately tiny. Load, generate, close. No sampling parameters, no streaming, no tools, no
 * network: a component that cannot reach anything cannot be made to exfiltrate, whatever its
 * input says, and that is a property of what this exposes rather than of how it is called.
 */
interface TextGenerator : AutoCloseable {

    /**
     * Renders [prompt] through the model's own chat template and generates greedily until the
     * model stops or [maxTokens] is reached.
     *
     * The template is not a convenience. An instruction-tuned model handed a bare string is not
     * being instructed — it continues the text, wanders into an invented conversation and emits
     * turns of its own, which a downstream verifier then rejects. That reads as a model too small
     * for the task and is nothing of the kind.
     */
    fun generate(prompt: String, maxTokens: Int = 200): String
}

/** Opening a generator failed. Callers degrade rather than propagate: the index is an aid. */
class GeneratorUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Opens a generator over a model file.
 *
 * [contextTokens] bounds prompt plus generation together. [gpuLayers] is how many layers to place
 * on an accelerator; zero is CPU, which is the portable answer and the one the throughput numbers
 * above were measured on.
 */
expect fun openGenerator(
    modelPath: String,
    contextTokens: Int = 4096,
    gpuLayers: Int = 0,
): TextGenerator
