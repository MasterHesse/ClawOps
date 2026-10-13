package com.clawops.harness.fake;

import java.util.Map;

/** 测试工具用的极简 JSON 序列化（harness 主代码零第三方依赖，测试侧不引 Jackson）。 */
final class JsonSupport {

  private JsonSupport() {}

  static String toJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
    }
    return sb.append('}').toString();
  }

  private static String value(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    if (value instanceof Map<?, ?> map) {
      return toJson(stringKeyMap(map));
    }
    return quote(value.toString());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> stringKeyMap(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  private static String quote(String text) {
    StringBuilder sb = new StringBuilder(text.length() + 2);
    sb.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.append('"').toString();
  }
}
