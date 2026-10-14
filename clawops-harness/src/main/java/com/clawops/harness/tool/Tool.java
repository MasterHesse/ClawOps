package com.clawops.harness.tool;

import com.clawops.harness.spi.Permission;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个可被 Agent 调用的工具方法。
 *
 * <p>标注在 public 方法上，ToolRegistry 通过反射扫描生成 {@link com.clawops.harness.spi.ToolDefinition}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {

  /** 工具名，默认取方法名。 */
  String name() default "";

  /** 工具描述，用于 Prompt。 */
  String description() default "";

  /** 权限等级。 */
  Permission permission() default Permission.READ;

  /** 单次调用超时（毫秒）。 */
  long timeoutMillis() default 5000;
}
