package com.clawops.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 错误码双源一致性校验：ApiErrorCode 枚举（代码侧）与 error-codes.yaml（机器可读契约）必须一一对应， 防止只改一处导致 Agent 降级判断 / 前端 / CLI
 * 三方不一致（conventions/api.md §3）。
 */
@DisplayName("错误码契约（枚举 ↔ error-codes.yaml）")
class ApiErrorCodeContractTest {

  @Test
  @DisplayName("枚举与 yaml 的 name/code 集合完全一致")
  @SuppressWarnings("unchecked")
  void shouldKeepEnumAndYamlInSync() throws IOException {
    Set<String> yamlEntries = new HashSet<>();
    Set<String> httpByCode = new HashSet<>();
    try (InputStream in = ApiErrorCodeContractTest.class.getResourceAsStream("/error-codes.yaml")) {
      assertThat(in).as("error-codes.yaml 应在 classpath 上").isNotNull();
      Map<String, Object> root = new Yaml().load(in);
      List<Map<String, Object>> codes = (List<Map<String, Object>>) root.get("codes");
      assertThat(codes).isNotEmpty();
      for (Map<String, Object> entry : codes) {
        yamlEntries.add(entry.get("name") + ":" + entry.get("code"));
        httpByCode.add(String.valueOf(entry.get("http")));
        assertThat(entry).containsKeys("code", "name", "http", "retryable", "degradeAction");
      }
    }

    Set<String> enumEntries = new HashSet<>();
    for (ApiErrorCode error : ApiErrorCode.values()) {
      enumEntries.add(error.name() + ":" + error.code());
    }

    assertThat(enumEntries).isEqualTo(yamlEntries);
    assertThat(httpByCode).doesNotContain("null");
  }

  @Test
  @DisplayName("fromCode 能反查全部已注册错误码")
  void shouldResolveEveryCode() {
    for (ApiErrorCode error : ApiErrorCode.values()) {
      assertThat(ApiErrorCode.fromCode(error.code())).isEqualTo(error);
    }
  }
}
