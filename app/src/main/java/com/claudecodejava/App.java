package com.claudecodejava;

import com.claudecodejava.cli.SessionManager;
import com.claudecodejava.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

  @Override
  public void start(Stage primaryStage) {
    var sessionManager = new SessionManager();
    var mainWindow = new MainWindow(sessionManager);

    var scene = new Scene(mainWindow, 900, 700);
    scene.getStylesheets().add(getClass().getResource("dark-theme.css").toExternalForm());

    primaryStage.setTitle("Claude Code Java");
    primaryStage.setScene(scene);
    primaryStage.show();

    mainWindow.focusInput();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
