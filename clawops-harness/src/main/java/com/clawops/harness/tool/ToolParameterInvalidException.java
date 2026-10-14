package com.clawops.harness.tool;

/**
 * 工具参数校验失败异常。
 *
 * <p>映射到错误码 3001（TOOL_PARAM_INVALID）。
 */
public class ToolParameterInvalidException extends RuntimeException {

  private final String toolName;

  public ToolParameterInvalidException(String toolName, String message) {
    super("[" + toolName + "] " + message);
    this.toolName = toolName;
  }

  public String getToolName() {
    return toolName;
  }
}
