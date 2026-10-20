package com.clawops.harness.task;

import java.util.List;
import java.util.Optional;

/**
 * 任务持久化 SPI（harness 纯内核，无第三方依赖）。
 *
 * <p>实现由 orchestrator 提供（JPA/JDBC 落 PG）。harness 仅定义契约，不引入任何持久层依赖。
 */
public interface TaskStore {

  /** 新建任务（CREATED）。 */
  TaskRecord create(TaskRecord task);

  /** 按主键查找。 */
  Optional<TaskRecord> find(String taskId);

  /** 按状态集合查找（断点恢复扫描 RUNNING/PAUSED 任务用）。 */
  List<TaskRecord> findByStates(TaskState... states);

  /** 状态迁移落库（实现须校验状态机合法性）。 */
  TaskRecord update(TaskRecord task);
}
