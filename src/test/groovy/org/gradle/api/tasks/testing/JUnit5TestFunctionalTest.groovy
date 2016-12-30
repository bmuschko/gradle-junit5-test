package org.gradle.api.tasks.testing

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class JUnit5TestFunctionalTest extends Specification {
    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    File buildFile

    def setup() {
        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        List<File> pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }
        def classpathString = pluginClasspath
                .collect { it.absolutePath.replace('\\', '\\\\') }
                .collect { "'$it'" }
                .join(", ")

        buildFile = testProjectDir.newFile('build.gradle')
        buildFile << """
            buildscript {
                dependencies {
                    classpath files($classpathString)
                }
            }

            apply plugin: 'java'
            
            sourceCompatibility = 1.8
            targetCompatibility = 1.8
            
            repositories {
                mavenCentral()
            }

            dependencies {
                testCompile 'org.junit.jupiter:junit-jupiter-api:5.0.0-M3'
            }
            
            test.enabled = false
            
            task junit5Test(type: org.gradle.api.tasks.testing.JUnit5Test) {
                dependsOn testClasses
            }
            
            check.dependsOn junit5Test
        """
    }

    def "can discover and execute JUnit 5 successful test"() {
        given:
        createTest(true)

        when:
        def result = createRunner().build()

        then:
        result.task(":junit5Test").outcome == SUCCESS
        result.output.contains('1 tests found')
        result.output.contains('1 tests started')
        result.output.contains('1 tests successful')
        result.output.contains('0 tests failed')
    }

    def "can discover and execute JUnit 5 failing test"() {
        given:
        createTest(false)

        when:
        def result = createRunner().buildAndFail()

        then:
        result.task(":junit5Test").outcome == FAILED
        result.output.contains('1 tests found')
        result.output.contains('1 tests started')
        result.output.contains('0 tests successful')
        result.output.contains('1 tests failed')
    }

    private GradleRunner createRunner() {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('build')
            .forwardOutput()
    }

    private void createTest(boolean successful) {
        def testSourceDir = testProjectDir.newFolder('src', 'test', 'java', 'org', 'gradle', 'test')
        new File(testSourceDir, 'JUnit5Test.java') << """
            package org.gradle.test;
            
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            class SimpleTest {
                @Test
                void someTest() {
                    assertTrue($successful);
                }
            }
        """
    }
}
