import org.daiv.dependency.Versions

buildscript {
    repositories {
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven("https://artifactory.daiv.org/artifactory/gradle-dev-local")
    }
    dependencies {
        classpath("org.daiv.dependency:DependencyHandling:0.0.122")
    }
}


plugins {
    kotlin("multiplatform") version "1.4.10"
    id("com.jfrog.artifactory") version "4.17.2"
    id("org.daiv.dependency.VersionsPlugin") version "0.1.3"
    `maven-publish`
}

val versions = org.daiv.dependency.DefaultDependencyBuilder(Versions.current())
group = "org.daiv.coroutines"
version = versions.setVersion { coroutinesLib }

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
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    
    sourceSets {
        val commonMain by getting{
            dependencies{
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
        val nativeMain by getting
        val nativeTest by getting
    }
}
artifactory {
    setContextUrl("${project.findProperty("daiv_contextUrl")}")
    publish(delegateClosureOf<org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig> {
        repository(delegateClosureOf<groovy.lang.GroovyObject> {
            setProperty("repoKey", "gradle-dev-local")
            setProperty("username", project.findProperty("daiv_user"))
            setProperty("password", project.findProperty("daiv_password"))
            setProperty("maven", true)
        })
        defaults(delegateClosureOf<groovy.lang.GroovyObject> {
            invokeMethod("publications", arrayOf("jvm", "js", "kotlinMultiplatform", "metadata", "linuxX64"))
            setProperty("publishPom", true)
            setProperty("publishArtifacts", true)
        })
    })
}

versionPlugin {
    versionPluginBuilder = Versions.versionPluginBuilder {
        versionMember = { coroutinesLib }
        resetVersion = { copy(coroutinesLib = it) }
    }
    setDepending(tasks)
}

