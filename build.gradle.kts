import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync

val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows

@Suppress("UNCHECKED_CAST")
val execTaskClass = Class.forName("org.gradle.api.tasks.Exec") as Class<Exec>
@Suppress("UNCHECKED_CAST")
val syncTaskClass = Class.forName("org.gradle.api.tasks.Sync") as Class<Sync>

val rootGradlew = if (isWindows) file("gradlew.bat") else file("gradlew")
val pluginGradlew = if (isWindows) file("plugin/gradlew.bat") else file("plugin/gradlew")
val forge1122Gradlew = if (isWindows) file("mod/1.12.2/gradlew.bat") else file("mod/1.12.2/gradlew")
val jarExecutable = if (isWindows) file(System.getProperty("java.home") + "/bin/jar.exe") else file(System.getProperty("java.home") + "/bin/jar")
val commonProjectDir = file("common")
val modProjectDir = file("mod")
val pluginProjectDir = file("plugin")
val forge1122ProjectDir = file("mod/1.12.2")
val commonGradleUserHome = file(".gradle-user-home/common")
val forge1122GradleUserHome = file(".gradle-user-home/forge1122")
val rootProps = java.util.Properties()
val rootPropsStream = java.io.FileInputStream(file("gradle.properties"))
rootProps.load(rootPropsStream)
rootPropsStream.close()
val projectVersion = rootProps.getProperty("version", "0.0.0")
val mod1211NeoForgeJar = file("mod/1.21.1/neoforge/build/libs/BlackBoxPro-neoforge-1.21.1-" + projectVersion + ".jar")

tasks.register("common_build", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "构建 common jar"
        task.workingDir = rootDir
        task.commandLine(rootGradlew.absolutePath, "-p", commonProjectDir.absolutePath, "--no-daemon", "jar")
    }
})

tasks.register("plugin_build", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "build 独立项目 plugin"
        task.workingDir = pluginProjectDir
        task.commandLine(pluginGradlew.absolutePath, "--no-daemon", "build")
    }
})

tasks.register("plugin_clean", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "clean 独立项目 plugin"
        task.workingDir = pluginProjectDir
        task.commandLine(pluginGradlew.absolutePath, "--no-daemon", "clean")
        task.isIgnoreExitValue = true
    }
})

tasks.register("forge1122_build", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "build 独立项目 1.12.2"
        task.dependsOn("common_build")
        task.workingDir = forge1122ProjectDir
        task.commandLine(
            forge1122Gradlew.absolutePath,
            "-g",
            forge1122GradleUserHome.absolutePath,
            "--no-daemon",
            "clean",
            "build"
        )
    }
})


tasks.register("forge1122_clean", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "clean 独立项目 1.12.2"
        task.workingDir = forge1122ProjectDir
        task.commandLine(
            forge1122Gradlew.absolutePath,
            "-g",
            forge1122GradleUserHome.absolutePath,
            "--no-daemon",
            "clean"
        )
        task.isIgnoreExitValue = true
    }
})

tasks.register("mod2111_build", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "构建 mod 1.21.11 客户端产物"
        task.workingDir = rootDir
        task.commandLine(
            rootGradlew.absolutePath,
            "-p",
            modProjectDir.absolutePath,
            "--no-daemon",
            ":1.21.11:runtime:build",
            ":1.21.11:fabric:build",
            ":1.21.11:neoforge:build"
        )
    }
})

tasks.register("mod1214_build", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "构建 mod 1.21.4 客户端产物（Fabric）"
        task.workingDir = rootDir
        task.commandLine(
            rootGradlew.absolutePath,
            "-p",
            modProjectDir.absolutePath,
            "--no-daemon",
            ":1.21.4:runtime:build",
            ":1.21.4:fabric:build"
        )
    }
})

tasks.register("mod1211_build", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        task.group = "standalone"
        task.description = "构建 mod 1.21.1 客户端产物"
        task.workingDir = rootDir
        task.commandLine(
            rootGradlew.absolutePath,
            "-p",
            modProjectDir.absolutePath,
            "--no-daemon",
            ":1.21.1:runtime:build",
            ":1.21.1:fabric:build",
            ":1.21.1:neoforge:classes",
            ":1.21.1:neoforge:processResources"
        )
    }
})

tasks.register("mod1211_pack_neoforge", execTaskClass, object : Action<Exec> {
    override fun execute(task: Exec) {
        mod1211NeoForgeJar.parentFile.mkdirs()
        task.group = "standalone"
        task.description = "打包 mod 1.21.1 NeoForge 客户端 jar"
        task.workingDir = rootDir
        task.dependsOn("mod1211_build")
        task.commandLine(
            jarExecutable.absolutePath,
            "--create",
            "--file",
            mod1211NeoForgeJar.absolutePath,
            "-C",
            file("mod/1.21.1/neoforge/build/classes/kotlin/main").absolutePath,
            ".",
            "-C",
            file("mod/1.21.1/runtime/build/classes/kotlin/main").absolutePath,
            "."
        )
    }
})

val collectJars = tasks.register("collectJars", syncTaskClass, object : Action<Sync> {
    override fun execute(task: Sync) {
        task.group = "build"
        task.description = "收集客户端 mod 与服务端插件的 jar 到根 build/libs"
        task.into(layout.buildDirectory.dir("libs"))
        // 1.21.11
        task.from(fileTree("mod/1.21.11/fabric/build/libs"))
        task.from(fileTree("mod/1.21.11/neoforge/build/libs"))
        // 1.21.4
        task.from(fileTree("mod/1.21.4/fabric/build/libs"))
        // 1.21.1
        task.from(fileTree("mod/1.21.1/fabric/build/libs"))
        task.from(fileTree("mod/1.21.1/neoforge/build/libs"))
        // 1.12.2
        task.from(fileTree("mod/1.12.2/forge/build/libs"))
        // plugin
        task.from(fileTree("plugin/build/libs"))
    }
})

tasks.register("buildAll", object : Action<Task> {
    override fun execute(task: Task) {
        task.group = "build"
        task.description = "构建 common、1.21.11/1.21.4/1.21.1 客户端、1.12.2 客户端与服务端插件并收集 jar 到根 build/libs"
        task.dependsOn("common_build", "mod2111_build", "mod1214_build", "mod1211_pack_neoforge", "plugin_build", "forge1122_build")
        task.finalizedBy(collectJars)
    }
})

tasks.register("cleanAll", object : Action<Task> {
    override fun execute(task: Task) {
        task.group = "build"
        task.description = "清理所有模块的 build 目录（含根 build 目录）"
        task.dependsOn("plugin_clean", "forge1122_clean")
        task.doLast {
            delete(
                layout.buildDirectory,
                file("common/build"),
                file("plugin/build"),
                file("mod/build"),
                file("mod/1.21.11/runtime/build"),
                file("mod/1.21.11/fabric/build"),
                file("mod/1.21.11/neoforge/build"),
                file("mod/1.21.4/runtime/build"),
                file("mod/1.21.4/fabric/build"),
                file("mod/1.21.1/runtime/build"),
                file("mod/1.21.1/fabric/build"),
                file("mod/1.21.1/neoforge/build"),
                file("mod/1.12.2/build"),
                file("mod/1.12.2/runtime/build"),
                file("mod/1.12.2/forge/build")
            )
        }
    }
})