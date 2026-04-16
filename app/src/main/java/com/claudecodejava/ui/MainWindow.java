package com.claudecodejava.ui;

import com.claudecodejava.cli.StreamEvent;
import java.util.List;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Main application window: toolbar + tabbed chat sessions. */
public class MainWindow extends BorderPane {

  private final ToolBar toolBar;
  private final TabPane tabPane;

  private List<String> allowedTools = List.of();
  private List<String> disallowedTools = List.of();
  private String mcpConfigPath = null;
  private final SessionHistoryView sessionHistory;
  private boolean historyVisible = false;

  public MainWindow(String initialDirectory) {
    getStyleClass().add("main-window");

    tabPane = new TabPane();
    tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
    tabPane.getStyleClass().add("chat-tab-pane");
    VBox.setVgrow(tabPane, Priority.ALWAYS);

    // "+" tab to create new tabs
    var addTab = new Tab("+");
    addTab.setClosable(false);
    addTab.getStyleClass().add("add-tab");
    tabPane.getTabs().add(addTab);

    toolBar = new ToolBar(initialDirectory);
    toolBar.setOnDirectoryChanged(
        dir -> {
          var tab = getActiveTab();
          if (tab != null) tab.updateDirectory(dir);
        });
    toolBar.setOnToolsConfig(this::showToolConfig);
    toolBar.setOnMcpConfig(this::showMcpConfig);
    toolBar.setOnSessionHistory(this::toggleSessionHistory);
    toolBar.setOnUsage(this::showUsage);
    toolBar.setOnPlugins(this::showPlugins);
    toolBar.setOnSettingsChanged(
        () -> {
          var tab = getActiveTab();
          if (tab != null) {
            syncSettings(tab);
            String model = toolBar.getModel();
            String effort = toolBar.getEffort();
            String mode = toolBar.getPermissionMode();
            tab.showSystemMessage(
                "Settings: model=" + model + ", effort=" + effort + ", mode=" + mode,
                "system-info");
          }
        });

    sessionHistory = new SessionHistoryView(this::resumeSession);

    // Tab selection listener (must be after toolBar init)
    tabPane
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (_, _, newTab) -> {
              if (newTab == addTab) {
                addNewTab(initialDirectory);
              } else if (newTab instanceof ChatTab ct) {
                toolBar.setDirectory(ct.getSessionManager().getWorkingDirectory());
              }
            });

    setTop(toolBar);
    setCenter(tabPane);

    // Create first tab
    addNewTab(initialDirectory);
  }

  public void focusInput() {
    var tab = getActiveTab();
    if (tab != null) tab.focusInput();
  }

  /** Set up keyboard shortcuts on the scene. Call after scene is set. */
  public void setupKeyboardShortcuts() {
    var scene = getScene();
    if (scene == null) return;

    // Ctrl+T: new tab
    scene
        .getAccelerators()
        .put(
            new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
            () -> addNewTab(toolBar.getDirectory()));

    // Ctrl+W: close current tab
    scene
        .getAccelerators()
        .put(
            new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),
            () -> {
              var tab = getActiveTab();
              if (tab != null && tabPane.getTabs().size() > 2) {
                tabPane.getTabs().remove(tab);
              }
            });
  }

  private void addNewTab(String directory) {
    var chatTab = new ChatTab(directory);
    syncSettings(chatTab);

    // Insert before the "+" tab
    int addTabIndex = tabPane.getTabs().size() - 1;
    tabPane.getTabs().add(addTabIndex, chatTab);
    tabPane.getSelectionModel().select(chatTab);
    chatTab.focusInput();
  }

  private ChatTab getActiveTab() {
    Tab selected = tabPane.getSelectionModel().getSelectedItem();
    return selected instanceof ChatTab ct ? ct : null;
  }

  private void syncSettings(ChatTab tab) {
    tab.updateSettings(
        toolBar.getPermissionMode(),
        toolBar.getModel(),
        toolBar.getEffort(),
        allowedTools,
        disallowedTools,
        mcpConfigPath);
  }

  private void toggleSessionHistory() {
    historyVisible = !historyVisible;
    if (historyVisible) {
      var tab = getActiveTab();
      String dir =
          tab != null ? tab.getSessionManager().getWorkingDirectory() : toolBar.getDirectory();
      sessionHistory.loadSessions(dir, this::resumeSession);
      setLeft(sessionHistory);
    } else {
      setLeft(null);
    }
  }

  private void resumeSession(String sessionId) {
    var tab = getActiveTab();
    if (tab != null) {
      tab.getSessionManager().setCurrentSessionId(sessionId);
      syncSettings(tab);

      // Load and display the full conversation history
      var messages =
          SessionHistoryView.loadConversation(
              tab.getSessionManager().getWorkingDirectory(), sessionId);
      tab.loadSessionHistory(messages);
      tab.focusInput();
    }
  }

  private void showPlugins() {
    new PluginDialog(getScene().getWindow()).show();
  }

  private void showUsage() {
    var tab = getActiveTab();
    if (tab == null) return;

    var data = tab.getUsageData();
    if (data.rateLimits().isEmpty()) {
      // No rate limit data yet — fetch it, then open the dialog
      var dialog = new UsageDialog(data);
      dialog.showLoading();
      dialog.show();
      tab.fetchQuotaCheck(() -> dialog.updateData(tab.getUsageData()));
    } else {
      new UsageDialog(data).showAndWait();
    }
  }

  private void showToolConfig() {
    var dialog = new ToolConfigDialog(allowedTools, disallowedTools);
    dialog
        .showAndWait()
        .ifPresent(
            config -> {
              allowedTools = config.allowed();
              disallowedTools = config.disallowed();
            });
  }

  private void showMcpConfig() {
    var tab = getActiveTab();
    List<StreamEvent.McpServer> servers = tab != null ? tab.getLastMcpServers() : List.of();
    var dialog = new McpConfigDialog(servers, mcpConfigPath);
    dialog.showAndWait().ifPresent(path -> mcpConfigPath = path);
  }
}
