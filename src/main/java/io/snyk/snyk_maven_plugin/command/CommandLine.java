package io.snyk.snyk_maven_plugin.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface CommandLine {

    String INTEGRATION_NAME = "MAVEN_PLUGIN";

    Process start() throws IOException;

    /**
     * Creates a ProcessBuilder for executing a CLI command with specified parameters.
     * @example
     * ProcessBuilder pb = CommandLine.asProcessBuilder("/path/to/cli", commandObj, Optional.of("apiToken123"), Arrays.asList("--arg1", "--arg2"), true);
     * System.out.println(pb.command()); // Expected output sample: ["/path/to/cli", "command", "--integration-name=snyk-maven-plugin", "--arg1", "--arg2"]
     * @param {String} cliExecutablePath - The file system path to the CLI executable.
     * @param {Command} command - The command object representing the CLI command to be executed.
     * @param {Optional<String>} apiToken - An optional API token for authentication.
     * @param {List<String>} args - A list of additional arguments to pass to the CLI command.
     * @param {boolean} color - Flag indicating whether to force color output in the CLI.
     * @return {ProcessBuilder} - A ProcessBuilder configured with the specified CLI command and environment settings.
     * @description
     *   - Automatically includes the integration name parameter in the command.
     *   - Filters out empty arguments and any arguments starting with "--integration-name".
     *   - Sets the "FORCE_COLOR" environment variable based on the color flag.
     *   - Adds the API token to the environment if present.
     */
    static ProcessBuilder asProcessBuilder(
        String cliExecutablePath,
        Command command,
        Optional<String> apiToken,
        List<String> args,
        boolean color
    ) {
        List<String> parts = new ArrayList<>();

        parts.add(cliExecutablePath);
        Collections.addAll(parts, command.commandParameters());
        parts.add("--integration-name=" + INTEGRATION_NAME);

        args.stream()
            .map(String::trim)
            .filter(arg -> !arg.isEmpty())
            .filter(arg -> !arg.startsWith("--integration-name"))
                .forEach(parts::add);

        ProcessBuilder pb = new ProcessBuilder(parts);

        if (color) {
            pb.environment().put("FORCE_COLOR", "true");
        }

        apiToken.ifPresent((t) -> pb.environment().put("SNYK_TOKEN", t));

        return pb;
    }
}
