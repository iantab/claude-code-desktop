package com.claudecodejava.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Scrollable chat view that displays a list of messages. */
public class ChatView extends ScrollPane {

  private final VBox messagesBox;
  private final MarkdownRenderer markdownRenderer;
  private final StackPane overlayContainer;
  private final AutoScrollHandler autoScrollHandler;
  private MessageCell currentAssistantCell;
  private Label thinkingLabel;
  private Timeline thinkingAnimation;

  public ChatView() {
    markdownRenderer = new MarkdownRenderer();
    messagesBox = new VBox();
    messagesBox.setSpacing(4);
    messagesBox.setPadding(new Insets(8, 8, 30, 8));
    messagesBox.getStyleClass().add("messages-box");

    setContent(messagesBox);
    setFitToWidth(true);
    setHbarPolicy(ScrollBarPolicy.NEVER);
    setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
    getStyleClass().add("chat-scroll");

    // Clip the ScrollPane to its bounds so WebView native windows
    // can't extend beyond the viewport and steal mouse events.
    var scrollClip = new Rectangle();
    scrollClip.widthProperty().bind(widthProperty());
    scrollClip.heightProperty().bind(heightProperty());
    setClip(scrollClip);

    overlayContainer = new StackPane(this);
    StackPane.setAlignment(this, Pos.TOP_LEFT);
    autoScrollHandler = new AutoScrollHandler(this, overlayContainer);

    // Welcome message
    var welcomeBox = new VBox(8);
    welcomeBox.setAlignment(Pos.CENTER);
    welcomeBox.setPadding(new Insets(60, 20, 20, 20));
    welcomeBox.getStyleClass().add("welcome-label");

    var title = new Label("Claude Code");
    title.getStyleClass().add("welcome-title");

    var subtitle = new Label("Type a message to get started");
    subtitle.getStyleClass().add("welcome-subtitle");

    welcomeBox.getChildren().addAll(title, subtitle);
    messagesBox.getChildren().add(welcomeBox);
  }

  /** Add a user message bubble. */
  public void addUserMessage(String text) {
    removeWelcome();
    var cell = new MessageCell("user", text, null);
    messagesBox.getChildren().add(cell);
    Animations.fadeIn(cell, Duration.millis(150));
    scrollToBottom();
  }

  private FadeTransition thinkingPulse;

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

    // Pulsing opacity
    thinkingPulse = new FadeTransition(Duration.millis(800), thinkingLabel);
    thinkingPulse.setFromValue(0.5);
    thinkingPulse.setToValue(1.0);
    thinkingPulse.setCycleCount(Animation.INDEFINITE);
    thinkingPulse.setAutoReverse(true);
    thinkingPulse.play();
  }

  /** Hide the thinking indicator. */
  public void hideThinkingIndicator() {
    if (thinkingAnimation != null) {
      thinkingAnimation.stop();
      thinkingAnimation = null;
    }
    if (thinkingPulse != null) {
      thinkingPulse.stop();
      thinkingPulse = null;
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
    Animations.fadeIn(currentAssistantCell, Duration.millis(150));
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

  /** Returns the StackPane wrapper that should be used in layouts instead of this ScrollPane. */
  public StackPane getContainer() {
    return overlayContainer;
  }

  public void clear() {
    hideThinkingIndicator();
    messagesBox.getChildren().clear();
    currentAssistantCell = null;
  }

  private void removeWelcome() {
    messagesBox
        .getChildren()
        .removeIf(node -> node.getStyleClass().contains("welcome-label"));
  }

  private void scrollToBottom() {
    if (autoScrollHandler != null && autoScrollHandler.isActive()) return;
    Platform.runLater(() -> setVvalue(1.0));
  }
}
