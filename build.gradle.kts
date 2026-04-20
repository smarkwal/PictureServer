plugins {
    application
    jacoco
    id("com.diffplug.spotless") version "6.25.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass = "net.markwalder.pictureserver.Main"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = version
    }
}

tasks.build {
    dependsOn("fatJar")
}

// Build-time enforcement of formatting rules. The editor-side equivalent is
// .editorconfig — keep the two in sync when changing rules here.
spotless {
    java {
        importOrder("\\#", "")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("kotlinDsl") {
        target("*.kts")
        trimTrailingWhitespace()
        endWithNewline()
        indentWithSpaces(4)
    }
    format("javascript") {
        target("src/**/*.js")
        trimTrailingWhitespace()
        endWithNewline()
        indentWithSpaces(4)
    }
    format("css") {
        target("src/**/*.css")
        trimTrailingWhitespace()
        endWithNewline()
        indentWithSpaces(4)
    }
    format("htmlSvg") {
        target("src/**/*.html", "src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
        indentWithSpaces(2)
    }
    format("markdown") {
        target("**/*.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a self-contained executable JAR with all runtime dependencies."
    archiveClassifier = "all"
    manifest {
        attributes["Main-Class"] = application.mainClass
        attributes["Implementation-Version"] = version
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
