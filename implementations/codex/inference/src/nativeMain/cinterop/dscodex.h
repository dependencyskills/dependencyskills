// The flat ABI. Shared by the JVM binding (FFM, by symbol name) and the Kotlin/Native targets
// (cinterop, from this header), so both see one contract rather than two descriptions of one.
#ifndef DSCODEX_H
#define DSCODEX_H

#ifdef __cplusplus
extern "C" {
#endif

/** Loads a model. Returns NULL on failure; the caller degrades rather than crashing. */
void *dsc_load(const char *path, int n_ctx, int n_gpu_layers);

/** Renders one user message through the model's own chat template. Bytes written, or negative. */
int dsc_apply_template(void *handle, const char *user, char *out, int out_len);

/** Generates greedily until end-of-generation or max_tokens. Bytes written, or negative. */
int dsc_generate(void *handle, const char *prompt, char *out, int out_len, int max_tokens);

/**
 * Loads an encoder. `pooling` is a `llama_pooling_type` and is REQUIRED: passing UNSPECIFIED (-1)
 * fails rather than falling back to the model's own metadata.
 *
 * The fallback is the trap. A GGUF carries whatever pooling the conversion inferred from the
 * model card, and this project measured the documented pooling to be the wrong one for its use
 * (RAD-0048). A default would therefore be wrong silently: the vectors come out the right shape
 * and the wrong basis, and only re-running an eval would show it.
 */
void *dsc_encoder_load(const char *path, int pooling);

/** The pooling actually in effect. Compare it against what the model was published with. */
int dsc_encoder_pooling(void *handle);

/** The width of a vector this encoder produces. */
int dsc_encoder_dim(void *handle);

/**
 * Embeds one string. Writes at most `out_len` floats and returns the model's full width, so a
 * caller can tell a truncated copy from a complete one. Negative on failure.
 *
 * The vector is the pooled output, NOT normalised. Normalisation is the caller's, because the
 * index has an opinion about it and this does not.
 */
int dsc_embed(void *handle, const char *text, float *out, int out_len);

void dsc_free(void *handle);

#ifdef __cplusplus
}
#endif
#endif
