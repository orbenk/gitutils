plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.orbenk"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

intellij {
    version.set("2023.3.2")
    type.set("IC") // IntelliJ IDEA Community — funciona também na Ultimate
    plugins.set(listOf("Git4Idea"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")   // 2023.3
        untilBuild.set("251.*") // 2025.1
    }

    // Necessário apenas para publicar no Marketplace
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN") ?: "")
        privateKey.set(System.getenv("PRIVATE_KEY") ?: "")
        password.set(System.getenv("PRIVATE_KEY_PASSWORD") ?: "")
    }
    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN") ?: "")
    }
}
