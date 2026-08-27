// test23 phase B — does a JVM runtime reproduce the embeddings every retrieval number
// in this project rests on?
//
// Reference: experiments/test5/corpus-vecs.json, produced by mlx-community/bge-m3-mlx-fp16
// (Python, Apple Silicon). Here: BAAI/bge-m3 official ONNX export, via DJL over ONNX Runtime,
// on CPU. Same text construction as test5/embed_corpus.py, same 900-char truncation.
//
// Two tests, the second stronger than it looks:
//   1. cosine(MLX vector, ONNX vector) per entry - do the vectors agree at all
//   2. recall with ONNX QUERY vectors against the MLX CORPUS - a mixed basis, so any
//      systematic offset between the runtimes shows up as lost recall rather than hiding

import ai.djl.huggingface.tokenizers.*;
import ai.djl.ndarray.*;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.types.*;
import ai.djl.repository.zoo.*;
import ai.djl.inference.*;
import ai.djl.translate.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class EmbedProbe {
    static final Path HERE = Paths.get("").toAbsolutePath();
    static final int MAXCHARS = 900;
    static final Pattern COMMENT = Pattern.compile("^\\s*/\\*\\*|\\*/\\s*$|^\\s*\\*\\s?", Pattern.MULTILINE);

    /** identical to test5/embed_corpus.py key_text() */
    static String keyText(JsonObject e) {
        String doc = COMMENT.matcher(e.get("doc").getAsString()).replaceAll("");
        doc = String.join(" ", doc.trim().split("\\s+"));
        if (doc.length() > MAXCHARS) doc = doc.substring(0, MAXCHARS);
        String sym = e.get("symbol").getAsString();
        return sym.substring(sym.lastIndexOf('.') + 1) + ". " + doc;
    }

    static float[] norm(float[] v) {
        double n = 0; for (float x : v) n += x * x; n = Math.sqrt(n);
        float[] o = new float[v.length];
        for (int i = 0; i < v.length; i++) o[i] = (float) (v[i] / n);
        return o;
    }
    static double cos(float[] a, float[] b) {
        double d = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { d += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        return d / (Math.sqrt(na) * Math.sqrt(nb));
    }
    static double cos(float[] a, List<Float> b) {
        double d = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { d += a[i]*b.get(i); na += a[i]*a[i]; nb += b.get(i)*b.get(i); }
        return d / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static void main(String[] args) throws Exception {
        Path t5 = HERE.resolve("../test5");
        JsonArray corpus  = JsonParser.parseReader(Files.newBufferedReader(t5.resolve("corpus.json"))).getAsJsonArray();
        JsonArray queries = JsonParser.parseReader(Files.newBufferedReader(t5.resolve("queries.json"))).getAsJsonArray();
        JsonObject vecFile = JsonParser.parseReader(Files.newBufferedReader(t5.resolve("corpus-vecs.json"))).getAsJsonObject();
        JsonArray mlxVecs = vecFile.getAsJsonArray("vecs");
        System.out.println("reference: " + vecFile.get("model").getAsString()
                + "   " + mlxVecs.size() + " vectors, dim " + mlxVecs.get(0).getAsJsonArray().size());

        HuggingFaceTokenizer tok = HuggingFaceTokenizer.newInstance(HERE.resolve("model/tokenizer.json"));
        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelPath(HERE.resolve("model"))
                .optEngine("OnnxRuntime")
                .optTranslator(new BgeTranslator(tok))
                .build();

        try (ZooModel<String, float[]> model = criteria.loadModel();
             Predictor<String, float[]> p = model.newPredictor()) {

            // --- 1. vector agreement on a sample -------------------------------------
            int N = Integer.parseInt(args.length > 0 ? args[0] : "300");
            Random rng = new Random(11);
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < corpus.size(); i++) idx.add(i);
            Collections.shuffle(idx, rng);
            idx = idx.subList(0, N);

            double lo = 1, sum = 0; int under99 = 0;
            long t0 = System.currentTimeMillis();
            for (int i : idx) {
                float[] v = Arrays.copyOf(p.predict(keyText(corpus.get(i).getAsJsonObject())), 1024);
                List<Float> ref = new ArrayList<>();
                for (JsonElement x : mlxVecs.get(i).getAsJsonArray()) ref.add(x.getAsFloat());
                double c = cos(v, ref);
                sum += c; lo = Math.min(lo, c); if (c < 0.99) under99++;
            }
            long ms = System.currentTimeMillis() - t0;
            System.out.printf("%n1. vector agreement over %d entries%n", N);
            System.out.printf("   mean cosine(MLX, ONNX) = %.5f   min = %.5f   below 0.99 = %d%n", sum/N, lo, under99);
            System.out.printf("   %d ms for %d embeddings  (%.0f ms each, CPU)%n", ms, N, (double) ms / N);

            // --- 2. recall: ONNX query vectors against the MLX corpus -----------------
            System.out.println("\n2. recall@k - ONNX query vectors vs the MLX corpus (mixed basis)");
            int[] KS = {1, 3, 5, 10};
            Map<Integer,Integer> hits = new LinkedHashMap<>();
            for (int k : KS) hits.put(k, 0);
            int scored = 0;
            for (JsonElement qe : queries) {
                JsonObject q = qe.getAsJsonObject();
                String target = q.get("target").getAsString();
                int gold = -1;
                for (int i = 0; i < corpus.size(); i++)
                    if (corpus.get(i).getAsJsonObject().get("symbol").getAsString().equals(target)) { gold = i; break; }
                if (gold < 0) continue;
                scored++;
                float[] qv = Arrays.copyOf(p.predict(q.get("query").getAsString()), 1024);
                double[] sc = new double[corpus.size()];
                for (int i = 0; i < corpus.size(); i++) {
                    List<Float> ref = new ArrayList<>();
                    for (JsonElement x : mlxVecs.get(i).getAsJsonArray()) ref.add(x.getAsFloat());
                    sc[i] = cos(qv, ref);
                }
                int rank = 1;
                for (int i = 0; i < sc.length; i++) if (sc[i] > sc[gold]) rank++;
                for (int k : KS) if (rank <= k) hits.put(k, hits.get(k) + 1);
            }
            for (int k : KS) System.out.printf("   recall@%-2d  %d of %d%n", k, hits.get(k), scored);
        }
        System.out.println("\nOK");
    }

    /** BGE-M3 dense: CLS token of last_hidden_state, L2-normalised. */
    static class BgeTranslator implements Translator<String, float[]> {
        final HuggingFaceTokenizer tok;
        BgeTranslator(HuggingFaceTokenizer t) { this.tok = t; }
        public NDList processInput(TranslatorContext ctx, String text) {
            Encoding e = tok.encode(text);
            NDManager m = ctx.getNDManager();
            return new NDList(
                m.create(e.getIds()).expandDims(0),
                m.create(e.getAttentionMask()).expandDims(0));
        }
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            // Two outputs: [0] token_embeddings, [1] sentence_embedding (CLS-pooled).
            // The reference vectors were produced by mlx-embeddings, which MEAN-pools -
            // measured here as cosine 1.00000 against mean, 0.75 against CLS.
            NDArray tokens = list.get(0).get(0);
            float[] mean = tokens.mean(new int[]{0}).toFloatArray();
            float[] cls  = list.get(1).get(0).toFloatArray();
            float[] out = new float[2048];
            System.arraycopy(norm(mean), 0, out, 0, 1024);
            System.arraycopy(norm(cls),  0, out, 1024, 1024);
            return out;
        }
        public Batchifier getBatchifier() { return null; }
    }
}
