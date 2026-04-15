package com.claudecodejava.cli;

/** Tracks the current session ID for multi-turn conversations. */
public class SessionManager {

  private String currentSessionId;
  private String workingDirectory;

  public SessionManager() {
    this.workingDirectory = System.getProperty("user.dir");
  }

  public String getCurrentSessionId() {
    return currentSessionId;
  }

  public void setCurrentSessionId(String sessionId) {
    this.currentSessionId = sessionId;
  }

  public String getWorkingDirectory() {
    return workingDirectory;
  }

  public void setWorkingDirectory(String workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  public void newSession() {
    this.currentSessionId = null;
  }
}
