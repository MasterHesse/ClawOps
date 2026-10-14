package com.clawops.connector.metrics;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.tool.Tool;
import com.clawops.harness.tool.ToolParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Prometheus 指标连接器。
 *
 * <p>真实实现：调用 Prometheus HTTP API /api/v1/query 并返回结果 JSON 摘要。
 */
public class MetricsConnector {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String baseUrl;
  private final HttpClient httpClient;

  public MetricsConnector(String baseUrl) {
    this.baseUrl = baseUrl == null || baseUrl.isEmpty() ? "http://localhost:9090" : baseUrl;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public MetricsConnector() {
    this(null);
  }

  @Tool(
      name = "metrics.query",
      description = "使用 PromQL 查询 Prometheus 指标，返回结果 JSON",
      permission = Permission.READ,
      timeoutMillis = 10000)
  public String query(@ToolParam(name = "promql", description = "PromQL 表达式") String promql)
      throws IOException, InterruptedException {
    if (promql == null || promql.isEmpty()) {
      return "{\"error\":\"promql cannot be empty\"}";
    }
    String encoded = URLEncoder.encode(promql, StandardCharsets.UTF_8);
    String url = baseUrl + "/api/v1/query?query=" + encoded;

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    if (response.statusCode() != 200) {
      return "{\"error\":\"HTTP " + response.statusCode() + "\"}";
    }
    return compact(response.body());
  }

  private String compact(String json) throws IOException {
    JsonNode node = MAPPER.readTree(json);
    JsonNode result = node.path("data").path("result");
    if (result.isMissingNode() || result.isEmpty()) {
      return "{\"status\":\"success\",\"result\":[]}";
    }
    return MAPPER.writeValueAsString(result);
  }
}
