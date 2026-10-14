package com.clawops.harness.tool;

import com.clawops.harness.spi.ToolDefinition;
import com.clawops.harness.spi.ToolExecutor;
import com.clawops.harness.spi.ToolResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 工具注册表。
 *
 * <p>支持通过注解扫描 POJO 注册工具，或手动注册 {@link ToolDefinition}。
 */
public class ToolRegistry {

  private final Map<String, ToolDefinition> definitions = new LinkedHashMap<>();

  /** 扫描 provider 中所有带 {@link Tool} 注解的 public 方法并注册。 */
  public ToolRegistry register(Object provider) {
    Objects.requireNonNull(provider, "provider must not be null");
    for (Method method : provider.getClass().getMethods()) {
      Tool annotation = method.getAnnotation(Tool.class);
      if (annotation == null) {
        continue;
      }
      method.setAccessible(true);
      String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
      if (definitions.containsKey(name)) {
        throw new IllegalArgumentException("工具名冲突: " + name);
      }
      Map<String, Object> schema = JsonSchemaBuilder.build(method);
      ToolExecutor executor = createExecutor(provider, method, name, schema);
      ToolDefinition definition =
          new ToolDefinition(
              name,
              annotation.description(),
              annotation.permission(),
              schema,
              executor,
              Duration.ofMillis(annotation.timeoutMillis()));
      definitions.put(name, definition);
    }
    return this;
  }

  /** 直接注册一个工具定义。 */
  public ToolRegistry register(ToolDefinition definition) {
    Objects.requireNonNull(definition, "definition must not be null");
    String name = definition.name();
    if (definitions.containsKey(name)) {
      throw new IllegalArgumentException("工具名冲突: " + name);
    }
    definitions.put(name, definition);
    return this;
  }

  public Optional<ToolDefinition> get(String name) {
    return Optional.ofNullable(definitions.get(name));
  }

  public Collection<ToolDefinition> all() {
    return definitions.values();
  }

  public boolean isEmpty() {
    return definitions.isEmpty();
  }

  private ToolExecutor createExecutor(
      Object provider, Method method, String toolName, Map<String, Object> schema) {
    return call -> {
      Map<String, Object> args = call.arguments();
      ParameterValidator.validate(toolName, schema, args);
      Object[] params = mapArguments(method, args);
      try {
        Object result = method.invoke(provider, params);
        return wrapResult(result);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return ToolResult.error(cause.getMessage());
      } catch (IllegalAccessException e) {
        return ToolResult.error(e.getMessage());
      }
    };
  }

  private Object[] mapArguments(Method method, Map<String, Object> args) {
    Parameter[] parameters = method.getParameters();
    Object[] values = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      ToolParam annotation = parameter.getAnnotation(ToolParam.class);
      String argName =
          annotation == null || annotation.name().isEmpty()
              ? parameter.getName()
              : annotation.name();
      Object value = args.get(argName);
      values[i] = convertValue(value, parameter.getType());
    }
    return values;
  }

  @SuppressWarnings("unchecked")
  private Object convertValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }
    if (targetType.isInstance(value)) {
      return value;
    }
    if (targetType == String.class) {
      return value.toString();
    }
    if ((targetType == int.class || targetType == Integer.class)
        && value instanceof Number number) {
      return number.intValue();
    }
    if ((targetType == long.class || targetType == Long.class) && value instanceof Number number) {
      return number.longValue();
    }
    if ((targetType == double.class || targetType == Double.class)
        && value instanceof Number number) {
      return number.doubleValue();
    }
    if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof String s) {
      return Boolean.parseBoolean(s);
    }
    if (targetType.isEnum() && value instanceof String s) {
      return Enum.valueOf((Class<Enum>) targetType, s);
    }
    return value;
  }

  private ToolResult wrapResult(Object result) {
    if (result == null) {
      return ToolResult.success("");
    }
    if (result instanceof ToolResult toolResult) {
      return toolResult;
    }
    return ToolResult.success(result.toString());
  }
}
