package com.clawops.harness.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 工具方法参数描述。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

  /** 参数名；为空时使用编译后的参数名（需 -parameters）。 */
  String name() default "";

  /** 参数描述。 */
  String description() default "";

  /** 是否必填。 */
  boolean required() default true;

  /** 默认值说明（仅文档，运行时由调用方提供）。 */
  String defaultValue() default "";
}
