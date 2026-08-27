// test24 — where the encoder size cutoff is.
//
// 5 models from 2,267 MB down to 33 MB, both poolings, on test5's deterministic 220-entry
// subset (the only size where the raw-doc baseline still has recall to lose). Both poolings
// come out of one forward pass, so this is a full factorial, not a confound.
//
// Reference numbers to beat: BGE-M3 mean-pooled, which is what every prior result used.

import ai.djl.huggingface.tokenizers.*;
import ai.djl.ndarray.*;
import ai.djl.ndarray.NDArray;
import ai.djl.repository.zoo.*;
import ai.djl.inference.*;
import ai.djl.translate.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Sweep {
    static final Path HERE = Paths.get("").toAbsolutePath();
    static final Pattern COMMENT = Pattern.compile("^\\s*/\\*\\*|\\*/\\s*$|^\\s*\\*\\s?", Pattern.MULTILINE);
    static final int[] KS = {1, 3, 5, 10};

    static String keyText(JsonObject e) {
        String doc = COMMENT.matcher(e.get("doc").getAsString()).replaceAll("");
        doc = String.join(" ", doc.trim().split("\\s+"));
        if (doc.length() > 900) doc = doc.substring(0, 900);
        String s = e.get("symbol").getAsString();
        return s.substring(s.lastIndexOf('.') + 1) + ". " + doc;
    }
    static float[] norm(float[] v) {
        double n = 0; for (float x : v) n += x*x; n = Math.sqrt(n);
        float[] o = new float[v.length];
        for (int i = 0; i < v.length; i++) o[i] = (float)(v[i]/n);
        return o;
    }
    static double cos(float[] a, float[] b) {
        double d=0; for (int i=0;i<a.length;i++) d += a[i]*b[i]; return d;   // both normalised
    }

    public static void main(String[] args) throws Exception {
        Path t5 = HERE.resolve("../test5");
        JsonArray corpus  = JsonParser.parseReader(Files.newBufferedReader(t5.resolve("corpus.json"))).getAsJsonArray();
        JsonArray queries = JsonParser.parseReader(Files.newBufferedReader(t5.resolve("queries.json"))).getAsJsonArray();
        JsonArray sub     = JsonParser.parseReader(Files.newBufferedReader(HERE.resolve("subset220.json"))).getAsJsonArray();

        int[] idx = new int[sub.size()];
        for (int i = 0; i < sub.size(); i++) idx[i] = sub.get(i).getAsInt();

        String[][] models = {
            {"bge-m3-fp32   (2267 MB)", "../test23/model"},
            {"bge-base-fp32 ( 418 MB)", "models/bge-base-fp32"},
            {"bge-small-fp32( 136 MB)", "models/bge-small-fp32"},
            {"bge-small-fp16(  64 MB)", "models/bge-small-fp16"},
            {"bge-small-int8(  33 MB)", "models/bge-small-int8"},
        };

        System.out.printf("%-24s %-6s %-5s %-5s %-5s %-5s %s%n",
                "model", "pool", "r@1", "r@3", "r@5", "r@10", "ms/emb");
        System.out.println("-".repeat(74));

        for (String[] m : models) {
            Path mp = HERE.resolve(m[1]);
            if (!Files.exists(mp.resolve("model.onnx"))) { System.out.println(m[0] + "  MISSING"); continue; }
            HuggingFaceTokenizer tok = HuggingFaceTokenizer.newInstance(mp.resolve("tokenizer.json"));
            Criteria<String, float[]> c = Criteria.builder().setTypes(String.class, float[].class)
                    .optModelPath(mp).optEngine("OnnxRuntime")
                    .optTranslator(new Dual(tok, !m[1].contains("test23"))).build();
            try (ZooModel<String,float[]> zm = c.loadModel(); Predictor<String,float[]> p = zm.newPredictor()) {
                int dim = p.predict("probe").length / 2;

                float[][][] cv = new float[2][idx.length][];   // [pool][entry]
                long t0 = System.currentTimeMillis();
                for (int i = 0; i < idx.length; i++) {
                    float[] both = p.predict(keyText(corpus.get(idx[i]).getAsJsonObject()));
                    cv[0][i] = norm(Arrays.copyOfRange(both, 0, dim));
                    cv[1][i] = norm(Arrays.copyOfRange(both, dim, dim*2));
                }
                double msEmb = (System.currentTimeMillis() - t0) / (double) idx.length;

                float[][][] qv = new float[2][queries.size()][];
                for (int qi = 0; qi < queries.size(); qi++) {
                    float[] both = p.predict(queries.get(qi).getAsJsonObject().get("query").getAsString());
                    qv[0][qi] = norm(Arrays.copyOfRange(both, 0, dim));
                    qv[1][qi] = norm(Arrays.copyOfRange(both, dim, dim*2));
                }

                String[] pools = {"mean", "CLS"};
                for (int pool = 0; pool < 2; pool++) {
                    Map<Integer,Integer> hits = new LinkedHashMap<>();
                    for (int k : KS) hits.put(k, 0);
                    int scored = 0;
                    for (int qi = 0; qi < queries.size(); qi++) {
                        String target = queries.get(qi).getAsJsonObject().get("target").getAsString();
                        int gold = -1;
                        for (int i = 0; i < idx.length; i++)
                            if (corpus.get(idx[i]).getAsJsonObject().get("symbol").getAsString().equals(target)) { gold = i; break; }
                        if (gold < 0) continue;
                        scored++;
                        double gs = cos(qv[pool][qi], cv[pool][gold]);
                        int rank = 1;
                        for (int i = 0; i < idx.length; i++) if (cos(qv[pool][qi], cv[pool][i]) > gs) rank++;
                        for (int k : KS) if (rank <= k) hits.put(k, hits.get(k)+1);
                    }
                    System.out.printf("%-24s %-6s %-5s %-5s %-5s %-5s %.1f%n", pool == 0 ? m[0] : "", pools[pool],
                            hits.get(1)+"/"+scored, hits.get(3)+"/"+scored, hits.get(5)+"/"+scored, hits.get(10)+"/"+scored,
                            pool == 0 ? msEmb : 0.0);
                }
            }
        }
    }

    /** emits [mean-pooled | CLS] concatenated, so both come from one forward pass */
    static class Dual implements Translator<String, float[]> {
        final HuggingFaceTokenizer tok; final boolean typeIds;
        Dual(HuggingFaceTokenizer t, boolean ti) { tok = t; typeIds = ti; }
        public NDList processInput(TranslatorContext ctx, String text) {
            Encoding e = tok.encode(text);
            NDManager m = ctx.getNDManager();
            NDArray ids  = m.create(e.getIds()).expandDims(0);       ids.setName("input_ids");
            NDArray mask = m.create(e.getAttentionMask()).expandDims(0); mask.setName("attention_mask");
            NDList in = new NDList(ids, mask);
            if (typeIds) {
                NDArray tt = m.create(e.getTypeIds()).expandDims(0);  tt.setName("token_type_ids");
                in.add(tt);
            }
            return in;
        }
        public float[] processOutput(TranslatorContext ctx, NDList l) {
            NDArray tokens = l.get(0).get(0);                 // (seq, hidden)
            float[] mean = tokens.mean(new int[]{0}).toFloatArray();
            float[] cls  = tokens.get(0).toFloatArray();
            float[] out = new float[mean.length * 2];
            System.arraycopy(mean, 0, out, 0, mean.length);
            System.arraycopy(cls, 0, out, mean.length, cls.length);
            return out;
        }
        public Batchifier getBatchifier() { return null; }
    }
}
