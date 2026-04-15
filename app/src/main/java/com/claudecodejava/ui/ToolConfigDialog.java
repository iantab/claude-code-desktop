package com.claudecodejava.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/** Dialog to configure which tools are allowed or denied. */
public class ToolConfigDialog extends Dialog<ToolConfigDialog.ToolConfig> {

  public record ToolConfig(List<String> allowed, List<String> disallowed) {}

  private static final List<String> TOOLS =
      List.of(
          "Read",
          "Write",
          "Edit",
          "Bash",
          "Glob",
          "Grep",
          "Agent",
          "WebSearch",
          "WebFetch",
          "Monitor",
          "NotebookEdit");

  private final Map<String, ComboBox<String>> toolCombos = new LinkedHashMap<>();

  public ToolConfigDialog(List<String> currentAllowed, List<String> currentDisallowed) {
    setTitle("Tool Configuration");
    setHeaderText("Configure tool access for this session");

    var applyType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
    getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

    var grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(8);
    grid.setPadding(new Insets(16));

    grid.add(new Label("Tool"), 0, 0);
    grid.add(new Label("Permission"), 1, 0);

    int row = 1;
    for (String tool : TOOLS) {
      var label = new Label(tool);
      var combo = new ComboBox<String>();
      combo.getItems().addAll("Default", "Allow", "Deny");

      if (currentAllowed.contains(tool)) {
        combo.setValue("Allow");
      } else if (currentDisallowed.contains(tool)) {
        combo.setValue("Deny");
      } else {
        combo.setValue("Default");
      }

      toolCombos.put(tool, combo);
      grid.add(label, 0, row);
      grid.add(combo, 1, row);
      row++;
    }

    getDialogPane().setContent(grid);

    setResultConverter(
        buttonType -> {
          if (buttonType == applyType) {
            var allowed = new ArrayList<String>();
            var disallowed = new ArrayList<String>();
            for (var entry : toolCombos.entrySet()) {
              String value = entry.getValue().getValue();
              if ("Allow".equals(value)) {
                allowed.add(entry.getKey());
              } else if ("Deny".equals(value)) {
                disallowed.add(entry.getKey());
              }
            }
            return new ToolConfig(allowed, disallowed);
          }
          return null;
        });
  }
}
