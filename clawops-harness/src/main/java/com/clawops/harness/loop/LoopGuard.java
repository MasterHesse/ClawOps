package com.clawops.harness.loop;

import com.clawops.harness.spi.ToolCall;
import java.util.Map;
import java.util.TreeMap;

/**
 * 死循环检测器。
 *
 * <p>记录最近窗口内的工具调用签名（工具名 + 规范化参数），同一签名出现次数达到阈值即判定为死循环。
 */
public class LoopGuard {

  private final int threshold;
  private CallSignature lastSignature;
  private int consecutiveCount = 0;

  public LoopGuard() {
    this(2);
  }

  public LoopGuard(int threshold) {
    if (threshold < 1) {
      throw new IllegalArgumentException("threshold must be >= 1");
    }
    this.threshold = threshold;
  }

  /** 检查本次调用是否允许执行。 */
  public boolean check(ToolCall call) {
    CallSignature signature = CallSignature.of(call);
    if (signature.equals(lastSignature)) {
      consecutiveCount++;
    } else {
      lastSignature = signature;
      consecutiveCount = 1;
    }
    return consecutiveCount <= threshold;
  }

  public void reset() {
    lastSignature = null;
    consecutiveCount = 0;
  }

  public int threshold() {
    return threshold;
  }

  private record CallSignature(String name, String argsKey) {

    static CallSignature of(ToolCall call) {
      Map<String, Object> sorted =
          new TreeMap<>(call.arguments() == null ? Map.of() : call.arguments());
      return new CallSignature(call.name(), sorted.toString());
    }
  }
}
