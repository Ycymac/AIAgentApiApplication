# Codex Process

> 本文档用于记录项目当前开发进度、Git 结构化日志、未提交变更、风险和下一步建议。**当前快照时间：2026-05-27。**

## 1. 当前状态摘要

| 项目项 | 当前状态 |
| --- | --- |
| 当前分支 | `rag`，跟踪 `origin/rag` |
| 最新提交 | `7bcd298`，2026-05-26，`rag-service_feature：修复了一系列问题` |
| 工作区状态 | 存在 5 个 unstaged 修改；无 staged、无 untracked |
| 文档状态 | 本文件已按当前工作区状态更新；`codex_coding_guidance.md` 已补充首包探测维护指引 |
| 验证状态 | 用户已手动验证首包检测机制可正常执行对话；`infrastructure-ai` 编译链路已通过一次跳过测试执行的 Maven 验证 |

### 状态颜色图例

| 状态 | 标识 | 含义 |
| --- | --- | --- |
| <span style="color:#2e7d32">已完成</span> | 已完成 | 功能已实现并经过基本验证。 |
| <span style="color:#ef6c00">开发中</span> | 开发中 | 功能仍在联调或补充验证中。 |
| <span style="color:#6a1b9a">未提交</span> | 未提交 | 当前工作区已有变更，但尚未进入 Git 提交历史。 |
| <span style="color:#616161">待确认</span> | 待确认 | 需要人工确认业务意图、环境或提交策略。 |
| <span style="color:#c62828">风险较高</span> | 风险较高 | 存在明显安全、配置、测试或提交风险。 |
| <span style="color:#1565c0">文档更新</span> | 文档更新 | 文档随当前代码状态同步调整。 |

## 2. 模块开发进度

| 模块 | 状态 | 已完成 | 未完成/待确认 | 未提交内容 | 风险 | 下一步 |
| --- | --- | --- | --- | --- | --- | --- |
| infrastructure-ai | <span style="color:#6a1b9a">未提交</span> | 首包探测由 `CountDownLatch + 多个 Atomic 状态` 改为 `CompletableFuture<Result>`；首个决定性事件 first-wins；用户已验证流式对话可正常执行 | Full test 仍受既有 `framework` 测试配置阻塞，需后续单独修复或排除 | `FirstPacketAwaiter.java` 修改 | 中 | 提交前复查首包探测 diff，并在测试策略明确后运行完整相关测试 |
| knowledge | <span style="color:#6a1b9a">未提交</span> | 当前配置文件已填入 OSS 与 AI provider 实际配置，便于本地联调 | 明文密钥不应直接提交到 Git 历史 | `knowledge/src/main/resources/application.yaml` 修改 | 高 | 提交前改回占位符或迁移到环境变量/本地私有配置 |
| rag | <span style="color:#6a1b9a">未提交</span> | 当前配置文件已填入 AI provider 实际配置，支持本地 RAG 对话联调 | 明文 API Key 不应直接提交到 Git 历史 | `rag/src/main/resources/application.yaml` 修改 | 高 | 提交前改回占位符或迁移到环境变量/本地私有配置 |
| docs | <span style="color:#1565c0">文档更新</span> | 进度文档已同步当前 Git 状态；指引文档已补充首包探测维护约束 | 后续代码或配置状态变化后需要继续同步 | `docs/codex_process.md`、`docs/codex_coding_guidance.md` 修改 | 低 | 提交前确认文档快照与最终 diff 一致 |

## 3. Git 进度日志摘要

| 时间 | 提交/变更 | 涉及模块 | 进度类型 | 内容摘要 |
| --- | --- | --- | --- | --- |
| 2026-05-27 | working tree | infrastructure-ai/knowledge/rag/docs | <span style="color:#6a1b9a">未提交</span> | 首包探测改为 `CompletableFuture<Result>`；本地配置填入实际密钥；进度和指引文档同步更新。 |
| 2026-05-26 | `7bcd298` | rag-service | <span style="color:#2e7d32">功能开发</span> | 修复 RAG service 相关一系列问题。 |

## 4. 未提交变更

### staged 变更

当前无 staged 变更。

### unstaged 变更

| 模块 | 文件 | 变更类型 | 进度判断 |
| --- | --- | --- | --- |
| infrastructure-ai | `infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/FirstPacketAwaiter.java` | M | <span style="color:#6a1b9a">未提交</span>：首包探测结果改由 `CompletableFuture<Result>` 承载，避免多个原子变量组合状态引发的并发判定不一致 |
| knowledge | `knowledge/src/main/resources/application.yaml` | M | <span style="color:#c62828">风险较高</span>：本地联调配置包含实际 OSS 与模型服务凭据，提交前必须脱敏 |
| rag | `rag/src/main/resources/application.yaml` | M | <span style="color:#c62828">风险较高</span>：本地联调配置包含实际模型服务凭据，提交前必须脱敏 |
| docs | `docs/codex_process.md` | M | <span style="color:#1565c0">文档更新</span>：根据当前工作区、最新提交、验证结果和风险项更新进度快照 |
| docs | `docs/codex_coding_guidance.md` | M | <span style="color:#1565c0">文档更新</span>：补充 `FirstPacketAwaiter` 的 `CompletableFuture<Result>` first-wins 维护指引 |

### untracked 变更

当前无 untracked 变更。

## 5. 已完成功能

- **AI 基础设施**：已有 LLM、Embedding、Rerank、多模型路由、模型健康状态、OpenAI 风格 SSE 解析、流式取消句柄等能力。
- **首包探测机制**：`FirstPacketAwaiter` 当前使用 `CompletableFuture<Result>` 表达一次性探测结果；`markContent`、`markComplete`、`markError` 均通过 `complete` 固化首个决定性事件，后续回调不会覆盖首包判定。
- **RAG 流式对话验证**：用户已确认当前首包检测机制可正常使用，能够正常执行对话。

## 6. 开发中功能

- **配置安全收敛**：`knowledge` 与 `rag` 配置中存在用于本地联调的真实凭据，需要在提交前恢复为占位符或迁移为环境变量。
- **测试基线修复**：`mvn -pl infrastructure-ai -am test` 会在既有 `framework` 测试 `FrameworkApplicationTests` 处失败，原因是缺少 `@SpringBootConfiguration`；该问题与首包探测改动无直接关系。

## 7. 待确认事项与风险

| 风险 | 等级 | 说明 | 建议 |
| --- | --- | --- | --- |
| 明文凭据 | <span style="color:#c62828">风险较高</span> | `knowledge/src/main/resources/application.yaml` 和 `rag/src/main/resources/application.yaml` 当前包含实际 OSS/API 配置。 | 禁止直接提交真实凭据；提交前改回 `needed`、环境变量占位符或拆分本地私有配置。 |
| 测试状态 | <span style="color:#616161">待确认</span> | 跳过测试执行的编译链路已通过；完整测试受既有 `framework` 测试配置阻塞。 | 修复或排除既有空 SpringBootTest 后再运行完整测试。 |
| 首包探测并发语义 | <span style="color:#2e7d32">已完成</span> | `CompletableFuture.complete` 具备 first-wins 语义，匹配首包探测只采纳第一个决定性事件的需求。 | 后续不要再拆回多个独立 `AtomicBoolean/AtomicReference` 组合状态。 |

## 8. 下一步建议

1. **提交前先处理凭据**：优先脱敏 `knowledge` 与 `rag` 的 `application.yaml`。
2. **保留首包探测提交边界**：首包探测代码变更集中在 `FirstPacketAwaiter.java`，适合作为独立提交。
3. **补完整测试基线**：修复 `FrameworkApplicationTests` 的 Spring Boot 配置问题后，再运行 `mvn -pl infrastructure-ai -am test`。
4. **提交前检查**：执行 `git status --short` 与 `git diff --check`，确认没有误提交密钥、生成产物或临时调试文件。

## 9. 变更记录

| 时间 | 类型 | 内容 |
| --- | --- | --- |
| 2026-05-27 | <span style="color:#1565c0">文档更新</span> | 根据当前工作区更新进度快照；记录首包探测 `CompletableFuture<Result>` 改造、用户对话验证结果、配置凭据风险和测试基线状态。 |
| 2026-05-26 | <span style="color:#1565c0">文档更新</span> | 创建 `codex_coding_guidance.md` 和 `codex_process.md`，纳入接口、类指引、开发约束、Git 进度和未提交状态。 |
