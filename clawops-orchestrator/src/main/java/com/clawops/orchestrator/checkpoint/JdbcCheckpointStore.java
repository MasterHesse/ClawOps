package com.clawops.orchestrator.checkpoint;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.task.CheckpointStore;
import com.clawops.harness.task.StepCheckpoint;
import com.clawops.orchestrator.task.ContextSnapshotCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 基于 JdbcTemplate 的 {@link CheckpointStore} 实现。
 *
 * <p>task_step 落每步检查点（入参出参摘要）；task.context_messages 落全量上下文快照（断点恢复用）。 全量参数化（PG jsonb 经 ?::jsonb
 * cast）。
 */
public class JdbcCheckpointStore implements CheckpointStore {

  private final JdbcTemplate jdbc;
  private final ContextSnapshotCodec codec;

  public JdbcCheckpointStore(JdbcTemplate jdbc, ContextSnapshotCodec codec) {
    this.jdbc = jdbc;
    this.codec = codec;
  }

  @Override
  public void saveStep(StepCheckpoint cp) {
    jdbc.update(
        "INSERT INTO task_step"
            + " (task_id, seq, tool_name, tool_call_id, arguments, result_status,"
            + " result_summary, created_at)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
        UUID.fromString(cp.taskId()),
        cp.seq(),
        cp.toolName(),
        cp.toolCallId(),
        codec.serializeArguments(cp.arguments()),
        cp.resultStatus(),
        cp.resultSummary(),
        Timestamp.from(cp.createdAt() == null ? Instant.now() : cp.createdAt()));
  }

  @Override
  public List<StepCheckpoint> loadSteps(String taskId) {
    return jdbc.query(
        "SELECT task_id, seq, tool_name, tool_call_id, arguments::text AS arguments,"
            + " result_status, result_summary, created_at"
            + " FROM task_step WHERE task_id = ? ORDER BY seq",
        new StepRowMapper(codec),
        UUID.fromString(taskId));
  }

  @Override
  public void saveContext(String taskId, List<AgentMessage> context) {
    jdbc.update(
        "UPDATE task SET context_messages = ?::jsonb, updated_at = now() WHERE id = ?",
        codec.serialize(context),
        UUID.fromString(taskId));
  }

  @Override
  public List<AgentMessage> loadContext(String taskId) {
    String json =
        jdbc.queryForObject(
            "SELECT context_messages::text FROM task WHERE id = ?",
            String.class,
            UUID.fromString(taskId));
    return codec.deserializeContext(json);
  }

  private static final class StepRowMapper implements RowMapper<StepCheckpoint> {

    private final ContextSnapshotCodec codec;

    StepRowMapper(ContextSnapshotCodec codec) {
      this.codec = codec;
    }

    @Override
    public StepCheckpoint mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new StepCheckpoint(
          rs.getString("task_id"),
          rs.getInt("seq"),
          rs.getString("tool_call_id"),
          rs.getString("tool_name"),
          codec.deserializeArguments(rs.getString("arguments")),
          rs.getString("result_status"),
          rs.getString("result_summary"),
          rs.getTimestamp("created_at") == null
              ? Instant.now()
              : rs.getTimestamp("created_at").toInstant());
    }
  }
}
