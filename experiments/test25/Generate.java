// test25 — a batch generator over the JVM runtime, so the summariser's own harness can drive it.
//
// The summariser has exactly one seam: `run_model(prompt) -> text`. Everything that makes it a
// measured component - the prompt, the four properties, the verifier, adjudication, the
// degradation accounting - sits outside that function. So the honest way to compare runtimes and
// models is to replace only this, and leave the rest untouched.
//
// Batch rather than one call per entry because loading a model costs far more than generating
// from it, and 220 entries would otherwise pay that 220 times.
//
// Protocol: prompts arrive on stdin, one per record, terminated by a line "<<<END>>>".
// Completions leave on stdout, terminated the same way. Everything else goes to stderr.

import de.kherud.llama.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Generate {
    private static final String END = "<<<END>>>";

    public static void main(String[] args) throws IOException {
        String model = args[0];
        int predict = args.length > 1 ? Integer.parseInt(args[1]) : 200;

        long t0 = System.currentTimeMillis();
        ModelParameters params = new ModelParameters()
                .setModel(model)
                .setCtxSize(4096)
                .setGpuLayers(0);          // CPU: the question is portability, not peak speed
        try (LlamaModel llama = new LlamaModel(params)) {
            System.err.printf("[loaded %s in %d ms]%n", model, System.currentTimeMillis() - t0);
            // The native loader prints its extraction path to STDOUT, which would otherwise
            // arrive as the first record's completion. The driver reads until this marker, so
            // anything the library said before it is discarded rather than scored.
            System.out.println("<<<READY>>>");
            System.out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

            StringBuilder prompt = new StringBuilder();
            String line;
            int n = 0;
            while ((line = in.readLine()) != null) {
                if (!line.equals(END)) {
                    prompt.append(line).append('\n');
                    continue;
                }
                long t1 = System.nanoTime();
                // Apply the model's own chat template. Without it an instruction-tuned model is
                // not being instructed - it continues the text, wanders into an imagined
                // conversation and emits "Human:" turns, and the verifier then rejects the
                // result. That reads as a model too small for the task and is nothing of the kind.
                //
                // The binding templates in two steps: build the messages, ask for the rendered
                // string, then generate from that.
                InferenceParameters templating = new InferenceParameters("")
                        .setMessages(null, java.util.List.of(new Pair<>("user", prompt.toString())));
                String rendered = llama.applyTemplate(templating);
                InferenceParameters infer = new InferenceParameters(rendered)
                        .setTemperature(0.0f)
                        .setNPredict(predict);
                StringBuilder generated = new StringBuilder();
                for (LlamaOutput token : llama.generate(infer)) generated.append(token);
                long micros = (System.nanoTime() - t1) / 1000;
                out.println(generated.toString().replace("\n", "\\n"));
                out.println(END);
                System.err.printf("[%d: %d us]%n", ++n, micros);
                prompt.setLength(0);
            }
        }
        // llama.cpp leaves non-daemon threads behind, so the JVM will not exit on its own once
        // stdin closes. The driver would sit waiting for a process that has finished its work.
        System.exit(0);
    }
}
