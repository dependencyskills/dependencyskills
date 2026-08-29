// test25 phase A — can a JVM process generate from a local model, in-process, with no server?
//
// The summariser runs today by shelling out to `mlx-lm`, and MLX is Apple-only, so the mechanism
// behind every number that component rests on cannot ship. RAD-0051 sets out the probe; this is
// its first step, and it is deliberately the cheapest one: a 230 MB model already on disk, and the
// only question is whether the library loads and generates at all.
//
// IN-PROCESS ON PURPOSE. A local HTTP server would be easier and would cost the summariser's first
// property - no network, no tools - by turning a structural fact into a configuration promise.
// See RAD-0051.

import de.kherud.llama.*;
import de.kherud.llama.args.MiroStat;

public class Probe {
    public static void main(String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            System.err.println("usage: Probe <model.gguf> [prompt]");
            System.exit(2);
        }
        String model = args[0];
        String prompt = args.length > 1 && !args[1].isEmpty() ? args[1]
                : "Rewrite this documentation as one factual sentence of at most 40 words, "
                + "describing what it does. Do not address the reader.\n\n"
                + "Documentation: Returns the number of bytes remaining in this buffer. "
                + "The value is never negative and decreases as the buffer is consumed.\n\nSentence:";

        System.out.println("model: " + model);
        long t0 = System.currentTimeMillis();
        ModelParameters params = new ModelParameters()
                .setModel(model)
                .setCtxSize(2048)
                .setGpuLayers(0);            // CPU only: the probe is about portability, not speed

        try (LlamaModel llama = new LlamaModel(params)) {
            System.out.printf("loaded in %d ms%n", System.currentTimeMillis() - t0);
            InferenceParameters infer = new InferenceParameters(prompt)
                    .setTemperature(0.0f)
                    .setNPredict(120);
            long t1 = System.currentTimeMillis();
            StringBuilder out = new StringBuilder();
            for (LlamaOutput token : llama.generate(infer)) out.append(token);
            long ms = System.currentTimeMillis() - t1;
            System.out.printf("%ngenerated in %d ms%n---%n%s%n---%n", ms, out.toString().trim());
            System.out.println("OK");
        }
    }
}
