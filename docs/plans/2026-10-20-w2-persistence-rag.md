# W2 持久化 + RAG（M2）实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.
> 本计划为可接续执行的断点文档：任何会话可从此文档 + PROGRESS.md 恢复工作。

**Goal:** 任务持久化与断点恢复 + HITL 审批 + RAG 知识库（解析→切分→Embedding→pgvector 混合检索→引用溯源），全部 TDD，交付时 CI 全绿、覆盖率到最终门槛、打 tag v0.2.0。

**Architecture:** harness 新增纯 SPI（TaskStore/CheckpointStore/ApprovalGate），AgentLoop 扩展暂停与恢复语义；orchestrator 用 JPA+JdbcTemplate 实现落库与断点续跑编排（ResumableTaskRunner）；rag 纯 JDBC（DataSource 注入）+ LangChain4j EmbeddingModel 接口实现 pgvector HNSW + tsvector + RRF 混合检索；gateway 负责装配（数据源/Flyway/REST/前端静态页/错误码）。上云搁置（demo 阶段再上）。

**Tech Stack:** JDK 25 / Boot 4.1.1 / LangChain4j 1.20.2 / PG17+pgvector 0.8.7 / Flyway（Boot BOM 版本）/ PDFBox / Testcontainers 2.0.5

---

## 关键设计决策（详细论证见 ADR-004 / ADR-008 / ADR-009，随 Phase F 落盘）

1. **Flyway 脚本按领域分模块**：`V1__task_domain.sql` 在 orchestrator（task/task_step/audit_log/approval/eval_case），`V2__rag_domain.sql` 在 rag（kb_doc/kb_chunk + vector 扩展 + HNSW/GIN 索引）。gateway 启动时 Flyway 默认 `classpath:db/migration` 会合并扫描两个 jar 并按版本执行；各模块 IT 用 Flyway API（test scope）跑自身脚本。
2. **harness 零依赖不变**：TaskStore/CheckpointStore/ApprovalGate 均为纯接口 + record（TaskRecord/TaskState/StepCheckpoint）。上下文快照的 JSON 序列化由 orchestrator 侧（有 Jackson）完成，harness 只出纯数据。
3. **审批暂停-恢复语义**：AgentLoop 在 executeTool 前询问 ApprovalGate；需审批时返回 `LoopResult.paused(pendingCall, approvalId, ...)`（新 Status `PAUSED_FOR_APPROVAL`）。恢复 = 从最后快照重建 history（**尾部未配对的 assistant toolCalls 消息被 trim**，模型将重新发起调用），此时 Gate 按已 APPROVED 的 approval（taskId+toolName+arguments 匹配）放行。该语义写入 ADR-008。
4. **rag 模块纯净**：主代码仅依赖 langchain4j + pdfbox（ADR-009 白名单），数据访问纯 `java.sql`/`javax.sql.DataSource`（构造注入，由 gateway 提供带 PG 驱动的 DataSource），PG 驱动在 rag 为 test scope。
5. **中文全文检索**：PG simple 分析器不支持中文 → 应用层 CJK bigram 分词后写 tsv 列（非 generated），查询同理；英文照常分词。tsvector+GIN 不变，符合任务书"tsvector + GIN"。
6. **模型接入双轨**：gateway 新增 `Lc4jModelClient`/`Lc4jEmbeddingAdapter`（LangChain4j 类型不出 harness/orchestrator public API，只存在于 gateway adapter 内部）。`CLAWOPS_LLM_API_KEY` 存在 → OpenAi（langchain4j-open-ai）；否则 Demo/Fake（CI 无 key 路径）。
7. **embedding 列不锁维度**（`embedding vector`），Fake 与真实模型（1536 维）均可用；HNSW 索引不指定维度。
8. **quality-gates §2 依赖方向更新**（规约演进，Phase F 提交）：`gateway → harness, connector, orchestrator, rag, workers`，其余不变。

## 数据库 DDL（PG 方言基线：uuid 主键 gen_random_uuid()、timestamptz、jsonb、参数化）

V1（orchestrator）：`task(id uuid pk, prompt text, status varchar(20), pause_reason varchar(200), result text, steps int, token_usage jsonb, context_messages jsonb, created_at, updated_at)`；`task_step(id, task_id fk, seq, tool_name, tool_call_id, arguments jsonb, result_status, result_summary, created_at, unique(task_id,seq))`；`audit_log(id, task_id, actor, action, detail jsonb, created_at)`；`approval(id, task_id fk, tool_call_id, tool_name, arguments jsonb, required_permission, status[PENDING/APPROVED/REJECTED], reason, decided_by, created_at, decided_at)`；`eval_case(id, category, input, ground_truth jsonb, judge_rule, enabled bool, created_at)`。

V2（rag）：`CREATE EXTENSION IF NOT EXISTS vector`；`kb_doc(id, title, source_type[md/txt/pdf], chunk_size, overlap, content_length, status[READY/FAILED], created_at)`；`kb_chunk(id, doc_id fk cascade, seq, content, embedding vector, tsv tsvector, created_at, unique(doc_id,seq))` + `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)` + `CREATE INDEX ... USING gin (tsv)`。

## 新增错误码（只增不改）：2002 TASK_NOT_RESUMABLE(409)、2003 APPROVAL_STATE_CONFLICT(409)、4002 KB_EMBEDDING_FAILED(500)、4003 KB_DOC_NOT_FOUND(404)

## REST 契约（前缀 /api/v1，统一响应体）

- `POST /tasks` {message} → 202 {taskId}（异步执行）；`GET /tasks?page&pageSize` → 分页；`GET /tasks/{id}` → 详情含 steps；`POST /tasks/{id}/stream` → SSE（事件复用 delta/tool_call/tool_result/done/error + 新增 `approval_required`）；`POST /tasks/{id}/resume` → 202/409
- `GET /approvals?status=PENDING`；`POST /approvals/{id}/decision` {approve, reason, decidedBy} → 200/409
- `POST /kb/documents`（multipart: file + chunkSize + overlap）→ 201 {docId, chunkCount}；`GET /kb/documents`；`DELETE /kb/documents/{id}`；`POST /kb/search` {query, topK?} → 召回片段（含 docTitle/seq/score/来源标记 vector|fulltext|hybrid）；`POST /kb/ask` {question} → SSE，回答带引用列表

---

## Phase A：数据层基础（可独立验证）

### A1. orchestrator 域 Flyway V1
- Modify: `clawops-orchestrator/pom.xml`（+spring-boot-starter-data-jpa；test: testcontainers-postgresql、testcontainers-junit-jupiter、org.postgresql:postgresql、flyway-core、flyway-database-postgresql；jacoco 门槛暂保持 0.50）
- Create: `clawops-orchestrator/src/main/resources/db/migration/V1__task_domain.sql`
- Test: `clawops-orchestrator/src/test/java/com/clawops/orchestrator/it/FlywayTaskDomainIT.java`（@Tag("it")，Testcontainers pgvector 镜像 + Flyway.migrate().dataSource(...).locations("classpath:db/migration").load().migrate()，断言 5 表存在且列为预期类型）
- 验证：`.\mvnw.cmd -B -ntp -pl clawops-orchestrator -am verify`（IT 需 Docker）
- Commit: `feat(orchestrator): add task domain Flyway migration V1`

### A2. rag 域 Flyway V2
- Modify: `clawops-rag/pom.xml`（compile: org.apache.pdfbox:pdfbox（版本 3.x，属性入父 POM）；test: flyway-core、flyway-database-postgresql）
- Create: `clawops-rag/src/main/resources/db/migration/V2__rag_domain.sql`
- Test: `clawops-rag/src/test/java/com/clawops/rag/it/FlywayRagDomainIT.java`（同 A1，断言 kb_doc/kb_chunk 存在、vector 扩展、HNSW 与 GIN 索引存在）
- 验证 + Commit 同 A1（模块换 rag）

### A3. gateway 数据源与 Flyway
- Modify: `clawops-gateway/pom.xml`（+clawops-orchestrator、clawops-rag、spring-boot-starter-data-jpa、org.postgresql:postgresql(runtime)、org.flywaydb:flyway-core、flyway-database-postgresql、dev.langchain4j:langchain4j-open-ai）
- Modify: `clawops-gateway/src/main/resources/application.yml`（datasource url `jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:clawops}`、jpa ddl-auto: validate、flyway locations classpath:db/migration）
- 验证：起 Boot 应用（连 clawops-dev PG 5432）确认 V1+V2 全部执行（查 flyway_schema_history）；`.\mvnw.cmd -pl clawops-gateway -am package` 绿
- Commit: `feat(gateway): wire datasource + flyway for task/rag domains`

## Phase B：harness 扩展（TDD 单测，最终门槛 80%）

### B1. 任务 SPI（纯数据 + 接口）
- Create: `clawops-harness/.../harness/task/TaskState.java`（CREATED/RUNNING/PAUSED/SUCCEEDED/FAILED/CANCELLED，含 canTransitions）、`TaskRecord.java`（record + 校验）、`TaskStore.java`（create/find/findByStates/update）、`StepCheckpoint.java`（record：taskId/seq/toolName/toolCallId/arguments(Map)/resultStatus/resultSummary/timestamp）、`CheckpointStore.java`（saveStep/loadSteps/saveContext/loadContext）、`spi/ApprovalGate.java`（`Optional<String> requireApproval(ToolCall)`，返回审批单号）
- Test 先行：`task/TaskRecordTest`、`task/TaskStateTest`（状态机迁移合法性）
- Commit: `feat(harness): add task persistence SPI (TaskStore/CheckpointStore/ApprovalGate)`

### B2. ContextManager 恢复支持
- Modify: `clawops-harness/.../harness/context/ContextManager.java`（+`withHistory(List<AgentMessage>)`、+`trimUnresolvedToolCalls()`：移除尾部未配对 tool result 的 assistant toolCalls 消息）
- Test 先行：`context/ContextManagerHistoryTest`（注入历史后 build 包含历史；trim 掉尾部 assistant(toolCalls) 无 tool result 的消息；保留已配对的）
- Commit: `feat(harness): context history injection and unresolved tool-call trimming`

### B3. AgentLoop 暂停与恢复
- Modify: `loop/LoopResult.java`（+Status.PAUSED_FOR_APPROVAL、+组件 pendingToolCall/approvalId、工厂 `paused(...)`，旧工厂传 null——所有既有调用点经工厂无需改）、`spi/AgentEventListener.java`（+default onApprovalRequired(ToolCall,String)）、`loop/AgentLoop.java`（构造器/withApprovalGate；run 重载 `run(String task, List<AgentMessage> resumedHistory)`；executeTool 前 gate 检查 → paused 返回；for 循环 break）
- Test 先行：`loop/AgentLoopApprovalTest`（gate 返回 approvalId → LoopResult.PAUSED_FOR_APPROVAL 且 pendingToolCall 匹配、listener.onApprovalRequired 被调；gate 放行 → 正常执行；恢复 run(task, history) 时模型收到历史且可续跑完成；gate=null 兼容旧行为）
- 验证：harness 全模块测试绿 + jacoco ≥0.60（B 阶段完成后提到 0.80）
- Commit: `feat(harness): agent loop pause-for-approval and resume-from-history`
- Commit: `chore(harness): raise jacoco threshold to 0.80 (M2 freeze)`

## Phase C：orchestrator 持久化实现（TDD；覆盖率最终 70%）

### C1. Task JPA + JpaTaskStore
- Create: `.../orchestrator/task/TaskEntity.java`（@Entity task 表；token_usage/context_messages 用 @JdbcTypeCode(SqlTypes.JSON) String 或 String 列直存）、`TaskEntityRepository.java`（Spring Data）、`JpaTaskStore.java implements TaskStore`（harness SPI ↔ entity 映射；update 时状态迁移校验）
- Test 先行（IT）：`it/JpaTaskStoreIT`（Testcontainers PG + Flyway V1 + 手动构建 DataSource + 直接 new repository？——Spring Data 需 context；采用 @DataJpaTest + @ServiceConnection/@Testcontainers 或手动 JpaEntityManageScenario。**决策：用 `@DataJpaTest` + `@Testcontainers` + `@ServiceConnection`（Testcontainers 2.x spring-boot 支持，Boot 4 内建）**）
- Commit: `feat(orchestrator): JPA task store`

### C2. JdbcCheckpointStore + audit
- Create: `.../orchestrator/checkpoint/ContextSnapshotCodec.java`（AgentMessage ↔ JSON，Jackson；ToolCall/ToolResult 还原）、`JdbcCheckpointStore.java implements CheckpointStore`（task_step insert + task.context_messages 更新；loadSteps/loadContext）、`audit/JdbcAuditWriter.java`（audit_log insert：actor/action/detail jsonb）
- Test 先行（单测）：`checkpoint/ContextSnapshotCodecTest`（round-trip：user/assistant(toolCalls)/tool 消息完整还原）
- Test 先行（IT）：`it/JdbcCheckpointStoreIT`（JdbcTemplate on Testcontainers；saveStep 3 次后 loadSteps 顺序与字段；saveContext/loadContext round-trip）
- Commit: `feat(orchestrator): checkpoint store with context snapshot codec`

### C3. 审批状态机 + JdbcApprovalStore
- Create: `.../orchestrator/approval/ApprovalRecord.java`（record）、`ApprovalStatus.java`（PENDING/APPROVED/REJECTED + decide 校验：非 PENDING 决策抛 ApprovalStateException）、`ApprovalStore.java`（接口：create/find/findPendingByTask/decide）、`JdbcApprovalStore.java`
- Test 先行（单测）：`approval/ApprovalStatusTest`（PENDING→APPROVED ok；PENDING→REJECTED ok；APPROVED→REJECTED 抛；REJECTED→APPROVED 抛）
- Test 先行（IT）：`it/JdbcApprovalStoreIT`（create→find→decide→find；重复 decide 抛且落库不变）
- Commit: `feat(orchestrator): approval state machine and store`

### C4. ResumableTaskRunner（编排核心）
- Create: `.../orchestrator/runner/TaskRunner.java`（构造注入 AgentLoop 原型/TaskStore/CheckpointStore/ApprovalStore/AuditWriter/ExecutorService；`submit(prompt)`→task RUNNING+异步 run；监听器把 onToolResult 写 StepCheckpoint + saveContext（trim 后）+ audit；gate（内部类 `PauseOnDangerousGate`：DANGEROUS 工具→创建 approval→返回 approvalId）；paused→task PAUSED+pause_reason；`decide(approvalId, approve, by)`→状态机+audit+（approve）task RUNNING+`resume(taskId)`：从 loadContext 重建 history、`ApprovedCallGate`（放行匹配 APPROVED 的 toolName+arguments）重跑；`resume` 幂等校验状态）；`runner/TaskRunnerException`（映射 2002/2003 由 gateway 转 ApiError）
- Test 先行（单测，fake stores + StubModelClient（自研 implements ModelClient，可编程队列，注意不是 langchain4j））：`runner/TaskRunnerTest`（完整流：submit→工具步落 checkpoint→成功；DANGEROUS 工具→PAUSED+approval 创建；decide(approve)→续跑成功；decide(reject)→task FAILED/CANCELLED 且不执行；重复 decide 抛；非 PAUSED resume 抛）
- Test 先行（IT）：`it/TaskResumeIT`（真 PG + 真 Jdbc/JPA store + StubModelClient 编排两阶段：阶段 1 跑到 DANGEROUS 暂停后"丢弃 runner 实例（模拟进程死亡）"；阶段 2 新建 TaskRunner（同库）→ find PAUSED → resume → 断言续跑成功且 task_step 全序）
- 验证记录（kill -9 真实场景）：`docs/metrics/w2-resume-verification.md`（Phase F 手动验证补记录）
- Commit: `feat(orchestrator): resumable task runner with HITL approval`

### C5. orchestrator ArchUnit + 门槛
- Create: `.../orchestrator/arch/OrchestratorArchTest.java`（不依赖 langchain4j/兄弟模块除 harness+connector/spring-web 不限但 public API 禁 langchain4j 类型——规则落地：noClasses().that().resideInAPackage("com.clawops.orchestrator..").should().dependOnClassesThat().resideInAPackage("dev.langchain4j..")）
- Modify: `clawops-orchestrator/pom.xml` jacoco 0.50→0.70
- Commit: `test(orchestrator): arch rules + raise coverage to 0.70 (M2 freeze)`

## Phase D：RAG（TDD；覆盖率最终 65%）

### D1. Chunker
- Create: `.../rag/chunk/Chunker.java`（`List<Chunk> split(String text, int chunkSize, int overlap)`；按段落→句子边界聚合；参数校验：chunkSize≥50、0≤overlap<chunkSize；Chunk(seq, content)）
- Test 先行：`chunk/ChunkerTest`（空文本→空列表；超长段落按句子/字符二次切；overlap 正确重叠；参数非法抛 IllegalArgumentException）
- Commit: `feat(rag): tunable chunker`

### D2. CJK bigram 分词
- Create: `.../rag/search/CjkBigramTokenizer.java`（`String tokenize(String text)`：CJK 字符两两组合、拉丁词原样、小写、空格连接；用于 tsv 写入与查询）
- Test 先行：`search/CjkBigramTokenizerTest`（纯中文、中英混合、纯英文、空串）
- Commit: `feat(rag): cjk bigram tokenizer for tsvector`

### D3. DocumentParser
- Create: `.../rag/parse/DocumentParser.java`（接口 + `ParsedDocument(title, text)`）、`parse/MarkdownParser.java`、`parse/PlainTextParser.java`、`parse/PdfParser.java`（PDFBox）、`parse/DocumentParserFactory.java`（按扩展名/类型分发；未知类型抛→KB_PARSE_FAILED 对应异常 `DocumentParseException`）
- Test 先行：`parse/DocumentParserTest`（md 保留标题文本去标记；txt 原样；pdf 用小样例文件（测试资源 1 页）；工厂分发与未知类型）
- Commit: `feat(rag): document parsers for md/txt/pdf`

### D4. PgVectorStore（向量入库与检索）
- Create: `.../rag/store/PgVectorStore.java`（构造注入 DataSource；`insertDoc(KbDocRecord)`/`insertChunks(docId, List<Chunk>, List<float[]> embeddings, tokenizer)`——embedding 字符串 `'[1.0,2.0]'::vector` 参数化 + tsv `to_tsvector('simple', ?)`；`searchByVector(float[] query, int topK)` 返回 (chunkId, docId, seq, content, distance)；`searchByFulltext(String tokenizedQuery, int topK)` `tsv @@ to_tsquery('simple', ?)`（查询词 AND 连接需分词处理：用 `plainto_tsquery('simple', ?)` 兼容）；deleteDoc/listDocs/getDoc；全量参数化 PreparedStatement）
- Test 先行（IT）：`it/PgVectorStoreIT`（Flyway V2 容器；灌 3 chunk 已知向量 → searchByVector 余弦最近正确排序；searchByFulltext 中文 bigram 命中；deleteDoc 级联）
- Commit: `feat(rag): pgvector store with parameterized jdbc`

### D5. HybridRetriever + RRF
- Create: `.../rag/search/RrfFusion.java`（纯函数：`List<Scored> fuse(List<Scored> vectorRanked, List<Scored> fulltextRanked, int topK, int k=60)`；RRF score=Σ 1/(k+rank)）、`search/HybridRetriever.java`（组合 PgVectorStore + EmbeddingProvider + tokenizer；`retrieve(query, topK)` → List<RetrievedChunk(docId, docTitle, seq, content, score, source: vector|fulltext|hybrid)>；topK 可配默认 5）
- Test 先行（单测）：`search/RrfFusionTest`（双榜都出现的排前；只在单榜按 RRF；topK 截断；空榜）
- Test 先行（IT）：`it/HybridRetrieverIT`（FakeEmbedding 确定性向量 + 已知语料：中文词面命中但向量错开时仍被融合召回）
- Create: `.../rag/embedding/FakeEmbeddingProvider.java`（测试/无 key 环境：`float[] embed(String)` 确定性 hash 向量 256 维，同词同向量）、`embedding/EmbeddingProvider.java`（接口：`float[] embed(String text)`、`List<float[]> embedAll(List<String>)`、`int dimension()`）
- Commit: `feat(rag): hybrid retrieval with rrf fusion`

### D6. RagService（编排 + 引用）
- Create: `.../rag/RagService.java`（`ingest(title, sourceType, InputStream/content, chunkSize, overlap)`：parse→chunk→embed→store，记录 doc；`retrieve(query, topK)`；`citation view`：RetrievedChunk 携带 docTitle/seq 供前端跳转）、`rag/RagIngestException`（→4001/4002）
- Test 先行（IT）：`it/RagServiceIT`（md 文档灌入 → chunk 数与 overlap 参数生效 → retrieve 引用含 docTitle/seq/content）
- Create: `.../rag/arch/RagArchTest.java`（不依赖 com.clawops 兄弟模块；不依赖 spring；public API 无 langchain4j 类型例外——EmbeddingProvider 是自研接口，rag 内不直接暴露 langchain4j 类型）
- Modify: `clawops-rag/pom.xml` jacoco 0.50→0.65
- Commit: `feat(rag): rag service orchestration with citations` + `test(rag): arch rules + coverage 0.65 (M2 freeze)`

## Phase E：gateway 集成（覆盖率最终 60%）

### E1. 装配配置
- Create: `clawops-gateway/.../gateway/rag/RagConfig.java`（@Bean RagService/PgVectorStore/HybridRetriever/EmbeddingProvider：有 CLAWOPS_LLM_API_KEY → LangChain4j OpenAiEmbeddingModel 包装 `Lc4jEmbeddingProvider`，否则 FakeEmbeddingProvider）、`gateway/orchestrator/OrchestratorConfig.java`（@Bean TaskRunner/JdbcCheckpointStore/ApprovalStore/AuditWriter/TaskStore——JPA repo 由 Spring 扫描：`@SpringBootApplication` 需扩 scanBasePackages 或 `@EnableJpaRepositories(basePackages="com.clawops.orchestrator")` + `@EntityScan`）
- Modify: `ClawOpsGatewayApplication`（scanBasePackages = com.clawops）
- Commit: `feat(gateway): assemble orchestrator and rag beans`

### E2. LangChain4j 模型适配（条件双轨）
- Create: `clawops-gateway/.../gateway/chat/Lc4jModelClient.java implements ModelClient`（AgentMessage↔langchain4j ChatMessage 转换；工具调用映射 ToolExecutionRequest→ToolCall）、`chat/ModelClientConfig.java`（@ConditionalOnProperty(clawops.llm.api-key) → OpenAiChatModel 包装；缺省 → DemoModelClient）
- Test 先行：`chat/Lc4jModelClientTest`（用 FakeChatModel（langchain4j，test-jar）作为底层 ChatModel：文本回复映射、toolCalls 映射、消息角色转换 round-trip）
- Commit: `feat(gateway): langchain4j model client adapter with conditional wiring`

### E3. 任务域 REST + SSE
- Create: `gateway/task/TaskController.java`（POST /api/v1/tasks、GET /tasks、GET /tasks/{id}、POST /tasks/{id}/stream（SSE，复用 SseEventBuilder + 新事件 approval_required）、POST /tasks/{id}/resume）、`task/TaskService.java`（薄封装 TaskRunner + SSE 订阅（运行中任务事件总线：`TaskEventBus` ConcurrentHashMap<taskId, SseEmitter list>；submit 时若任务运行中则挂 emitter；暂停事件 approval_required 推送）、`task/TaskDto`（view：id/status/prompt/result/steps/pauseReason/createdAt + steps 明细）
- Test 先行：`task/TaskControllerTest`（MockMvc + fake TaskService 或真 bean+stub TaskRunner：创建返回 202+taskId；列表分页；详情；resume 状态冲突→2002 错误体；404→1004）
- 错误码：+2002 TASK_NOT_RESUMABLE、2003 APPROVAL_STATE_CONFLICT、4002、4003（ApiErrorCode + error-codes.yaml 同步，ApiErrorCodeContractTest 自动校验）
- Commit: `feat(gateway): task REST endpoints with SSE and resume`

### E4. 审批 REST
- Create: `gateway/approval/ApprovalController.java`（GET /api/v1/approvals?status=、POST /api/v1/approvals/{id}/decision）、`approval/ApprovalService.java`（调 TaskRunner.decide；映射 ApprovalStateException→2003）
- Test 先行：`approval/ApprovalControllerTest`（pending 列表；approve→200；重复决策→2003 错误体；404）
- Commit: `feat(gateway): approval endpoints`

### E5. 知识库 REST
- Create: `gateway/kb/KbController.java`（POST /kb/documents multipart、GET /kb/documents、DELETE /kb/documents/{id}、POST /kb/search、POST /kb/ask SSE）、`kb/KbService.java`（上传→RagService.ingest（限制大小 ≤5MB、类型白名单）；search→retrieve；ask→AgentLoop + KnowledgeSearchTool SSE，done 后附加 citations）
- Test 先行：`kb/KbControllerTest`（上传非法类型→400/4001；上传成功→201 chunkCount；search 返回片段结构；删除 404→1004/4003）
- Commit: `feat(gateway): knowledge base endpoints`

### E6. knowledge.search 工具
- Create: `gateway/kb/KnowledgeSearchTool.java`（@Tool(name="knowledge.search", permission=READ)：`search(String query)`→RagService.retrieve top5 格式化为带引用文本）、Modify: `HarnessConfig` 注册
- Test 先行：`kb/KnowledgeSearchToolTest`（注入 stub RagService；输出含 docTitle/seq）
- Commit: `feat(gateway): knowledge.search tool wired into tool registry`

### E7. openapi.json 重新生成
- 跑 gateway `OpenApiContractIT` 或 springdoc 导出流程（读该 IT 确认生成方式），产物提交 `docs/openapi/openapi.json`
- Commit: `chore(openapi): regenerate contract for w2 endpoints`

### E8. 前端静态页
- Create: `static/tasks.html`（任务列表 + 详情抽屉：状态徽标、步骤时间线、PAUSED 显示恢复/审批入口、SSE 订阅按钮）、`static/kb.html`（文档列表、上传表单（文件+chunkSize+overlap）、检索测试框（query→召回片段卡片：docTitle#seq 高亮 + source/score）、问答框 SSE）
- Modify: `static/chat.html`（SSE 新增 approval_required 事件→审批卡片（批准/驳回按钮→POST decision）；导航链接 tasks/kb）
- 验证：手动打开三页走通（附录录屏脚本）
- Commit: `feat(console): task center, kb management and approval cards (static pages)`

### E9. gateway 门槛 + ArchUnit
- Create: `gateway/arch/GatewayArchTest.java`（依赖方向：仅 harness/connector/orchestrator/rag/workers + spring；禁止依赖 skills/mcp/cli）
- Modify: gateway jacoco 0.40→0.60
- Commit: `test(gateway): arch rules + coverage 0.60 (M2 freeze)`

## Phase F：评测、文档、收尾

### F1. 真实文档灌入
- 用本项目 docs/（TASK_BOOK.md、conventions/*.md、ADR-0*.md、PROGRESS.md）经 `/api/v1/kb/documents` 灌入（chunkSize=500, overlap=80 起步可调），记录文档数/切分参数/chunk 数 → `docs/metrics/w2-kb-ingest.md`

### F2. RAG 开/关评测（≥10 样本，真实 API）
- Create: `eval/w2-rag-eval.md`（10+ 条基于灌入文档的问答样本 + 判定标准）+ 评测执行记录（开 RAG：kb/ask 或对话 + knowledge.search；关 RAG：纯对话）
- 产出：`docs/metrics/w2-rag-comparison.md`（对比表：命中率、引用正确数、token 消耗；禁止估算，填实测）

### F3. 断点恢复验证记录
- 手动：起应用 → 提交含 DANGEROUS 工具任务 → 审批挂起 → `taskkill /F` 杀进程 → 重启 → resume → 续跑成功；记录步骤与 SQL 证据（task_step 序列）→ `docs/metrics/w2-resume-verification.md`

### F4. ADR 与规约更新
- Create: `docs/adr/ADR-004-pgvector-hybrid-retrieval.md`（自建 kb_chunk + 原生 SQL vs LangChain4j PgVectorEmbeddingStore：选自建，因混合检索/tsvector/引用溯源需要联合查询；HNSW cosine；不锁维度）
- Create: `docs/adr/ADR-008-approval-pause-resume-semantics.md`（trim 尾部未配对 assistant toolCalls + ApprovedCallGate 按 toolName+arguments 放行）
- Create: `docs/adr/ADR-009-rag-dependency-whitelist.md`（pdfbox 纳入 rag 第三方白名单；PG 驱动 test/runtime 由 gateway 提供）
- Modify: `docs/conventions/quality-gates.md` §2（gateway → +orchestrator, rag）+ `docs/versions.lock.md`（pdfbox 版本）

### F5. 进度文档
- Modify: `docs/PROGRESS.md`（W2 全部勾选 + commit/耗时、ADR 索引、每日日志、指标登记表 RAG 行、完成度汇总 M2）

### F6. 最终验证 + tag
- `.\mvnw.cmd -B -ntp verify` 全绿（含 IT、jacoco 最终门槛、SpotBugs、ArchUnit、MySQL grep）
- `git tag v0.2.0`

---

## 执行状态跟踪（每完成一个任务在此打勾并写 commit hash）

- [x] A1 (7bc8458) - [x] A2 (c010471) - [x] A3 (ed10a25)
- [x] B1 (7986740) - [x] B2 (c64f6b9) - [x] B3 (fddce76)
- [x] C1 (eeae54d) - [x] C2 (e65f…) - [x] C3 (6e09e47) - [x] C4 (5872200) - [x] C5 (4825181)
- [x] D1 (44a2e59) - [x] D2 (525baeb) - [x] D3 (0459b2f) - [x] D4 (c90f34e) - [x] D5 (e75cc15) - [x] D6
- [x] E1  - [ ] E2  - [ ] E3  - [ ] E4  - [ ] E5  - [ ] E6  - [ ] E7  - [ ] E8  - [ ] E9
- [ ] F1  - [ ] F2  - [ ] F3  - [ ] F4  - [ ] F5  - [ ] F6

## 环境备忘

- dev 基础设施：compose project `clawops-dev`，**PG 固定 5433 端口**（宿主机 5432 被 Windows 本机 PostgreSQL 占用，2026-10-20 记录于 PROGRESS 问题日志），凭据 clawops/clawops-dev/clawops；`.env` 已创建（不入库）
- Boot 4 模块化要点：Flyway 自动配置需 `spring-boot-starter-flyway`（仅加 flyway-core 不会触发自动配置）
- Windows 构建：`.\mvnw.cmd -B -ntp ...`；Spotless googleJavaFormat
- 集成测试需 Docker（已可用）；CI 用 GitHub Actions ubuntu runner
- LLM key 在 `.env`（不入库）；CI 无 key 走 Demo/Fake 路径
- 测试命名：`should<Expect>When<Condition>` + 中文 @DisplayName；IT 加 @Tag("it")；AssertJ；禁 Thread.sleep
