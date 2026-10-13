package com.clawops.harness;

/**
 * Harness 模块占位类。
 *
 * <p>本模块为测试公共模块，主源码在 M0 阶段暂无实际类型；保留此类以保证 ArchUnit 包切片规则 有类可分析，待 W1 注入 FakeChatModel SPI
 * 后可移除或演化为正式契约。
 */
public final class Harness {
  private Harness() {}
}
