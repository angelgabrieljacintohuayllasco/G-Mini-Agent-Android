pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "G-Mini-Agent-Android"

include(":core")

// El modulo :app necesita el SDK de Android. Para correr solo las pruebas de
// logica de :core en una maquina sin ese SDK, exporta GMINI_SKIP_APP=true antes
// de invocar Gradle (por ejemplo: GMINI_SKIP_APP=true ./gradlew :core:test).
if (System.getenv("GMINI_SKIP_APP") != "true") {
    include(":app")
}
