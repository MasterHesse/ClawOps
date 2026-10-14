package com.clawops.harness.spi;

/**
 * 工具执行器。
 *
 * <p>所有真实工具逻辑（HTTP 调用、数据库查询等）通过实现此接口注入 ToolRegistry，harness 主代码不感知具体实现。
 */
@FunctionalInterface
public interface ToolExecutor {

  /**
   * 执行工具调用。
   *
   * @param call 模型发起的工具调用请求
   * @return 工具执行结果
   */
  ToolResult execute(ToolCall call);
}
