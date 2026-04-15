package com.claudecodejava.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Scrollable chat view that displays a list of messages. */
public class ChatView extends ScrollPane {

  private final VBox messagesBox;
  private final MarkdownRenderer markdownRenderer;
  private MessageCell currentAssistantCell;
  private Label thinkingLabel;
  private Timeline thinkingAnimation;

  public ChatView() {
    markdownRenderer = new MarkdownRenderer();
    messagesBox = new VBox();
    messagesBox.setSpacing(0);
    messagesBox.setPadding(new Insets(8));
    messagesBox.getStyleClass().add("messages-box");

    setContent(messagesBox);
    setFitToWidth(true);
    setHbarPolicy(ScrollBarPolicy.NEVER);
    setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
    getStyleClass().add("chat-scroll");

    // Welcome message
    var welcome = new Label("Claude Code Java \u2014 Type a message to get started");
    welcome.getStyleClass().add("welcome-label");
    welcome.setWrapText(true);
    welcome.setPadding(new Insets(20));
    messagesBox.getChildren().add(welcome);
  }

  /** Add a user message bubble. */
  public void addUserMessage(String text) {
    removeWelcome();
    var cell = new MessageCell("user", text, null);
    messagesBox.getChildren().add(cell);
    scrollToBottom();
  }

  /** Show animated "Claude is thinking..." indicator. */
  public void showThinkingIndicator() {
    hideThinkingIndicator();

    thinkingLabel = new Label("Claude is thinking");
    thinkingLabel.getStyleClass().addAll("thinking-indicator");
    thinkingLabel.setPadding(new Insets(8, 16, 8, 16));
    messagesBox.getChildren().add(thinkingLabel);
    scrollToBottom();

    // Animate dots
    final int[] dotCount = {0};
    thinkingAnimation =
        new Timeline(
            new KeyFrame(
                Duration.millis(400),
                e -> {
                  dotCount[0] = (dotCount[0] % 3) + 1;
                  thinkingLabel.setText("Claude is thinking" + ".".repeat(dotCount[0]));
                }));
    thinkingAnimation.setCycleCount(Timeline.INDEFINITE);
    thinkingAnimation.play();
  }

  /** Hide the thinking indicator. */
  public void hideThinkingIndicator() {
    if (thinkingAnimation != null) {
      thinkingAnimation.stop();
      thinkingAnimation = null;
    }
    if (thinkingLabel != null) {
      messagesBox.getChildren().remove(thinkingLabel);
      thinkingLabel = null;
    }
  }

  /** Start a new assistant message that will receive streaming tokens. */
  public void startAssistantMessage() {
    removeWelcome();
    hideThinkingIndicator();
    currentAssistantCell = new MessageCell("assistant", "", markdownRenderer);
    messagesBox.getChildren().add(currentAssistantCell);
    scrollToBottom();
  }

  /** Append a text token to the current streaming assistant message. */
  public void appendToken(String text) {
    if (currentAssistantCell != null) {
      currentAssistantCell.appendText(text);
      scrollToBottom();
    }
  }

  /** Replace the entire content of the current assistant message. */
  public void replaceAssistantContent(String text) {
    if (currentAssistantCell != null) {
      currentAssistantCell.replaceContent(text);
      scrollToBottom();
    }
  }

  /** Finalize the current assistant message (force final render). */
  public void finalizeAssistantMessage() {
    if (currentAssistantCell != null) {
      currentAssistantCell.finalizeContent();
      currentAssistantCell = null;
      scrollToBottom();
    }
  }

  /** Add a system/info message (tool calls, errors, etc.) */
  public void addSystemMessage(String text, String styleClass) {
    var label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add("system-message");
    label.getStyleClass().add(styleClass);
    label.setPadding(new Insets(4, 16, 4, 16));
    messagesBox.getChildren().add(label);
    scrollToBottom();
  }

  /** Add a question view with clickable options. */
  public void addQuestionView(QuestionView questionView) {
    messagesBox.getChildren().add(questionView);
    scrollToBottom();
  }

  public void clear() {
    hideThinkingIndicator();
    messagesBox.getChildren().clear();
    currentAssistantCell = null;
  }

  private void removeWelcome() {
    messagesBox
        .getChildren()
        .removeIf(node -> node instanceof Label l && l.getStyleClass().contains("welcome-label"));
  }

  private void scrollToBottom() {
    Platform.runLater(() -> setVvalue(1.0));
  }
}
