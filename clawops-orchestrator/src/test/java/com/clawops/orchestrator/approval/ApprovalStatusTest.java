package com.clawops.orchestrator.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 审批状态机校验（conventions/testing.md §3 TDD 强制范围）。 */
@DisplayName("ApprovalStatus 审批状态机")
class ApprovalStatusTest {

  @Test
  @DisplayName("PENDING 可决策为 APPROVED 或 REJECTED")
  void shouldDecideFromPending() {
    assertThat(ApprovalStatus.PENDING.decide(ApprovalStatus.APPROVED))
        .isEqualTo(ApprovalStatus.APPROVED);
    assertThat(ApprovalStatus.PENDING.decide(ApprovalStatus.REJECTED))
        .isEqualTo(ApprovalStatus.REJECTED);
  }

  @Test
  @DisplayName("已决策的状态不可再次决策")
  void shouldRejectRedecision() {
    assertThatThrownBy(() -> ApprovalStatus.APPROVED.decide(ApprovalStatus.REJECTED))
        .isInstanceOf(ApprovalStateException.class);
    assertThatThrownBy(() -> ApprovalStatus.REJECTED.decide(ApprovalStatus.APPROVED))
        .isInstanceOf(ApprovalStateException.class);
  }

  @Test
  @DisplayName("决策目标只能是 APPROVED 或 REJECTED")
  void shouldRejectInvalidDecisionTarget() {
    assertThatThrownBy(() -> ApprovalStatus.PENDING.decide(ApprovalStatus.PENDING))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ApprovalStatus.PENDING.decide(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("isDecided：PENDING 为否，APPROVED/REJECTED 为是")
  void shouldReportDecided() {
    assertThat(ApprovalStatus.PENDING.isDecided()).isFalse();
    assertThat(ApprovalStatus.APPROVED.isDecided()).isTrue();
    assertThat(ApprovalStatus.REJECTED.isDecided()).isTrue();
  }
}
