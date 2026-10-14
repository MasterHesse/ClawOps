package com.clawops.harness.permission;

/**
 * 权限校验结果。
 *
 * @param allowed 是否允许
 * @param reason 拒绝原因（允许时为空）
 */
public record PermissionCheckResult(boolean allowed, String reason) {

  public static PermissionCheckResult granted() {
    return new PermissionCheckResult(true, null);
  }

  public static PermissionCheckResult denied(String reason) {
    return new PermissionCheckResult(false, reason);
  }
}
