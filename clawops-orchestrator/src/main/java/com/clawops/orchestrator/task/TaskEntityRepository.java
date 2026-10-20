package com.clawops.orchestrator.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 任务实体仓储。 */
public interface TaskEntityRepository extends JpaRepository<TaskEntity, UUID> {

  @Query("SELECT t FROM TaskEntity t WHERE t.status IN :states ORDER BY t.createdAt DESC")
  List<TaskEntity> findByStates(@Param("states") List<String> states);
}
