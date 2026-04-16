package com.claudecodejava.ui;

/** Shared text utilities. */
final class TextUtils {

  private TextUtils() {}

  static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }
}
