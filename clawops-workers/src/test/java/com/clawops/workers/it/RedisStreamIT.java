package com.clawops.workers.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.utility.DockerImageName;

/** 集成测试模板（M0）：Redis 7 容器 + Stream 基本读写（任务队列底座，W3 长任务异步化将复用本模式）。 */
@Tag("it")
@EnabledIfDockerAvailable
@DisplayName("Redis 7 Stream（集成测试模板）")
class RedisStreamIT {

  private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4.11");

  @Test
  @DisplayName("应能 XADD 写入并 XRANGE 读回任务消息")
  void shouldAddAndReadStreamMessages() {
    GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
    redis.start();
    try {
      RedisURI uri = RedisURI.builder().redis(redis.getHost(), redis.getMappedPort(6379)).build();
      RedisClient client = RedisClient.create(uri);
      try {
        RedisCommands<String, String> sync = client.connect().sync();

        String messageId =
            sync.xadd("clawops:test:tasks", Map.of("taskId", "t-1", "action", "diagnose"));
        assertThat(messageId).isNotBlank();

        List<StreamMessage<String, String>> messages =
            sync.xrange("clawops:test:tasks", Range.create("0", "+"));
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getBody()).containsEntry("action", "diagnose");
      } finally {
        client.shutdown();
      }
    } finally {
      redis.stop();
    }
  }
}
