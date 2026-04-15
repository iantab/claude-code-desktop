package com.claudecodejava.ui;

import com.claudecodejava.cli.StreamEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Dialog showing cumulative usage stats and rate limit info. */
public class UsageDialog extends Dialog<Void> {

  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

  private static final Map<String, String> LIMIT_LABELS =
      Map.of(
          "five_hour", "Session Limit (5 hour)",
          "seven_day", "Weekly Limit",
          "seven_day_opus", "Opus Weekly Limit",
          "seven_day_sonnet", "Sonnet Weekly Limit",
          "overage", "Extra Usage");

  private final VBox content;
  private Label loadingLabel;

  public UsageDialog(ChatTab.UsageData data) {
    setTitle("Usage");
    setHeaderText("Session Usage & Rate Limits");
    getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    getDialogPane()
        .getStylesheets()
        .add(getClass().getResource("/com/claudecodejava/dark-theme.css").toExternalForm());
    getDialogPane().getStyleClass().add("dark-dialog");

    content = new VBox(16);
    content.setPadding(new Insets(16));

    buildContent(data);
    getDialogPane().setContent(content);
  }

  /** Show a loading indicator in the rate limit section. */
  public void showLoading() {
    if (loadingLabel != null) {
      loadingLabel.setText("Fetching rate limit data...");
    }
  }

  /** Update the dialog with fresh data (called after quota check completes). */
  public void updateData(ChatTab.UsageData data) {
    content.getChildren().clear();
    buildContent(data);
  }

  private void buildContent(ChatTab.UsageData data) {
    // Rate Limit section
    var rlTitle = new Label("Rate Limits");
    rlTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
    content.getChildren().add(rlTitle);

    if (data.rateLimits() != null && !data.rateLimits().isEmpty()) {
      for (var entry : data.rateLimits().entrySet()) {
        addRateLimitSection(entry.getKey(), entry.getValue());
      }
    } else {
      loadingLabel = new Label("No rate limit data yet");
      loadingLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-style: italic;");
      content.getChildren().add(loadingLabel);
    }

    // Session totals
    var sessionTitle = new Label("Session Totals");
    sessionTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
    content.getChildren().add(sessionTitle);

    var sGrid = new GridPane();
    sGrid.setHgap(16);
    sGrid.setVgap(4);
    int row = 0;

    addRow(sGrid, row++, "Model", data.model() != null ? data.model() : "default");
    addRow(sGrid, row++, "Messages", String.valueOf(data.messageCount()));
    addRow(sGrid, row++, "Total cost", String.format("$%.4f", data.totalCostUsd()));
    addRow(sGrid, row++, "Input tokens", formatTokens(data.totalInputTokens()));
    addRow(sGrid, row++, "Output tokens", formatTokens(data.totalOutputTokens()));
    addRow(sGrid, row++, "Cache read tokens", formatTokens(data.totalCacheReadTokens()));
    addRow(sGrid, row++, "Cache write tokens", formatTokens(data.totalCacheCreateTokens()));

    if (data.totalDurationMs() > 0) {
      double seconds = data.totalDurationMs() / 1000.0;
      addRow(sGrid, row++, "Total API time", String.format("%.1fs", seconds));
    }

    content.getChildren().add(sGrid);
  }

  private void addRateLimitSection(String type, StreamEvent.RateLimit rl) {
    String label = LIMIT_LABELS.getOrDefault(type, type);

    var grid = new GridPane();
    grid.setHgap(16);
    grid.setVgap(4);
    grid.setPadding(new Insets(4, 0, 8, 12));
    int row = 0;

    var typeLabel = new Label(label);
    typeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #cdd6f4;");
    grid.add(typeLabel, 0, row, 2, 1);
    row++;

    addRow(grid, row++, "Status", formatStatus(rl.status()));

    if (rl.utilization() >= 0) {
      int pct = (int) Math.floor(rl.utilization() * 100);
      addRow(grid, row++, "Used", pct + "%");
    }

    if (rl.resetsAt() > 0) {
      var resetInstant = Instant.ofEpochSecond(rl.resetsAt());
      addRow(grid, row++, "Resets at", TIME_FMT.format(resetInstant));

      var remaining = Duration.between(Instant.now(), resetInstant);
      if (remaining.isPositive()) {
        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();
        String countdown = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        addRow(grid, row++, "Time remaining", countdown);
      }
    }

    content.getChildren().add(grid);
  }

  private static String formatStatus(String status) {
    return switch (status) {
      case "allowed" -> "OK";
      case "allowed_warning" -> "Warning - approaching limit";
      case "rejected" -> "Rate limited";
      default -> status;
    };
  }

  private void addRow(GridPane grid, int row, String label, String value) {
    var labelNode = new Label(label + ":");
    labelNode.setStyle("-fx-text-fill: #a6adc8;");
    var valueNode = new Label(value);
    valueNode.setStyle("-fx-text-fill: #cdd6f4;");
    grid.add(labelNode, 0, row);
    grid.add(valueNode, 1, row);
  }

  private static String formatTokens(int tokens) {
    if (tokens >= 1_000_000) {
      return String.format("%.2fM", tokens / 1_000_000.0);
    } else if (tokens >= 1000) {
      return String.format("%.1fk", tokens / 1000.0);
    }
    return String.valueOf(tokens);
  }
}
