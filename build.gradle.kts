plugins {
    id("java")
    id("com.google.protobuf") version "0.9.4" apply false
}

group = "com.im"
version = "0.4.1"

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
