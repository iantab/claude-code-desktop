package com.claudecodejava.ui;

import com.claudecodejava.cli.ClaudeProcess;
import com.claudecodejava.cli.SessionManager;
import com.claudecodejava.cli.StreamEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Main application window: toolbar + chat view + input area + status bar. */
public class MainWindow extends BorderPane {

  private final ToolBar toolBar;
  private final ChatView chatView;
  private final InputArea inputArea;
  private final StatusBar statusBar;
  private final SessionManager sessionManager;
  private ClaudeProcess currentProcess;
  private boolean assistantStarted = false;

  public MainWindow(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    getStyleClass().add("main-window");

    chatView = new ChatView();
    VBox.setVgrow(chatView, Priority.ALWAYS);

    inputArea = new InputArea();
    inputArea.setOnSend(this::handleSend);
    inputArea.setOnCancel(this::handleCancel);
    inputArea.setOnNewSession(this::handleNewSession);

    statusBar = new StatusBar();

    // InputArea + StatusBar stacked at the bottom
    var bottomPane = new VBox(inputArea, statusBar);

    toolBar = new ToolBar(sessionManager.getWorkingDirectory());
    toolBar.setOnDirectoryChanged(
        dir -> {
          sessionManager.setWorkingDirectory(dir);
          sessionManager.newSession();
          chatView.clear();
        });

    setTop(toolBar);
    setCenter(chatView);
    setBottom(bottomPane);
  }

  public void focusInput() {
    inputArea.focus();
  }

  private void handleSend(String message) {
    sessionManager.setWorkingDirectory(toolBar.getDirectory());

    chatView.addUserMessage(message);
    chatView.showThinkingIndicator();
    inputArea.setBusy(true);
    assistantStarted = false;

    currentProcess = new ClaudeProcess();
    currentProcess.start(
        message,
        sessionManager.getCurrentSessionId(),
        sessionManager.getWorkingDirectory(),
        toolBar.getPermissionMode(),
        toolBar.isPlanMode(),
        toolBar.getModel(),
        this::handleEvent,
        this::handleDone);
  }

  private void handleEvent(StreamEvent event) {
    switch (event) {
      case StreamEvent.Init init -> {
        sessionManager.setCurrentSessionId(init.sessionId());
        statusBar.setModel(init.model());
      }

      case StreamEvent.AssistantMessage msg -> {
        // Show tool names from this event
        for (String tool : msg.toolNames()) {
          chatView.addSystemMessage("  " + tool, "tool-use-message");
        }
        // Only create a message cell if there's actual text content
        if (!msg.text().isBlank()) {
          chatView.hideThinkingIndicator();
          chatView.finalizeAssistantMessage();
          chatView.startAssistantMessage();
          chatView.replaceAssistantContent(msg.text());
          assistantStarted = true;
          // Re-show thinking - more work may follow
          chatView.showThinkingIndicator();
        } else if (!msg.toolNames().isEmpty()) {
          // Tools are running but no text yet - show thinking indicator
          chatView.showThinkingIndicator();
        }

        // Handle interactive tool calls
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

      case StreamEvent.Unknown unknown -> {
        // Silently ignore unknown events
      }
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

  private void handleNewSession() {
    sessionManager.newSession();
    chatView.clear();
  }
}
