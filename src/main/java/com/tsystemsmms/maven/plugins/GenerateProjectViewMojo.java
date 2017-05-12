package com.tsystemsmms.maven.plugins;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

/**
 * TODO Write Javadoc
 */
@Mojo(name = "generate-project-view", aggregator = true)
public class GenerateProjectViewMojo extends AbstractMojo {

    @Parameter(defaultValue = "project-views", property = "outputDir", required = true)
    private String outputBaseDirectory;

    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject aggregatorProject;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession mavenSession;

    public void execute() throws MojoExecutionException {

        final MavenExecutionRequest request = this.mavenSession.getRequest();
        final String viewProjectName = generateViewProjectName(request.getSelectedProjects());

        final MavenProject viewProject = new MavenProject();
        viewProject.setModelVersion(this.aggregatorProject.getModelVersion());
        viewProject.setGroupId(this.aggregatorProject.getGroupId());
        viewProject.setArtifactId(viewProjectName);
        viewProject.setPackaging("pom");

        final Path viewRootPath = FileSystems.getDefault().getPath(this.mavenSession.getExecutionRootDirectory(), this.outputBaseDirectory, viewProjectName);
        final List<String> viewProjectModules = viewProject.getModules();
        for (final MavenProject project : this.reactorProjects) {

            final String name = project.getName();
            final String packaging = project.getPackaging();
            if ("pom".equals(packaging)) {
                this.getLog().debug("Not adding module " + name + " (" + packaging + ")");
            } else {
                this.getLog().debug("Adding module " + name + " (" + packaging + ")");
                final String projectPath = project.getBasedir().getPath();
                final Path relativePath = viewRootPath.relativize(FileSystems.getDefault().getPath(projectPath));
                viewProjectModules.add(relativePath.toString());
            }
        }

        final File outputFile = new File(viewRootPath.toFile(), "pom.xml");
        try {
            new DefaultModelWriter().write(outputFile, null, viewProject.getModel());
            this.getLog().info("Generated " + outputFile);
        } catch (final IOException e) {
            this.getLog().error("Cannot write " + outputFile, e);
        }
    }

    private static String generateViewProjectName(final List<String> selectedProjects) {
        final StringBuilder projectName = new StringBuilder();

        for (final String selectedProject : selectedProjects) {

            if (projectName.length() > 0) {
                projectName.append("_");
            }

            projectName.append(StringUtils.removeAll(selectedProject, ".*[:/]"));
        }

        if (projectName.length() == 0) {
            projectName.append("my-project");
        }

        projectName.append("_view");

        return projectName.toString();
    }
}
