package com.clawops.orchestrator.approval;

import com.clawops.harness.spi.Permission;
import com.clawops.harness.spi.ToolCall;
import com.clawops.orchestrator.task.ContextSnapshotCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** 基于 JdbcTemplate 的 {@link ApprovalStore} 实现（approval 表）。 */
public class JdbcApprovalStore implements ApprovalStore {

  private final JdbcTemplate jdbc;
  private final ContextSnapshotCodec codec;

  public JdbcApprovalStore(JdbcTemplate jdbc, ContextSnapshotCodec codec) {
    this.jdbc = jdbc;
    this.codec = codec;
  }

  @Override
  public ApprovalRecord create(ApprovalRecord approval) {
    jdbc.update(
        "INSERT INTO approval"
            + " (id, task_id, tool_call_id, tool_name, arguments, required_permission,"
            + " status, reason, decided_by, created_at)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)",
        UUID.fromString(approval.id()),
        UUID.fromString(approval.taskId()),
        approval.toolCallId(),
        approval.toolName(),
        codec.serializeArguments(approval.arguments()),
        approval.requiredPermission().name(),
        approval.status().name(),
        approval.reason(),
        approval.decidedBy(),
        Timestamp.from(approval.createdAt()));
    return approval;
  }

  @Override
  public Optional<ApprovalRecord> find(String approvalId) {
    List<ApprovalRecord> rows =
        jdbc.query(
            "SELECT id, task_id, tool_call_id, tool_name, arguments::text AS arguments,"
                + " required_permission, status, reason, decided_by, created_at, decided_at"
                + " FROM approval WHERE id = ?",
            new ApprovalRowMapper(codec),
            UUID.fromString(approvalId));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  @Override
  public List<ApprovalRecord> findPendingByTask(String taskId) {
    return queryByTaskAndStatus(taskId, "PENDING");
  }

  @Override
  public List<ApprovalRecord> findApprovedByTask(String taskId) {
    return queryByTaskAndStatus(taskId, "APPROVED");
  }

  private List<ApprovalRecord> queryByTaskAndStatus(String taskId, String status) {
    return jdbc.query(
        "SELECT id, task_id, tool_call_id, tool_name, arguments::text AS arguments,"
            + " required_permission, status, reason, decided_by, created_at, decided_at"
            + " FROM approval WHERE task_id = ? AND status = ? ORDER BY created_at",
        new ApprovalRowMapper(codec),
        UUID.fromString(taskId),
        status);
  }

  @Override
  public ApprovalRecord decide(
      String approvalId, ApprovalStatus decision, String reason, String decidedBy) {
    ApprovalRecord current =
        find(approvalId).orElseThrow(() -> new IllegalStateException("审批单不存在: " + approvalId));
    ApprovalStatus next = current.status().decide(decision); // 校验合法性，已决策抛 ApprovalStateException
    int affected =
        jdbc.update(
            "UPDATE approval SET status = ?, reason = ?, decided_by = ?, decided_at = now()"
                + " WHERE id = ? AND status = 'PENDING'",
            next.name(),
            reason,
            decidedBy,
            UUID.fromString(approvalId));
    if (affected == 0) {
      throw new ApprovalStateException("审批单并发状态变更: " + approvalId);
    }
    return find(approvalId).orElseThrow();
  }

  /** 由 ToolCall 构造 PENDING 审批单的便捷工厂（供 TaskRunner 调用）。 */
  public ApprovalRecord createPending(
      String approvalId, String taskId, ToolCall call, Permission required) {
    return create(ApprovalRecord.pending(approvalId, taskId, call, required, Instant.now()));
  }

  private static final class ApprovalRowMapper implements RowMapper<ApprovalRecord> {

    private final ContextSnapshotCodec codec;

    ApprovalRowMapper(ContextSnapshotCodec codec) {
      this.codec = codec;
    }

    @Override
    public ApprovalRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
      Timestamp decidedAt = rs.getTimestamp("decided_at");
      return new ApprovalRecord(
          rs.getString("id"),
          rs.getString("task_id"),
          rs.getString("tool_call_id"),
          rs.getString("tool_name"),
          codec.deserializeArguments(rs.getString("arguments")),
          Permission.valueOf(rs.getString("required_permission")),
          ApprovalStatus.valueOf(rs.getString("status")),
          rs.getString("reason"),
          rs.getString("decided_by"),
          rs.getTimestamp("created_at").toInstant(),
          decidedAt == null ? null : decidedAt.toInstant());
    }
  }
}
