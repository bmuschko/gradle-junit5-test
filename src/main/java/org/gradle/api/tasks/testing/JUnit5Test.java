package org.gradle.api.tasks.testing;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.File;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.platform.engine.discovery.ClassNameFilter.STANDARD_INCLUDE_PATTERN;
import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

public class JUnit5Test extends DefaultTask {

    @TaskAction
    public void executeTests() {
        Set<Path> launcherRuntimeClasspath = createLauncherRuntimeClasspath();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader customClassLoader = createCustomClassLoader(launcherRuntimeClasspath, originalClassLoader);

        try {
            Thread.currentThread().setContextClassLoader(customClassLoader);
            TestExecutionSummary summary = executeTests(launcherRuntimeClasspath);
            summary.printTo(new PrintWriter(System.out));

            if (summary.getTotalFailureCount() > 0) {
                throw new GradleException("At least one test case failed");
            }
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private ClassLoader createCustomClassLoader(Collection<Path> paths, ClassLoader parentClassLoader) {
        List<URL> urls = new ArrayList<>();

        for (Path path : paths) {
            urls.add(toURL(path));
        }

        return URLClassLoader.newInstance(urls.toArray(new URL[urls.size()]), parentClassLoader);
    }

    private URL toURL(Path path) {
        try {
            return path.toUri().toURL();
        }
        catch (MalformedURLException ex) {
            throw new GradleException("Invalid classpath entry: " + path, ex);
        }
    }

    private Set<Path> createLauncherRuntimeClasspath() {
        JavaPluginConvention javaConvention = getProject().getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet testSourceSet = javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        File classesDir = testSourceSet.getOutput().getClassesDir();
        Set<Path> paths = new HashSet<>();
        paths.add(Paths.get(classesDir.toURI()));
        return paths;
    }

    private TestExecutionSummary executeTests(Set<Path> classpathRoots) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClasspathRoots(classpathRoots))
                .filters(includeClassNamePatterns(STANDARD_INCLUDE_PATTERN))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        return listener.getSummary();
    }
}
