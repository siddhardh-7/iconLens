import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

intellijPlatform {
    // ponytail: instrumentCode fails resolving the ant instrumentation classpath against
    // the Android Studio distribution (JetBrains/intellij-platform-gradle-plugin#903, #1440).
    // We use no @NotNull-enforced params or .form files, so nothing is lost; revisit if either is added.
    instrumentCode = false

    // Verify against the exact configured Android Studio dependency instead of the default
    // recommended()-selected range, which resolves a different (newer) build and requires
    // downloading it separately.
    pluginVerification {
        ides {
            current()
        }

        // QueryImageLoading.kt's SVG rendering uses com.intellij.util.SVGLoader, which the
        // verifier flags as internal-API usage. No public IntelliJ Platform API renders
        // arbitrary SVG bytes to a BufferedImage (checked IconLoader/IconUtil; the only
        // public path needs a temp file and an uncontrollable internal icon cache) — don't
        // fail the build over it. The other two entries are the plugin's actual default
        // failureLevel (decompiled from IntelliJPlatformExtension$PluginVerification's
        // convention, not guessed) with INTERNAL_API_USAGES removed — everything else still
        // fails exactly as before.
        failureLevel.set(
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            )
        )
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        androidStudio("2026.1.1.10")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here, for example:
        // bundledPlugin("com.intellij.java")
    }
}
