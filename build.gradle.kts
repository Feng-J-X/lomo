// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    // alias(libs.plugins.androidxBaselineProfile) apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    alias(libs.plugins.versionCatalogUpdate)
    alias(libs.plugins.benManesVersions)
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.0")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.0")
        }
    }
}

// Configure the dependency update check to only offer stable versions
fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

// tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
//     rejectVersionIf {
//         isNonStable(candidate.version) && !isNonStable(currentVersion)
//     }
// }
