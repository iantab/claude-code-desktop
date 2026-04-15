package com.claudecodejava.ui;

import com.claudecodejava.cli.ClaudeProcess;
import com.claudecodejava.cli.SessionManager;
import com.claudecodejava.cli.StreamEvent;
import java.util.List;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** A single chat tab with its own session, chat view, input area, and status bar. */
public class ChatTab extends Tab {

  private final ChatView chatView;
  private final InputArea inputArea;
  private final StatusBar statusBar;
  private final SessionManager sessionManager;
  private ClaudeProcess currentProcess;
  private boolean assistantStarted = false;

  // Shared settings (set by MainWindow before each send)
  private String permissionMode = "default";
  private String model = "sonnet";
  private String effort = "high";
  private List<String> allowedTools = List.of();
  private List<String> disallowedTools = List.of();
  private String mcpConfigPath = null;

  public ChatTab(String directory) {
    sessionManager = new SessionManager();
    sessionManager.setWorkingDirectory(directory);

    setText(directoryBasename(directory));
    setClosable(true);

    chatView = new ChatView();
    VBox.setVgrow(chatView, Priority.ALWAYS);

    inputArea = new InputArea();
    inputArea.setOnSend(this::handleSend);
    inputArea.setOnCancel(this::handleCancel);

    statusBar = new StatusBar();

    var container = new VBox(chatView, inputArea, statusBar);
    VBox.setVgrow(chatView, Priority.ALWAYS);
    setContent(container);

    // Right-click context menu for renaming
    var renameItem = new MenuItem("Rename");
    renameItem.setOnAction(
        e -> {
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
    setContextMenu(new ContextMenu(renameItem));
  }

  public SessionManager getSessionManager() {
    return sessionManager;
  }

  public void focusInput() {
    inputArea.focus();
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

  public StatusBar getStatusBar() {
    return statusBar;
  }

  public List<StreamEvent.McpServer> getLastMcpServers() {
    return lastMcpServers;
  }

  private List<StreamEvent.McpServer> lastMcpServers = List.of();

  private void handleSend(String message) {
    launchProcess(message, false);
  }

  private void launchProcess(String message, boolean continueSession) {
    chatView.addUserMessage(message);
    chatView.showThinkingIndicator();
    inputArea.setBusy(true);
    assistantStarted = false;

    currentProcess = new ClaudeProcess();
    currentProcess.start(
        message,
        continueSession ? null : sessionManager.getCurrentSessionId(),
        sessionManager.getWorkingDirectory(),
        permissionMode,
        "plan".equals(permissionMode),
        model,
        effort,
        continueSession,
        allowedTools,
        disallowedTools,
        mcpConfigPath,
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
          chatView.hideThinkingIndicator();
          chatView.addQuestionView(new QuestionView(msg.question(), this::handleSend));
        }
        if (msg.exitPlanMode()) {
          chatView.hideThinkingIndicator();
          chatView.addQuestionView(
              QuestionView.forPlanApproval(this::handleSend, () -> inputArea.focus()));
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
      }

      case StreamEvent.ToolUse toolUse -> {
        chatView.addSystemMessage(toolUse.toolName(), "tool-use-message");
      }

      case StreamEvent.ToolResult toolResult -> {
        chatView.addSystemMessage(
            "  Result: " + truncate(toolResult.output(), 200), "tool-use-message");
      }

      case StreamEvent.Error error -> {
        chatView.hideThinkingIndicator();
        chatView.addSystemMessage("Error: " + error.message(), "error-message");
      }

      case StreamEvent.RateLimit rl -> {
        statusBar.updateFromRateLimit(rl);
        if (!"allowed".equals(rl.status())) {
          chatView.addSystemMessage("Rate limited: " + rl.rateLimitType(), "error-message");
        }
      }

      case StreamEvent.Unknown unknown -> {}
    }
  }

  private void handleDone() {
    chatView.hideThinkingIndicator();
    chatView.finalizeAssistantMessage();
    inputArea.setBusy(false);
    inputArea.focus();
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

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }

  private static String directoryBasename(String path) {
    if (path == null || path.isBlank()) return "New Tab";
    var p = java.nio.file.Path.of(path);
    return p.getFileName() != null ? p.getFileName().toString() : path;
  }
}
