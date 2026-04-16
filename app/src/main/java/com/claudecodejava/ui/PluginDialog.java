package com.claudecodejava.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Plugin management window: installed plugins and marketplace. */
public class PluginDialog {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int CLI_TIMEOUT_SECONDS = 30;

  private final Stage stage;
  private final VBox installedList;
  private final VBox marketplaceList;
  private final Label statusLabel;
  private final ProgressIndicator spinner;
  private final List<JsonNode> allMarketplacePlugins = new ArrayList<>();
  private final Map<String, Integer> installCounts = new HashMap<>();
  private final List<String> installedIds = new ArrayList<>();

  public PluginDialog(Window owner) {
    stage = new Stage();
    stage.setTitle("Plugins");
    stage.initModality(Modality.APPLICATION_MODAL);
    if (owner != null) stage.initOwner(owner);

    statusLabel = new Label("");
    statusLabel.getStyleClass().add("status-item");

    spinner = new ProgressIndicator();
    spinner.setMaxSize(14, 14);
    spinner.setVisible(false);

    // === Header ===
    var titleLabel = new Label("Plugins");
    titleLabel.getStyleClass().addAll("plugin-name");

    var header = new HBox(titleLabel);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(12, 16, 8, 16));
    header.getStyleClass().add("plugin-header");

    // === Installed tab ===
    installedList = new VBox(6);
    installedList.setPadding(new Insets(8));
    installedList.getStyleClass().add("plugin-list");

    var installedScroll = new ScrollPane(installedList);
    installedScroll.setFitToWidth(true);
    installedScroll.getStyleClass().add("plugin-scroll");

    var installedPane = new VBox(installedScroll);
    VBox.setVgrow(installedScroll, Priority.ALWAYS);

    // === Marketplace tab ===
    var searchField = new TextField();
    searchField.setPromptText("Search plugins...");
    searchField.getStyleClass().add("plugin-search");
    searchField.textProperty().addListener((_, _, text) -> filterMarketplace(text));

    marketplaceList = new VBox(6);
    marketplaceList.setPadding(new Insets(8));
    marketplaceList.getStyleClass().add("plugin-list");

    var marketplaceScroll = new ScrollPane(marketplaceList);
    marketplaceScroll.setFitToWidth(true);
    marketplaceScroll.getStyleClass().add("plugin-scroll");

    var marketplacePane = new VBox(8, searchField, marketplaceScroll);
    marketplacePane.setPadding(new Insets(8, 8, 0, 8));
    VBox.setVgrow(marketplaceScroll, Priority.ALWAYS);

    // === TabPane ===
    var tabPane = new TabPane();
    tabPane.getStyleClass().add("chat-tab-pane");
    var tab1 = new Tab("Installed", installedPane);
    tab1.setClosable(false);
    var tab2 = new Tab("Marketplace", marketplacePane);
    tab2.setClosable(false);
    tabPane.getTabs().addAll(tab1, tab2);

    // === Footer ===
    var reloadButton = new Button("Reload");
    reloadButton.getStyleClass().add("toolbar-button");
    reloadButton.setOnAction(_ -> reloadMarketplace());
    Animations.addPressEffect(reloadButton);

    var closeButton = new Button("Close");
    closeButton.getStyleClass().add("toolbar-button");
    closeButton.setOnAction(_ -> stage.close());
    Animations.addPressEffect(closeButton);

    var footerSpacer = new Region();
    HBox.setHgrow(footerSpacer, Priority.ALWAYS);

    var footer = new HBox(8, statusLabel, spinner, footerSpacer, reloadButton, closeButton);
    footer.setAlignment(Pos.CENTER_LEFT);
    footer.setPadding(new Insets(6, 16, 6, 16));
    footer.getStyleClass().add("plugin-footer");

    // === Root ===
    var root = new BorderPane();
    root.getStyleClass().add("main-window");
    root.setTop(header);
    root.setCenter(tabPane);
    root.setBottom(footer);

    var scene = new Scene(root, 750, 520);
    var css = getClass().getResource("/com/claudecodejava/dark-theme.css");
    if (css != null) scene.getStylesheets().add(css.toExternalForm());
    stage.setScene(scene);

    loadInstalled();
    loadMarketplace();
  }

  public void show() {
    stage.showAndWait();
  }

  // --- Data loading ---

  private void loadInstalled() {
    installedList.getChildren().clear();
    installedList.getChildren().add(createInfoLabel("Loading installed plugins..."));

    Thread.ofVirtual()
        .name("plugin-list")
        .start(
            () -> {
              try {
                var result = runCli("plugin", "list", "--json");
                var plugins = MAPPER.readTree(result);
                Platform.runLater(
                    () -> {
                      installedList.getChildren().clear();
                      if (!plugins.isArray() || plugins.isEmpty()) {
                        installedList.getChildren().add(createInfoLabel("No plugins installed"));
                        return;
                      }
                      for (JsonNode p : plugins) {
                        installedList.getChildren().add(createInstalledCard(p));
                      }
                    });
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      installedList.getChildren().clear();
                      var err = new Label("Failed to load: " + e.getMessage());
                      err.getStyleClass().add("error-message");
                      installedList.getChildren().add(err);
                    });
              }
            });
  }

  private void loadMarketplace() {
    marketplaceList.getChildren().clear();
    marketplaceList.getChildren().add(createInfoLabel("Loading marketplace..."));

    Thread.ofVirtual()
        .name("marketplace-load")
        .start(
            () -> {
              try {
                loadInstallCounts();
                loadInstalledIds();
                loadMarketplacePlugins();

                allMarketplacePlugins.sort(
                    (a, b) -> {
                      int countA = installCounts.getOrDefault(a.path("name").asText(""), 0);
                      int countB = installCounts.getOrDefault(b.path("name").asText(""), 0);
                      return Integer.compare(countB, countA);
                    });

                Platform.runLater(() -> renderMarketplace(""));
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      marketplaceList.getChildren().clear();
                      var err = new Label("Failed to load marketplace: " + e.getMessage());
                      err.getStyleClass().add("error-message");
                      marketplaceList.getChildren().add(err);
                    });
              }
            });
  }

  private void loadInstallCounts() throws Exception {
    var path =
        Path.of(System.getProperty("user.home"), ".claude", "plugins", "install-counts-cache.json");
    if (!Files.exists(path)) return;
    var counts = MAPPER.readTree(Files.readString(path)).path("counts");
    if (!counts.isArray()) return;
    for (JsonNode c : counts) {
      String plugin = c.path("plugin").asText("");
      String name = plugin.contains("@") ? plugin.split("@")[0] : plugin;
      installCounts.put(name, c.path("unique_installs").asInt(0));
    }
  }

  private void loadInstalledIds() throws Exception {
    var arr = MAPPER.readTree(runCli("plugin", "list", "--json"));
    installedIds.clear();
    if (!arr.isArray()) return;
    for (JsonNode p : arr) {
      String id = p.path("id").asText("");
      installedIds.add(id.contains("@") ? id.split("@")[0] : id);
    }
  }

  private void loadMarketplacePlugins() throws Exception {
    var path =
        Path.of(
            System.getProperty("user.home"),
            ".claude",
            "plugins",
            "marketplaces",
            "claude-plugins-official",
            ".claude-plugin",
            "marketplace.json");
    allMarketplacePlugins.clear();
    if (!Files.exists(path)) return;
    var plugins = MAPPER.readTree(Files.readString(path)).path("plugins");
    if (!plugins.isArray()) return;
    for (JsonNode p : plugins) {
      allMarketplacePlugins.add(p);
    }
  }

  private void filterMarketplace(String query) {
    renderMarketplace(query == null ? "" : query.trim().toLowerCase());
  }

  private void renderMarketplace(String query) {
    marketplaceList.getChildren().clear();
    int shown = 0;
    for (JsonNode p : allMarketplacePlugins) {
      String name = p.path("name").asText("");
      String desc = p.path("description").asText("");
      if (!query.isEmpty()
          && !name.toLowerCase().contains(query)
          && !desc.toLowerCase().contains(query)) {
        continue;
      }
      marketplaceList.getChildren().add(createMarketplaceCard(p));
      if (++shown >= 100) break;
    }
    if (shown == 0) {
      marketplaceList.getChildren().add(createInfoLabel("No plugins found"));
    }
  }

  // --- Card builders ---

  private VBox createInstalledCard(JsonNode plugin) {
    String id = plugin.path("id").asText("unknown");
    String name = id.contains("@") ? id.split("@")[0] : id;
    String source = id.contains("@") ? id.substring(id.indexOf("@")) : "";
    boolean enabled = plugin.path("enabled").asBoolean(true);
    String scope = plugin.path("scope").asText("user");

    var nameLabel = new Label(name);
    nameLabel.getStyleClass().add("plugin-name");

    var sourceLabel = new Label(source + "  ·  " + scope);
    sourceLabel.getStyleClass().add("plugin-meta");

    var statusDot = new Label(enabled ? "● Enabled" : "○ Disabled");
    statusDot.getStyleClass().add(enabled ? "plugin-status-enabled" : "plugin-status-disabled");

    var toggleBtn = new Button(enabled ? "Disable" : "Enable");
    toggleBtn.getStyleClass().add("toolbar-button");
    toggleBtn.setOnAction(_ -> runPluginCommand(enabled ? "disable" : "enable", id, toggleBtn));

    var updateBtn = new Button("Update");
    updateBtn.getStyleClass().add("toolbar-button");
    updateBtn.setOnAction(_ -> runPluginCommand("update", id, updateBtn));

    var uninstallBtn = new Button("Uninstall");
    uninstallBtn.getStyleClass().addAll("toolbar-button", "text-danger");
    uninstallBtn.setOnAction(_ -> runPluginCommand("uninstall", id, uninstallBtn));

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    var topRow = new HBox(8, nameLabel, statusDot, spacer, toggleBtn, updateBtn, uninstallBtn);
    topRow.setAlignment(Pos.CENTER_LEFT);

    var card = new VBox(4, topRow, sourceLabel);
    card.getStyleClass().add("plugin-card");
    return card;
  }

  private VBox createMarketplaceCard(JsonNode plugin) {
    String name = plugin.path("name").asText("");
    String desc = plugin.path("description").asText("");
    String category = plugin.path("category").asText("");
    int installs = installCounts.getOrDefault(name, 0);
    boolean alreadyInstalled = installedIds.contains(name);

    var nameLabel = new Label(name);
    nameLabel.getStyleClass().add("plugin-name");

    var categoryLabel = new Label(category);
    categoryLabel.getStyleClass().add("plugin-category");
    categoryLabel.setVisible(!category.isEmpty());
    categoryLabel.setManaged(!category.isEmpty());

    var installsLabel = new Label(installs > 0 ? formatInstalls(installs) + " installs" : "");
    installsLabel.getStyleClass().add("plugin-meta");

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    var installBtn = new Button(alreadyInstalled ? "Installed" : "Install");
    if (alreadyInstalled) {
      installBtn.getStyleClass().add("toolbar-button");
    } else {
      installBtn.getStyleClass().add("plugin-install-button");
    }
    installBtn.setDisable(alreadyInstalled);
    installBtn.setOnAction(_ -> runPluginCommand("install", name, installBtn));

    var topRow = new HBox(8, nameLabel, categoryLabel, installsLabel, spacer, installBtn);
    topRow.setAlignment(Pos.CENTER_LEFT);

    var descLabel = new Label(desc);
    descLabel.getStyleClass().add("plugin-description");
    descLabel.setWrapText(true);

    var card = new VBox(4, topRow, descLabel);
    card.getStyleClass().add("plugin-card");
    return card;
  }

  private static Label createInfoLabel(String text) {
    var label = new Label(text);
    label.getStyleClass().add("plugin-description");
    return label;
  }

  // --- CLI helpers ---

  private void runPluginCommand(String action, String pluginName, Button trigger) {
    trigger.setDisable(true);
    setStatus("claude plugin " + action + " " + pluginName + "...");
    spinner.setVisible(true);

    Thread.ofVirtual()
        .name("plugin-" + action)
        .start(
            () -> {
              try {
                runCli("plugin", action, pluginName);
                Platform.runLater(
                    () -> {
                      setStatus(action + " " + pluginName + " — done");
                      spinner.setVisible(false);
                      loadInstalled();
                      loadMarketplace();
                    });
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      setStatus("Error: " + e.getMessage());
                      spinner.setVisible(false);
                      trigger.setDisable(false);
                    });
              }
            });
  }

  private void reloadMarketplace() {
    setStatus("Updating marketplace...");
    spinner.setVisible(true);

    Thread.ofVirtual()
        .name("marketplace-update")
        .start(
            () -> {
              try {
                runCli("plugin", "marketplace", "update");
                Platform.runLater(
                    () -> {
                      setStatus("Marketplace updated");
                      spinner.setVisible(false);
                      loadMarketplace();
                    });
              } catch (Exception e) {
                Platform.runLater(
                    () -> {
                      setStatus("Update failed: " + e.getMessage());
                      spinner.setVisible(false);
                    });
              }
            });
  }

  private void setStatus(String text) {
    statusLabel.setText(text);
  }

  private static String runCli(String... args) throws Exception {
    var cmd = new ArrayList<String>();
    cmd.add("claude");
    cmd.addAll(List.of(args));

    var pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    var proc = pb.start();
    try {
      var sb = new StringBuilder();
      try (var reader =
          new BufferedReader(
              new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line).append("\n");
        }
      }
      if (!proc.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        proc.destroyForcibly();
        throw new RuntimeException("Command timed out");
      }
      int exit = proc.exitValue();
      if (exit != 0) {
        String output = sb.toString().trim();
        throw new RuntimeException(output.isEmpty() ? "exit code " + exit : output);
      }
      return sb.toString();
    } finally {
      if (proc.isAlive()) proc.destroyForcibly();
    }
  }

  private static String formatInstalls(int count) {
    if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
    if (count >= 1_000) return String.format("%.1fK", count / 1_000.0);
    return String.valueOf(count);
  }
}
