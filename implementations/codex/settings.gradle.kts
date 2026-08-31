// Toolchain resolution, so `JavaLanguageVersion.of(26)` is satisfiable on a machine that does not
// already have it rather than failing with "no matching toolchains". The modules pin 26 because
// they emit Java 22 bytecode and their tests need jdk.incubator.vector; leaving that to whatever
// JDK happened to be installed is how the test JVM ended up being one that has neither.
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

rootProject.name = "codex"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

include("core")
include("encoder")
include("harvester")
include("classifier")
include("inference")
include("index")
include("indexer")
include("summariser")
include("server")
