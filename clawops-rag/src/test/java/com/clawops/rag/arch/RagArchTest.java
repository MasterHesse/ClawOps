package com.clawops.rag.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * rag 架构守护（quality-gates.md §2 + ADR-009）：
 *
 * <ul>
 *   <li>不依赖任何 com.clawops 兄弟模块（rag 独立，由 gateway 装配）
 *   <li>不依赖 Spring（纯 JDBC，DataSource 由 gateway 注入）
 * </ul>
 */
@AnalyzeClasses(packages = "com.clawops.rag")
class RagArchTest {

  @ArchTest
  ArchRule mustNotDependOnSiblingModules =
      noClasses()
          .that()
          .resideInAPackage("com.clawops.rag..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.clawops.harness..",
              "com.clawops.connector..",
              "com.clawops.orchestrator..",
              "com.clawops.gateway..",
              "com.clawops.skills..",
              "com.clawops.workers..");

  @ArchTest
  ArchRule mustNotDependOnSpring =
      noClasses()
          .that()
          .resideInAPackage("com.clawops.rag..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");
}
