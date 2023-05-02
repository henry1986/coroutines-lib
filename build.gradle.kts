import org.daiv.dependency.Versions

buildscript {
    repositories {
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        mavenCentral()
    }
    dependencies {
        classpath("org.daiv.dependency:DependencyHandling:0.1.57")
    }
}


plugins {
    kotlin("multiplatform") version "1.6.10"
    id("org.daiv.dependency.VersionsPlugin") version "0.1.4"
    id("signing")
    `maven-publish`
}

val versions = org.daiv.dependency.DefaultDependencyBuilder(Versions.current())
group = "org.daiv.coroutines"
version = versions.setVersion { coroutinesLib }
//version = "0.1.8-local"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    js(LEGACY) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
//    val nativeTarget = when {
//        hostOs == "Mac OS X" -> macosX64("native")
//        hostOs == "Linux" -> linuxX64("native")
//        isMingwX64 -> mingwX64("native")
//        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
//    }

    
    sourceSets {
        val commonMain by getting{
            dependencies{
                implementation(versions.kutil())
                implementation(versions.coroutines())
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
//        val nativeMain by getting
//        val nativeTest by getting
    }
}


val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

signing {
    sign(publishing.publications)
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar.get())
        pom {
            packaging = "jar"
            name.set("coroutines-lib")
            description.set("extention utils for kotlin coroutines")
            url.set("https://github.com/henry1986/coroutines-lib")
            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            issueManagement {
                system.set("Github")
                url.set("https://github.com/henry1986/coroutines-lib/issues")
            }
            scm {
                connection.set("scm:git:https://github.com/henry1986/coroutines-lib.git")
                developerConnection.set("scm:git:https://github.com/henry1986/coroutines-lib.git")
                url.set("https://github.com/henry1986/kutil")
            }
            developers {
                developer {
                    id.set("henry86")
                    name.set("Martin Heinrich")
                    email.set("martin.heinrich.dresden@gmx.de")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatypeRepository"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials(PasswordCredentials::class)
        }
    }
}

versionPlugin {
    versionPluginBuilder = Versions.versionPluginBuilder {
        versionMember = { coroutinesLib }
        resetVersion = { copy(coroutinesLib = it) }
        publishTaskName = "publish"
    }
    setDepending(tasks, "publish")
}

