package com.claudecodejava.ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Input area at the bottom of the chat. Enter to send, Shift+Enter for newline. */
public class InputArea extends VBox {

  private final TextArea textArea;
  private final Button sendButton;
  private final Button cancelButton;
  private Consumer<String> onSend;
  private Runnable onCancel;
  private boolean isBusy = false;

  private static final double LINE_HEIGHT = 20.0;
  private static final double PADDING = 18.0;
  private static final int MIN_ROWS = 3;
  private static final int MAX_ROWS = 16;
  public static final double MIN_HEIGHT = LINE_HEIGHT * MIN_ROWS + PADDING;
  public static final double MAX_HEIGHT = LINE_HEIGHT * MAX_ROWS + PADDING;

  private double manualMinHeight = MIN_HEIGHT;

  public InputArea() {
    setSpacing(8);
    setPadding(new Insets(8, 16, 10, 16));
    getStyleClass().add("input-area");

    textArea = new TextArea();
    textArea.setPromptText("Message Claude... (Enter to send, Shift+Enter for newline)");
    textArea.setMinHeight(MIN_HEIGHT);
    textArea.setMaxHeight(MAX_HEIGHT);
    textArea.setPrefHeight(MIN_HEIGHT);
    textArea.setWrapText(true);
    textArea.getStyleClass().add("input-textarea");

    // Auto-grow as user types (never shrinks below manual resize height)
    textArea
        .textProperty()
        .addListener(
            (obs, old, newText) -> {
              int lineCount =
                  Math.max(MIN_ROWS, newText == null ? 1 : newText.split("\n", -1).length);
              lineCount = Math.min(lineCount, MAX_ROWS);
              double targetHeight = Math.max(manualMinHeight, lineCount * LINE_HEIGHT + PADDING);
              textArea.setPrefHeight(targetHeight);
            });

    textArea.setOnKeyPressed(
        e -> {
          if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
            e.consume();
            send();
          } else if (e.getCode() == KeyCode.ESCAPE) {
            e.consume();
            if (onCancel != null && isBusy) {
              onCancel.run();
            }
          } else if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.UP) {
            // Ctrl+Shift+Up: grow text area by one row
            e.consume();
            setInputHeight(textArea.getPrefHeight() + LINE_HEIGHT);
          } else if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.DOWN) {
            // Ctrl+Shift+Down: shrink text area by one row
            e.consume();
            setInputHeight(textArea.getPrefHeight() - LINE_HEIGHT);
          }
        });

    sendButton = new Button("Send");
    sendButton.getStyleClass().add("send-button");
    sendButton.setOnAction(e -> send());
    Animations.addPressEffect(sendButton);

    cancelButton = new Button("Cancel");
    cancelButton.getStyleClass().add("cancel-button");
    cancelButton.setOnAction(
        e -> {
          if (onCancel != null) onCancel.run();
        });
    Animations.addPressEffect(cancelButton);
    cancelButton.setVisible(false);
    cancelButton.setManaged(false);

    var buttonBar = new HBox(8);
    buttonBar.setAlignment(Pos.CENTER_RIGHT);
    buttonBar.getChildren().addAll(cancelButton, sendButton);

    getChildren().addAll(textArea, buttonBar);
  }

  /** Get the current text area preferred height. */
  public double getInputHeight() {
    return textArea.getPrefHeight();
  }

  /** Set the text area height (called by the resize divider in ChatTab). */
  public void setInputHeight(double height) {
    double clamped = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));
    textArea.setPrefHeight(clamped);
    textArea.setMinHeight(clamped);
    manualMinHeight = clamped;
  }

  public void setOnSend(Consumer<String> onSend) {
    this.onSend = onSend;
  }

  public void setOnCancel(Runnable onCancel) {
    this.onCancel = onCancel;
  }

  public void setBusy(boolean busy) {
    this.isBusy = busy;
    sendButton.setDisable(busy);
    textArea.setDisable(busy);
    cancelButton.setVisible(busy);
    cancelButton.setManaged(busy);
  }

  public void focus() {
    textArea.requestFocus();
  }

  private void send() {
    if (isBusy) return;
    String text = textArea.getText().trim();
    if (text.isEmpty()) return;
    textArea.clear();
    if (onSend != null) onSend.accept(text);
  }
}
