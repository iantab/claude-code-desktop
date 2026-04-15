plugins {
    application
    alias(libs.plugins.javafx)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.controls", "javafx.web")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.flexmark.core)
    implementation(libs.flexmark.tables)
    implementation(libs.flexmark.gfm.strikethrough)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "com.claudecodejava.App"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics,javafx.web,ALL-UNNAMED"
    )
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
