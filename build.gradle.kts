import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.5.5"
    kotlin("jvm") version "1.5.31"
    kotlin("plugin.spring") version "1.5.31"
    kotlin("plugin.serialization") version "1.5.31"
    id("org.jetbrains.kotlinx.kover") version "0.5.0-RC"
}

group = "com.rarible.flow"
version = "1.1.4"
java.sourceCompatibility = JavaVersion.VERSION_1_8

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.openapitools:openapi-generator-gradle-plugin:5.0.0")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.5.21")
    }
}

allprojects {

    apply {
        plugin("java")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("io.spring.dependency-management")
//        plugin("jacoco")
    }

    repositories {
        mavenLocal()

        mavenCentral()

        maven {
            url = uri("https://jitpack.io")
        }

        maven {
            url = uri("https://repo.rarible.org/repository/maven-public")
            metadataSources {
                mavenPom()
                artifact()
            }
        }

        maven {
            name = "nexus-maven-public"
            url = uri("http://nexus.rarible.int/repository/maven-public/")
            isAllowInsecureProtocol = true
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        maven {
            name = "nexus-maven-public"
            url = uri("http://nexus-ext.rarible.int/repository/maven-public/")
            isAllowInsecureProtocol = true
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf("-progressive", "-Xskip-metadata-version-check")
        }

    }
}

subprojects {
    dependencies {
        implementation(enforcedPlatform("org.springframework.boot:spring-boot-dependencies:2.5.5"))
        implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2020.0.3"))
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
        implementation(rootProject.libs.flow.sdk)

        implementation("org.springframework.boot:spring-boot-autoconfigure")
        implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
        implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("io.projectreactor:reactor-test")
        testImplementation(rootProject.testLibs.rarible.core.test)
        testImplementation(rootProject.testLibs.bundles.kotest)

    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "1.8"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        reports {
            junitXml.required.set(true)
            junitXml.mergeReruns.set(true)
            junitXml.outputLocation.set(
                project.buildDir.resolve("surefire-reports")
            )
        }

        extensions.configure(kotlinx.kover.api.KoverTaskExtension::class) {
            excludes = listOf(
                "com.rarible.flow.core.config.*",
                "com.rarible.flow.api.config.*",
                "com.rarible.flow.scanner.config.*",
                "com.rarible.flow.scanner.ScannerApplicationKt",
                "com.rarible.flow.scanner.migrations.*"
            )
            includes = listOf("com.rarible.flow.*")
        }
    }
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = true
}

//tasks.koverCollectProjectsReports {
//    outputDir.set(layout.buildDirectory.dir("all-projects-reports") )
//}

//task<JacocoReport>("coverage") {
//    dependsOn("coverageMerge")
//    additionalSourceDirs.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
//    sourceDirectories.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
//    classDirectories.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].output })
//    executionData.setFrom(project.fileTree(".") {
//        include("**/build/jacoco/*.exec")
//        exclude("**/build/jacoco/coverageMerge.exec")
//        exclude("/target/jacoco-aggregate.exec")
//    })
//
//    reports {
//        xml.required.set(true)
//        xml.outputLocation.set(file("${buildDir}/reports/jacoco/coverage.xml"))
//        csv.required.set(true)
//        html.required.set(true)
//        html.outputLocation.set(file("${buildDir}/reports/jacoco/html"))
//    }
//
//    copy {
//        from("$buildDir/jacoco/coverageMerge.exec")
//        rename("coverageMerge.exec", "jacoco.exec")
//        into("target/reports/jacoco")
//    }
//}



project("e2e") {
    tasks.withType<Test> {
        onlyIf {
            project.hasProperty("runE2e")
        }
    }
}

kover {
    this.coverageEngine.set(kotlinx.kover.api.CoverageEngine.JACOCO)
    instrumentAndroidPackage = false
}
