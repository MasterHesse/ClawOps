package com.clawops.orchestrator.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * orchestrator 架构守护（quality-gates.md §2）：
 *
 * <ul>
 *   <li>不依赖 LangChain4j（Agent 内核与模型解耦）
 *   <li>不依赖兄弟模块（除 harness）：模型/UI/RAG/技能/Worker 由上层装配
 * </ul>
 */
@AnalyzeClasses(packages = "com.clawops.orchestrator")
class OrchestratorArchTest {

  @ArchTest
  ArchRule mustNotDependOnLangchain4j =
      noClasses()
          .that()
          .resideInAPackage("com.clawops.orchestrator..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("dev.langchain4j..");

  @ArchTest
  ArchRule mustNotDependOnSiblingModulesExceptHarness =
      noClasses()
          .that()
          .resideInAPackage("com.clawops.orchestrator..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.clawops.connector..",
              "com.clawops.rag..",
              "com.clawops.gateway..",
              "com.clawops.skills..",
              "com.clawops.workers..");
}
