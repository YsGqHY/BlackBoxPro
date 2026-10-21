import java.util.Properties

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://neoforged.forgecdn.net/releases/")
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "BlackBoxPro-mod"

// �������ԣ�ͨ�����ã��� kotlin �汾��fabric-language-kotlin �ȣ�
// ���汾ģ��ͨ���Լ��� gradle.properties ���� minecraft_version �Ȱ汾�������
val sharedProps = Properties().apply {
    file("gradle.properties").reader().use(::load)
}

gradle.beforeProject {
    sharedProps.forEach { key, value ->
        extensions.extraProperties[key.toString()] = value
    }
}

include("common")
include("1.21.11:runtime")
include("1.21.11:fabric")
include("1.21.11:neoforge")
include("1.21.4:runtime")
include("1.21.4:fabric")
include("1.21.1:runtime")
include("1.21.1:fabric")
include("1.21.1:neoforge")

project(":common").projectDir = file("../common")
project(":1.21.11").projectDir = file("1.21.11")
project(":1.21.11:runtime").projectDir = file("1.21.11/runtime")
project(":1.21.11:fabric").projectDir = file("1.21.11/fabric")
project(":1.21.11:neoforge").projectDir = file("1.21.11/neoforge")
project(":1.21.4").projectDir = file("1.21.4")
project(":1.21.4:runtime").projectDir = file("1.21.4/runtime")
project(":1.21.4:fabric").projectDir = file("1.21.4/fabric")
project(":1.21.1").projectDir = file("1.21.1")
project(":1.21.1:runtime").projectDir = file("1.21.1/runtime")
project(":1.21.1:fabric").projectDir = file("1.21.1/fabric")
project(":1.21.1:neoforge").projectDir = file("1.21.1/neoforge")
