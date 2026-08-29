// How long does loading a model cost, and how many embeddings does that buy?
// The number that decides whether a resident service is worth its packaging.
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.*;

public class LoadCost {
    public static void main(String[] a) throws Throwable {
        Path dylib = Paths.get("../../implementations/codex/inference/src/jvmMain/resources/dscodex/macos-aarch64/libdscodex.dylib");
        Path gguf = Paths.get("bge-small-en-v1.5-f16.gguf");
        Arena arena = Arena.ofShared();
        SymbolLookup lib = SymbolLookup.libraryLookup(dylib, arena);
        Linker l = Linker.nativeLinker();
        MethodHandle load = l.downcallHandle(lib.find("dsc_encoder_load").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MethodHandle embed = l.downcallHandle(lib.find("dsc_embed").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MethodHandle free = l.downcallHandle(lib.find("dsc_free").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        MemorySegment path = arena.allocateFrom(gguf.toString());
        MemorySegment out = arena.allocate(ValueLayout.JAVA_FLOAT, 384);
        MemorySegment text = arena.allocateFrom("returns the number of bytes written to the channel");

        for (int run = 0; run < 3; run++) {
            long t0 = System.nanoTime();
            MemorySegment h = (MemorySegment) load.invoke(path, 1);
            long loaded = System.nanoTime();
            for (int i = 0; i < 20; i++) embed.invoke(h, text, out, 384);
            long embedded = System.nanoTime();
            free.invoke(h);
            double loadMs = (loaded - t0) / 1e6, perEmbed = (embedded - loaded) / 1e6 / 20;
            System.out.printf("run %d: load %.0f ms, embed %.1f ms  -> load == %.0f embeddings%n",
                    run + 1, loadMs, perEmbed, loadMs / perEmbed);
        }
    }
}
