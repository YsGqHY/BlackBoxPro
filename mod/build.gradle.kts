import java.util.Properties

plugins {
    id("fabric-loom") version "1.14-SNAPSHOT" apply false
    id("net.neoforged.moddev") version "2.0.140" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
}

val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
val gradlew1122 = if (isWindows) file("1.12.2/gradlew.bat") else file("1.12.2/gradlew")

val sharedProps = Properties().apply {
    file("gradle.properties").reader().use(::load)
}

// 从根目录 gradle.properties 读取版本号（单一来源）
val rootProps = Properties().apply {
    file("../gradle.properties").reader().use(::load)
}

allprojects {
    group = "com.blackboxpro"
    version = rootProps.getProperty("version", sharedProps.getProperty("version", "0.0.0"))
}

subprojects {
    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

val build1122 = tasks.register<Exec>("build1122") {
    group = "build"
    description = "使用 1.12.2 独立 Gradle 构建 runtime 与 forge"
    dependsOn(":common:jar")
    workingDir = file("1.12.2")
    commandLine(gradlew1122.absolutePath, "--no-daemon", "build")
}

val clean1122 = tasks.register<Exec>("clean1122") {
    group = "build"
    description = "清理 1.12.2 独立 Gradle 构建产物"
    workingDir = file("1.12.2")
    commandLine(gradlew1122.absolutePath, "--no-daemon", "clean")
    isIgnoreExitValue = true
}

val collectJars = tasks.register<Copy>("collectJars") {
    group = "build"
    description = "收集 common/runtime/fabric/neoforge/1.12.2 的 jar 到 mod/build/libs"
    dependsOn(build1122)

    // 仅做产物收集：遇到重复文件名时跳过重复项，避免 Gradle 9+ 直接失败
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    into(layout.buildDirectory.dir("libs"))
    from(project(":common").layout.buildDirectory.dir("libs"))

    // 1.21.11
    from(project(":1.21.11:runtime").layout.buildDirectory.dir("libs"))
    from(project(":1.21.11:fabric").layout.buildDirectory.dir("libs"))
    from(project(":1.21.11:neoforge").layout.buildDirectory.dir("libs"))

    // 1.21.4
    from(project(":1.21.4:runtime").layout.buildDirectory.dir("libs"))
    from(project(":1.21.4:fabric").layout.buildDirectory.dir("libs"))

    // 1.21.1
    from(project(":1.21.1:runtime").layout.buildDirectory.dir("libs"))
    from(project(":1.21.1:fabric").layout.buildDirectory.dir("libs"))
    from(project(":1.21.1:neoforge").layout.buildDirectory.dir("libs"))

    // 1.12.2
    from(file("1.12.2/build/libs"))
}

tasks.register("buildAll") {
    group = "build"
    description = "构建 common、1.21.11、1.21.1、1.12.2 并收集 jar"
    dependsOn(
        ":common:build",

        // 1.21.11
        ":1.21.11:runtime:build",
        ":1.21.11:fabric:build",
        ":1.21.11:neoforge:build",

        // 1.21.4
        ":1.21.4:runtime:build",
        ":1.21.4:fabric:build",

        // 1.21.1
        ":1.21.1:runtime:build",
        ":1.21.1:fabric:build",
        ":1.21.1:neoforge:build",

        // 1.12.2
        build1122
    )
    finalizedBy(collectJars)
}
