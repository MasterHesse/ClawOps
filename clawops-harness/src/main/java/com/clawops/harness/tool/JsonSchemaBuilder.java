package com.clawops.harness.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Java 方法生成 OpenAPI 风格 JSON Schema 子集。
 *
 * <p>支持基本类型、枚举；对象/集合类型统一按字符串处理（W1 够用）。
 */
final class JsonSchemaBuilder {

  private JsonSchemaBuilder() {}

  static Map<String, Object> build(Method method) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();

    for (Parameter parameter : method.getParameters()) {
      ToolParam annotation = parameter.getAnnotation(ToolParam.class);
      String paramName =
          annotation == null || annotation.name().isEmpty()
              ? parameter.getName()
              : annotation.name();
      String description = annotation == null ? "" : annotation.description();
      boolean isRequired = annotation == null || annotation.required();

      Map<String, Object> property = new LinkedHashMap<>();
      property.put("type", jsonTypeFor(parameter.getType()));
      if (!description.isEmpty()) {
        property.put("description", description);
      }
      if (parameter.getType().isEnum()) {
        property.put("enum", enumValues(parameter.getType()));
      }
      properties.put(paramName, property);
      if (isRequired) {
        required.add(paramName);
      }
    }

    schema.put("properties", properties);
    schema.put("required", required);
    return schema;
  }

  private static String jsonTypeFor(Class<?> type) {
    if (type == String.class) {
      return "string";
    }
    if (type == int.class
        || type == Integer.class
        || type == long.class
        || type == Long.class
        || type == short.class
        || type == Short.class) {
      return "integer";
    }
    if (type == double.class
        || type == Double.class
        || type == float.class
        || type == Float.class) {
      return "number";
    }
    if (type == boolean.class || type == Boolean.class) {
      return "boolean";
    }
    if (type.isEnum()) {
      return "string";
    }
    return "string";
  }

  private static List<String> enumValues(Class<?> enumType) {
    List<String> values = new ArrayList<>();
    for (Object constant : enumType.getEnumConstants()) {
      values.add(constant.toString());
    }
    return values;
  }
}
