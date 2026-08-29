// The default encoder, packaged for Maven Central.
//
// The weights are NOT in this repository. They are fetched from their origin at build
// time and verified against pinned SHA-256 digests, so the artifact is reproducible and
// its provenance is checkable rather than asserted. A repository that carries 134 MB of
// third-party binary is a repository nobody can clone.

plugins { base; `maven-publish` }

version = providers.gradleProperty("encoderVersion").get()   // versions with the model, not the plugin



/**
 * Verifies the committed model before anything packages it, and copies only what verified.
 *
 * The weights used to be downloaded at build time; they are now in the repository, which removes
 * the network from the build entirely. What must not go with the download is the **check**. A
 * binary in a repository can be replaced, corrupted by a bad merge, or truncated by a transfer,
 * and none of those announce themselves — so the digest is pinned here and compared on every
 * build, exactly as it was when the file arrived over HTTP.
 *
 * The output is a copy rather than the file in place, so the only bytes reachable by the jar are
 * bytes this task approved. Wiring the jar straight at `model/` would let it package an
 * unverified file the day someone reorders a task.
 */
abstract class VerifyModel : DefaultTask() {

    @get:InputFile abstract val model: RegularFileProperty

    /** The expected SHA-256. An `@Input`, so re-pinning it re-runs the check. */
    @get:Input abstract val digest: Property<String>

    @get:OutputDirectory abstract val into: DirectoryProperty

    @TaskAction
    fun verify() {
        val source = model.get().asFile
        val actual = java.security.MessageDigest.getInstance("SHA-256")
            .digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        // A hard failure, not a warning. A verifier that passes everything is indistinguishable
        // from no verifier, and this project has shipped that twice.
        check(actual == digest.get()) {
            "${source.name} failed verification.\n  expected ${digest.get()}\n  actual   $actual\n" +
                "The committed model is not the one model/RECIPE.md describes. Do not package it."
        }
        val target = into.get().asFile
        // Emptied first. This directory held the old ONNX pair until the GGUF replaced it, and a
        // task that only ever adds files will happily package a predecessor's leftovers - which
        // it did, once, before this line existed.
        target.deleteRecursively()
        target.mkdirs()
        source.copyTo(java.io.File(target, source.name))
        logger.lifecycle("verified ${source.name} (${source.length() / 1_000_000} MB)")
    }
}

// BAAI/bge-small-en-v1.5 converted to GGUF F16 - see model/RECIPE.md for the inputs, the converter
// commit and the command. Committed rather than fetched because the conversion is byte-reproducible
// and nobody publishes a first-party GGUF of this model: ADR-0013.
val verifyModel by tasks.registering(VerifyModel::class) {
    description = "Check the committed model against its pinned digest before packaging it."
    model = layout.projectDirectory.file("model/model.gguf")
    digest = "86776c71a9890f0246d12022ee8e5e9cf382012917ad8e611bb269b91f6b3e21"
    into = layout.buildDirectory.dir("model")
}

val encoderJar by tasks.registering(Jar::class) {
    archiveBaseName = "encoder"
    // The task's own output property, not a path spelled a second time. Gradle takes the
    // dependency from the provider, so there is no `dependsOn` to forget and no way for the two
    // to disagree about where the files are.
    from(verifyModel.flatMap { it.into }) { into("dependencyskills/encoder") }
    from(projectDir) { include("LICENSE", "NOTICE"); into("META-INF") }
    from("model/RECIPE.md") { into("dependencyskills/encoder") }
    manifest {
        attributes(
            "Implementation-Title" to "dependencyskills encoder",
            "Implementation-Version" to project.version,
            "Encoder-Model" to "BAAI/bge-small-en-v1.5",
            "Encoder-Format" to "gguf-f16",
            "Encoder-Dimensions" to "384",
            // RAD-0048 measured this; the GGUF's own metadata says CLS and is NOT the value to
            // use. `dsc_encoder_load` requires pooling as an argument for exactly this reason.
            "Encoder-Pooling" to "mean",
            "Encoder-License" to "MIT",
            "Encoder-Digest" to "86776c71a9890f0246d12022ee8e5e9cf382012917ad8e611bb269b91f6b3e21",
        )
    }
}

// Deliberately NOT wired into `assemble`. A contributor who is not shipping a model should not
// pay to build one; the publication below carries the dependency, so `publish` still builds it.

publishing {
    publications.create<MavenPublication>("encoder") {
        artifactId = "encoder"
        artifact(encoderJar)
        pom {
            name = "dependencyskills encoder"
            description = "The default embedding model for the dependencyskills codex. The manifest names which model; the artifact does not, so swapping it is a version bump rather than a new coordinate."
            licenses { license { name = "MIT"; url = "https://opensource.org/licenses/MIT" } }
        }
    }
}
