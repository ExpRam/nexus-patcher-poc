plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "ru.expram.patcher"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.javassist:javassist:3.33.0-GA")
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "ru.expram.patcher.Patcher",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
}