package com.clawops.harness.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolDefinition;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PermissionManager 权限分级校验")
class PermissionManagerTest {

  private final ToolDefinition readTool =
      new ToolDefinition("read", "", Permission.READ, Map.of(), call -> null, null);
  private final ToolDefinition writeTool =
      new ToolDefinition("write", "", Permission.WRITE, Map.of(), call -> null, null);
  private final ToolDefinition dangerousTool =
      new ToolDefinition("danger", "", Permission.DANGEROUS, Map.of(), call -> null, null);

  @Test
  @DisplayName("默认 WRITE 权限允许 READ/WRITE，拒绝 DANGEROUS")
  void shouldDenyDangerousByDefault() {
    PermissionManager manager = new PermissionManager();

    assertThat(manager.check(readTool).allowed()).isTrue();
    assertThat(manager.check(writeTool).allowed()).isTrue();
    assertThat(manager.check(dangerousTool).allowed()).isFalse();
  }

  @Test
  @DisplayName("READ 权限只允许 READ")
  void shouldRestrictToReadOnly() {
    PermissionManager manager = new PermissionManager(Permission.READ);

    assertThat(manager.check(readTool).allowed()).isTrue();
    assertThat(manager.check(writeTool).allowed()).isFalse();
    assertThat(manager.check(dangerousTool).allowed()).isFalse();
  }

  @Test
  @DisplayName("提权后可执行 DANGEROUS 工具")
  void shouldAllowDangerousAfterElevation() {
    PermissionManager manager = new PermissionManager();

    manager.elevate(Permission.DANGEROUS);

    assertThat(manager.current()).isEqualTo(Permission.DANGEROUS);
    assertThat(manager.check(dangerousTool).allowed()).isTrue();
  }

  @Test
  @DisplayName("提权参数不能为 null")
  void shouldRejectNullElevation() {
    PermissionManager manager = new PermissionManager();

    assertThatThrownBy(() -> manager.elevate(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
