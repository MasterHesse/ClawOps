package com.clawops.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolResult;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ToolRegistry 注解注册与参数校验")
class ToolRegistryTest {

  static class SampleTools {

    @Tool(name = "greet", description = "问候", permission = Permission.READ)
    public String greet(@ToolParam(description = "名字") String name) {
      return "Hello " + name;
    }

    @Tool(description = "加法", permission = Permission.READ)
    public int add(@ToolParam(description = "a") int a, @ToolParam(description = "b") int b) {
      return a + b;
    }

    @Tool(name = "danger", description = "高危", permission = Permission.DANGEROUS)
    public ToolResult danger() {
      return ToolResult.success("done");
    }
  }

  @Test
  @DisplayName("扫描注解后可通过工具名查找")
  void shouldRegisterAnnotatedMethods() {
    ToolRegistry registry = new ToolRegistry().register(new SampleTools());

    assertThat(registry.all()).hasSize(3);
    assertThat(registry.get("greet")).isPresent();
    assertThat(registry.get("add")).isPresent();
    assertThat(registry.get("danger")).isPresent();
  }

  @Test
  @DisplayName("生成的 ToolDefinition 包含正确的权限与 Schema")
  void shouldBuildCorrectDefinition() {
    ToolRegistry registry = new ToolRegistry().register(new SampleTools());

    ToolDefinition greet = registry.get("greet").orElseThrow();
    assertThat(greet.permission()).isEqualTo(Permission.READ);
    assertThat(greet.description()).isEqualTo("问候");
    @SuppressWarnings("unchecked")
    Map<String, Object> properties =
        (Map<String, Object>) greet.parameterSchema().get("properties");
    assertThat(properties).containsKey("name");
    assertThat(properties.get("name")).isInstanceOf(Map.class);

    ToolDefinition danger = registry.get("danger").orElseThrow();
    assertThat(danger.permission()).isEqualTo(Permission.DANGEROUS);
  }

  @Test
  @DisplayName("执行工具可返回正确结果")
  void shouldExecuteRegisteredTool() {
    ToolRegistry registry = new ToolRegistry().register(new SampleTools());

    ToolDefinition greet = registry.get("greet").orElseThrow();
    ToolResult result =
        greet.executor().execute(new ToolCall("c1", "greet", Map.of("name", "Claw")));

    assertThat(result.status()).isEqualTo(ToolResult.STATUS_SUCCESS);
    assertThat(result.content()).isEqualTo("Hello Claw");
  }

  @Test
  @DisplayName("参数类型错误时校验失败")
  void shouldRejectInvalidParameterType() {
    ToolRegistry registry = new ToolRegistry().register(new SampleTools());

    ToolDefinition add = registry.get("add").orElseThrow();
    ToolCall call = new ToolCall("c2", "add", Map.of("a", "not-a-number", "b", 2));

    assertThatThrownBy(() -> add.executor().execute(call))
        .isInstanceOf(ToolParameterInvalidException.class)
        .hasMessageContaining("类型错误");
  }

  @Test
  @DisplayName("缺少必填参数时校验失败")
  void shouldRejectMissingRequiredParameter() {
    ToolRegistry registry = new ToolRegistry().register(new SampleTools());

    ToolDefinition greet = registry.get("greet").orElseThrow();
    ToolCall call = new ToolCall("c3", "greet", Map.of());

    assertThatThrownBy(() -> greet.executor().execute(call))
        .isInstanceOf(ToolParameterInvalidException.class)
        .hasMessageContaining("缺少必填参数");
  }

  @Test
  @DisplayName("手动注册 ToolDefinition 不冲突")
  void shouldAllowManualRegistration() {
    ToolRegistry registry = new ToolRegistry();
    ToolDefinition manual =
        new ToolDefinition(
            "manual", "手动", Permission.READ, Map.of(), call -> ToolResult.success("ok"), null);

    registry.register(manual);

    Optional<ToolDefinition> found = registry.get("manual");
    assertThat(found).isPresent();
    assertThat(found.get().executor().execute(new ToolCall("c", "manual", Map.of())).content())
        .isEqualTo("ok");
  }
}
