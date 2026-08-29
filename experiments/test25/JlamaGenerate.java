// test25 — the same batch protocol as Generate.java, over JLama instead of llama.cpp.
//
// Same seam, same protocol, same driver. The two runtimes differ in engine and model format and
// in nothing else the summariser can see, which is what makes the comparison a comparison.
//
// JLama reads safetensors rather than GGUF, so it cannot use the models already on this machine
// and fetches from Hugging Face on first use.

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class JlamaGenerate {
    private static final String END = "<<<END>>>";

    public static void main(String[] args) throws IOException {
        String name = args[0];                                  // e.g. Qwen/Qwen2.5-0.5B-Instruct
        int predict = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        File cache = new File(System.getProperty("user.home"), ".jlama/models");
        cache.mkdirs();

        long t0 = System.currentTimeMillis();
        File local = SafeTensorSupport.maybeDownloadModel(cache.getAbsolutePath(), name);
        System.err.printf("[fetched %s in %d ms]%n", name, System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();
        AbstractModel model = ModelSupport.loadModel(local, DType.F32, DType.I8);
        System.err.printf("[loaded in %d ms]%n", System.currentTimeMillis() - t1);
        System.out.println("<<<READY>>>");
        System.out.flush();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        StringBuilder prompt = new StringBuilder();
        String line;
        int n = 0;
        while ((line = in.readLine()) != null) {
            if (!line.equals(END)) {
                prompt.append(line).append('\n');
                continue;
            }
            // Apply the model's own chat template. Without it an instruction-tuned model is not
            // being instructed - it continues the text, wanders into an imagined conversation and
            // emits "Human:" turns, and the verifier then rejects the result. That reads as a
            // model too small for the task and is nothing of the kind.
            PromptContext ctx = model.promptSupport()
                    .map(s -> s.builder().addUserMessage(prompt.toString()).build())
                    .orElseGet(() -> PromptContext.of(prompt.toString()));
            // JLama's `ntokens` is the TOTAL budget, prompt included - not the generation limit.
            // Passing a generation count caps the whole thing below the prompt's own length and
            // fails as "Prompt exceeds max tokens", which reads like a context-window problem and
            // is not one.
            int budget = model.encodePrompt(ctx).length + predict;
            Generator.Response r = model.generate(
                    UUID.randomUUID(), ctx, 0.0f, budget, (t, f) -> {});
            out.println(r.responseText.replace("\n", "\\n"));
            out.println(END);
            System.err.printf("[%d: %d ms]%n", ++n, r.generateTimeMs);
            prompt.setLength(0);
        }
        System.exit(0);
    }
}
