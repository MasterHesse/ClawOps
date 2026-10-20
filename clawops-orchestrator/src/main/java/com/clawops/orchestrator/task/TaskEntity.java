package com.clawops.orchestrator.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 任务实体（映射 task 表，PG 方言基线：uuid 主键 / timestamptz / jsonb）。
 *
 * <p>token_usage 与 context_messages 为 jsonb 列，以 String 存储原始 JSON（序列化由 ContextSnapshotCodec 完成）。
 */
@Entity
@Table(name = "task")
public class TaskEntity {

  @Id
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @Column(name = "prompt", nullable = false)
  private String prompt;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "pause_reason", length = 200)
  private String pauseReason;

  @Column(name = "result")
  private String result;

  @Column(name = "steps", nullable = false)
  private int steps;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "token_usage", columnDefinition = "jsonb")
  private String tokenUsage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "context_messages", columnDefinition = "jsonb")
  private String contextMessages;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getPauseReason() {
    return pauseReason;
  }

  public void setPauseReason(String pauseReason) {
    this.pauseReason = pauseReason;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public int getSteps() {
    return steps;
  }

  public void setSteps(int steps) {
    this.steps = steps;
  }

  public String getTokenUsage() {
    return tokenUsage;
  }

  public void setTokenUsage(String tokenUsage) {
    this.tokenUsage = tokenUsage;
  }

  public String getContextMessages() {
    return contextMessages;
  }

  public void setContextMessages(String contextMessages) {
    this.contextMessages = contextMessages;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
