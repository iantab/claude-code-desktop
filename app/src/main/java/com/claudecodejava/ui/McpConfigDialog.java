package com.claudecodejava.ui;

import com.claudecodejava.cli.StreamEvent;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Dialog showing MCP server status and config path input. */
public class McpConfigDialog extends Dialog<String> {

  public McpConfigDialog(List<StreamEvent.McpServer> servers, String currentConfigPath) {
    setTitle("MCP Servers");
    setHeaderText("Model Context Protocol server configuration");
    getDialogPane()
        .getStylesheets()
        .add(getClass().getResource("/com/claudecodejava/dark-theme.css").toExternalForm());
    getDialogPane().getStyleClass().add("dark-dialog");

    var applyType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
    getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

    var content = new VBox(12);
    content.setPadding(new Insets(16));

    // Server status list
    if (!servers.isEmpty()) {
      var statusLabel = new Label("Connected Servers:");
      statusLabel.setStyle("-fx-font-weight: bold;");
      content.getChildren().add(statusLabel);

      var grid = new GridPane();
      grid.setHgap(16);
      grid.setVgap(4);

      int row = 0;
      for (var server : servers) {
        var nameLabel = new Label(server.name());
        var statusIndicator = new Label(server.status());
        statusIndicator.setStyle(
            "connected".equals(server.status())
                ? "-fx-text-fill: #a6e3a1;"
                : "-fx-text-fill: #f9e2af;");
        grid.add(nameLabel, 0, row);
        grid.add(statusIndicator, 1, row);
        row++;
      }
      content.getChildren().add(grid);
    } else {
      content.getChildren().add(new Label("No MCP servers detected yet. Send a message first."));
    }

    // Config path input
    var configLabel = new Label("MCP Config Path (JSON file):");
    configLabel.setStyle("-fx-font-weight: bold; -fx-padding: 12 0 0 0;");
    var configField = new TextField(currentConfigPath != null ? currentConfigPath : "");
    configField.setPromptText("e.g. ~/.claude/mcp-servers.json");

    content.getChildren().addAll(configLabel, configField);
    getDialogPane().setContent(content);

    setResultConverter(
        buttonType -> {
          if (buttonType == applyType) {
            String path = configField.getText().trim();
            return path.isEmpty() ? null : path;
          }
          return null;
        });
  }
}
