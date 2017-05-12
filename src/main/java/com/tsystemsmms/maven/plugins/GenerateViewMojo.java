package com.tsystemsmms.maven.plugins;

import org.apache.commons.lang3.StringUtils;
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
import java.util.Arrays;
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

    /**
     * A comma-separated list of packaging types. Projects with the specified packaging types will not be added to the
     * generated view project.
     */
    @Parameter(property = "excludedPackagingTypes")
    private String excludedPackagingTypes;

    private List<String> excludedPackagingTypesList;

    /**
     * Denotes if only leaf project are added for the generated view project or if projects that contain sub modules
     * are also added.
     */
    @Parameter(defaultValue = "true", property = "onlyLeafProjects")
    private boolean onlyLeafProjects;

    /** The name of the base directory below the execution root directory, where the generate view projects are placed. */
    @SuppressWarnings("unused")
    @Parameter(defaultValue = "${session.executionRootDirectory}/project-views", property = "outputBaseDirectory", required = true)
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

    /**
     * The name (artifact ID) of the generated view project. When the {@code viewProjectName} is not specified, it will
     * be calculated using the following rules:
     * <ul>
     * <li>
     * If a project list ({@code -pl} or {@code --projects}) is specified, the artifact IDs of the project list elements
     * will be concatenated (using an underscore as separator). The generated view project name will be provided with
     * the suffix {@code _view}.
     * </li>
     * <li>
     * If no project list ({@code -pl} or {@code --projects}) is specified, the {@code viewProjectName} will simply be
     * {@code my-project_view}.
     * </li>
     * </ul>
     * <p>
     * <p>
     * A comma-separated list of packaging types. Projects with the specified packaging types will not be added to the
     * generated view project.
     */
    @Parameter(property = "viewProjectName")
    private String viewProjectName;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException {
        this.init();

        final String viewProjectName = this.calcViewProjectName();

        final MavenProject viewProject = new MavenProject();
        viewProject.setModelVersion(this.project.getModelVersion());
        viewProject.setGroupId(this.project.getGroupId());
        viewProject.setArtifactId(viewProjectName);
        viewProject.setPackaging("pom");

        final Path viewProjectBasedir = this.calcViewProjectBasedir(viewProjectName);
        final List<String> viewProjectModules = viewProject.getModules();
        for (final MavenProject project : this.reactorProjects) {

            final String name = project.getName();
            if (this.isAllowedForView(project)) {
                this.getLog().debug("Adding module " + name);
                final String projectPath = project.getBasedir().getPath();
                final Path relativePath = viewProjectBasedir.relativize(FileSystems.getDefault().getPath(projectPath));
                viewProjectModules.add(relativePath.toString());
            } else {
                this.getLog().debug("Omitting module " + name);
            }
        }

        final File outputFile = new File(viewProjectBasedir.toFile(), "pom.xml");
        try {
            new DefaultModelWriter().write(outputFile, null, viewProject.getModel());
            this.getLog().info("Generated " + outputFile);
        } catch (final IOException e) {
            this.getLog().error("Cannot write " + outputFile, e);
        }
    }

    private void init() {
        if (StringUtils.isNotBlank(this.excludedPackagingTypes)) {
            this.excludedPackagingTypesList = Arrays.asList(StringUtils.split(this.excludedPackagingTypes, ","));
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean isAllowedForView(final MavenProject project) {
        if (this.onlyLeafProjects && !project.getModules().isEmpty()) {
            return false;
        } else if (this.excludedPackagingTypesList != null && this.excludedPackagingTypesList.contains(project.getPackaging())) {
            return false;
        } else {
            return true;
        }
    }

    private String calcViewProjectName() {
        final StringBuilder projectName = new StringBuilder();

        if (StringUtils.isNotBlank(this.viewProjectName)) {
            projectName.append(this.viewProjectName);
        } else {
            for (final String selectedProject : this.session.getRequest().getSelectedProjects()) {
                if (projectName.length() > 0) {
                    projectName.append("_");
                }
                projectName.append(StringUtils.removeAll(selectedProject, ".*[:/]"));
            }

            if (projectName.length() == 0) {
                projectName.append("my-project");
            }

            projectName.append("_view");
        }

        return projectName.toString();
    }

    private Path calcViewProjectBasedir(final String viewProjectName) {
        final Path viewRootPath = FileSystems.getDefault().getPath(this.outputBaseDirectory, viewProjectName);
        if (viewRootPath.isAbsolute()) {
            return viewRootPath;
        } else {
            return FileSystems.getDefault().getPath(this.session.getExecutionRootDirectory(), this.outputBaseDirectory, viewProjectName);
        }
    }
}
