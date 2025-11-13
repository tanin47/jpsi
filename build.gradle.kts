import org.apache.tools.ant.taskdefs.Java
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.nio.file.Paths

plugins {
    `java-library`
    application
    jacoco
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

tasks.register<Exec>("jdeps") {
    dependsOn("assemble")
    val jdepsBin = Paths.get(System.getProperty("java.home"), "bin", "jdeps")
    commandLine(
        jdepsBin,
        "--module-path", tasks.named<JavaCompile>("compileJava").get().classpath.asPath,
        "--multi-release", "base",
        "--print-module-deps",
        tasks.jar.get().outputs.files.asPath,
    )
}

tasks.register<Exec>("jlink") {
    dependsOn("assemble", "copyDependencies", "copyJar")
    val jlinkBin = Paths.get(System.getProperty("java.home"), "bin", "jlink")
    outputs.file("./build/jlink")

    commandLine(
        jlinkBin,
        "--ignore-signing-information",
        "--no-header-files", "--no-man-pages", "--strip-debug",
        "-p", tasks.named("copyJar").get().outputs.files.singleFile.absolutePath,
        "--add-modules", "java.base,java.desktop,java.logging,java.net.http,jcef,jdk.unsupported,org.bouncycastle.lts.pkix,org.bouncycastle.lts.prov,org.bouncycastle.lts.util,java.security.jgss,java.security.sasl,jdk.crypto.ec,jdk.crypto.cryptoki",
        "--output", layout.buildDirectory.dir("jlink").get().asFile.absolutePath,
    )
}

tasks.register<Exec>("jpackage") {
    dependsOn("jlink")
    val jreHome = "/Users/tanin/projects/jpsi/jdk/jbr_jcef-21.0.9-osx-aarch64-b895.147/Contents/Home"
    val jpackageBin = Paths.get(System.getProperty("java.home"), "bin", "jpackage")

    commandLine(
        jpackageBin,
        "--app-content", Paths.get(jreHome).parent.resolve("Frameworks").toFile().absolutePath,
        "--main-class", mainClassName,
        "--module", "tanin.jpsi/tanin.jpsi.Main",
        "--runtime-image", tasks.named("jlink").get().outputs.files.singleFile.absolutePath,
        "--vendor", "tanin.jpsi",
        "--module-path", tasks.named("copyJar").get().outputs.files.singleFile.absolutePath,
        "--dest", layout.buildDirectory.dir("dist").get().asFile.absolutePath,
    )

}
