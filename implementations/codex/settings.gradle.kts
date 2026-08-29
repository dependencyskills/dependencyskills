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
include("summariser")
include("server")
