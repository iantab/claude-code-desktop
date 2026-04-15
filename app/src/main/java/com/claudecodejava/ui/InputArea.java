package com.claudecodejava.ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Input area at the bottom of the chat. Enter to send, Shift+Enter for newline. */
public class InputArea extends VBox {

  private final TextArea textArea;
  private final Button sendButton;
  private final Button cancelButton;
  private final Button newSessionButton;
  private Consumer<String> onSend;
  private Runnable onCancel;
  private Runnable onNewSession;
  private boolean isBusy = false;

  public InputArea() {
    setSpacing(8);
    setPadding(new Insets(12, 16, 12, 16));
    getStyleClass().add("input-area");

    textArea = new TextArea();
    textArea.setPromptText("Message Claude... (Enter to send, Shift+Enter for newline)");
    textArea.setPrefRowCount(3);
    textArea.setMaxHeight(120);
    textArea.setWrapText(true);
    textArea.getStyleClass().add("input-textarea");

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
          }
        });

    sendButton = new Button("Send");
    sendButton.getStyleClass().add("send-button");
    sendButton.setOnAction(e -> send());

    cancelButton = new Button("Cancel");
    cancelButton.getStyleClass().add("cancel-button");
    cancelButton.setOnAction(
        e -> {
          if (onCancel != null) onCancel.run();
        });
    cancelButton.setVisible(false);
    cancelButton.setManaged(false);

    newSessionButton = new Button("New Chat");
    newSessionButton.getStyleClass().add("new-session-button");
    newSessionButton.setOnAction(
        e -> {
          if (onNewSession != null) onNewSession.run();
        });

    var buttonBar = new HBox(8);
    buttonBar.setAlignment(Pos.CENTER_RIGHT);
    buttonBar.getChildren().addAll(newSessionButton, cancelButton, sendButton);

    VBox.setVgrow(textArea, Priority.ALWAYS);
    getChildren().addAll(textArea, buttonBar);
  }

  public void setOnSend(Consumer<String> onSend) {
    this.onSend = onSend;
  }

  public void setOnCancel(Runnable onCancel) {
    this.onCancel = onCancel;
  }

  public void setOnNewSession(Runnable onNewSession) {
    this.onNewSession = onNewSession;
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
