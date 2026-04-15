package com.claudecodejava.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Sidebar panel listing past sessions that can be resumed. */
public class SessionHistoryView extends ScrollPane {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

  public record SessionInfo(String sessionId, String timestamp, String preview) {}

  private final VBox listBox;

  public SessionHistoryView(Consumer<String> onResumeSession) {
    listBox = new VBox(2);
    listBox.setPadding(new Insets(8));
    listBox.getStyleClass().add("session-history-list");

    setContent(listBox);
    setFitToWidth(true);
    setHbarPolicy(ScrollBarPolicy.NEVER);
    setPrefWidth(280);
    getStyleClass().add("session-history");

    var title = new Label("Session History");
    title.getStyleClass().add("session-history-title");
    title.setPadding(new Insets(4, 8, 8, 8));
    listBox.getChildren().add(title);

    // Placeholder
    var placeholder = new Label("Loading...");
    placeholder.getStyleClass().add("session-history-placeholder");
    listBox.getChildren().add(placeholder);
  }

  /** Load sessions for the given working directory. */
  public void loadSessions(String workingDirectory, Consumer<String> onResumeSession) {
    listBox
        .getChildren()
        .removeIf(
            node ->
                !(node instanceof Label l && l.getStyleClass().contains("session-history-title")));

    var sessions = scanSessions(workingDirectory);
    if (sessions.isEmpty()) {
      var empty = new Label("No sessions found");
      empty.getStyleClass().add("session-history-placeholder");
      listBox.getChildren().add(empty);
      return;
    }

    for (var session : sessions) {
      var btn = new Button();
      btn.setMaxWidth(Double.MAX_VALUE);
      btn.getStyleClass().add("session-item");
      VBox.setVgrow(btn, Priority.NEVER);

      String label = session.timestamp() + "\n" + truncate(session.preview(), 60);
      btn.setText(label);
      btn.setWrapText(true);

      btn.setOnAction(e -> onResumeSession.accept(session.sessionId()));
      listBox.getChildren().add(btn);
    }
  }

  private List<SessionInfo> scanSessions(String workingDirectory) {
    var sessions = new ArrayList<SessionInfo>();

    // Convert working directory to Claude's project path format
    String dirPath = workingDirectory.replace(":", "-").replace("/", "-").replace("\\", "-");
    if (dirPath.startsWith("-")) dirPath = dirPath.substring(1);
    Path projectDir =
        Path.of(System.getProperty("user.home"), ".claude", "projects", "C-" + dirPath);

    if (!Files.isDirectory(projectDir)) {
      // Try without C- prefix
      projectDir = Path.of(System.getProperty("user.home"), ".claude", "projects", dirPath);
    }

    if (!Files.isDirectory(projectDir)) return sessions;

    try (var stream = Files.list(projectDir)) {
      var jsonlFiles =
          stream
              .filter(p -> p.toString().endsWith(".jsonl"))
              .sorted(
                  Comparator.comparingLong(
                      p -> {
                        try {
                          return -Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                          return 0L;
                        }
                      }))
              .limit(20)
              .toList();

      for (Path file : jsonlFiles) {
        var info = parseSessionFile(file);
        if (info != null) sessions.add(info);
      }
    } catch (IOException ignored) {
    }

    return sessions;
  }

  private SessionInfo parseSessionFile(Path file) {
    String filename = file.getFileName().toString();
    String sessionId = filename.replace(".jsonl", "");

    try (BufferedReader reader = Files.newBufferedReader(file)) {
      String firstLine = reader.readLine();
      if (firstLine == null) return null;

      JsonNode node = MAPPER.readTree(firstLine);
      String timestamp = node.path("timestamp").asText("");
      String content = node.path("content").asText("");

      String formattedTime = timestamp;
      try {
        var instant = Instant.parse(timestamp);
        formattedTime = DATE_FMT.format(instant);
      } catch (Exception ignored) {
      }

      if (content.isBlank()) content = "(empty)";
      return new SessionInfo(sessionId, formattedTime, content);
    } catch (IOException e) {
      return null;
    }
  }

  /** A single message from a session's conversation history. */
  public record ChatMessage(String role, String text) {}

  /** Parse full conversation from a session JSONL file. */
  public static List<ChatMessage> loadConversation(String workingDirectory, String sessionId) {
    var messages = new ArrayList<ChatMessage>();

    String dirPath = workingDirectory.replace(":", "-").replace("/", "-").replace("\\", "-");
    if (dirPath.startsWith("-")) dirPath = dirPath.substring(1);
    Path projectDir =
        Path.of(System.getProperty("user.home"), ".claude", "projects", "C-" + dirPath);

    if (!Files.isDirectory(projectDir)) {
      projectDir = Path.of(System.getProperty("user.home"), ".claude", "projects", dirPath);
    }

    Path sessionFile = projectDir.resolve(sessionId + ".jsonl");
    if (!Files.isRegularFile(sessionFile)) return messages;

    try (BufferedReader reader = Files.newBufferedReader(sessionFile)) {
      String line;
      while ((line = reader.readLine()) != null) {
        try {
          JsonNode node = MAPPER.readTree(line);
          String type = node.path("type").asText("");

          if ("user".equals(type) || "assistant".equals(type)) {
            JsonNode message = node.path("message");
            String role = message.path("role").asText("");
            JsonNode content = message.path("content");

            String text = "";
            if (content.isTextual()) {
              text = content.asText("");
            } else if (content.isArray()) {
              var sb = new StringBuilder();
              for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText(""))) {
                  sb.append(block.path("text").asText(""));
                }
              }
              text = sb.toString();
            }

            if (!text.isBlank()) {
              messages.add(new ChatMessage(role, text));
            }
          }
        } catch (Exception ignored) {
        }
      }
    } catch (IOException ignored) {
    }

    return messages;
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }
}
