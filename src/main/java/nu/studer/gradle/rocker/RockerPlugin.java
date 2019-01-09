package nu.studer.gradle.rocker;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static nu.studer.gradle.rocker.StringUtils.capitalize;

@SuppressWarnings("unused")
public class RockerPlugin implements Plugin<Project> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RockerPlugin.class);

    @Override
    public void apply(final Project project) {
        // apply Java base plugin, making it possible to also use the rocker plugin for Android builds
        project.getPlugins().apply(JavaBasePlugin.class);

        // allow to configure the rocker version via extension property
        RockerVersion.applyDefaultVersion(project);

        // use the configured rocker version on all rocker dependencies
        enforceRockerVersion(project);

        // add rocker DSL extension
        NamedDomainObjectContainer<RockerConfig> container = project.container(RockerConfig.class, name -> new RockerConfig(name, project));
        project.getExtensions().add("rocker", container);

        // create configuration for the runtime classpath of the rocker compiler (shared by all rocker configuration domain objects)
        final Configuration configuration = createRockerCompilerRuntimeConfiguration(project);

        // create a rocker task for each rocker configuration domain object
        container.all(config -> {
            // create rocker task
            RockerCompile rocker = project.getTasks().create(config.getCompileTaskName(), RockerCompile.class);
            rocker.setDescription("Invokes the Rocker template engine.");
            rocker.setGroup("Rocker");
            rocker.setConfig(config);
            rocker.setRuntimeClasspath(configuration);

            // wire task dependencies such that the rocker task creates the sources before the corresponding Java compile task compiles them and
            // add the rocker-runtime to the compile configuration in order to be able to compile the generated sources
            SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
            SourceSet sourceSet = sourceSets.findByName(config.name);
            if (sourceSet != null) {
                project.getTasks().getByName(sourceSet.getCompileJavaTaskName()).dependsOn(rocker);
                project.getDependencies().add(sourceSet.getCompileConfigurationName(), "com.fizzed:rocker-runtime");
            }
        });
    }

    private void enforceRockerVersion(final Project project) {
        project.getConfigurations().all(configuration ->
            configuration.getResolutionStrategy().eachDependency(details -> {
                ModuleVersionSelector requested = details.getRequested();
                if (requested.getGroup().equals("com.fizzed") && requested.getName().startsWith("rocker-")) {
                    details.useVersion(RockerVersion.fromProject(project).asString());
                }
            })
        );
    }

    private Configuration createRockerCompilerRuntimeConfiguration(Project project) {
        Configuration rockerCompilerRuntime = project.getConfigurations().create("rockerCompiler");
        rockerCompilerRuntime.setDescription("The classpath used to invoke the Rocker template engine. Add your additional dependencies here.");
        project.getDependencies().add(rockerCompilerRuntime.getName(), "com.fizzed:rocker-compiler");
        project.getDependencies().add(rockerCompilerRuntime.getName(), "org.slf4j:slf4j-simple:1.7.25");
        return rockerCompilerRuntime;
    }

}
