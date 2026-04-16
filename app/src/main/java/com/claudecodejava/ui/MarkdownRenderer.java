package com.claudecodejava.ui;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.util.List;

/** Converts markdown text to styled HTML for display in a WebView. */
public class MarkdownRenderer {

  private final Parser parser;
  private final HtmlRenderer renderer;

  public MarkdownRenderer() {
    var options = new MutableDataSet();
    options.set(
        Parser.EXTENSIONS, List.of(TablesExtension.create(), StrikethroughExtension.create()));

    parser = Parser.builder(options).build();
    renderer = HtmlRenderer.builder(options).build();
  }

  public String renderToHtml(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return wrapInTemplate("");
    }
    var document = parser.parse(markdown);
    var htmlBody = renderer.render(document);
    return wrapInTemplate(htmlBody);
  }

  private String wrapInTemplate(String bodyHtml) {
    return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: 'Cascadia Mono', 'Cascadia Code', 'JetBrains Mono', 'Fira Code', Consolas, monospace;
                    font-size: 13px;
                    line-height: 1.6;
                    color: #cdd6f4;
                    background: transparent;
                    padding: 8px 0;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                }
                p { margin-bottom: 8px; }
                p:last-child { margin-bottom: 0; }
                code {
                    font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace;
                    font-size: 13px;
                    background: #313244;
                    padding: 2px 6px;
                    border-radius: 4px;
                    color: #fab387;
                }
                pre {
                    background: #181825;
                    border: 1px solid #45475a;
                    border-left: 3px solid #89b4fa;
                    border-radius: 8px;
                    padding: 12px 16px;
                    margin: 8px 0;
                    overflow-x: auto;
                }
                pre code {
                    background: transparent;
                    padding: 0;
                    color: #cdd6f4;
                    font-size: 13px;
                }
                h1, h2, h3, h4 {
                    color: #cdd6f4;
                    margin: 12px 0 6px 0;
                }
                h1 { font-size: 1.4em; }
                h2 { font-size: 1.2em; }
                h3 { font-size: 1.1em; }
                a { color: #89b4fa; text-decoration: none; }
                a:hover { text-decoration: underline; color: #b4d0fb; }
                ul, ol { margin: 4px 0 8px 24px; }
                li { margin-bottom: 2px; }
                blockquote {
                    border-left: 3px solid #89b4fa;
                    padding-left: 12px;
                    margin: 8px 0;
                    color: #a6adc8;
                }
                table {
                    border-collapse: collapse;
                    margin: 8px 0;
                    width: 100%%;
                }
                th, td {
                    border: 1px solid #45475a;
                    padding: 6px 12px;
                    text-align: left;
                }
                th { background: #313244; color: #cdd6f4; }
                strong { color: #cdd6f4; }
                hr { border: none; border-top: 1px solid #45475a; margin: 12px 0; }

                /* Simple syntax highlighting */
                .keyword { color: #89b4fa; }
                .string { color: #fab387; }
                .comment { color: #a6e3a1; }
                .number { color: #fab387; }
                </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """
        .formatted(bodyHtml);
  }
}
