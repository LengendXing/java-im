plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.im"
version = "0.1.1"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

javafx {
    version = "17"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation(project(":protocol"))
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.github.leewyatt:rxcontrols:11.0.3")
}

application {
    mainClass.set("com.im.client.Main")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
