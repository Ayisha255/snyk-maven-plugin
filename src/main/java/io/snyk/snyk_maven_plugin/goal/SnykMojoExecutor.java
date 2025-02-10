package io.snyk.snyk_maven_plugin.goal;

import io.snyk.snyk_maven_plugin.command.Command;
import io.snyk.snyk_maven_plugin.command.CommandLine;
import io.snyk.snyk_maven_plugin.command.CommandRunner;
import io.snyk.snyk_maven_plugin.command.CommandRunner.LineLogger;
import io.snyk.snyk_maven_plugin.download.ExecutableDownloader;
import io.snyk.snyk_maven_plugin.download.FileDownloader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;

public class SnykMojoExecutor implements MojoExecutor {

    private final static int EXIT_CODE_OK = 0;
    private final static int EXIT_CODE_ACTION_NEEDED = 1;

    private final SnykMojo mojo;

    public SnykMojoExecutor(SnykMojo mojo) {
        this.mojo = mojo;
    }

    /**
     * Executes the snyk command or skips execution based on the plugin configuration.
     * @example
     * void result = SnykMojoExecutor.execute();
     * // This will execute the Snyk command and handle its exit code accordingly.
     * @param {void} None - This method does not take any parameters.
     * @return {void} - This method does not return any value.
     * @description
     *   - Handles different exit codes returned by the Snyk command execution.
     *   - Uses Mojo configuration to decide whether to skip the command execution.
     *   - Logs messages based on the outcome of the command or if execution is skipped.
     *   - Throws exceptions when the Snyk command results in an error exit code and fails on issues.
     */
    @Override
    public void execute() throws MojoFailureException, MojoExecutionException {
        if (mojo.shouldSkip()) {
            mojo.getLog().info("snyk " + mojo.getCommand().commandName() + " skipped");
            return;
        }

        int exitCode = executeCommand();

        switch (exitCode) {
            case EXIT_CODE_OK:
                break;
            case EXIT_CODE_ACTION_NEEDED:
                if (!mojo.getFailOnIssues()) {
                    mojo.getLog().warn("snyk " + mojo.getCommand().commandName()
                            + " did find issues, but the plugin is configured"
                            + " to not fail in this situation.");
                    break;
                }
            default:
                throw new MojoFailureException("snyk command exited with non-zero exit code ("
                        + exitCode + "). See output for details.");
        }
    }

    /**
     * Executes a specified command and returns the exit status.
     * @example
     * int status = executeCommand();
     * System.out.println(status); // Expected output: 0 for success, other for failure
     * @return {int} - The exit status of the executed command, typically 0 for success.
     * @throws {MojoExecutionException} - If an error occurs during command execution.
     * @description
     *   - Downloads the executable if not provided and sets up the command line for execution.
     *   - Logs the snyk executable path and version for debugging purposes.
     *   - Ensures the command is executed within the project's root directory.
     *   - Handles all exceptions by throwing a MojoExecutionException with the original message.
     */
    private int executeCommand() throws MojoExecutionException {
        try {
            Log log = mojo.getLog();

            String executablePath = mojo.getExecutable()
                .orElseGet(this::downloadExecutable)
                .getAbsolutePath();

            log.info("Snyk Executable Path: " + executablePath);
            log.info("Snyk CLI Version:     " + getVersion(executablePath));

            ProcessBuilder commandLine = CommandLine.asProcessBuilder(
                executablePath,
                mojo.getCommand(),
                mojo.getApiToken(),
                mojo.getArguments(),
                mojo.supportsColor()
            ).directory(getProjectRootDirectory());

            if (log.isDebugEnabled()) {
                log.debug("Snyk Command: "
                        + String.join(" ", commandLine.command()));
            }

            return CommandRunner.run(commandLine::start, log::info, log::error);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private File getProjectRootDirectory() {
        MavenProject project = (MavenProject) mojo.getPluginContext().get("project");

        if (project == null) {
            throw new IllegalStateException("the `project` is missing from the plugin context");
        }

        return project.getBasedir();
    }

    /**
     * Retrieves the version of the executable located at the specified path.
     * @example
     * String result = getVersion("/path/to/executable");
     * System.out.println(result); // Expected output: e.g. "1.0.0"
     * @param {String} executablePath - The path to the executable for which the version is being retrieved.
     * @return {String} - A string representing the version of the executable.
     * @description
     *   - Executes a command to fetch version details using a command line process.
     *   - Collects the standard output of the process execution.
     *   - Trims the collected output to remove any surrounding white spaces.
     */
    private String getVersion(String executablePath) {
        ProcessBuilder versionCommandLine = CommandLine.asProcessBuilder(
            executablePath,
            Command.VERSION,
            Optional.empty(),
            emptyList(),
            false
        );
        List<String> stdout = new ArrayList<>();
        LineLogger ignore = line -> {};
        CommandRunner.run(versionCommandLine::start, stdout::add, ignore);
        return String.join("", stdout).trim();
    }

    private File downloadExecutable() {
        return ExecutableDownloader.iterateAndEnsure(
            mojo.getDownloadUrls(),
            mojo.getDownloadDestination(),
            mojo.getUpdatePolicy(),
            FileDownloader::downloadFile
        );
    }

}
