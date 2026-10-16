import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// 1.21.4 仅接入 Fabric 端：runtime 模块只承载与 MC 无关的共享桥接层
// （com.blackboxpro.runtime.*），不引入 NeoForm / NeoForge 依赖。
// Fabric 模块通过 srcDir 直接编译这些共享源码。
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

base {
    archivesName.set("BlackBoxPro-runtime-${property("minecraft_version")}")
}

repositories {
    maven("https://repo.tabooproject.org/repository/releases/")
    mavenCentral()
}

dependencies {
    api(project(":common"))
    api("org.slf4j:slf4j-api:2.0.16")
    implementation("org.tabooproject.reflex:reflex:1.2.3")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}
