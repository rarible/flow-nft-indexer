plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}


dependencies {
    implementation(project(":backend-api-model"))
    implementation(project(":backend-core"))
    implementation(project(":util"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("com.netflix.graphql.dgs:graphql-dgs-client:4.9.7")
    implementation(rootProject.libs.bundles.flow.models)
    implementation(rootProject.libs.bundles.flow.apis)
    implementation(rootProject.libs.bundles.rarible.core)
    implementation(rootProject.libs.rarible.core.starter)
    implementation(rootProject.libs.rarible.core.logging)
    implementation(rootProject.libs.rarible.core.kafka)
    implementation(rootProject.libs.blockchain.scanner.model)
    implementation(rootProject.libs.rarible.currency.starer)
    implementation(rootProject.libs.rarible.core.apm.starter)
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    destinationDirectory.set(file("./target/boot"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
