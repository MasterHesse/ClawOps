package com.clawops.connector.log;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.tool.Tool;
import com.clawops.harness.tool.ToolParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mock 日志连接器。
 *
 * <p>内存存储日志行，支持按级别/关键字搜索，支持注入 ERROR 用于测试。
 */
public class MockLogConnector {

  private final List<String> logs = new ArrayList<>();

  public MockLogConnector() {
    logs.add("INFO  service started");
    logs.add("INFO  connected to database");
    logs.add("WARN  slow query detected");
  }

  /** 注入一行日志（测试/演示用）。 */
  public void inject(String line) {
    logs.add(line);
  }

  @Tool(name = "log.search", description = "搜索 Mock 日志，可按级别和关键字过滤", permission = Permission.READ)
  public String search(
      @ToolParam(name = "level", description = "日志级别：INFO/WARN/ERROR/ALL", required = false)
          String level,
      @ToolParam(name = "keyword", description = "关键字", required = false) String keyword) {
    String levelFilter = level == null || level.isEmpty() ? "ALL" : level.toUpperCase(Locale.ROOT);
    String keywordFilter = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);

    List<String> matches = new ArrayList<>();
    for (String line : logs) {
      boolean levelMatch =
          "ALL".equals(levelFilter) || line.toUpperCase(Locale.ROOT).startsWith(levelFilter);
      boolean keywordMatch =
          keywordFilter.isEmpty() || line.toLowerCase(Locale.ROOT).contains(keywordFilter);
      if (levelMatch && keywordMatch) {
        matches.add(line);
      }
    }

    if (matches.isEmpty()) {
      return "未找到匹配日志";
    }
    return String.join("\n", matches);
  }
}
