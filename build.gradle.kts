import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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
