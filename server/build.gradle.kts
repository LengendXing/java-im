dependencies {
    implementation(project(":protocol"))
    implementation("io.vertx:vertx-core:4.5.4")
    implementation("io.vertx:vertx-web:4.5.4")
    implementation("io.vertx:vertx-redis-client:4.5.4")
    implementation("io.vertx:vertx-mysql-client:4.5.4")
    implementation("io.vertx:vertx-config:4.5.4")
    implementation("io.vertx:vertx-hazelcast:4.5.4")
    implementation("org.noear:folkmq-transport-netty:1.7.13")
    implementation("com.alibaba.nacos:nacos-client:2.3.2")
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    implementation("com.eatthepath:pushy:0.15.4")
    implementation("com.google.firebase:firebase-admin:9.4.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("mysql:mysql-connector-java:8.0.33")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.vertx:vertx-junit5:4.5.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    manifest { attributes["Main-Class"] = "com.im.server.Main" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
