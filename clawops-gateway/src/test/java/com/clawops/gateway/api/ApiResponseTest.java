package com.clawops.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("统一响应体")
class ApiResponseTest {

  @Test
  @DisplayName("成功响应 code=0 且 traceId 为 16 位")
  void shouldBuildOkResponse() {
    ApiResponse<String> response = ApiResponse.ok("payload");

    assertThat(response.code()).isZero();
    assertThat(response.message()).isEqualTo("ok");
    assertThat(response.data()).isEqualTo("payload");
    assertThat(response.traceId()).hasSize(16).matches("[0-9a-f]+");
  }

  @Test
  @DisplayName("错误响应携带错误码与默认文案，data 为空")
  void shouldBuildErrorResponse() {
    ApiResponse<Void> response = ApiResponse.error(ApiErrorCode.TOOL_TIMEOUT, "trace-001");

    assertThat(response.code()).isEqualTo(3002);
    assertThat(response.message()).isEqualTo("工具执行超时");
    assertThat(response.data()).isNull();
    assertThat(response.traceId()).isEqualTo("trace-001");
  }
}
