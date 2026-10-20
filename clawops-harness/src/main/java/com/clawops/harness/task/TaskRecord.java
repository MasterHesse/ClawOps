package com.clawops.harness.task;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenUsage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 任务记录（不可变）。
 *
 * <p>承载任务持久化所需的全部字段：身份、提示词、状态、步数、token 用量、上下文快照（断点恢复用）、暂停原因、结果与时间戳。 状态迁移经 {@link #withStatus} /
 * {@link #pause} / {@link #complete} / {@link #fail} / {@link #cancel} 产生新实例， 迁移合法性由 {@link
 * TaskState#canTransitionTo} 校验。
 *
 * @param id 任务 ID（UUID 字符串）
 * @param prompt 任务提示词
 * @param status 当前状态
 * @param steps 已执行步数
 * @param tokenUsage token 累计用量
 * @param contextMessages 上下文快照（断点恢复重建历史用）
 * @param pauseReason 暂停原因（仅 PAUSED 时非空）
 * @param result 最终结果（SUCCESS 时为回答，FAILED 时为错误说明）
 * @param createdAt 创建时间
 * @param updatedAt 最近更新时间
 */
public record TaskRecord(
    String id,
    String prompt,
    TaskState status,
    int steps,
    TokenUsage tokenUsage,
    List<AgentMessage> contextMessages,
    String pauseReason,
    String result,
    Instant createdAt,
    Instant updatedAt) {

  public TaskRecord {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(prompt, "prompt must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(tokenUsage, "tokenUsage must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (prompt.isBlank()) {
      throw new IllegalArgumentException("prompt must not be blank");
    }
    if (steps < 0) {
      throw new IllegalArgumentException("steps must be >= 0");
    }
    contextMessages = contextMessages == null ? List.of() : List.copyOf(contextMessages);
  }

  /** 新建任务工厂（CREATED，默认值）。 */
  public static TaskRecord create(String id, String prompt, Instant now) {
    return new TaskRecord(
        id, prompt, TaskState.CREATED, 0, TokenUsage.zero(), List.of(), null, null, now, now);
  }

  /** 通用状态迁移：校验合法性、更新步数与时间戳。 */
  public TaskRecord withStatus(TaskState next, int steps) {
    if (!status.canTransitionTo(next)) {
      throw new IllegalStateException("非法状态迁移: " + status + " -> " + next);
    }
    if (steps < 0) {
      throw new IllegalArgumentException("steps must be >= 0");
    }
    return new TaskRecord(
        id, prompt, next, steps, tokenUsage, contextMessages, null, null, createdAt, Instant.now());
  }

  /** 暂停任务（RUNNING → PAUSED）。 */
  public TaskRecord pause(String reason) {
    if (!status.canTransitionTo(TaskState.PAUSED)) {
      throw new IllegalStateException("非法暂停: " + status);
    }
    return new TaskRecord(
        id,
        prompt,
        TaskState.PAUSED,
        steps,
        tokenUsage,
        contextMessages,
        reason,
        null,
        createdAt,
        Instant.now());
  }

  /** 完成（PAUSED/RUNNING → SUCCEEDED），携带最终结果、步数、用量与上下文快照。 */
  public TaskRecord complete(
      String result, int steps, TokenUsage usage, List<AgentMessage> context) {
    if (!status.canTransitionTo(TaskState.SUCCEEDED)) {
      throw new IllegalStateException("非法完成: " + status);
    }
    return new TaskRecord(
        id,
        prompt,
        TaskState.SUCCEEDED,
        steps,
        usage,
        context,
        null,
        result,
        createdAt,
        Instant.now());
  }

  /** 失败（PAUSED/RUNNING → FAILED）。 */
  public TaskRecord fail(String reason, int steps, TokenUsage usage, List<AgentMessage> context) {
    if (!status.canTransitionTo(TaskState.FAILED)) {
      throw new IllegalStateException("非法失败: " + status);
    }
    return new TaskRecord(
        id,
        prompt,
        TaskState.FAILED,
        steps,
        usage,
        context,
        null,
        reason,
        createdAt,
        Instant.now());
  }

  /** 取消（任意非终态 → CANCELLED）。 */
  public TaskRecord cancel() {
    if (status.isTerminal()) {
      throw new IllegalStateException("终态不可取消: " + status);
    }
    return new TaskRecord(
        id,
        prompt,
        TaskState.CANCELLED,
        steps,
        tokenUsage,
        contextMessages,
        null,
        null,
        createdAt,
        Instant.now());
  }
}
