package com.clawops.harness.spi;

/**
 * 工具权限分级。
 *
 * <p>READ：只读查询；WRITE：可改变系统状态；DANGEROUS：高危操作（如重启、删数据），默认需要 HITL 审批。
 */
public enum Permission {
  READ,
  WRITE,
  DANGEROUS;

  /**
   * 当前权限是否足够执行目标权限的工具。
   *
   * @param required 工具要求的最小权限
   * @return true 当且仅当当前权限级别不低于 required
   */
  public boolean covers(Permission required) {
    return this.ordinal() >= required.ordinal();
  }
}
