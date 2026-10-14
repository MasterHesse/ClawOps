package com.clawops.connector.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MockLogConnector 日志搜索")
class MockLogConnectorTest {

  @Test
  @DisplayName("可按关键字搜索日志")
  void shouldSearchByKeyword() {
    MockLogConnector connector = new MockLogConnector();

    String result = connector.search("ALL", "database");

    assertThat(result).contains("connected to database");
  }

  @Test
  @DisplayName("可按级别搜索日志")
  void shouldSearchByLevel() {
    MockLogConnector connector = new MockLogConnector();

    String result = connector.search("WARN", "");

    assertThat(result).contains("slow query detected");
    assertThat(result).doesNotContain("INFO");
  }

  @Test
  @DisplayName("注入 ERROR 后可被搜索")
  void shouldInjectErrorLog() {
    MockLogConnector connector = new MockLogConnector();
    connector.inject("ERROR connection timeout");

    String result = connector.search("ERROR", "");

    assertThat(result).contains("connection timeout");
  }
}
