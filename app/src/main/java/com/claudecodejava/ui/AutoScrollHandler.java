package com.claudecodejava.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

/** Browser-style middle-click auto-scroll for a ScrollPane. */
public class AutoScrollHandler {

  private static final double DEAD_ZONE = 15.0;
  private static final double SPEED_FACTOR = 0.8;
  private static final double SPEED_EXPONENT = 1.5;

  private final ScrollPane scrollPane;
  private final Group anchorIcon;
  private final AnimationTimer scrollTimer;

  private boolean active = false;
  private double anchorY;
  private double currentMouseY;
  private long lastNanos;

  public AutoScrollHandler(ScrollPane scrollPane, StackPane overlayContainer) {
    this.scrollPane = scrollPane;
    this.anchorIcon = createAnchorIcon();
    overlayContainer.getChildren().add(anchorIcon);

    this.scrollTimer =
        new AnimationTimer() {
          @Override
          public void handle(long now) {
            if (lastNanos == 0) {
              lastNanos = now;
              return;
            }
            double elapsedSeconds = (now - lastNanos) / 1_000_000_000.0;
            lastNanos = now;

            double deltaY = currentMouseY - anchorY;
            if (Math.abs(deltaY) < DEAD_ZONE) return;

            double effectiveDelta = deltaY > 0 ? deltaY - DEAD_ZONE : deltaY + DEAD_ZONE;
            double sign = Math.signum(effectiveDelta);
            double magnitude = Math.abs(effectiveDelta);
            double pixelsPerSecond = sign * SPEED_FACTOR * Math.pow(magnitude, SPEED_EXPONENT);

            double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollableHeight = contentHeight - viewportHeight;
            if (scrollableHeight <= 0) return;

            double vvalueDelta = (pixelsPerSecond * elapsedSeconds) / scrollableHeight;
            double newVvalue = scrollPane.getVvalue() + vvalueDelta;
            scrollPane.setVvalue(Math.max(0.0, Math.min(1.0, newVvalue)));
          }
        };

    installHandlers();
  }

  public boolean isActive() {
    return active;
  }

  private void installHandlers() {
    scrollPane.addEventFilter(
        MouseEvent.MOUSE_PRESSED,
        e -> {
          if (e.getButton() == MouseButton.MIDDLE) {
            if (active) {
              deactivate();
            } else {
              activate(e.getX(), e.getY());
            }
            e.consume();
          } else if (active) {
            deactivate();
          }
        });

    scrollPane.addEventFilter(
        MouseEvent.MOUSE_MOVED,
        e -> {
          if (active) currentMouseY = e.getY();
        });

    scrollPane.addEventFilter(
        MouseEvent.MOUSE_DRAGGED,
        e -> {
          if (active) currentMouseY = e.getY();
        });

    scrollPane.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (active && e.getCode() == KeyCode.ESCAPE) {
            deactivate();
            e.consume();
          }
        });

    scrollPane.addEventFilter(
        ScrollEvent.SCROLL,
        e -> {
          if (active) deactivate();
        });
  }

  private void activate(double x, double y) {
    active = true;
    anchorY = y;
    currentMouseY = y;
    lastNanos = 0;

    anchorIcon.setLayoutX(x);
    anchorIcon.setLayoutY(y);
    anchorIcon.setVisible(true);

    scrollPane.setCursor(Cursor.NONE);
    scrollPane.requestFocus();
    scrollTimer.start();
  }

  private void deactivate() {
    active = false;
    anchorIcon.setVisible(false);
    scrollTimer.stop();
    scrollPane.setCursor(Cursor.DEFAULT);
  }

  private static Group createAnchorIcon() {
    Circle circle = new Circle(10);
    circle.setFill(Color.rgb(137, 180, 250, 0.3));
    circle.setStroke(Color.rgb(137, 180, 250, 0.8));
    circle.setStrokeWidth(1.5);

    Polygon upArrow = new Polygon(0, -4, -4, 2, 4, 2);
    upArrow.setTranslateY(-16);
    upArrow.setFill(Color.rgb(205, 214, 244, 0.8));

    Polygon downArrow = new Polygon(0, 4, -4, -2, 4, -2);
    downArrow.setTranslateY(16);
    downArrow.setFill(Color.rgb(205, 214, 244, 0.8));

    Group icon = new Group(circle, upArrow, downArrow);
    icon.setManaged(false);
    icon.setVisible(false);
    icon.setMouseTransparent(true);
    return icon;
  }
}
