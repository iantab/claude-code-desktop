package com.claudecodejava.ui;

import com.claudecodejava.cli.StreamEvent;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Inline widget showing a question from Claude with clickable option buttons. */
public class QuestionView extends VBox {

  public QuestionView(StreamEvent.QuestionData question, Consumer<String> onChoice) {
    setSpacing(8);
    setPadding(new Insets(12, 16, 12, 16));
    getStyleClass().add("question-view");

    var questionLabel = new Label(question.question());
    questionLabel.setWrapText(true);
    questionLabel.getStyleClass().add("question-text");
    getChildren().add(questionLabel);

    if (question.options().isEmpty()) {
      var hint = new Label("Type your answer below");
      hint.getStyleClass().add("question-hint");
      getChildren().add(hint);
    } else if (question.multiSelect()) {
      buildMultiSelect(question, onChoice);
    } else {
      buildSingleSelect(question, onChoice);
    }
  }

  private void buildSingleSelect(StreamEvent.QuestionData question, Consumer<String> onChoice) {
    for (var option : question.options()) {
      var btn = new Button(option.label());
      btn.getStyleClass().add("option-button");
      btn.setMaxWidth(Double.MAX_VALUE);

      if (!option.description().isBlank()) {
        btn.setText(option.label() + " \u2014 " + option.description());
      }

      btn.setOnAction(
          e -> {
            getChildren().stream()
                .filter(node -> node instanceof Button)
                .forEach(node -> node.setDisable(true));
            btn.getStyleClass().add("option-selected");
            onChoice.accept(option.label());
          });

      getChildren().add(btn);
    }
  }

  private void buildMultiSelect(StreamEvent.QuestionData question, Consumer<String> onChoice) {
    Set<String> selected = new LinkedHashSet<>();

    var submitBtn = new Button("Submit");
    submitBtn.getStyleClass().addAll("option-button", "submit-button");
    submitBtn.setMaxWidth(Double.MAX_VALUE);
    submitBtn.setDisable(true);

    for (var option : question.options()) {
      var btn = new Button(option.label());
      btn.getStyleClass().add("option-button");
      btn.setMaxWidth(Double.MAX_VALUE);

      if (!option.description().isBlank()) {
        btn.setText(option.label() + " \u2014 " + option.description());
      }

      btn.setOnAction(
          e -> {
            if (selected.contains(option.label())) {
              selected.remove(option.label());
              btn.getStyleClass().remove("option-selected");
            } else {
              selected.add(option.label());
              btn.getStyleClass().add("option-selected");
            }
            submitBtn.setDisable(selected.isEmpty());
          });

      getChildren().add(btn);
    }

    submitBtn.setOnAction(
        e -> {
          getChildren().stream()
              .filter(node -> node instanceof Button)
              .forEach(node -> node.setDisable(true));
          String joined = selected.stream().collect(Collectors.joining(", "));
          onChoice.accept(joined);
        });

    getChildren().add(submitBtn);
  }

  /** Creates a plan approval view with Approve / Request Changes buttons. */
  public static QuestionView forPlanApproval(Consumer<String> onChoice, Runnable onRequestChanges) {
    var question =
        new StreamEvent.QuestionData(
            "Plan complete. Ready to proceed?", java.util.List.of(), false, null);
    var view = new QuestionView(question, onChoice);

    view.getChildren()
        .removeIf(node -> node instanceof Label l && l.getStyleClass().contains("question-hint"));

    var approveBtn = new Button("Approve Plan");
    approveBtn.getStyleClass().addAll("option-button", "approve-button");
    approveBtn.setMaxWidth(Double.MAX_VALUE);
    approveBtn.setOnAction(
        e -> {
          approveBtn.setDisable(true);
          approveBtn.getStyleClass().add("option-selected");
          onChoice.accept("yes, proceed with the plan");
        });

    var changesBtn = new Button("Request Changes");
    changesBtn.getStyleClass().addAll("option-button", "changes-button");
    changesBtn.setMaxWidth(Double.MAX_VALUE);
    changesBtn.setOnAction(
        e -> {
          onRequestChanges.run();
        });

    view.getChildren().addAll(approveBtn, changesBtn);
    return view;
  }
}
