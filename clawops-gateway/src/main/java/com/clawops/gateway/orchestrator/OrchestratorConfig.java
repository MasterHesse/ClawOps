package com.clawops.gateway.orchestrator;

import com.clawops.orchestrator.approval.JdbcApprovalStore;
import com.clawops.orchestrator.audit.JdbcAuditWriter;
import com.clawops.orchestrator.checkpoint.JdbcCheckpointStore;
import com.clawops.orchestrator.task.ContextSnapshotCodec;
import com.clawops.orchestrator.task.JpaTaskStore;
import com.clawops.orchestrator.task.TaskEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/** 装配 orchestrator 持久层 bean（stores + codec），JPA 仓储扫描 orchestrator 域。 */
@Configuration
@EnableJpaRepositories(basePackages = "com.clawops.orchestrator")
public class OrchestratorConfig {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  ContextSnapshotCodec contextSnapshotCodec(ObjectMapper objectMapper) {
    return new ContextSnapshotCodec(objectMapper);
  }

  @Bean
  JpaTaskStore jpaTaskStore(TaskEntityRepository repository, ObjectMapper objectMapper) {
    return new JpaTaskStore(repository, objectMapper);
  }

  @Bean
  JdbcCheckpointStore jdbcCheckpointStore(JdbcTemplate jdbcTemplate, ContextSnapshotCodec codec) {
    return new JdbcCheckpointStore(jdbcTemplate, codec);
  }

  @Bean
  JdbcApprovalStore jdbcApprovalStore(JdbcTemplate jdbcTemplate, ContextSnapshotCodec codec) {
    return new JdbcApprovalStore(jdbcTemplate, codec);
  }

  @Bean
  JdbcAuditWriter jdbcAuditWriter(JdbcTemplate jdbcTemplate, ContextSnapshotCodec codec) {
    return new JdbcAuditWriter(jdbcTemplate, codec);
  }
}
