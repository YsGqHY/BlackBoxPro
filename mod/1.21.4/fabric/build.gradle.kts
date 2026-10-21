import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import java.util.Properties

plugins {
    id("fabric-loom")
    id("org.jetbrains.kotlin.jvm")
}

val localProps = Properties().apply {
    projectDir.parentFile.resolve("gradle.properties").reader().use(::load)
}
fun localProp(key: String) = localProps.getProperty(key)
    ?: error("Missing property '$key' in mod/1.21.4/gradle.properties")

evaluationDependsOn(":1.21.4:runtime")

val commonSourceSet = project(":common").the<SourceSetContainer>()["main"]
val runtimeSharedDir = project(":1.21.4:runtime").file("src/main/kotlin/com/blackboxpro/runtime")

the<SourceSetContainer>()["main"].java.srcDir(runtimeSharedDir)

base {
    archivesName.set("BlackBoxPro-fabric-${localProp("minecraft_version")}")
}

loom {
    accessWidenerPath.set(file("src/main/resources/blackboxpro.accesswidener"))
}

repositories {
    maven("https://repo.tabooproject.org/repository/releases/")
    mavenCentral()
}

val shadeReflex: Configuration by configurations.creating

dependencies {
    implementation(project(":common"))
    implementation("org.tabooproject.reflex:reflex:1.2.3")
    shadeReflex("org.tabooproject.reflex:reflex:1.2.3")
    shadeReflex("org.tabooproject.reflex:analyser:1.2.3")

    minecraft("com.mojang:minecraft:${localProp("minecraft_version")}")
    mappings("net.fabricmc:yarn:${localProp("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${localProp("loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${localProp("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${localProp("fabric_kotlin_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
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

tasks.named<Jar>("jar") {
    from(commonSourceSet.output)
    from({ shadeReflex.files.map { zipTree(it) } }) {
        exclude("META-INF/**")
        exclude("org/objectweb/asm/**")
        exclude("org/apache/commons/**")
        exclude("kotlin/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("sourcesJar") {
    from(commonSourceSet.allSource)
}
