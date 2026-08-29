// Resolves JLama's runtime classpath into ./lib-jlama so the probe runs the same way the
// llama.cpp one does - plain `java -cp`. The two runtimes are being compared, so neither gets a
// nicer harness than the other.
//
// The `java` plugin is here for its ATTRIBUTES, not to build anything: without a target JVM
// attribute Gradle cannot choose between a dependency's variants.
plugins { java }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

val jlama: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
    }
}

dependencies {
    jlama("com.github.tjake:jlama-core:0.8.4")
    jlama("com.github.tjake:jlama-native:0.8.4:osx-aarch_64")
}

tasks.register<Copy>("libs") {
    from(jlama)
    into(layout.projectDirectory.dir("../lib-jlama"))
}
