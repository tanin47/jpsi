import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.panteleyev.jpackage.JPackageTask

plugins {
    `java-library`
    application
    jacoco
    id("org.panteleyev.jpackageplugin") version "1.7.6"
}

group = "tanin.jpsi"
version = "1.0.0"

description = "Build cross-platform desktop apps with Java, JavaScript, HTML, and CSS"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    sourceSets {
        main {
            resources {
                srcDir("build/compiled-frontend-resources")
            }
        }
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report

    reports {
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle:bcpkix-lts8on:2.73.9")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.36.0")
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging {
        events("started", "passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        showExceptions = true
        showCauses = true
        exceptionFormat = TestExceptionFormat.FULL
    }

}

var mainClassName = "tanin.jpsi.Main"
application {
    mainClass.set(mainClassName)
}

tasks.jar {
    manifest.attributes["Main-Class"] = mainClassName
}

tasks.register<Exec>("compileTailwind") {
    inputs.files(fileTree("frontend"))
    outputs.dir("build/compiled-frontend-resources")

    environment("NODE_ENV", "production")

    commandLine(
        "./node_modules/.bin/postcss",
        "./frontend/stylesheets/tailwindbase.css",
        "--config",
        ".",
        "--output",
        "./build/compiled-frontend-resources/assets/stylesheets/tailwindbase.css"
    )
}

tasks.register<Exec>("compileSvelte") {
    inputs.files(fileTree("frontend"))
    outputs.dir("build/compiled-frontend-resources")

    environment("NODE_ENV", "production")
    environment("ENABLE_SVELTE_CHECK", "true")

    commandLine(
        "./node_modules/webpack/bin/webpack.js",
        "--config",
        "./webpack.config.js",
        "--output-path",
        "./build/compiled-frontend-resources/assets",
        "--mode",
        "production"
    )
}

tasks.processResources {
    dependsOn("compileTailwind")
    dependsOn("compileSvelte")
}

tasks.named("sourcesJar") {
    dependsOn("compileTailwind")
    dependsOn("compileSvelte")
}

// For CI validation.
tasks.register("printVersion") {
    doLast {
        print("$version")
    }
}

tasks.register("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath).into(layout.buildDirectory.dir("jmods"))
}

tasks.register("copyJar", Copy::class) {
    from(tasks.jar).into(layout.buildDirectory.dir("jmods"))
}

tasks.named<JPackageTask>("jpackage") {
    dependsOn("assemble", "copyDependencies", "copyJar")
    vendor = "tanin.jpsi"
    destination = layout.buildDirectory.dir("dist")
    println(System.getProperty("java.home"))
    runtimeImage = File(System.getProperty("java.home"))
    module = "tanin.jpsi/tanin.jpsi.Main"
    modulePaths.setFrom(tasks.named("copyJar"))
    mainClass = mainClassName
    appContent.setFrom(System.getProperty("java.home") + "/../Frameworks")
}
