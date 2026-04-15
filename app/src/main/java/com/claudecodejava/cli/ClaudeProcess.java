package com.claudecodejava.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Spawns the claude CLI as a subprocess and streams events back to the UI. */
public class ClaudeProcess {

  private Process process;
  private volatile boolean cancelled = false;

  public void start(
      String message,
      String sessionId,
      String workingDir,
      String permissionMode,
      boolean planMode,
      String model,
      String effort,
      boolean continueLastSession,
      List<String> allowedTools,
      List<String> disallowedTools,
      String mcpConfigPath,
      String ipcDir,
      Consumer<StreamEvent> onEvent,
      Runnable onDone) {
    cancelled = false;

    Thread.ofVirtual()
        .name("claude-process")
        .start(
            () -> {
              try {
                List<String> command =
                    buildCommand(
                        message,
                        sessionId,
                        permissionMode,
                        planMode,
                        model,
                        effort,
                        continueLastSession,
                        allowedTools,
                        disallowedTools,
                        mcpConfigPath,
                        ipcDir);
                var pb = new ProcessBuilder(command);
                pb.directory(new java.io.File(workingDir));
                pb.redirectErrorStream(false);
                if (ipcDir != null) {
                  pb.environment().put("CLAUDE_JAVA_IPC_DIR", ipcDir);
                }
                pb.redirectInput(
                    ProcessBuilder.Redirect.from(
                        new java.io.File(
                            System.getProperty("os.name").toLowerCase().contains("win")
                                ? "NUL"
                                : "/dev/null")));

                process = pb.start();

                try (var reader =
                    new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                  String line;
                  while (!cancelled && (line = reader.readLine()) != null) {
                    String jsonLine = line.trim();
                    if (jsonLine.isEmpty()) continue;

                    StreamEvent event = EventParser.parse(jsonLine);
                    Platform.runLater(() -> onEvent.accept(event));
                  }
                }

                // Read stderr for error messages
                try (var errReader =
                    new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                  var errOutput = new StringBuilder();
                  String line;
                  while ((line = errReader.readLine()) != null) {
                    errOutput.append(line).append("\n");
                  }
                  if (!errOutput.isEmpty()) {
                    var errorEvent = new StreamEvent.Error(errOutput.toString().trim());
                    Platform.runLater(() -> onEvent.accept(errorEvent));
                  }
                }

                process.waitFor();
              } catch (Exception e) {
                if (!cancelled) {
                  var errorEvent = new StreamEvent.Error(e.getMessage());
                  Platform.runLater(() -> onEvent.accept(errorEvent));
                }
              } finally {
                Platform.runLater(onDone);
              }
            });
  }

  public void cancel() {
    cancelled = true;
    if (process != null && process.isAlive()) {
      process.destroyForcibly();
    }
  }

  private List<String> buildCommand(
      String message,
      String sessionId,
      String permissionMode,
      boolean planMode,
      String model,
      String effort,
      boolean continueLastSession,
      List<String> allowedTools,
      List<String> disallowedTools,
      String mcpConfigPath,
      String ipcDir) {
    var cmd = new ArrayList<String>();
    cmd.add("claude");
    cmd.add("-p");
    cmd.add(message);
    cmd.add("--output-format");
    cmd.add("stream-json");
    cmd.add("--verbose");

    if (continueLastSession) {
      cmd.add("--continue");
    } else if (sessionId != null && !sessionId.isBlank()) {
      cmd.add("--resume");
      cmd.add(sessionId);
    }

    if (permissionMode != null && !"default".equals(permissionMode)) {
      cmd.add("--permission-mode");
      cmd.add(permissionMode);
    }

    if (model != null && !model.isBlank()) {
      cmd.add("--model");
      cmd.add(model);
    }

    if (effort != null && !effort.isBlank()) {
      cmd.add("--effort");
      cmd.add(effort);
    }

    if (mcpConfigPath != null && !mcpConfigPath.isBlank()) {
      cmd.add("--mcp-config");
      cmd.add(mcpConfigPath);
    }

    // Build allowed tools list (always include WebSearch, WebFetch)
    var allAllowed = new ArrayList<>(List.of("WebSearch", "WebFetch"));
    if (allowedTools != null) {
      allAllowed.addAll(allowedTools);
    }
    cmd.add("--allowedTools");
    cmd.add(String.join(",", allAllowed));

    if (disallowedTools != null && !disallowedTools.isEmpty()) {
      cmd.add("--disallowedTools");
      cmd.add(String.join(",", disallowedTools));
    }

    if (ipcDir != null) {
      cmd.add("--settings");
      cmd.add(ipcDir + "/hook-settings.json");
    }

    return cmd;
  }
}
