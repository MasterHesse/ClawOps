package com.clawops.orchestrator.audit;

import com.clawops.orchestrator.task.ContextSnapshotCodec;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 全链路审计落库（audit_log）：谁、何时、做了什么、参数与结果摘要（jsonb detail）。
 *
 * <p>taskId 可空（非任务级审计，如系统级事件）。
 */
public class JdbcAuditWriter {

  private final JdbcTemplate jdbc;
  private final ContextSnapshotCodec codec;

  public JdbcAuditWriter(JdbcTemplate jdbc, ContextSnapshotCodec codec) {
    this.jdbc = jdbc;
    this.codec = codec;
  }

  public void write(String taskId, String actor, String action, Map<String, Object> detail) {
    jdbc.update(
        "INSERT INTO audit_log (task_id, actor, action, detail, created_at)"
            + " VALUES (?, ?, ?, ?::jsonb, now())",
        taskId == null ? null : UUID.fromString(taskId),
        actor,
        action,
        codec.serializeArguments(detail));
  }
}
