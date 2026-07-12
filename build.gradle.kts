plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// The :storage parent project picks up legacy sources under storage/src/ that depend on
// excluded modules. Exclude its source directories so only child projects compile.
project(":storage") {
    the<JavaPluginExtension>().sourceSets {
        named("main") {
            java.setSrcDirs(emptyList<String>())
        }
    }
    tasks.named("compileKotlin") { enabled = false }
}
