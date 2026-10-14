package com.clawops.harness.permission;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolDefinition;

/**
 * 工具权限管理器。
 *
 * <p>每个会话持有一个实例，默认最大权限为 WRITE；DANGEROUS 工具默认被拒绝，需经 HITL 审批后临时提权。
 */
public class PermissionManager {

  private Permission maxPermission;

  public PermissionManager() {
    this(Permission.WRITE);
  }

  public PermissionManager(Permission maxPermission) {
    this.maxPermission = maxPermission;
  }

  /** 校验工具是否可在当前权限下执行。 */
  public PermissionCheckResult check(ToolDefinition tool) {
    if (tool == null) {
      return PermissionCheckResult.denied("工具不存在");
    }
    if (maxPermission.covers(tool.permission())) {
      return PermissionCheckResult.granted();
    }
    return PermissionCheckResult.denied(
        "权限不足：需要 " + tool.permission() + "，当前最大权限为 " + maxPermission);
  }

  /** 临时提升会话权限（审批通过后调用）。 */
  public void elevate(Permission permission) {
    if (permission == null) {
      throw new IllegalArgumentException("permission must not be null");
    }
    if (permission.ordinal() < this.maxPermission.ordinal()) {
      return;
    }
    this.maxPermission = permission;
  }

  public Permission current() {
    return maxPermission;
  }
}
