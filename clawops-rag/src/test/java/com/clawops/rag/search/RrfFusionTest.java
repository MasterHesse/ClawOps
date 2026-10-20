package com.clawops.rag.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.rag.store.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RRF 融合（conventions/testing.md §3 TDD）。 */
@DisplayName("RrfFusion 倒数排名融合")
class RrfFusionTest {

  private final RrfFusion fusion = new RrfFusion();

  @Test
  @DisplayName("双榜都出现的 chunk 排在单榜之前")
  void shouldRankDualPresenceFirst() {
    RetrievedChunk shared = chunk("c1", "共享");
    RetrievedChunk vOnly = chunk("c2", "仅向量");
    RetrievedChunk fOnly = chunk("c3", "仅全文");

    List<RetrievedChunk> fused = fusion.fuse(List.of(shared, vOnly), List.of(shared, fOnly), 5, 60);

    assertThat(fused.get(0).chunkId()).isEqualTo("c1");
    assertThat(fused.get(0).source()).isEqualTo("hybrid");
  }

  @Test
  @DisplayName("单榜 chunk 按 RRF 分数排序")
  void shouldRankSingleListByRrf() {
    RetrievedChunk a = chunk("c1", "a");
    RetrievedChunk b = chunk("c2", "b");
    List<RetrievedChunk> fused = fusion.fuse(List.of(a, b), List.of(), 5, 60);
    assertThat(fused).extracting(RetrievedChunk::chunkId).containsExactly("c1", "c2");
    assertThat(fused.get(0).source()).isEqualTo("vector");
  }

  @Test
  @DisplayName("topK 截断")
  void shouldTruncateByTopK() {
    List<RetrievedChunk> vec = List.of(chunk("c1", "a"), chunk("c2", "b"), chunk("c3", "c"));
    assertThat(fusion.fuse(vec, List.of(), 2, 60)).hasSize(2);
  }

  @Test
  @DisplayName("双空榜返回空")
  void shouldReturnEmptyForEmptyInputs() {
    assertThat(fusion.fuse(List.of(), List.of(), 5, 60)).isEmpty();
  }

  private static RetrievedChunk chunk(String id, String content) {
    return new RetrievedChunk(id, "doc", "doc", 1, content, 0.0, "vector");
  }
}
