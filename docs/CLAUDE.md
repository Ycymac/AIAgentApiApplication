# Claude 开发指引

> 本文件是 Claude 在本仓库工作的入口指南，基于 `docs/codex_coding_guidance.md`（接口/类/方法参考）与 `docs/codex_process.md`（进度与风险快照）提炼。**开始任何开发前，请先读完本文件，并按需查阅上述两份 Codex 文档。**

## 1. 文档导航

| 文档 | 用途 | 何时查阅 |
| --- | --- | --- |
| `docs/CLAUDE.md`（本文件） | Claude 工作总览、核心约定、协作流程 | 每次开始任务前 |
| `docs/codex_coding_guidance.md` | 全量接口清单、模块/类/方法逐项参考 | 定位类、方法、HTTP 接口边界时 |
| `docs/codex_process.md` | 开发进度、Git 日志、未提交变更、风险与下一步 | 提交前、了解当前状态时 |
| `docs/memo/memo_index.md` | Codex / Claude Code 跨 agent 轻量记忆索引 | 任务可能涉及历史决策、未完成链路或跨会话交接时先读索引 |

> 维护约定：当代码、接口或进度发生变化时，应同步更新对应的 Codex 文档；本文件随项目结构性变化更新。

## 2. 项目概览

| 项目项 | 内容 |
| --- | --- |
| 技术栈 | Java 17、Spring Boot 3、多模块 Maven 项目 |
| 父工程 | `AIApplication`，根 `pom.xml` 聚合 6 个模块 |
| 业务定位 | AI 面试 + RAG 知识问答平台（用户、面试 Agent、知识库、AI 基础设施、RAG 问答） |
| 统一返回 | 多数接口返回 `Result<T>`；RAG 流式问答返回 `SseEmitter` |

## 3. 模块架构

| 模块 | 主要职责 |
| --- | --- |
| `user-service` | 用户注册/登录/登出、JWT 校验、管理员权限拦截、简历表单管理 |
| `agent` | AI 面试题生成、答案评估、报告生成、面试记录访问 |
| `framework` | 统一响应、异常体系、用户上下文、幂等切面、SSE 发送、MQ 基础对象 |
| `knowledge` | 知识库/文档/Chunk 管理，文档解析、分块、向量写入与异步分块消息 |
| `infrastructure-ai` | LLM、Embedding、Rerank、模型路由、模型健康、OpenAI 风格 SSE 解析与 HTTP 调用 |
| `rag` | RAG 流式问答、会话记忆、问题改写、意图识别、检索通道、提示词与会话管理 |

> 各模块的具体类清单、HTTP 接口和方法签名见 `docs/codex_coding_guidance.md` 第 3、4、5 节。

## 4. 代码约定

- **包根**：`com.ycy.aiapplication`，子模块在其下分包（如 `rag`、`knowledge`、`infrastructure.ai`）。
- **分层命名**：`controller` 入口、`service`/`service.impl` 业务、`dao.mapper` + `dao.entity`（`*DO`）数据访问、`dto`(`*ReqDTO`/`*RespDTO`)、`vo`(`*VO`)、`common/pojo`、`config`、`toolkit`/`util`、`common/constant`、`common/enums`、`exception`。
- **返回与异常**：统一用 `Result`/`Results` 包装；异常走 `framework` 的 `GlobalExceptionHandler` 与 `AbstractException` 体系（`ClientException`/`ServiceException`/`RemoteException`）。
- **幂等**：通过 `@IdempotentSubmit` / `@IdempotentConsume` 注解 + 对应切面实现，写接口标注幂等控制。
- **新增功能时**：复用既有分层与命名，新增类后请在 `codex_coding_guidance.md` 对应模块小节登记。

## 5. 关键维护约束

> 这些是已验证的设计决定，改动前务必理解原因，不要回退。

- **首包探测（`infrastructure-ai/.../FirstPacketAwaiter.java`）**：探测结果由 `CompletableFuture<Result>` 承载，`markContent`/`markComplete`/`markError` 均通过 `complete` 固化首个决定性事件（first-wins）。**不要拆回多个独立 `AtomicBoolean/AtomicReference` 组合状态。**
- **凭据安全（高风险）**：`knowledge/src/main/resources/application.yaml` 与 `rag/src/main/resources/application.yaml` 在本地联调时可能含真实 OSS/API Key。**禁止提交真实凭据**，提交前改回占位符或迁移到环境变量/本地私有配置。
- **测试基线**：`mvn -pl infrastructure-ai -am test` 当前会因既有 `FrameworkApplicationTests` 缺少 `@SpringBootConfiguration` 而失败，与功能改动无关；需要完整测试时先修复或排除该测试。

## 6. 协作流程建议

1. **任务开始**：读本文件 → 若任务可能依赖历史上下文，先读 `docs/memo/memo_index.md`，只打开标题/关键词/Use when 明确匹配的 1-2 个 memo → 用 `codex_coding_guidance.md` 定位涉及的模块/类/接口 → 用 `codex_process.md` 确认当前进度与风险。
2. **开发中**：遵循既有分层、命名与 `Result`/异常/幂等约定；最小化跨模块改动。
3. **提交前**：
   - 执行 `git status --short` 与 `git diff --check`，确认无误提交的密钥、生成产物或临时调试文件；
   - 对 `application.yaml` 凭据脱敏；
   - 同步更新相关 Codex 文档与（如有结构性变化）本文件。
4. **验证**：能编译/局部测试的改动尽量本地验证；受既有测试配置阻塞时，记录在 `codex_process.md` 的风险/待确认项中。

## 7. 跨 agent 记忆约定

`docs/memo` 只作为轻量交接层，不作为聊天记录归档。目标是让 Codex 与 Claude Code 复用关键事实，同时避免默认消耗大量上下文。

- 默认只读 `docs/memo/memo_index.md`；不要全文读取所有 memo。
- 仅当 memo 的标题、关键词或 `Use when` 与当前任务直接匹配时，才读取具体 memo。
- 每个具体 memo 顶部应维护 `Agent Handoff Summary`，让后续 agent 优先只读开头即可继续。
- memo 更新采用事件触发：有可复用的决策、验证命令、产出文件、风险或下一步时再更新；不要因固定轮数机械更新。
- 新建 memo 时同步更新 `docs/memo/memo_index.md`。
- 禁止写入密钥、token、cookie、密码、私钥、Authorization header 或 credential-like 配置值。
