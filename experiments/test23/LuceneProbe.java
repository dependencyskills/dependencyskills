// test23 phase A — can Lucene hold two vectors per entry, and can it filter a kNN
// query by a coordinate set from INSIDE the search rather than after it?
//
// The decisive case is constructed on purpose: the globally nearest vectors all belong
// to coordinates the asking project does not have. A post-filter returns nothing; a
// real filtered kNN returns the in-scope matches sitting below them.

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import java.nio.file.*;
import java.util.*;
import org.apache.lucene.util.BytesRef;

public class LuceneProbe {
    static final int DIM = 1024;                 // BGE-M3
    static final int COORDS = 600;               // libraries on the machine
    static final int PER_COORD = 25;             // entries per library  -> 15,000 docs
    static final Random RNG = new Random(11);

    /** unit vector pointing mostly along axis `axis`, with `noise` spread */
    static float[] vec(int axis, float noise) {
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) v[i] = (float) (RNG.nextGaussian() * noise);
        v[axis % DIM] += 1.0f;
        double n = 0; for (float x : v) n += x * x; n = Math.sqrt(n);
        for (int i = 0; i < DIM; i++) v[i] /= (float) n;
        return v;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("test23");
        try (Directory d = FSDirectory.open(dir);
             IndexWriter w = new IndexWriter(d, new IndexWriterConfig())) {

            // Coordinates 0..9 are "in scope". Everything else is another project's.
            // Out-of-scope docs are placed CLOSER to the query axis than in-scope ones.
            for (int c = 0; c < COORDS; c++) {
                boolean inScope = c < 10;
                for (int j = 0; j < PER_COORD; j++) {
                    Document doc = new Document();
                    doc.add(new StringField("coord", "lib:" + c, Field.Store.YES));
                    doc.add(new StringField("id", c + "/" + j, Field.Store.YES));
                    // in-scope: noisier (further from axis 0). out-of-scope: tighter (nearer).
                    doc.add(new KnnFloatVectorField("raw", vec(0, inScope ? 0.06f : 0.02f),
                            VectorSimilarityFunction.COSINE));
                    doc.add(new KnnFloatVectorField("rewrite", vec(1, 0.05f),
                            VectorSimilarityFunction.COSINE));
                    w.addDocument(doc);
                }
            }
            w.commit();

            try (IndexReader r = DirectoryReader.open(d)) {
                IndexSearcher s = new IndexSearcher(r);
                StoredFields sf = s.storedFields();
                float[] q = vec(0, 0.0f);

                System.out.println("docs indexed: " + r.numDocs()
                        + "   fields per doc: raw + rewrite (both " + DIM + "-dim)");

                // --- 1. two vector fields on one document, queried independently ---
                var rawHits = s.search(new KnnFloatVectorQuery("raw", q, 10), 10);
                var rewHits = s.search(new KnnFloatVectorQuery("rewrite", vec(1, 0f), 10), 10);
                System.out.println("\n1. two vectors per doc, no schema hack");
                System.out.println("   raw field     -> " + rawHits.scoreDocs.length + " hits");
                System.out.println("   rewrite field -> " + rewHits.scoreDocs.length + " hits");

                // --- 2. unfiltered top-10: how many are in scope? ---
                int unfilteredInScope = 0;
                for (var sd : rawHits.scoreDocs)
                    if (Integer.parseInt(sf.document(sd.doc).get("coord").substring(4)) < 10)
                        unfilteredInScope++;
                System.out.println("\n2. unfiltered kNN k=10");
                System.out.println("   in-scope hits: " + unfilteredInScope + " of 10"
                        + "   <- a post-filter would return " + unfilteredInScope + " results");

                // --- 3. filtered kNN: scope applied inside the search ---
                List<BytesRef> scope = new ArrayList<>();
                for (int c = 0; c < 10; c++) scope.add(new BytesRef("lib:" + c));
                Query filter = new TermInSetQuery("coord", scope);
                var filtered = s.search(new KnnFloatVectorQuery("raw", q, 10, filter), 10);
                int filteredInScope = 0;
                for (var sd : filtered.scoreDocs)
                    if (Integer.parseInt(sf.document(sd.doc).get("coord").substring(4)) < 10)
                        filteredInScope++;
                System.out.println("\n3. filtered kNN k=10, filter = 10 coordinates");
                System.out.println("   hits: " + filtered.scoreDocs.length
                        + "   in-scope: " + filteredInScope + " of " + filtered.scoreDocs.length);

                // --- 4. a realistically large scope set ---
                for (int size : new int[]{50, 200, 500}) {
                    List<BytesRef> big = new ArrayList<>();
                    for (int c = 0; c < size; c++) big.add(new BytesRef("lib:" + c));
                    Query bf = new TermInSetQuery("coord", big);
                    long t0 = System.nanoTime();
                    TopDocs td = null;
                    for (int i = 0; i < 20; i++) td = s.search(new KnnFloatVectorQuery("raw", q, 10, bf), 10);
                    long us = (System.nanoTime() - t0) / 20 / 1000;
                    System.out.printf("   filter of %3d coordinates -> %d hits, %,d us/query%n",
                            size, td.scoreDocs.length, us);
                }
            }
        }
        System.out.println("\nOK");
    }
}
