package com.claudecodejava;

import com.claudecodejava.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

  @Override
  public void start(Stage primaryStage) {
    String initialDir = System.getProperty("user.dir");
    var mainWindow = new MainWindow(initialDir);

    var scene = new Scene(mainWindow, 1100, 750);
    scene.getStylesheets().add(getClass().getResource("dark-theme.css").toExternalForm());

    mainWindow.setupKeyboardShortcuts();

    primaryStage.setTitle("Claude Code Java");
    primaryStage.setScene(scene);
    primaryStage.show();

    mainWindow.focusInput();
  }

  static void main(String[] args) {
    launch(args);
  }
}
