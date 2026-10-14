package com.clawops.connector.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MetricsConnector Prometheus 查询")
class MetricsConnectorTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/query",
        (HttpExchange exchange) -> {
          String query = exchange.getRequestURI().getQuery();
          String response =
              "{\"status\":\"success\","
                  + "\"data\":{\"resultType\":\"vector\","
                  + "\"result\":[{\"metric\":{\"__name__\":\"up\"},\"value\":[1,\"1\"]}]}}";
          byte[] bytes = response.getBytes();
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  @DisplayName("可查询 Prometheus 并返回结果摘要")
  void shouldQueryPrometheus() throws Exception {
    MetricsConnector connector = new MetricsConnector(baseUrl);

    String result = connector.query("up");

    assertThat(result).contains("__name__").contains("up");
  }

  @Test
  @DisplayName("空 PromQL 返回错误说明")
  void shouldRejectEmptyPromql() throws Exception {
    MetricsConnector connector = new MetricsConnector(baseUrl);

    String result = connector.query("");

    assertThat(result).contains("error");
  }
}
