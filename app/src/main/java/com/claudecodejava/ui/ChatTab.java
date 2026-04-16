package com.claudecodejava.ui;

import com.claudecodejava.cli.ClaudeProcess;
import com.claudecodejava.cli.EventParser;
import com.claudecodejava.cli.SessionManager;
import com.claudecodejava.cli.StreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.VBox;

/** A single chat tab with its own session, chat view, input area, and status bar. */
public class ChatTab extends Tab {

  private final ChatView chatView;
  private final InputArea inputArea;
  private final StatusBar statusBar;
  private final SessionManager sessionManager;
  private ClaudeProcess currentProcess;
  private boolean assistantStarted = false;
  private boolean hookQuestionPending = false;
  private StreamEvent.QuestionData pendingQuestionData = null;
  private final Path ipcDir;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  // Shared settings (set by MainWindow before each send)
  private String permissionMode = "default";
  private String model = "sonnet";
  private String effort = "high";
  private List<String> allowedTools = List.of();
  private List<String> disallowedTools = List.of();
  private String mcpConfigPath = null;

  // Cumulative usage stats
  private double totalCostUsd = 0;
  private int totalInputTokens = 0;
  private int totalOutputTokens = 0;
  private int totalCacheReadTokens = 0;
  private int totalCacheCreateTokens = 0;
  private int totalDurationMs = 0;
  private int messageCount = 0;
  private final Map<String, StreamEvent.RateLimit> rateLimits = new LinkedHashMap<>();
  private volatile boolean quotaCheckDone = false;

  public ChatTab(String directory) {
    sessionManager = new SessionManager();
    sessionManager.setWorkingDirectory(directory);

    // Set up IPC directory for hook-based question answering
    ipcDir = Path.of(System.getProperty("java.io.tmpdir"), "claude-java-" + System.nanoTime());
    setupIpcFiles();

    setText(directoryBasename(directory));
    setClosable(true);
    setOnCloseRequest(
        e -> {
          var tp = getTabPane();
          if (tp != null && tp.getTabs().size() <= 2) {
            e.consume(); // Don't close the last chat tab
          }
        });

    chatView = new ChatView();

    inputArea = new InputArea();
    inputArea.setOnSend(this::handleSend);
    inputArea.setOnCancel(this::handleCancel);

    statusBar = new StatusBar();

    var chatContainer = chatView.getContainer();
    VBox.setVgrow(chatContainer, Priority.ALWAYS);
    chatContainer.setMinHeight(0);

    // Clip the chatContainer to its bounds so WebView native windows
    // don't extend beyond and steal mouse events from the divider below.
    var chatClip = new Rectangle();
    chatClip.widthProperty().bind(chatContainer.widthProperty());
    chatClip.heightProperty().bind(chatContainer.heightProperty());
    chatContainer.setClip(chatClip);

    // Visual-only divider bar between chat and input
    var resizeDivider = new Region();
    resizeDivider.setPrefHeight(6);
    resizeDivider.setMinHeight(6);
    resizeDivider.setMaxHeight(6);
    resizeDivider.setMouseTransparent(true); // events handled by container filters
    resizeDivider.setStyle("-fx-background-color: #313244;");

    var container = new VBox(chatContainer, resizeDivider, inputArea, statusBar);
    setContent(container);

    // Resize via container-level event filters (capturing phase).
    // This bypasses WebView's native event interception because filters
    // fire parent-first, before any child (including WebView) processes events.
    final double[] dragState = new double[2]; // [startScreenY, startHeight]
    final boolean[] resizing = {false};
    final double RESIZE_ZONE = 8.0;

    // Use scene coordinates for all boundary checks -- e.getY() is relative to the
    // pick target node (which varies), but e.getSceneY() is always absolute.
    container.addEventFilter(
        MouseEvent.MOUSE_MOVED,
        e -> {
          var dividerInScene = resizeDivider.localToScene(0, 0);
          if (dividerInScene == null) return;
          double dividerSceneY = dividerInScene.getY();
          double dividerSceneBottom = dividerSceneY + resizeDivider.getHeight();
          double mouseY = e.getSceneY();
          if (mouseY >= dividerSceneY - RESIZE_ZONE
              && mouseY <= dividerSceneBottom + RESIZE_ZONE) {
            container.setCursor(Cursor.N_RESIZE);
            resizeDivider.setStyle("-fx-background-color: #89b4fa;");
            e.consume();
          } else if (!resizing[0]) {
            container.setCursor(Cursor.DEFAULT);
            resizeDivider.setStyle("-fx-background-color: #313244;");
          }
        });

    container.addEventFilter(
        MouseEvent.MOUSE_PRESSED,
        e -> {
          var dividerInScene = resizeDivider.localToScene(0, 0);
          if (dividerInScene == null) return;
          double dividerSceneY = dividerInScene.getY();
          double dividerSceneBottom = dividerSceneY + resizeDivider.getHeight();
          double mouseY = e.getSceneY();
          if (mouseY >= dividerSceneY - RESIZE_ZONE
              && mouseY <= dividerSceneBottom + RESIZE_ZONE) {
            dragState[0] = e.getScreenY();
            dragState[1] = inputArea.getInputHeight();
            resizing[0] = true;
            e.consume();
          }
        });

    container.addEventFilter(
        MouseEvent.MOUSE_DRAGGED,
        e -> {
          if (resizing[0]) {
            double deltaY = dragState[0] - e.getScreenY();
            inputArea.setInputHeight(dragState[1] + deltaY);
            e.consume();
          }
        });

    container.addEventFilter(
        MouseEvent.MOUSE_RELEASED,
        e -> {
          if (resizing[0]) {
            resizing[0] = false;
            container.setCursor(Cursor.DEFAULT);
            resizeDivider.setStyle("-fx-background-color: #313244;");
            e.consume();
          }
        });

    // Clean up IPC dir when tab is closed
    setOnClosed(_ -> cleanupIpcDir());

    // Right-click context menu for renaming
    var renameItem = new MenuItem("Rename");
    renameItem.setOnAction(
        _ -> {
          var dialog = new TextInputDialog(getText());
          dialog.setTitle("Rename Tab");
          dialog.setHeaderText(null);
          dialog.setContentText("Tab name:");
          dialog
              .showAndWait()
              .ifPresent(
                  name -> {
                    if (!name.isBlank()) setText(name);
                  });
        });
    var closeItem = new MenuItem("Close");
    closeItem.setOnAction(
        _ -> {
          var tp = getTabPane();
          // Don't close if it's the last real tab (keep at least 1 besides the "+" tab)
          if (tp != null && tp.getTabs().size() > 2) {
            tp.getTabs().remove(this);
          }
        });

    setContextMenu(new ContextMenu(renameItem, closeItem));
  }

  public SessionManager getSessionManager() {
    return sessionManager;
  }

  public void focusInput() {
    inputArea.focus();
  }

  public void showSystemMessage(String text, String styleClass) {
    chatView.addSystemMessage(text, styleClass);
  }

  /** Load conversation history from a session file and display it in the chat. */
  public void loadSessionHistory(List<SessionHistoryView.ChatMessage> messages) {
    chatView.clear();
    for (var msg : messages) {
      if ("user".equals(msg.role())) {
        chatView.addUserMessage(msg.text());
      } else if ("assistant".equals(msg.role())) {
        chatView.startAssistantMessage();
        chatView.replaceAssistantContent(msg.text());
        chatView.finalizeAssistantMessage();
      }
    }
    chatView.addSystemMessage("Session restored. Type a message to continue.", "system-info");
  }

  public void updateSettings(
      String permissionMode,
      String model,
      String effort,
      List<String> allowedTools,
      List<String> disallowedTools,
      String mcpConfigPath) {
    this.permissionMode = permissionMode;
    this.model = model;
    this.effort = effort;
    this.allowedTools = allowedTools;
    this.disallowedTools = disallowedTools;
    this.mcpConfigPath = mcpConfigPath;
  }

  public void updateDirectory(String directory) {
    sessionManager.setWorkingDirectory(directory);
    sessionManager.newSession();
    chatView.clear();
    setText(directoryBasename(directory));
  }



  public List<StreamEvent.McpServer> getLastMcpServers() {
    return lastMcpServers;
  }

  /** Usage data snapshot for the UsageDialog. */
  public record UsageData(
      String model,
      int messageCount,
      double totalCostUsd,
      int totalInputTokens,
      int totalOutputTokens,
      int totalCacheReadTokens,
      int totalCacheCreateTokens,
      int totalDurationMs,
      Map<String, StreamEvent.RateLimit> rateLimits) {}

  public UsageData getUsageData() {
    return new UsageData(
        model,
        messageCount,
        totalCostUsd,
        totalInputTokens,
        totalOutputTokens,
        totalCacheReadTokens,
        totalCacheCreateTokens,
        totalDurationMs,
        Map.copyOf(rateLimits));
  }

  /** Run a lightweight quota check to fetch rate limit data without a real conversation. */
  public void fetchQuotaCheck(Runnable onComplete) {
    if (quotaCheckDone) {
      if (onComplete != null) Platform.runLater(onComplete);
      return;
    }
    Thread.ofVirtual()
        .name("quota-check")
        .start(
            () -> {
              try {
                var cmd =
                    List.of(
                        "claude",
                        "-p",
                        "hi",
                        "--output-format",
                        "stream-json",
                        "--verbose",
                        "--bare",
                        "--max-budget-usd",
                        "0.01");
                var pb = new ProcessBuilder(cmd);
                pb.directory(new java.io.File(sessionManager.getWorkingDirectory()));
                pb.redirectErrorStream(false);
                pb.redirectInput(
                    ProcessBuilder.Redirect.from(
                        new java.io.File(
                            System.getProperty("os.name").toLowerCase().contains("win")
                                ? "NUL"
                                : "/dev/null")));
                var proc = pb.start();
                try (var reader =
                    new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                    String json = line.trim();
                    if (json.isEmpty()) continue;
                    StreamEvent event = EventParser.parse(json);
                    if (event instanceof StreamEvent.RateLimit rl
                        && !rl.rateLimitType().isEmpty()) {
                      Platform.runLater(
                          () -> {
                            rateLimits.put(rl.rateLimitType(), rl);
                            statusBar.updateFromRateLimit(rl);
                          });
                    }
                  }
                }
                proc.waitFor();
                quotaCheckDone = true;
              } catch (Exception ignored) {
              } finally {
                if (onComplete != null) Platform.runLater(onComplete);
              }
            });
  }

  private List<StreamEvent.McpServer> lastMcpServers = List.of();

  private void handleSend(String message) {
    launchProcess(message);
  }

  private void handleQuestionAnswer(String answer) {
    if (pendingQuestionData != null) {
      writeAnswerFile(pendingQuestionData, answer);
      chatView.addUserMessage(answer);
      chatView.showThinkingIndicator();
      inputArea.setBusy(true);
      pendingQuestionData = null;
      hookQuestionPending = false;
    }
  }

  private void launchProcess(String message) {
    chatView.addUserMessage(message);
    chatView.showThinkingIndicator();
    inputArea.setBusy(true);
    assistantStarted = false;

    currentProcess = new ClaudeProcess();
    currentProcess.start(
        message,
        sessionManager.getCurrentSessionId(),
        sessionManager.getWorkingDirectory(),
        permissionMode,
        "plan".equals(permissionMode),
        model,
        effort,
        false,
        allowedTools,
        disallowedTools,
        mcpConfigPath,
        ipcDir.toString(),
        this::handleEvent,
        this::handleDone);
  }

  private void handleEvent(StreamEvent event) {
    switch (event) {
      case StreamEvent.Init init -> {
        sessionManager.setCurrentSessionId(init.sessionId());
        statusBar.setModel(init.model());
        lastMcpServers = init.mcpServers();
      }

      case StreamEvent.AssistantMessage msg -> {
        for (String tool : msg.toolNames()) {
          chatView.addSystemMessage("  " + tool, "tool-use-message");
        }
        if (!msg.text().isBlank()) {
          chatView.hideThinkingIndicator();
          chatView.finalizeAssistantMessage();
          chatView.startAssistantMessage();
          chatView.replaceAssistantContent(msg.text());
          assistantStarted = true;
          chatView.showThinkingIndicator();
        } else if (!msg.toolNames().isEmpty()) {
          chatView.showThinkingIndicator();
        }

        if (msg.question() != null) {
          hookQuestionPending = true;
          pendingQuestionData = msg.question();
          chatView.hideThinkingIndicator();
          chatView.finalizeAssistantMessage();
          inputArea.setBusy(false);
          assistantStarted = false;
          chatView.addQuestionView(new QuestionView(msg.question(), this::handleQuestionAnswer));
        }
        if (msg.exitPlanMode()) {
          chatView.hideThinkingIndicator();
          chatView.finalizeAssistantMessage();
          inputArea.setBusy(false);
          assistantStarted = false;
          chatView.addQuestionView(
              QuestionView.forPlanApproval(this::handleSend, inputArea::focus));
        }
      }

      case StreamEvent.Result result -> {
        chatView.hideThinkingIndicator();
        if (!assistantStarted && !result.text().isBlank()) {
          chatView.startAssistantMessage();
          chatView.appendToken(result.text());
          assistantStarted = true;
        }
        chatView.finalizeAssistantMessage();
        statusBar.updateFromResult(result);

        // Accumulate usage
        totalCostUsd += result.costUsd();
        totalInputTokens += result.inputTokens();
        totalOutputTokens += result.outputTokens();
        totalCacheReadTokens += result.cacheReadTokens();
        totalCacheCreateTokens += result.cacheCreateTokens();
        totalDurationMs += result.durationMs();
        messageCount++;
      }

      case StreamEvent.ToolUse toolUse ->
          chatView.addSystemMessage(toolUse.toolName(), "tool-use-message");

      case StreamEvent.ToolResult toolResult ->
          chatView.addSystemMessage(
              "  Result: " + TextUtils.truncate(toolResult.output(), 200), "tool-use-message");

      case StreamEvent.Error error -> {
        chatView.hideThinkingIndicator();
        chatView.addSystemMessage("Error: " + error.message(), "error-message");
      }

      case StreamEvent.RateLimit rl -> {
        if (!rl.rateLimitType().isEmpty()) {
          rateLimits.put(rl.rateLimitType(), rl);
        }
        quotaCheckDone = true;
        statusBar.updateFromRateLimit(rl);
        if (!"allowed".equals(rl.status())) {
          chatView.addSystemMessage("Rate limited: " + rl.rateLimitType(), "error-message");
        }
      }

      case StreamEvent.Unknown _ -> {}
    }
  }

  private void handleDone() {
    if (!hookQuestionPending) {
      chatView.hideThinkingIndicator();
      chatView.finalizeAssistantMessage();
      inputArea.setBusy(false);
      inputArea.focus();
    }
    assistantStarted = false;
  }

  private void handleCancel() {
    if (currentProcess != null) {
      currentProcess.cancel();
    }
    chatView.hideThinkingIndicator();
    inputArea.setBusy(false);
    chatView.addSystemMessage("Cancelled", "system-info");
  }


  private void setupIpcFiles() {
    try {
      Files.createDirectories(ipcDir);

      // Write the hook script
      String hookScript =
          """
          #!/bin/bash
          ANSWER_FILE="$CLAUDE_JAVA_IPC_DIR/answer.json"
          rm -f "$ANSWER_FILE"
          for i in $(seq 1 600); do
              [ -f "$ANSWER_FILE" ] && break
              sleep 0.5
          done
          if [ -f "$ANSWER_FILE" ]; then
              cat "$ANSWER_FILE"
              rm -f "$ANSWER_FILE"
          else
              echo '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"User did not respond in time"}}'
          fi
          """;
      Files.writeString(ipcDir.resolve("question-hook.sh"), hookScript);

      // Write the hook settings JSON
      String hookPath = ipcDir.resolve("question-hook.sh").toString().replace("\\", "/");
      String settingsJson =
          """
          {
            "hooks": {
              "PreToolUse": [
                {
                  "matcher": "AskUserQuestion",
                  "hooks": [{"type": "command", "command": "bash %s"}]
                }
              ]
            }
          }
          """
              .formatted(hookPath);
      Files.writeString(ipcDir.resolve("hook-settings.json"), settingsJson);
    } catch (Exception e) {
      System.err.println("Failed to set up IPC files: " + e.getMessage());
    }
  }

  private void writeAnswerFile(StreamEvent.QuestionData question, String answer) {
    try {
      ObjectNode root = JSON_MAPPER.createObjectNode();
      ObjectNode hookOutput = root.putObject("hookSpecificOutput");
      hookOutput.put("hookEventName", "PreToolUse");
      hookOutput.put("permissionDecision", "allow");

      ObjectNode updatedInput = hookOutput.putObject("updatedInput");
      if (question.rawQuestions() != null) {
        updatedInput.set("questions", question.rawQuestions());
      }

      ObjectNode answers = updatedInput.putObject("answers");
      answers.put(question.question(), answer);

      Path tmpFile = ipcDir.resolve("answer.json.tmp");
      Path answerFile = ipcDir.resolve("answer.json");
      Files.writeString(tmpFile, JSON_MAPPER.writeValueAsString(root));
      Files.move(tmpFile, answerFile, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      System.err.println("Failed to write answer file: " + e.getMessage());
    }
  }

  private void cleanupIpcDir() {
    try {
      if (Files.exists(ipcDir)) {
        try (var walk = Files.walk(ipcDir)) {
          walk.sorted(java.util.Comparator.reverseOrder())
              .forEach(p -> { var _ = p.toFile().delete(); });
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static String directoryBasename(String path) {
    if (path == null || path.isBlank()) return "New Tab";
    var p = java.nio.file.Path.of(path);
    return p.getFileName() != null ? p.getFileName().toString() : path;
  }
}
