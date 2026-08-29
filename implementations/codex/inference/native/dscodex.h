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

void dsc_free(void *handle);

#ifdef __cplusplus
}
#endif
#endif
