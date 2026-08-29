// A flat ABI over llama.cpp, so nothing but pointers, ints and bytes crosses into the JVM.
//
// WHY A SHIM AND NOT A DIRECT BINDING. llama.cpp's entry points take parameter structs BY VALUE,
// and those structs change between releases - `llama_model_params` gained five fields between the
// build `de.kherud:llama` pins and today. Describing them in Java means a hand-maintained memory
// layout per llama.cpp version, where a mismatch is not a compile error but silent memory
// corruption. Here a changed struct is a compile failure in a hundred lines of C that we own.
//
// It is also why this binds `llama.h` and not `server.hpp`: the published JVM binding embeds
// llama.cpp's example-server internals, which is why it has been frozen on one build since
// March 2025.

#include "dscodex.h"
#include "llama.h"
#include <stdlib.h>
#include <string.h>

struct dsc_session {
    struct llama_model   *model;
    struct llama_context *ctx;
    struct llama_sampler *sampler;
    const struct llama_vocab *vocab;
};

static int dsc_initialised = 0;

// Loads a model. Returns NULL on failure; the caller degrades rather than crashing.
void *dsc_load(const char *path, int n_ctx, int n_gpu_layers) {
    if (!dsc_initialised) { llama_backend_init(); dsc_initialised = 1; }

    struct llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = n_gpu_layers;

    struct llama_model *model = llama_model_load_from_file(path, mp);
    if (!model) return NULL;

    struct llama_context_params cp = llama_context_default_params();
    cp.n_ctx   = (unsigned) n_ctx;
    cp.n_batch = (unsigned) n_ctx;

    struct llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); return NULL; }

    // Greedy only. The summariser runs at temperature zero because a component whose output is
    // verified and whose behaviour was measured on one model has no use for sampling variety.
    struct llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    struct dsc_session *s = malloc(sizeof(struct dsc_session));
    s->model = model; s->ctx = ctx; s->sampler = sampler;
    s->vocab = llama_model_get_vocab(model);
    return s;
}

// Renders one user message through the model's own chat template. Returns the length written, or
// a negative value. An instruction-tuned model handed a bare string is not being instructed - it
// continues the text - so this is not a convenience.
int dsc_apply_template(void *handle, const char *user, char *out, int out_len) {
    struct dsc_session *s = handle;
    const char *tmpl = llama_model_chat_template(s->model, NULL);
    if (!tmpl) return -1;
    struct llama_chat_message msg = { "user", user };
    return llama_chat_apply_template(tmpl, &msg, 1, true, out, out_len);
}

// Generates greedily until end-of-generation or max_tokens. Returns bytes written, or negative.
int dsc_generate(void *handle, const char *prompt, char *out, int out_len, int max_tokens) {
    struct dsc_session *s = handle;

    int n_prompt = -llama_tokenize(s->vocab, prompt, (int) strlen(prompt), NULL, 0, true, true);
    if (n_prompt <= 0) return -1;
    llama_token *tokens = malloc(sizeof(llama_token) * (size_t) n_prompt);
    if (llama_tokenize(s->vocab, prompt, (int) strlen(prompt), tokens, n_prompt, true, true) < 0) {
        free(tokens);
        return -2;
    }

    llama_memory_clear(llama_get_memory(s->ctx), true);

    struct llama_batch batch = llama_batch_get_one(tokens, n_prompt);
    int written = 0;
    for (int produced = 0; produced < max_tokens; produced++) {
        if (llama_decode(s->ctx, batch)) { free(tokens); return -3; }

        llama_token next = llama_sampler_sample(s->sampler, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, next)) break;

        char piece[256];
        int n = llama_token_to_piece(s->vocab, next, piece, sizeof(piece), 0, true);
        if (n < 0) break;
        if (written + n >= out_len) break;          // truncate rather than overrun
        memcpy(out + written, piece, (size_t) n);
        written += n;

        batch = llama_batch_get_one(&next, 1);
    }
    out[written] = '\0';
    free(tokens);
    return written;
}

void dsc_free(void *handle) {
    struct dsc_session *s = handle;
    if (!s) return;
    llama_sampler_free(s->sampler);
    llama_free(s->ctx);
    llama_model_free(s->model);
    free(s);
}
