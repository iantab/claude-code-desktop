# Claude Code Desktop

A fast, lightweight desktop GUI for [Claude Code](https://docs.anthropic.com/en/docs/claude-code) built with Java and JavaFX. A native Claude Code desktop app and alternative client that wraps the Claude Code CLI with a clean, responsive interface.

## Why?

Anthropic's official Claude Code desktop app is slow and laggy. It consumes excessive memory and often stutters during long coding sessions. This project provides a **fast, native Claude Code desktop alternative** that stays responsive even during heavy tool use.

Instead of reimplementing the Claude Code protocol, this app spawns the `claude` CLI as a subprocess and parses its streaming JSON output. This means:

- **No API key needed** -- uses your existing Claude Max / Pro subscription
- **All Claude Code features work** -- tools, agents, plan mode, sessions, MCP servers
- **Lightweight** -- native Java desktop app, no Electron, no Chrome, minimal memory footprint

## Features

- **No API key needed** -- uses your existing Claude Max / Pro subscription
- **Streaming markdown** -- tables, bold, code blocks, and links rendered properly
- **Tool visibility** -- see every Read, Bash, WebSearch, Agent call with details
- **Model & mode selector** -- switch models (Opus/Sonnet/Haiku) and permission modes (plan, acceptEdits, etc.)
- **Interactive plan mode** -- approve or request changes to plans with clickable buttons
- **Usage stats** -- tokens, cost, and rate limit countdown in the status bar
- **Dark theme** -- Catppuccin-inspired UI with Cascadia Mono font

## Prerequisites

- **Java 21+** (tested with Java 25)
- **Claude Code CLI** installed and authenticated (`npm install -g @anthropic-ai/claude-code && claude auth login`)

## Quick Start

```bash
git clone https://github.com/your-username/claude-code-java.git
cd claude-code-java
./gradlew run
```

## Build

```bash
# Build
./gradlew build

# Run
./gradlew run

# Format code
./gradlew spotlessApply
```

## How It Works

The app is a thin GUI shell around the Claude Code CLI. When you send a message:

1. Spawns `claude -p "your message" --output-format stream-json --verbose`
2. Parses JSON events line-by-line from stdout
3. Renders text, tool calls, questions, and results in the chat UI
4. Captures `session_id` for multi-turn conversations via `--resume`

This architecture means every Claude Code feature works out of the box -- tools, MCP servers, CLAUDE.md files, hooks, skills, and more.

## Tech Stack

- **Java 25** with virtual threads
- **JavaFX 25** for the desktop UI
- **flexmark-java** for markdown-to-HTML rendering
- **Jackson** for JSON parsing
- **Spotless** with google-java-format for code formatting
- **Gradle** (Kotlin DSL) build system

## Project Structure

```
app/src/main/java/com/claudecodejava/
  App.java                    # JavaFX application entry point
  cli/
    ClaudeProcess.java        # Spawns claude CLI subprocess
    EventParser.java          # Parses stream-json events
    SessionManager.java       # Tracks session IDs for --resume
    StreamEvent.java          # Typed event records (sealed interface)
  ui/
    ChatView.java             # Scrollable message list
    InputArea.java            # Text input with Send/Cancel/New Chat
    MainWindow.java           # Main layout wiring events to UI
    MarkdownRenderer.java     # flexmark markdown-to-HTML
    MessageCell.java          # Single message (Label + WebView on finalize)
    QuestionView.java         # Clickable question/option buttons
    StatusBar.java            # Model, tokens, cost, rate limit display
    ToolBar.java              # Directory, model, and mode selectors
```

## Keywords

claude code desktop, claude code desktop app, claude code gui, claude code java, claude code client, claude desktop alternative, anthropic claude code desktop, claude code native app, fast claude code, lightweight claude code