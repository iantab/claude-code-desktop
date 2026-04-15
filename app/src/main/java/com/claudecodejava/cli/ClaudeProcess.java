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

  /**
   * Starts a claude CLI subprocess on a virtual thread.
   *
   * @param message The user's message
   * @param sessionId Session ID for --resume, or null for new session
   * @param workingDir Working directory for the claude process
   * @param onEvent Callback for each parsed event (called on FX thread)
   * @param onDone Callback when process completes (called on FX thread)
   */
  public void start(
      String message,
      String sessionId,
      String workingDir,
      String permissionMode,
      boolean planMode,
      String model,
      Consumer<StreamEvent> onEvent,
      Runnable onDone) {
    cancelled = false;

    Thread.ofVirtual()
        .name("claude-process")
        .start(
            () -> {
              try {
                List<String> command =
                    buildCommand(message, sessionId, permissionMode, planMode, model);
                var pb = new ProcessBuilder(command);
                pb.directory(new java.io.File(workingDir));
                pb.redirectErrorStream(false);
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
      String message, String sessionId, String permissionMode, boolean planMode, String model) {
    var cmd = new ArrayList<String>();
    cmd.add("claude");
    cmd.add("-p");
    cmd.add(message);
    cmd.add("--output-format");
    cmd.add("stream-json");
    cmd.add("--verbose");

    if (sessionId != null && !sessionId.isBlank()) {
      cmd.add("--resume");
      cmd.add(sessionId);
    }

    // "plan" is a permission mode, so use it directly
    if (permissionMode != null && !"default".equals(permissionMode)) {
      cmd.add("--permission-mode");
      cmd.add(permissionMode);
    }

    if (model != null && !model.isBlank()) {
      cmd.add("--model");
      cmd.add(model);
    }

    // Auto-allow safe read-only tools in non-interactive mode
    cmd.add("--allowedTools");
    cmd.add("WebSearch,WebFetch");

    return cmd;
  }
}
