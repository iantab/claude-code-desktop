package com.claudecodejava.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Parses a single JSON line from claude --output-format stream-json into a StreamEvent. */
public class EventParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Internal tools that should not be shown in the chat. */
  private static final Set<String> HIDDEN_TOOLS =
      Set.of("ToolSearch", "AskUserQuestion", "ExitPlanMode", "TodoWrite");

  public static StreamEvent parse(String jsonLine) {
    try {
      JsonNode root = MAPPER.readTree(jsonLine);
      String type = root.path("type").asText("");

      return switch (type) {
        case "system" -> parseSystem(root);
        case "rate_limit_event" -> parseRateLimit(root);
        case "assistant" -> parseAssistant(root);
        case "result" -> parseResult(root);
        default -> new StreamEvent.Unknown(type, root);
      };
    } catch (Exception e) {
      return new StreamEvent.Error("Failed to parse: " + e.getMessage());
    }
  }

  private static StreamEvent parseSystem(JsonNode root) {
    String subtype = root.path("subtype").asText("");
    if ("init".equals(subtype)) {
      var mcpServers = new ArrayList<StreamEvent.McpServer>();
      JsonNode servers = root.path("mcp_servers");
      if (servers.isArray()) {
        for (JsonNode s : servers) {
          mcpServers.add(
              new StreamEvent.McpServer(s.path("name").asText(""), s.path("status").asText("")));
        }
      }
      return new StreamEvent.Init(
          root.path("session_id").asText(""),
          root.path("model").asText(""),
          List.copyOf(mcpServers));
    }
    return new StreamEvent.Unknown("system:" + subtype, root);
  }

  private static StreamEvent parseRateLimit(JsonNode root) {
    JsonNode info = root.path("rate_limit_info");
    return new StreamEvent.RateLimit(
        info.path("status").asText(""),
        info.path("rateLimitType").asText(""),
        info.path("resetsAt").asLong(0));
  }

  private static StreamEvent parseAssistant(JsonNode root) {
    JsonNode message = root.path("message");
    JsonNode content = message.path("content");

    var sb = new StringBuilder();
    var toolNames = new ArrayList<String>();
    StreamEvent.QuestionData question = null;
    boolean exitPlanMode = false;

    if (content.isArray()) {
      for (JsonNode block : content) {
        String blockType = block.path("type").asText("");
        if ("text".equals(blockType)) {
          sb.append(block.path("text").asText(""));
        } else if ("tool_use".equals(blockType)) {
          String toolName = block.path("name").asText("unknown");
          JsonNode input = block.path("input");

          if ("AskUserQuestion".equals(toolName)) {
            question = parseQuestion(input);
          } else if ("ExitPlanMode".equals(toolName)) {
            exitPlanMode = true;
          }

          // Only show non-internal tools in the chat
          if (!HIDDEN_TOOLS.contains(toolName)) {
            toolNames.add(summarizeTool(toolName, input));
          }
        }
      }
    }

    return new StreamEvent.AssistantMessage(
        sb.toString(), List.copyOf(toolNames), question, exitPlanMode, message);
  }

  private static StreamEvent.QuestionData parseQuestion(JsonNode input) {
    JsonNode questions = input.path("questions");
    if (!questions.isArray() || questions.isEmpty()) {
      // Fallback: try direct question field
      String q = input.path("question").asText(null);
      return q != null ? new StreamEvent.QuestionData(q, List.of()) : null;
    }

    JsonNode first = questions.get(0);
    String questionText = first.path("question").asText("Question from Claude");
    var options = new ArrayList<StreamEvent.OptionData>();

    JsonNode opts = first.path("options");
    if (opts.isArray()) {
      for (JsonNode opt : opts) {
        options.add(
            new StreamEvent.OptionData(
                opt.path("label").asText(""), opt.path("description").asText("")));
      }
    }

    return new StreamEvent.QuestionData(questionText, List.copyOf(options));
  }

  private static String summarizeTool(String toolName, JsonNode input) {
    String detail =
        switch (toolName) {
          case "Read" -> input.path("file_path").asText(null);
          case "Write" -> input.path("file_path").asText(null);
          case "Edit" -> input.path("file_path").asText(null);
          case "Bash" -> truncate(input.path("command").asText(null), 80);
          case "Glob" -> input.path("pattern").asText(null);
          case "Grep" -> input.path("pattern").asText(null);
          case "Agent" -> truncate(input.path("description").asText(null), 60);
          case "WebSearch" -> truncate(input.path("query").asText(null), 80);
          case "WebFetch" -> truncate(input.path("url").asText(null), 80);
          default -> null;
        };
    return detail != null ? toolName + ": " + detail : toolName;
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }

  private static StreamEvent parseResult(JsonNode root) {
    JsonNode usage = root.path("usage");
    return new StreamEvent.Result(
        root.path("result").asText(""),
        root.path("session_id").asText(""),
        root.path("duration_ms").asInt(0),
        root.path("total_cost_usd").asDouble(0.0),
        root.path("stop_reason").asText(""),
        usage.path("input_tokens").asInt(0),
        usage.path("output_tokens").asInt(0),
        usage.path("cache_read_input_tokens").asInt(0),
        usage.path("cache_creation_input_tokens").asInt(0));
  }
}
