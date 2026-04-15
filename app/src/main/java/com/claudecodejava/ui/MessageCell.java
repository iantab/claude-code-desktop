package com.claudecodejava.ui;

import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * A single message in the chat view. Uses a plain Label during streaming for reliable sizing, then
 * swaps to a WebView with rendered markdown on finalize.
 */
public class MessageCell extends VBox {

  private final boolean isUser;
  private final MarkdownRenderer markdownRenderer;
  private Label contentLabel;
  private StringBuilder contentBuilder;

  public MessageCell(String role, String initialContent, MarkdownRenderer markdownRenderer) {
    this.isUser = "user".equals(role);
    this.markdownRenderer = markdownRenderer;

    setSpacing(4);
    setPadding(new Insets(8, 16, 8, 16));
    getStyleClass().add("message-cell");
    getStyleClass().add(isUser ? "user-message" : "assistant-message");

    // Role label
    var roleLabel = new Label(isUser ? "You" : "Claude");
    roleLabel.getStyleClass().add("role-label");
    roleLabel.getStyleClass().add(isUser ? "user-role" : "assistant-role");
    getChildren().add(roleLabel);

    contentLabel = new Label(initialContent != null ? initialContent : "");
    contentLabel.setWrapText(true);
    contentLabel.getStyleClass().add(isUser ? "user-text" : "assistant-text");
    getChildren().add(contentLabel);

    if (!isUser) {
      contentBuilder = new StringBuilder();
      if (initialContent != null && !initialContent.isEmpty()) {
        contentBuilder.append(initialContent);
      }
    }
  }

  /** Append streaming text to this assistant message. */
  public void appendText(String text) {
    if (isUser || contentBuilder == null) return;
    contentBuilder.append(text);
    contentLabel.setText(contentBuilder.toString());
  }

  /** Replace the entire content (for non-incremental updates). */
  public void replaceContent(String text) {
    if (isUser || contentBuilder == null) return;
    contentBuilder.setLength(0);
    contentBuilder.append(text);
    contentLabel.setText(text);
  }

  /** Finalize: swap Label for WebView with rendered markdown (assistant only). */
  public void finalizeContent() {
    if (isUser || contentBuilder == null || markdownRenderer == null) return;

    String text = contentBuilder.toString();
    if (text.isBlank()) return;

    // Replace the plain Label with a markdown-rendered WebView
    var webView = new WebView();
    webView.setPrefHeight(30);
    webView.setMinHeight(30);
    webView.setMaxWidth(Double.MAX_VALUE);
    webView.setPageFill(javafx.scene.paint.Color.TRANSPARENT);

    // Auto-resize height to content after load
    webView
        .getEngine()
        .getLoadWorker()
        .stateProperty()
        .addListener(
            (obs, old, state) -> {
              if (state == Worker.State.SUCCEEDED) {
                try {
                  Object result = webView.getEngine().executeScript("document.body.scrollHeight");
                  if (result instanceof Number num) {
                    double h = num.doubleValue() + 10;
                    webView.setPrefHeight(h);
                    webView.setMinHeight(h);
                    webView.setMaxHeight(h);
                  }
                } catch (Exception ignored) {
                }
              }
            });

    String html = markdownRenderer.renderToHtml(text);

    // Open links in system browser instead of inside the WebView
    webView
        .getEngine()
        .locationProperty()
        .addListener(
            (obs, oldUrl, newUrl) -> {
              if (newUrl != null && !newUrl.isBlank() && !newUrl.startsWith("data:")) {
                javafx.application.Platform.runLater(() -> webView.getEngine().loadContent(html));
                try {
                  java.awt.Desktop.getDesktop().browse(new java.net.URI(newUrl));
                } catch (Exception ignored) {
                }
              }
            });

    webView.getEngine().loadContent(html);

    // Swap the label for the webview
    int idx = getChildren().indexOf(contentLabel);
    if (idx >= 0) {
      getChildren().set(idx, webView);
    }
    contentLabel = null;
  }

  public String getContent() {
    if (isUser) return "";
    return contentBuilder != null ? contentBuilder.toString() : "";
  }
}
