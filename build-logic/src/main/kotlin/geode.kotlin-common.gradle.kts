plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("geode.provenance")
}

ktlint {
    android.set(true)
}

// Detekt lives here rather than in app/build.gradle.kts so that it actually covers the code.
// It was applied to :app alone, which meant the whole engine - every scene, the GL capability
// tier, the analysis graph, roughly all of the code that is hard to get right - was outside
// static analysis entirely, while the gate still reported green. Each module carries its own
// baseline of what was already there on the day it was brought under the tool; the point is
// that nothing NEW can regress, which is the same bargain config/detekt/detekt.yml strikes
// with its thresholds.
extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
}

// Which JDK compiles, as opposed to what that JDK emits. Left unset it silently follows
// JAVA_HOME, so a developer on Android Studio's JBR and CI on temurin were running different
// compilers against the same source - the drift that hid a broken detekt gate locally for as
// long as CI stayed green. 25 rather than the newest available: it is LTS, and the ceiling here
// is set by the least tolerant tool in the build, not by the newest JDK released.
extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// What the compiler emits, which is a separate decision from the toolchain above and the only
// one of the two that reaches a device. Moving this is gated on what D8/R8 will dex at
// minSdk 26, so it stays at 17 until that is measured on its own.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Detekt's embedded compiler needs both a supported analysis JDK and bytecode
// target. The daemon is separately pinned to 21 in gradle-daemon-jvm.properties;
// setting only jvmTarget cannot fix JavaVersion.current() on a Java 25 runtime.
val analysisJdk = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    jdkHome.set(analysisJdk.map { it.metadata.installationPath })
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
    jdkHome.set(analysisJdk.map { it.metadata.installationPath })
}
