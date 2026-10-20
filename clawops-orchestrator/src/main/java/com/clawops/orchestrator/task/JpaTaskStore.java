package com.clawops.orchestrator.task;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.task.TaskRecord;
import com.clawops.harness.task.TaskState;
import com.clawops.harness.task.TaskStore;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 JPA 的 {@link TaskStore} 实现（orchestrator 落 PG）。
 *
 * <p>TaskRecord(id String) ↔ TaskEntity(id UUID)；token_usage / context_messages 经
 * ContextSnapshotCodec 序列化为 jsonb。 update 时校验状态机合法性（从库读当前状态，校验 canTransitionTo）。
 */
public class JpaTaskStore implements TaskStore {

  private final TaskEntityRepository repository;
  private final ContextSnapshotCodec codec;

  public JpaTaskStore(
      TaskEntityRepository repository, com.fasterxml.jackson.databind.ObjectMapper mapper) {
    this(repository, new ContextSnapshotCodec(mapper));
  }

  public JpaTaskStore(TaskEntityRepository repository, ContextSnapshotCodec codec) {
    this.repository = repository;
    this.codec = codec;
  }

  @Override
  public TaskRecord create(TaskRecord task) {
    TaskEntity entity = toEntity(task);
    repository.save(entity);
    return toRecord(entity);
  }

  @Override
  public Optional<TaskRecord> find(String taskId) {
    return repository.findById(UUID.fromString(taskId)).map(this::toRecord);
  }

  @Override
  public List<TaskRecord> findByStates(TaskState... states) {
    List<String> names = Arrays.stream(states).map(TaskState::name).toList();
    return repository.findByStates(names).stream().map(this::toRecord).toList();
  }

  @Override
  public TaskRecord update(TaskRecord task) {
    TaskEntity entity =
        repository
            .findById(UUID.fromString(task.id()))
            .orElseThrow(() -> new IllegalStateException("任务不存在: " + task.id()));
    TaskState current = TaskState.valueOf(entity.getStatus());
    if (!current.canTransitionTo(task.status())) {
      throw new IllegalStateException(
          "非法状态迁移: " + current + " -> " + task.status() + " (task=" + task.id() + ")");
    }
    applyToEntity(entity, task);
    repository.save(entity);
    return toRecord(entity);
  }

  private TaskEntity toEntity(TaskRecord task) {
    TaskEntity entity = new TaskEntity();
    applyToEntity(entity, task);
    if (entity.getId() == null) {
      entity.setId(UUID.fromString(task.id()));
    }
    return entity;
  }

  private void applyToEntity(TaskEntity entity, TaskRecord task) {
    entity.setId(UUID.fromString(task.id()));
    entity.setPrompt(task.prompt());
    entity.setStatus(task.status().name());
    entity.setPauseReason(task.pauseReason());
    entity.setResult(task.result());
    entity.setSteps(task.steps());
    entity.setTokenUsage(codec.serialize(task.tokenUsage()));
    entity.setContextMessages(codec.serialize(task.contextMessages()));
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(task.createdAt());
    }
    entity.setUpdatedAt(task.updatedAt() == null ? Instant.now() : task.updatedAt());
  }

  private TaskRecord toRecord(TaskEntity entity) {
    TokenUsage usage = codec.deserializeToken(entity.getTokenUsage());
    List<AgentMessage> context = codec.deserializeContext(entity.getContextMessages());
    return new TaskRecord(
        entity.getId().toString(),
        entity.getPrompt(),
        TaskState.valueOf(entity.getStatus()),
        entity.getSteps(),
        usage,
        context,
        entity.getPauseReason(),
        entity.getResult(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
