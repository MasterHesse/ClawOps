package com.clawops.orchestrator.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * orchestrator 集成测试专用 Spring 引导配置（库模块无独立 @SpringBootApplication）。
 *
 * <p>触发 datasource / Flyway / JPA 自动配置，配合 {@code @ServiceConnection} 的 Testcontainers PG。
 * orchestrator 无 web starter，Jackson autoconfig 不激活，故显式提供 ObjectMapper bean。
 */
@SpringBootApplication
class OrchestratorTestApp {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
