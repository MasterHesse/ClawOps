package com.clawops.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/** ClawOps Gateway 启动类：扫描 com.clawops 全量模块，JPA entity 扫描覆盖 orchestrator 域。 */
@SpringBootApplication(scanBasePackages = "com.clawops")
@EntityScan(basePackages = "com.clawops")
public class ClawOpsGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ClawOpsGatewayApplication.class, args);
  }
}
