pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mozilla Maven — only used for org.mozilla.geckoview.
        // Declared with a content filter so build tools & F-Droid can verify
        // no other artefacts are pulled from this host.
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
            content {
                includeGroupByRegex("org\\.mozilla.*")
            }
        }
    }
}

rootProject.name = "Omni Browser"
include(":app")
