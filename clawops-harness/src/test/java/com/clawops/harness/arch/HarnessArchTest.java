package com.clawops.harness.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Harness 架构门禁（conventions/quality-gates.md §2 三条硬规则的落地，ADR-001）。
 *
 * <p>规则 2（harness 纯内核）与规则 3（LangChain4j 不出边界）在本模块自检；规则 1（模块间无循环） 以包切片形式先行，模块级校验随 W1 内核实现补全。
 */
@AnalyzeClasses(
    packages = "com.clawops.harness",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HarnessArchTest {

  @ArchTest
  static final ArchRule HARNESS_SHOULD_NOT_DEPEND_ON_SPRING =
      noClasses()
          .that()
          .resideInAPackage("com.clawops.harness..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..")
          .because("harness 是纯内核（ADR-001）：不依赖 Spring");

  @ArchTest
  static final ArchRule HARNESS_SHOULD_NOT_DEPEND_ON_SERVLET_OR_HTTP_CLIENTS =
      noClasses()
          .that()
          .resideInAPackage("com.clawops.harness..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "jakarta.servlet..", "java.net.http..", "org.apache.hc..", "okhttp3..")
          .because("harness 与外界只经自研 SPI 交互（ADR-001）");

  @ArchTest
  static final ArchRule HARNESS_SHOULD_NOT_EXPOSE_LANGCHAIN4J_TYPES =
      noClasses()
          .that()
          .resideInAPackage("com.clawops.harness..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..")
          .because("LangChain4j 类型不得出现在 harness（ADR-001 边界的机器化校验）");

  @ArchTest
  static final ArchRule HARNESS_SLICES_SHOULD_BE_FREE_OF_CYCLES =
      slices()
          .matching("com.clawops.harness.(**)")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true);
}
