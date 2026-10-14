package com.clawops.gateway.harness;

import com.clawops.connector.log.MockLogConnector;
import com.clawops.connector.metrics.MetricsConnector;
import com.clawops.gateway.chat.DemoModelClient;
import com.clawops.harness.context.ContextConfig;
import com.clawops.harness.loop.AgentLoop;
import com.clawops.harness.loop.LoopConfig;
import com.clawops.harness.loop.LoopGuard;
import com.clawops.harness.permission.PermissionManager;
import com.clawops.harness.retry.RetryConfig;
import com.clawops.harness.retry.RetryExecutor;
import com.clawops.harness.spi.ModelClient;
import com.clawops.harness.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 组装 Harness 内核与 Connector 实现的 Spring 配置。 */
@Configuration
public class HarnessConfig {

  @Bean
  ToolRegistry toolRegistry(
      @Value("${clawops.prometheus.url:http://localhost:9090}") String prometheusUrl) {
    return new ToolRegistry()
        .register(new MetricsConnector(prometheusUrl))
        .register(new MockLogConnector());
  }

  @Bean
  PermissionManager permissionManager() {
    return new PermissionManager();
  }

  @Bean
  RetryExecutor retryExecutor() {
    return new RetryExecutor();
  }

  @Bean
  LoopGuard loopGuard() {
    return new LoopGuard(2);
  }

  @Bean
  ModelClient modelClient() {
    return new DemoModelClient();
  }

  @Bean
  AgentLoop agentLoop(
      ModelClient modelClient,
      ToolRegistry toolRegistry,
      PermissionManager permissionManager,
      RetryExecutor retryExecutor,
      LoopGuard loopGuard) {
    return new AgentLoop(
        modelClient,
        toolRegistry,
        permissionManager,
        retryExecutor,
        new RetryConfig(),
        loopGuard,
        text -> text == null ? 0 : text.length() / 4,
        null,
        new LoopConfig(),
        new ContextConfig());
  }
}
