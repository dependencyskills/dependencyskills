// The default encoder, packaged for Maven Central.
//
// The weights are NOT in this repository. They are fetched from their origin at build
// time and verified against pinned SHA-256 digests, so the artifact is reproducible and
// its provenance is checkable rather than asserted. A repository that carries 134 MB of
// third-party binary is a repository nobody can clone.

plugins { base; `maven-publish` }

version = providers.gradleProperty("encoderVersion").get()   // versions with the model, not the plugin



// BAAI/bge-small-en-v1.5, MIT. First-party ONNX export - not a community re-quantisation.
val origin = "https://huggingface.co/BAAI/bge-small-en-v1.5/resolve/main"
val files = mapOf(
    "model.onnx"     to ("onnx/model.onnx" to "828e1496d7fabb79cfa4dcd84fa38625c0d3d21da474a00f08db0f559940cf35"),
    "tokenizer.json" to ("tokenizer.json"  to "d241a60d5e8f04cc1b2b3e9ef7a4921b27bf526d9f6050ab90f9267a1f9e5c66"),
)

val download by tasks.registering {
    description = "Fetch the model weights and verify them against the pinned digests."
    val out = layout.buildDirectory.dir("model")
    outputs.dir(out)
    doLast {
        files.forEach { (name, spec) ->
            val (path, sha) = spec
            val target = out.get().file(name).asFile
            if (!target.exists()) {
                logger.lifecycle("fetching $name")
                target.parentFile.mkdirs()
                uri("$origin/$path").toURL().openStream().use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
            val actual = java.security.MessageDigest.getInstance("SHA-256")
                .digest(target.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == sha) {
                "$name failed verification.\n  expected $sha\n  actual   $actual\n" +
                "The upstream artifact changed or the download is corrupt. Do not package it."
            }
            logger.lifecycle("verified $name (${target.length() / 1_000_000} MB)")
        }
    }
}

val encoderJar by tasks.registering(Jar::class) {
    dependsOn(download)
    archiveBaseName = "encoder"
    from(layout.buildDirectory.dir("model")) { into("dependencyskills/encoder") }
    from(projectDir) { include("LICENSE", "NOTICE"); into("META-INF") }
    manifest {
        attributes(
            "Implementation-Title" to "dependencyskills encoder",
            "Implementation-Version" to project.version,
            "Encoder-Model" to "BAAI/bge-small-en-v1.5",
            "Encoder-Dimensions" to "384",
            "Encoder-Pooling" to "mean",     // measured in RAD-0048; CLS is worse for this model
            "Encoder-License" to "MIT",
        )
    }
}

tasks.assemble { dependsOn(encoderJar) }

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
