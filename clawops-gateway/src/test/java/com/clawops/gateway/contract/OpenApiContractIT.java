package com.clawops.gateway.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * OpenAPI 契约守护（conventions/api.md §6）：运行时生成的契约必须与仓内 docs/openapi/openapi.json 一致，防止 AI 静默改接口。
 *
 * <p>重新生成：{@code ./mvnw -pl clawops-gateway failsafe:integration-test -Dopenapi.export=true}
 */
@Tag("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("OpenAPI 契约比对")
class OpenApiContractIT {

  private static final Path COMMITTED_CONTRACT = Path.of("..", "docs", "openapi", "openapi.json");

  @Value("${local.server.port}")
  private int port;

  private final ObjectMapper mapper = new ObjectMapper();

  private RestTestClient client() {
    return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  @DisplayName("运行时契约应与仓内 openapi.json 一致")
  void shouldMatchCommittedOpenApiContract() throws IOException {
    String actual =
        client()
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertThat(actual).isNotNull();

    if (Boolean.getBoolean("openapi.export")) {
      Files.createDirectories(COMMITTED_CONTRACT.getParent());
      Files.writeString(COMMITTED_CONTRACT, actual);
      return;
    }

    assertThat(COMMITTED_CONTRACT)
        .as("docs/openapi/openapi.json 不存在；先以 -Dopenapi.export=true 生成并显式提交")
        .exists();
    assertThat(mapper.readTree(actual))
        .isEqualTo(mapper.readTree(Files.readString(COMMITTED_CONTRACT)));
  }
}
