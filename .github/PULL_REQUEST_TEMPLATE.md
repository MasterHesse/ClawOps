# PR 模板（合并门禁清单见 docs/conventions/quality-gates.md §4，逐项自查）

## 变更说明

<!-- 做了什么、为什么；一次 PR 一个可验证单元 -->

## 关联

- 关联 Issue：#
- 关联 ADR（若偏离任务书）：ADR-___

## 合并门禁自查

- [ ] CI 全绿（Job 1–5：format-lint / build-test / integration / arch-coverage / mysql-guard）
- [ ] 新增 / 变更行为有对应测试；核心模块（testing.md §3 范围）测试先行
- [ ] 模块覆盖率不低于当前门槛（conventions/testing.md §4 矩阵）
- [ ] 涉及接口变更：openapi.json 已重新生成并有意提交（conventions/api.md §6）
- [ ] 涉及 prompt 变更：已在 PR 描述注明（conventions/llm.md 待补）
- [ ] AI 辅助生成：body 注明 `Assisted-by: <tool>`，人工 Review 修改点已记录到 docs/ai-log/
- [ ] 偏离任务书的决定：ADR 已登记，PR 关联 ADR 编号

## 提交约定

- Conventional Commits：feat | fix | docs | test | chore | refactor
- AI 辅助生成的提交在 body 注明 `Assisted-by: <tool>`
