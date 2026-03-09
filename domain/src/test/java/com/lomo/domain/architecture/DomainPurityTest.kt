package com.lomo.domain.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainPurityTest {
    private val moduleRoot = resolveModuleRoot("domain")

    @Test
    fun `domain does not depend on inject annotations`() {
        val sourceRoot = moduleRoot.resolve("src/main/java")
        val kotlinFiles = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders =
            kotlinFiles.filter { file ->
                val text = file.readText()
                text.contains("@Inject") || text.contains("javax.inject.Inject")
            }

        assertTrue(
            "Domain layer must stay framework-agnostic. Offenders: ${offenders.joinToString { it.path }}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `domain source only uses model repository and usecase categories`() {
        val sourceRoot = moduleRoot.resolve("src/main/java/com/lomo/domain")
        val allowedTopLevel = setOf("model", "repository", "usecase")
        val kotlinFiles = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders =
            kotlinFiles.filter { file ->
                val relative = file.relativeTo(sourceRoot).invariantSeparatorsPath
                val topLevel = relative.substringBefore('/')
                topLevel !in allowedTopLevel
            }

        assertTrue(
            "Domain source categories must be model/repository/usecase. Offenders: " +
                offenders.joinToString { it.path },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `domain repository package only declares interfaces`() {
        val sourceRoot = moduleRoot.resolve("src/main/java/com/lomo/domain/repository")
        val kotlinFiles = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders =
            kotlinFiles.filter { file ->
                val declarations =
                    file
                        .readLines()
                        .filter { line -> line.trimStart().matches(TOP_LEVEL_DECLARATION_PATTERN) }
                declarations.any { line -> !line.contains("interface ") }
            }

        assertTrue(
            "Domain repository contracts must be interfaces only. Offenders: " +
                offenders.joinToString { it.path },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `domain source does not keep compatibility typealias`() {
        val sourceRoot = moduleRoot.resolve("src/main/java/com/lomo/domain")
        val kotlinFiles = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders =
            kotlinFiles.filter { file ->
                file
                    .readLines()
                    .any { line -> line.trimStart().startsWith("typealias ") }
            }

        assertTrue(
            "Domain source must not declare typealias compatibility shims. Offenders: " +
                offenders.joinToString { it.path },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `domain module does not use android gradle plugin`() {
        val buildFile = moduleRoot.resolve("build.gradle.kts")
        val text = buildFile.readText()

        assertTrue(
            "Domain module must not apply Android Gradle plugins.",
            !text.contains("androidLibrary") && !text.contains("com.android.library"),
        )
    }

    @Test
    fun `domain module does not keep android manifest`() {
        val manifestFile = moduleRoot.resolve("src/main/AndroidManifest.xml")

        assertTrue(
            "Domain module must not keep AndroidManifest.xml.",
            !manifestFile.exists(),
        )
    }

    @Test
    fun `domain module does not depend on inject library`() {
        val buildFile = moduleRoot.resolve("build.gradle.kts")
        val text = buildFile.readText()

        assertTrue(
            "Domain module must not depend on inject libraries.",
            !text.contains("javax.inject") && !text.contains("jakarta.inject") && !text.contains("libs.javax.inject"),
        )
    }

    private fun resolveModuleRoot(moduleName: String): File {
        val currentDirPath = System.getProperty("user.dir") ?: "."
        val currentDir = File(currentDirPath)
        val candidateRoots =
            listOf(
                currentDir,
                currentDir.resolve(moduleName),
            )
        return checkNotNull(
            candidateRoots.firstOrNull { dir ->
                dir.name == moduleName && dir.resolve("build.gradle.kts").exists()
            },
        ) {
            "Failed to resolve $moduleName module root from $currentDirPath"
        }
    }

    private companion object {
        val TOP_LEVEL_DECLARATION_PATTERN =
            Regex(
                """^(?:public\s+|internal\s+|private\s+)?(?:sealed\s+)?(?:data\s+)?(?:class|object|interface|enum\s+class)\b.*""",
            )
    }
}
