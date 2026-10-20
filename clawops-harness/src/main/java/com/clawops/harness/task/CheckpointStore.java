package com.clawops.harness.task;

import com.clawops.harness.spi.AgentMessage;
import java.util.List;

/**
 * 检查点持久化 SPI（harness 纯内核）。
 *
 * <p>每步工具调用后落盘 {@link StepCheckpoint}（入参出参摘要），并保存当前上下文全量快照（断点恢复重建历史用）。 实现由 orchestrator 提供（JDBC +
 * jsonb）。
 */
public interface CheckpointStore {

  /** 落盘单步检查点。 */
  void saveStep(StepCheckpoint checkpoint);

  /** 按任务载入全部步骤（按 seq 升序）。 */
  List<StepCheckpoint> loadSteps(String taskId);

  /** 保存上下文全量快照（覆盖该任务的上次快照）。 */
  void saveContext(String taskId, List<AgentMessage> context);

  /** 载入上下文全量快照（最后一次保存）。 */
  List<AgentMessage> loadContext(String taskId);
}
