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
 * Creates a <i>pom.xml</i> file that provides a lightweight view on a (huge) multi project using <i>modules</i>
 * definitions.
 *
 * @author <a href="mailto:Marc.Rohlfs@t-systems.com">Marc Rohlfs, T-Systems Multimedia Solutions GmbH</a>
 */
@SuppressWarnings("unused")
@Mojo(name = "generate-view", aggregator = true)
public class GenerateViewMojo extends AbstractMojo {

    /** The name of the base directory below the execution root directory, where the generate view projects are placed. */
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "project-views", property = "outputDir", required = true)
    private String outputBaseDirectory;

    /**
     * The base project of the build. When a project list is specified, this is the first project in the build reactor.
     * Otherwise it is (most likely) the execution root project.
     */
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** The current list of projects being included in the build. */
    @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    /** The current build session instance. This is used to get information about the command invocation. */
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException {

        final MavenExecutionRequest request = this.session.getRequest();
        final String viewProjectName = generateViewProjectName(request.getSelectedProjects());

        final MavenProject viewProject = new MavenProject();
        viewProject.setModelVersion(this.project.getModelVersion());
        viewProject.setGroupId(this.project.getGroupId());
        viewProject.setArtifactId(viewProjectName);
        viewProject.setPackaging("pom");

        final Path viewRootPath = FileSystems.getDefault().getPath(this.session.getExecutionRootDirectory(), this.outputBaseDirectory, viewProjectName);
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
