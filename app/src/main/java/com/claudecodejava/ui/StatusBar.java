package com.claudecodejava.ui;

import com.claudecodejava.cli.StreamEvent;
import java.time.Duration;
import java.time.Instant;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Status bar showing model, usage tokens, cost, and rate limit info. */
public class StatusBar extends HBox {

  private final Label modelLabel;
  private final Label tokensLabel;
  private final Label costLabel;
  private final Label rateLimitLabel;

  public StatusBar() {
    setSpacing(16);
    setPadding(new Insets(4, 16, 4, 16));
    setAlignment(Pos.CENTER_LEFT);
    getStyleClass().add("status-bar");

    modelLabel = new Label("");
    modelLabel.getStyleClass().add("status-item");

    tokensLabel = new Label("");
    tokensLabel.getStyleClass().add("status-item");

    costLabel = new Label("");
    costLabel.getStyleClass().add("status-item");

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    rateLimitLabel = new Label("");
    rateLimitLabel.getStyleClass().add("status-item");

    getChildren().addAll(modelLabel, tokensLabel, costLabel, spacer, rateLimitLabel);
  }

  public void setModel(String model) {
    if (model != null && !model.isEmpty()) {
      modelLabel.setText("Model: " + model);
    }
  }

  public void updateFromResult(StreamEvent.Result result) {
    int totalIn = result.inputTokens() + result.cacheReadTokens() + result.cacheCreateTokens();
    int totalOut = result.outputTokens();
    tokensLabel.setText(
        "Tokens: " + formatTokenCount(totalIn) + " in / " + formatTokenCount(totalOut) + " out");

    if (result.costUsd() > 0) {
      costLabel.setText("Cost: $" + String.format("%.4f", result.costUsd()));
    }
  }

  public void updateFromRateLimit(StreamEvent.RateLimit rl) {
    if (rl.resetsAt() > 0) {
      var resetInstant = Instant.ofEpochSecond(rl.resetsAt());
      var now = Instant.now();
      var remaining = Duration.between(now, resetInstant);

      if (remaining.isPositive()) {
        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();
        String timeStr;
        if (hours > 0) {
          timeStr = hours + "h " + minutes + "m";
        } else {
          timeStr = minutes + "m";
        }
        rateLimitLabel.setText("Resets in: " + timeStr + " (" + rl.rateLimitType() + ")");
      } else {
        rateLimitLabel.setText("Limit reset");
      }
    }
  }

  private static String formatTokenCount(int tokens) {
    if (tokens >= 1000) {
      return String.format("%.1fk", tokens / 1000.0);
    }
    return String.valueOf(tokens);
  }
}
