package com.claudecodejava.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/** Lightweight animation utilities for interaction feedback. */
public final class Animations {

  private Animations() {}

  /** Adds a subtle scale-down press effect to a node (buttons, etc.). */
  public static void addPressEffect(Node node) {
    node.setOnMousePressed(
        e -> {
          var scale = new ScaleTransition(Duration.millis(80), node);
          scale.setToX(0.96);
          scale.setToY(0.96);
          scale.play();
        });
    node.setOnMouseReleased(
        e -> {
          var scale = new ScaleTransition(Duration.millis(80), node);
          scale.setToX(1.0);
          scale.setToY(1.0);
          scale.play();
        });
  }

  /** Fades a node in from transparent. */
  public static void fadeIn(Node node, Duration duration) {
    node.setOpacity(0);
    var fade = new FadeTransition(duration, node);
    fade.setFromValue(0);
    fade.setToValue(1);
    fade.play();
  }
}
