package com.clawops.rag.search;

import java.util.ArrayList;
import java.util.List;

/**
 * CJK 二元分词器：用于 PG tsvector 全文检索（simple 分析器不支持中文）。
 *
 * <p>连续 CJK 字符（U+4E00–U+9FFF）两两滑动组成 bigram；拉丁字母数字按词保留并小写； 空格连接，供 {@code to_tsvector('simple', ?)} 与
 * {@code plainto_tsquery('simple', ?)} 使用。
 */
public class CjkBigramTokenizer {

  public String tokenize(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    List<String> tokens = new ArrayList<>();
    int i = 0;
    int len = text.length();
    while (i < len) {
      char c = text.charAt(i);
      if (isCjk(c)) {
        int j = i;
        while (j < len && isCjk(text.charAt(j))) {
          j++;
        }
        String seg = text.substring(i, j);
        if (seg.length() == 1) {
          tokens.add(seg);
        } else {
          for (int k = 0; k < seg.length() - 1; k++) {
            tokens.add(seg.substring(k, k + 2));
          }
        }
        i = j;
      } else if (Character.isLetterOrDigit(c)) {
        int j = i;
        while (j < len && Character.isLetterOrDigit(text.charAt(j))) {
          j++;
        }
        tokens.add(text.substring(i, j).toLowerCase());
        i = j;
      } else {
        i++;
      }
    }
    return String.join(" ", tokens);
  }

  private static boolean isCjk(char c) {
    return c >= 0x4E00 && c <= 0x9FFF;
  }
}
