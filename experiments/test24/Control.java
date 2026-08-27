// Does the full 14,899-entry corpus stay near zero regardless of encoder?
// If it does, the ceiling is the retrieval key, not the model - which is the premise
// that makes measuring at 220 legitimate.
import ai.djl.huggingface.tokenizers.*;
import ai.djl.ndarray.*; import ai.djl.ndarray.NDArray;
import ai.djl.repository.zoo.*; import ai.djl.inference.*; import ai.djl.translate.*;
import com.google.gson.*; import java.nio.file.*; import java.util.*; import java.util.regex.*;

public class Control {
    static final Pattern C = Pattern.compile("^\\s*/\\*\\*|\\*/\\s*$|^\\s*\\*\\s?", Pattern.MULTILINE);
    static String kt(JsonObject e){ String d=C.matcher(e.get("doc").getAsString()).replaceAll("");
        d=String.join(" ", d.trim().split("\\s+")); if(d.length()>900) d=d.substring(0,900);
        String s=e.get("symbol").getAsString(); return s.substring(s.lastIndexOf('.')+1)+". "+d; }
    static float[] nm(float[] v){ double n=0; for(float x:v) n+=x*x; n=Math.sqrt(n);
        float[] o=new float[v.length]; for(int i=0;i<v.length;i++) o[i]=(float)(v[i]/n); return o; }
    public static void main(String[] a) throws Exception {
        Path here=Paths.get("").toAbsolutePath(), t5=here.resolve("../test5");
        JsonArray corpus=JsonParser.parseReader(Files.newBufferedReader(t5.resolve("corpus.json"))).getAsJsonArray();
        JsonArray queries=JsonParser.parseReader(Files.newBufferedReader(t5.resolve("queries.json"))).getAsJsonArray();
        Path mp=here.resolve("models/bge-small-int8");
        HuggingFaceTokenizer tok=HuggingFaceTokenizer.newInstance(mp.resolve("tokenizer.json"));
        Criteria<String,float[]> c=Criteria.builder().setTypes(String.class,float[].class)
            .optModelPath(mp).optEngine("OnnxRuntime").optTranslator(new Sweep.Dual(tok,true)).build();
        try(ZooModel<String,float[]> zm=c.loadModel(); Predictor<String,float[]> p=zm.newPredictor()){
            int dim=p.predict("probe").length/2;
            System.out.println("embedding "+corpus.size()+" entries with bge-small-int8 (33 MB)...");
            float[][] cv=new float[corpus.size()][];
            long t0=System.currentTimeMillis();
            for(int i=0;i<corpus.size();i++){
                cv[i]=nm(Arrays.copyOfRange(p.predict(kt(corpus.get(i).getAsJsonObject())),0,dim));
                if(i%3000==0) System.out.println("  "+i+"/"+corpus.size());
            }
            System.out.printf("  done in %.0f s%n",(System.currentTimeMillis()-t0)/1000.0);
            int[] KS={1,3,5,10}; Map<Integer,Integer> h=new LinkedHashMap<>();
            for(int k:KS) h.put(k,0);
            int scored=0;
            for(JsonElement qe:queries){ JsonObject q=qe.getAsJsonObject();
                String tgt=q.get("target").getAsString(); int gold=-1;
                for(int i=0;i<corpus.size();i++) if(corpus.get(i).getAsJsonObject().get("symbol").getAsString().equals(tgt)){gold=i;break;}
                if(gold<0) continue; scored++;
                float[] qv=nm(Arrays.copyOfRange(p.predict(q.get("query").getAsString()),0,dim));
                double gs=0; for(int k=0;k<dim;k++) gs+=qv[k]*cv[gold][k];
                int rank=1;
                for(int i=0;i<corpus.size();i++){ double s=0; for(int k=0;k<dim;k++) s+=qv[k]*cv[i][k]; if(s>gs) rank++; }
                for(int k:KS) if(rank<=k) h.put(k,h.get(k)+1);
            }
            System.out.printf("full corpus, mean pooling:  r@1 %d/%d  r@3 %d/%d  r@5 %d/%d  r@10 %d/%d%n",
                h.get(1),scored,h.get(3),scored,h.get(5),scored,h.get(10),scored);
            System.out.println("bge-m3 reference on the same corpus:  r@1 0/17  r@3 0/17  r@5 0/17  r@10 1/17");
        }
    }
}
