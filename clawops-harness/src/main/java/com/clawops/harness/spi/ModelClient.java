package com.clawops.harness.spi;

import java.util.Collection;
import java.util.List;

/**
 * 模型客户端 SPI。
 *
 * <p>harness 通过此接口与具体模型实现解耦；LangChain4j、Spring AI 或其他框架均通过 adapter 实现。
 */
@FunctionalInterface
public interface ModelClient {

  /**
   * 发起一次对话请求。
   *
   * @param messages 当前上下文消息
   * @param tools 可用的工具定义集合
   * @return 模型回复
   */
  ModelResponse chat(List<AgentMessage> messages, Collection<ToolDefinition> tools);
}
