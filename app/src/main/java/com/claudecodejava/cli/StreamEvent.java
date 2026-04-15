package com.claudecodejava.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Represents a parsed event from claude --output-format stream-json. */
public sealed interface StreamEvent {

  record OptionData(String label, String description) {}

  record QuestionData(
      String question, List<OptionData> options, boolean multiSelect, JsonNode rawQuestions) {}

  record McpServer(String name, String status) {}

  record Init(String sessionId, String model, List<McpServer> mcpServers) implements StreamEvent {}

  record RateLimit(String status, String rateLimitType, long resetsAt, double utilization)
      implements StreamEvent {}

  record AssistantMessage(
      String text,
      List<String> toolNames,
      QuestionData question,
      boolean exitPlanMode,
      JsonNode fullMessage)
      implements StreamEvent {}

  record ToolUse(String toolName, String toolId, JsonNode input) implements StreamEvent {}

  record ToolResult(String toolName, String output) implements StreamEvent {}

  record Result(
      String text,
      String sessionId,
      int durationMs,
      double costUsd,
      String stopReason,
      int inputTokens,
      int outputTokens,
      int cacheReadTokens,
      int cacheCreateTokens)
      implements StreamEvent {}

  record Error(String message) implements StreamEvent {}

  record Unknown(String type, JsonNode raw) implements StreamEvent {}
}
