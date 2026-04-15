package com.claudecodejava.ui;

import java.io.File;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;

/** Top toolbar with directory chooser and mode selector. */
public class ToolBar extends HBox {

  private static final String CUSTOM_OPTION = "Custom...";

  private final TextField directoryField;
  private final ComboBox<String> modeCombo;
  private final ComboBox<String> modelCombo;
  private final ComboBox<String> effortCombo;
  private String previousModel = "sonnet";
  private Consumer<String> onDirectoryChanged;
  private Runnable onToolsConfig;
  private Runnable onMcpConfig;
  private Runnable onSessionHistory;
  private Runnable onUsage;
  private Runnable onSettingsChanged;

  public ToolBar(String initialDirectory) {
    setSpacing(10);
    setPadding(new Insets(8, 16, 8, 16));
    setAlignment(Pos.CENTER_LEFT);
    getStyleClass().add("toolbar");

    // Directory section
    var dirLabel = new Label("Directory:");
    dirLabel.getStyleClass().add("toolbar-label");

    directoryField = new TextField(initialDirectory);
    directoryField.getStyleClass().add("directory-field");
    HBox.setHgrow(directoryField, Priority.ALWAYS);
    directoryField.setOnAction(
        e -> {
          if (onDirectoryChanged != null) {
            onDirectoryChanged.accept(directoryField.getText().trim());
          }
        });

    var browseButton = new Button("Browse");
    browseButton.getStyleClass().add("toolbar-button");
    browseButton.setOnAction(e -> browseDirectory());

    // Permission / mode selector
    var modeLabel = new Label("Mode:");
    modeLabel.getStyleClass().add("toolbar-label");

    modeCombo = new ComboBox<>();
    modeCombo.getItems().addAll("default", "plan", "acceptEdits", "auto", "bypassPermissions");
    modeCombo.setValue("default");
    modeCombo.getStyleClass().add("toolbar-combo");
    modeCombo.setOnAction(
        e -> {
          if (onSettingsChanged != null) onSettingsChanged.run();
        });

    // Model selector
    var modelLabel = new Label("Model:");
    modelLabel.getStyleClass().add("toolbar-label");

    modelCombo = new ComboBox<>();
    modelCombo.getItems().addAll("sonnet", "opus", "haiku", CUSTOM_OPTION);
    modelCombo.setValue("sonnet");
    modelCombo.getStyleClass().add("toolbar-combo");
    modelCombo.setPrefWidth(140);

    modelCombo.setOnAction(
        e -> {
          if (CUSTOM_OPTION.equals(modelCombo.getValue())) {
            var dialog = new TextInputDialog();
            dialog.setTitle("Custom Model");
            dialog.setHeaderText("Enter a model name");
            dialog.setContentText("Model:");
            dialog.getEditor().setPromptText("e.g. claude-sonnet-4-6");

            var result = dialog.showAndWait();
            if (result.isPresent() && !result.get().isBlank()) {
              String custom = result.get().trim();
              // Add before "Custom..." if not already present
              if (!modelCombo.getItems().contains(custom)) {
                int idx = modelCombo.getItems().indexOf(CUSTOM_OPTION);
                modelCombo.getItems().add(idx, custom);
              }
              modelCombo.setValue(custom);
              previousModel = custom;
            } else {
              // Cancelled - revert
              modelCombo.setValue(previousModel);
            }
          } else {
            previousModel = modelCombo.getValue();
          }
          if (onSettingsChanged != null) onSettingsChanged.run();
        });

    // Effort level
    var effortLabel = new Label("Effort:");
    effortLabel.getStyleClass().add("toolbar-label");

    effortCombo = new ComboBox<>();
    effortCombo.getItems().addAll("low", "medium", "high", "max");
    effortCombo.setValue("high");
    effortCombo.getStyleClass().add("toolbar-combo");
    effortCombo.setOnAction(
        e -> {
          if (onSettingsChanged != null) onSettingsChanged.run();
        });

    // Config buttons
    var toolsButton = new Button("Tools");
    toolsButton.getStyleClass().add("toolbar-button");
    toolsButton.setOnAction(
        e -> {
          if (onToolsConfig != null) onToolsConfig.run();
        });

    var mcpButton = new Button("MCP");
    mcpButton.getStyleClass().add("toolbar-button");
    mcpButton.setOnAction(
        e -> {
          if (onMcpConfig != null) onMcpConfig.run();
        });

    var historyButton = new Button("History");
    historyButton.getStyleClass().add("toolbar-button");
    historyButton.setOnAction(
        e -> {
          if (onSessionHistory != null) onSessionHistory.run();
        });

    var usageButton = new Button("Usage");
    usageButton.getStyleClass().add("toolbar-button");
    usageButton.setOnAction(
        e -> {
          if (onUsage != null) onUsage.run();
        });

    getChildren()
        .addAll(
            dirLabel,
            directoryField,
            browseButton,
            createSeparator(),
            modelLabel,
            modelCombo,
            createSeparator(),
            effortLabel,
            effortCombo,
            createSeparator(),
            modeLabel,
            modeCombo,
            createSeparator(),
            toolsButton,
            mcpButton,
            historyButton,
            usageButton);
  }

  public void setOnDirectoryChanged(Consumer<String> onDirectoryChanged) {
    this.onDirectoryChanged = onDirectoryChanged;
  }

  public String getDirectory() {
    return directoryField.getText().trim();
  }

  public void setDirectory(String dir) {
    directoryField.setText(dir);
  }

  public String getPermissionMode() {
    return modeCombo.getValue();
  }

  public String getModel() {
    String val = modelCombo.getValue();
    if (val == null || val.isBlank() || CUSTOM_OPTION.equals(val)) return null;
    return val.trim();
  }

  public String getEffort() {
    return effortCombo.getValue();
  }

  public void setOnToolsConfig(Runnable onToolsConfig) {
    this.onToolsConfig = onToolsConfig;
  }

  public void setOnMcpConfig(Runnable onMcpConfig) {
    this.onMcpConfig = onMcpConfig;
  }

  public void setOnSessionHistory(Runnable onSessionHistory) {
    this.onSessionHistory = onSessionHistory;
  }

  public void setOnUsage(Runnable onUsage) {
    this.onUsage = onUsage;
  }

  public void setOnSettingsChanged(Runnable onSettingsChanged) {
    this.onSettingsChanged = onSettingsChanged;
  }

  public boolean isPlanMode() {
    return "plan".equals(modeCombo.getValue());
  }

  private void browseDirectory() {
    var chooser = new DirectoryChooser();
    chooser.setTitle("Choose Working Directory");

    String current = directoryField.getText().trim();
    if (!current.isEmpty()) {
      var dir = new File(current);
      if (dir.isDirectory()) {
        chooser.setInitialDirectory(dir);
      }
    }

    var selected = chooser.showDialog(getScene().getWindow());
    if (selected != null) {
      directoryField.setText(selected.getAbsolutePath());
      if (onDirectoryChanged != null) {
        onDirectoryChanged.accept(selected.getAbsolutePath());
      }
    }
  }

  private Label createSeparator() {
    var sep = new Label("|");
    sep.getStyleClass().add("toolbar-separator");
    return sep;
  }
}
