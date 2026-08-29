// test26 — can the in-process llama.cpp library replace DJL/ONNX as the encoder?
//
// RAD-0047 established that a JVM runtime reproduces the embeddings every retrieval number in
// this project rests on, using DJL over ONNX Runtime. Since then the project has built its own
// llama.cpp binding for the generative side (#7). llama.cpp also embeds, which raises the
// question of whether the ONNX runtime is still needed at all.
//
// The narrow risk is the TOKENIZER. llama.cpp implements WordPiece itself from the GGUF vocab
// rather than reading `tokenizer.json`, and a divergence there is silent: the vector comes out
// the right shape and the wrong basis, and only re-running an eval would show it.
//
// Reference here is the SHIPPED path - `BAAI/bge-small-en-v1.5` ONNX, mean-pooled, the digests
// pinned in implementations/codex/encoder/build.gradle.kts - because the question is whether
// llama.cpp can replace that, not whether it can replace MLX.
//
//   java --enable-native-access=ALL-UNNAMED -cp "../test23/lib/*" EncoderProbe.java [N] [--recall]

import ai.djl.huggingface.tokenizers.*;
import ai.djl.ndarray.*;
import ai.djl.ndarray.types.*;
import ai.djl.repository.zoo.*;
import ai.djl.inference.*;
import ai.djl.translate.*;
import com.google.gson.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class EncoderProbe {
    static final Path HERE = Paths.get("").toAbsolutePath();
    static final int MAXCHARS = 900;
    static final int DIM = 384;
    static final Pattern COMMENT = Pattern.compile("^\\s*/\\*\\*|\\*/\\s*$|^\\s*\\*\\s?", Pattern.MULTILINE);

    // llama_pooling_type
    static final int NONE = 0, MEAN = 1, CLS = 2;

    /** identical to test5/embed_corpus.py key_text() and test23's */
    static String keyText(JsonObject e) {
        String doc = COMMENT.matcher(e.get("doc").getAsString()).replaceAll("");
        doc = String.join(" ", doc.trim().split("\\s+"));
        if (doc.length() > MAXCHARS) doc = doc.substring(0, MAXCHARS);
        String sym = e.get("symbol").getAsString();
        return sym.substring(sym.lastIndexOf('.') + 1) + ". " + doc;
    }

    /** indices of the corpus, best first */
    static Integer[] order(float[] q, float[][] base) {
        Integer[] idx = new Integer[base.length];
        double[] sc = new double[base.length];
        for (int i = 0; i < base.length; i++) { idx[i] = i; sc[i] = cos(q, base[i]); }
        Arrays.sort(idx, (a, b) -> Double.compare(sc[b], sc[a]));
        return idx;
    }
    static int overlap(Integer[] a, Integer[] b, int k) {
        Set<Integer> s = new HashSet<>(Arrays.asList(a).subList(0, k));
        int n = 0;
        for (int i = 0; i < k; i++) if (s.contains(b[i])) n++;
        return n;
    }
    static int rankOf(Integer[] o, int gold) {
        for (int i = 0; i < o.length; i++) if (o[i] == gold) return i + 1;
        return -1;
    }

    static double cos(float[] a, float[] b) {
        double d = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { d += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        return d / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ---- the llama.cpp side, through the same flat ABI the inference module uses -------------
    static final class Encoder implements AutoCloseable {
        final Arena arena = Arena.ofShared();
        final MethodHandle embed, pooling, dim, free;
        final MemorySegment handle;
        final MemorySegment out;

        Encoder(Path dylib, Path gguf, int wantPooling) throws Throwable {
            SymbolLookup lib = SymbolLookup.libraryLookup(dylib, arena);
            Linker linker = Linker.nativeLinker();
            MethodHandle load = linker.downcallHandle(lib.find("dsc_encoder_load").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            embed = linker.downcallHandle(lib.find("dsc_embed").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                      ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            pooling = linker.downcallHandle(lib.find("dsc_encoder_pooling").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            dim = linker.downcallHandle(lib.find("dsc_encoder_dim").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            free = linker.downcallHandle(lib.find("dsc_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

            handle = (MemorySegment) load.invoke(arena.allocateFrom(gguf.toString()), wantPooling);
            if (handle.equals(MemorySegment.NULL)) throw new IllegalStateException("dsc_encoder_load returned NULL");
            out = arena.allocate(ValueLayout.JAVA_FLOAT, DIM);
        }

        int pooling() throws Throwable { return (int) pooling.invoke(handle); }
        int dim() throws Throwable { return (int) dim.invoke(handle); }

        float[] embed(String text) throws Throwable {
            MemorySegment s = arena.allocateFrom(text);
            int n = (int) embed.invoke(handle, s, out, DIM);
            if (n < 0) throw new IllegalStateException("dsc_embed returned " + n);
            return out.toArray(ValueLayout.JAVA_FLOAT);
        }

        public void close() { try { free.invoke(handle); } catch (Throwable t) { } arena.close(); }
    }

    /** bge-small ONNX exposes only last_hidden_state, so pooling is ours to do - which is the point. */
    static class MeanTranslator implements Translator<String, float[]> {
        final HuggingFaceTokenizer tok;
        MeanTranslator(HuggingFaceTokenizer t) { this.tok = t; }
        public NDList processInput(TranslatorContext ctx, String text) {
            Encoding e = tok.encode(text);
            NDManager m = ctx.getNDManager();
            long[] ids = e.getIds();
            long[] types = new long[ids.length];              // BERT wants token_type_ids
            NDList in = new NDList(
                m.create(ids).expandDims(0),
                m.create(e.getAttentionMask()).expandDims(0),
                m.create(types).expandDims(0));
            in.get(0).setName("input_ids");
            in.get(1).setName("attention_mask");
            in.get(2).setName("token_type_ids");
            return in;
        }
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            // One sequence at a time, so there is no padding and a plain mean over the token axis
            // is the masked mean. Includes [CLS] and [SEP], which is what sentence-transformers
            // does and therefore what the reference numbers were produced with.
            return list.get(0).get(0).mean(new int[]{0}).toFloatArray();
        }
        public Batchifier getBatchifier() { return null; }
    }

    public static void main(String[] args) throws Throwable {
        int N = 300;
        boolean recall = false;
        for (String a : args) {
            if (a.equals("--recall")) recall = true; else N = Integer.parseInt(a);
        }

        Path t5 = HERE.resolve("../test5");
        JsonArray corpus  = JsonParser.parseReader(Files.newBufferedReader(t5.resolve("corpus.json"))).getAsJsonArray();
        JsonArray queries = JsonParser.parseReader(Files.newBufferedReader(t5.resolve("queries.json"))).getAsJsonArray();

        Path dylib = HERE.resolve("../../implementations/codex/inference/src/jvmMain/resources/dscodex/macos-aarch64/libdscodex.dylib");
        Path gguf  = HERE.resolve("bge-small-en-v1.5-f16.gguf");

        System.out.println("reference : BAAI/bge-small-en-v1.5 ONNX (fp32, 134 MB), mean-pooled, via DJL");
        System.out.println("candidate : the same weights as F16 GGUF (67 MB), via llama.cpp in-process");
        System.out.println("corpus    : " + corpus.size() + " entries, " + queries.size() + " queries\n");

        HuggingFaceTokenizer tok = HuggingFaceTokenizer.newInstance(HERE.resolve("model/tokenizer.json"));
        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelPath(HERE.resolve("model"))
                .optEngine("OnnxRuntime")
                .optTranslator(new MeanTranslator(tok))
                .build();

        try (ZooModel<String, float[]> model = criteria.loadModel();
             Predictor<String, float[]> onnx = model.newPredictor();
             Encoder mean = new Encoder(dylib, gguf, MEAN)) {

            System.out.printf("0. pooling is an argument, not a default%n");
            System.out.printf("   GGUF declares      CLS (the model card's pooling)%n");
            System.out.printf("   asked for          MEAN%n");
            System.out.printf("   in effect          %s   dim %d%n",
                    mean.pooling() == MEAN ? "MEAN" : String.valueOf(mean.pooling()), mean.dim());
            try (Encoder unspecified = new Encoder(dylib, gguf, -1)) {
                System.out.println("   UNSPECIFIED        LOADED - the guard did not hold");
            } catch (IllegalStateException expected) {
                System.out.println("   UNSPECIFIED        refused, as designed");
            }

            // --- 1. do the vectors agree? -------------------------------------------------
            Random rng = new Random(11);
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < corpus.size(); i++) idx.add(i);
            Collections.shuffle(idx, rng);
            idx = idx.subList(0, N);

            double lo = 1, sum = 0; int under99 = 0;
            long tOnnx = 0, tLlama = 0;
            for (int i : idx) {
                String text = keyText(corpus.get(i).getAsJsonObject());
                long a = System.nanoTime();
                float[] vo = onnx.predict(text);
                long b = System.nanoTime();
                float[] vl = mean.embed(text);
                long c = System.nanoTime();
                tOnnx += b - a; tLlama += c - b;
                double k = cos(vo, vl);
                sum += k; lo = Math.min(lo, k); if (k < 0.99) under99++;
            }
            System.out.printf("%n1. vector agreement over %d entries%n", N);
            // Seven places on purpose. F16 weights against fp32 should NOT agree exactly, and a
            // cosine printed as 1.00000 is indistinguishable from a probe comparing a buffer with
            // itself. The residual is the evidence that two runtimes actually ran.
            System.out.printf("   mean cosine(ONNX, llama.cpp) = %.7f   min = %.7f   below 0.99 = %d%n",
                    sum / N, lo, under99);
            System.out.printf("   ONNX      %.1f ms per embedding%n", tOnnx / 1e6 / N);
            System.out.printf("   llama.cpp %.1f ms per embedding%n", tLlama / 1e6 / N);

            // --- 2. what CLS would have cost, had the default been taken ------------------
            try (Encoder cls = new Encoder(dylib, gguf, CLS)) {
                List<Integer> sample = idx.subList(0, Math.min(50, idx.size()));
                double s2 = 0;
                for (int i : sample) {
                    String text = keyText(corpus.get(i).getAsJsonObject());
                    s2 += cos(onnx.predict(text), cls.embed(text));
                }
                System.out.printf("%n2. the same model under the pooling the GGUF declares%n");
                System.out.printf("   mean cosine(ONNX mean, llama.cpp CLS) = %.5f  over %d entries%n",
                        s2 / sample.size(), sample.size());
            }

            if (!recall) { System.out.println("\n(--recall to embed the full corpus and score retrieval)"); return; }

            // --- 3. recall, mixed basis ---------------------------------------------------
            System.out.println("\n3. embedding the full corpus with both runtimes");
            float[][] vo = new float[corpus.size()][], vl = new float[corpus.size()][];
            long t0 = System.currentTimeMillis();
            for (int i = 0; i < corpus.size(); i++) {
                String text = keyText(corpus.get(i).getAsJsonObject());
                vo[i] = onnx.predict(text);
                vl[i] = mean.embed(text);
                if (i % 2000 == 0) System.out.printf("   %d / %d%n", i, corpus.size());
            }
            System.out.printf("   done in %d s%n", (System.currentTimeMillis() - t0) / 1000);

            int[] KS = {1, 3, 5, 10};
            String[] names = {"ONNX queries vs ONNX corpus", "llama.cpp queries vs llama.cpp corpus",
                              "llama.cpp queries vs ONNX corpus (mixed)"};
            int[][] hits = new int[3][KS.length];
            int scored = 0;
            for (JsonElement qe : queries) {
                JsonObject q = qe.getAsJsonObject();
                String target = q.get("target").getAsString();
                int gold = -1;
                for (int i = 0; i < corpus.size(); i++)
                    if (corpus.get(i).getAsJsonObject().get("symbol").getAsString().equals(target)) { gold = i; break; }
                if (gold < 0) continue;
                scored++;
                String qt = q.get("query").getAsString();
                float[] qo = onnx.predict(qt), ql = mean.embed(qt);
                float[][][] pairs = {{qo}, {ql}, {ql}};
                float[][][] bases = {vo, vl, vo};
                for (int arm = 0; arm < 3; arm++) {
                    float[] qv = pairs[arm][0];
                    float[][] base = bases[arm];
                    double g = cos(qv, base[gold]);
                    int rank = 1;
                    for (float[] v : base) if (cos(qv, v) > g) rank++;
                    for (int j = 0; j < KS.length; j++) if (rank <= KS[j]) hits[arm][j]++;
                }
            }
            System.out.printf("%n   %-42s %s%n", "", "r@1   r@3   r@5  r@10");
            for (int arm = 0; arm < 3; arm++) {
                System.out.printf("   %-42s", names[arm]);
                for (int j = 0; j < KS.length; j++) System.out.printf("%4d/%-2d", hits[arm][j], scored);
                System.out.println();
            }

            // --- 4. do the two runtimes RANK the corpus the same way? ---------------------
            // Recall against a gold label is a weak instrument when recall is near zero: a
            // disagreement has almost nothing to destroy. This asks the sharper question - over
            // 14,899 candidates, does swapping the runtime change what comes back at all.
            System.out.println("\n4. agreement of the two rankings, per query");
            System.out.printf("   %-38s %8s %8s %8s%n", "query", "top10", "top100", "gold rank");
            int sumTop10 = 0, sumTop100 = 0, n = 0;
            for (JsonElement qe : queries) {
                JsonObject q = qe.getAsJsonObject();
                String qt = q.get("query").getAsString();
                float[] qo = onnx.predict(qt), ql = mean.embed(qt);
                Integer[] byO = order(qo, vo), byL = order(ql, vl);
                int o10 = overlap(byO, byL, 10), o100 = overlap(byO, byL, 100);
                sumTop10 += o10; sumTop100 += o100; n++;
                String target = q.get("target").getAsString();
                int gold = -1;
                for (int i = 0; i < corpus.size(); i++)
                    if (corpus.get(i).getAsJsonObject().get("symbol").getAsString().equals(target)) { gold = i; break; }
                String ranks = gold < 0 ? "-" : (rankOf(byO, gold) + " / " + rankOf(byL, gold));
                System.out.printf("   %-38s %6d/10 %7d/100 %9s%n",
                        qt.substring(0, Math.min(38, qt.length())), o10, o100, ranks);
            }
            System.out.printf("%n   mean top-10 agreement  %.1f / 10%n", (double) sumTop10 / n);
            System.out.printf("   mean top-100 agreement %.1f / 100%n", (double) sumTop100 / n);
        }
        System.out.println("\nOK");
    }
}
