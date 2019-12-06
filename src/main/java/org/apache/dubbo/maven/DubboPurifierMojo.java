package org.apache.dubbo.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Set;

@Mojo(name = "generate-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public final class DubboPurifierMojo extends AbstractMojo {

    @Parameter(
            defaultValue = "${project}",
            required = true,
            readonly = true)
    private MavenProject project;

    @Parameter(
            property = "generatedSourcesDirectory",
            defaultValue = "${project.build.directory}/generated-sources/dubbo")
    private File generatedSourcesDirectory;

    @Parameter(property = "basePackages")
    private Set<String> basePackages;


    @Parameter(property = "interfaces")
    private Set<String> interfaces;

    /**
     * Executes the mojo.
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.getLog().info("generated-sources: " + this.generatedSourcesDirectory.getPath());
        this.getLog().info("basePackages: " + this.basePackages);
        this.getLog().info("interfaces: " + this.interfaces);
        this.project.addCompileSourceRoot(this.generatedSourcesDirectory.getPath());
        DubboPurifier purifier = new DubboPurifier(this.generatedSourcesDirectory, this.basePackages);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            for (String interfaceName : this.interfaces) {
                getLog().info("purify interface: " + interfaceName);
                Class<?> interfaceClass = classLoader.loadClass(interfaceName);
                if (interfaceClass.isInterface()) {
                    purifier.purify(interfaceClass);
                }
            }
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }
}
