package com.clawops.harness.tool;

import java.util.List;
import java.util.Map;

/** 根据 JSON Schema 校验工具入参。 */
final class ParameterValidator {

  private ParameterValidator() {}

  @SuppressWarnings("unchecked")
  static void validate(String toolName, Map<String, Object> schema, Map<String, Object> args) {
    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
    List<String> required = (List<String>) schema.get("required");
    Map<String, Object> actual = args == null ? Map.of() : args;

    if (required != null) {
      for (String key : required) {
        if (!actual.containsKey(key) || actual.get(key) == null) {
          throw new ToolParameterInvalidException(toolName, "缺少必填参数: " + key);
        }
      }
    }

    if (properties != null) {
      for (Map.Entry<String, Object> entry : actual.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        Map<String, Object> property = (Map<String, Object>) properties.get(key);
        if (property == null) {
          throw new ToolParameterInvalidException(toolName, "未知参数: " + key);
        }
        String expectedType = (String) property.get("type");
        if (!isTypeMatch(expectedType, value)) {
          throw new ToolParameterInvalidException(
              toolName,
              "参数 " + key + " 类型错误，期望 " + expectedType + "，实际 " + value.getClass().getSimpleName());
        }
      }
    }
  }

  private static boolean isTypeMatch(String expectedType, Object value) {
    if (expectedType == null) {
      return true;
    }
    return switch (expectedType) {
      case "string" -> value instanceof String;
      case "integer" -> value instanceof Integer || value instanceof Long || value instanceof Short;
      case "number" -> value instanceof Number;
      case "boolean" -> value instanceof Boolean;
      default -> true;
    };
  }
}
