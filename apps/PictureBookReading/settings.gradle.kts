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
        flatDir {
            dirs("D:/Android/opencv/4.9/OpenCV-android-sdk/sdk/build/outputs/aar")
        }
    }
}
rootProject.name = "PictureBookReading"
include(":app")