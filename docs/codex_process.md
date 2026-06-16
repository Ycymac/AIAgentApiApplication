# Codex Process

> 本文档用于记录项目当前开发进度、Git 结构化日志、未提交变更、风险和下一步建议。**当前快照时间：2026-06-16。**

## 1. 当前状态摘要

| 项目项 | 当前状态 |
| --- | --- |
| 当前分支 | `rag`，跟踪 `origin/rag` |
| 最新提交 | `6901179`，2026-06-02，`rag-service_feature：优化模型调用提示词、确保 rerank 被调用、修复配置解析与写死问题` |
| 工作区状态 | 15 个 staged（RAG 评测模块，含**检索旁路 + 流式评测 chat-stream + chunk 数据集生成**）+ `docs/CLAUDE.md` + `WebConfiguration` 放行 `/api/rag/eval/**`；unstaged 修改：`ConversationMemorySummaryServiceImpl`（摘要失败/缺历史改为返回空）、`knowledge`/`rag` 的 `application.yaml`（含真实凭据）、`.gitignore` |
| 文档状态 | 本文件已同步至当前工作区；`codex_coding_guidance.md` 待补流式评测与 chunk probe 子模块；`docs/CLAUDE.md` 为入口指南。配套**外部 Python 评测工具** `D:\PythonProjects\aiapplication-rag-eval`，报告输出至 `docs/javis_test_report` |
| 验证状态 | 评测模块已端到端跑通：2026-06-16 用外部工具对真实历史问题与 chunk 集做了检索/流式/对照评测，产出 HTML 报告。**实测关键结论见 §10** |

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
| infrastructure-ai | <span style="color:#2e7d32">已完成</span> | 首包探测 `CompletableFuture<Result>` 改造已提交（`d16c758`）；将 `BaiLian`/`SiliconFlow` 等重复聊天客户端整合为统一 `OpenAIStyleChatClient`，降级兜底改为跨平台兜底模型列表（`86e1d45`） | 统一客户端 + 跨平台兜底链路缺少自动化测试覆盖 | 无（均已提交） | 低 | 在评测模块或集成环境补一次端到端降级/兜底验证 |
| rag | <span style="color:#6a1b9a">未提交</span> | 已提交：优化模型调用提示词、确保 rerank 被调用、修复配置解析异常与写死问题（`6901179`）；问题重写宽松兜底解析（`d16c758`）。新增：RAG 检索评测/可观测模块（`eval` 子包，属性开关 `app.rag-eval.enabled` 控制） | 评测模块尚未提交；`app.rag-eval` 配置块未写入 yaml，默认关闭 | `rag/.../eval/**` 7 个新文件、`rag/.../config/WebConfiguration.java`（放行 `/api/rag/eval/**`） | 低 | 评测模块作为独立提交；如需启用补 `app.rag-eval.enabled`/`log-path` 配置 |
| knowledge | <span style="color:#c62828">风险较高</span> | 配置文件保留本地联调能力 | 当前 `application.yaml` 填入真实 OSS 与 AI provider 凭据 | `knowledge/src/main/resources/application.yaml` 修改 | 高 | 提交前改回占位符或迁移到环境变量；视已暴露密钥为需轮换 |
| rag（配置） | <span style="color:#c62828">风险较高</span> | 配置文件支持本地 RAG 对话联调 | 当前 `application.yaml` 填入真实 AI provider 凭据 | `rag/src/main/resources/application.yaml` 修改 | 高 | 提交前改回占位符或迁移到环境变量；视已暴露密钥为需轮换 |
| docs | <span style="color:#1565c0">文档更新</span> | `docs/CLAUDE.md` 新增；进度与指引文档同步当前状态 | 后续代码或配置变化后继续同步 | `docs/CLAUDE.md`（新增，staged）、`docs/codex_process.md`、`docs/codex_coding_guidance.md` | 低 | 提交前确认文档快照与最终 diff 一致 |

## 3. Git 进度日志摘要

| 时间 | 提交/变更 | 涉及模块 | 进度类型 | 内容摘要 |
| --- | --- | --- | --- | --- |
| 2026-06-03 | working tree | rag/knowledge/docs | <span style="color:#6a1b9a">未提交</span> | 新增 RAG 检索评测模块（`eval` 子包，含 Controller/Service/DTO/Trace），`WebConfiguration` 放行 `/api/rag/eval/**`，新增 `docs/CLAUDE.md`；两份 `application.yaml` 填入真实凭据待脱敏。 |
| 2026-06-02 | `6901179` | rag-service | <span style="color:#2e7d32">功能开发</span> | 优化模型调用提示词缓解上下文影响；优化检索逻辑确保 rerank 被调用；修复配置解析异常与配置写死问题。 |
| 2026-05-30 | `86e1d45` | infrastructure-ai | <span style="color:#2e7d32">功能开发</span> | 将重复聊天客户端整合为统一 `OpenAIStyleChatClient`；降级策略改用各平台兜底模型构建兜底列表，提升整体可用性。 |
| 2026-05-27 | `d16c758` | rag/infrastructure-ai | <span style="color:#2e7d32">功能开发</span> | 首包探测改用 `CompletableFuture` 统一语义并简化代码；问题重写解析采用宽松模式兜底；补充日志提升可观测性。 |
| 2026-05-26 | `7bcd298` | rag-service | <span style="color:#2e7d32">功能开发</span> | 修复 RAG service 相关一系列问题。 |

## 4. 未提交变更

### staged 变更

| 模块 | 文件 | 变更类型 | 进度判断 |
| --- | --- | --- | --- |
| docs | `docs/CLAUDE.md` | A | <span style="color:#1565c0">文档更新</span>：新增 Claude 工作入口指南 |
| rag | `rag/.../rag/config/WebConfiguration.java` | M | <span style="color:#2e7d32">已完成</span>：JWT 拦截器放行 `/api/rag/eval/**`，便于评测脚本免登录访问 |
| rag | `rag/.../rag/eval/controller/RagEvalController.java` | A | <span style="color:#2e7d32">已完成</span>：暴露 `GET /api/rag/eval/retrieve` 评测入口，属性开关 `app.rag-eval.enabled=true` 才注册 |
| rag | `rag/.../rag/eval/service/RagEvalService.java` | A | <span style="color:#2e7d32">已完成</span>：评测服务接口 |
| rag | `rag/.../rag/eval/service/impl/RagEvalServiceImpl.java` | A | <span style="color:#2e7d32">已完成</span>：复跑「重写→意图→引导→检索→Prompt」全链路（不做 LLM 生成），输出结构化诊断 |
| rag | `rag/.../rag/eval/dto/RagEvalResponse.java` | A | <span style="color:#2e7d32">已完成</span>：评测响应 record（命中文档/chunk/意图/通道/各阶段耗时） |
| rag | `rag/.../rag/eval/trace/RagEvalTraceContext.java` | A | <span style="color:#2e7d32">已完成</span>：ThreadLocal 评测追踪上下文（traceId/runId/queryId） |
| rag | `rag/.../rag/eval/trace/RagEvalTraceWriter.java` | A | <span style="color:#2e7d32">已完成</span>：将评测事件写入 JSONL（`app.rag-eval.log-path`，默认 `logs/rag-eval.jsonl`） |
| rag | `rag/.../rag/eval/trace/RagEvalRetrievalAspect.java` | A | <span style="color:#2e7d32">已完成</span>：AOP 环绕检索通道与 rerank，记录耗时与重排前后排名变化 |

### unstaged 变更

| 模块 | 文件 | 变更类型 | 进度判断 |
| --- | --- | --- | --- |
| knowledge | `knowledge/src/main/resources/application.yaml` | M | <span style="color:#c62828">风险较高</span>：含真实 OSS（endpoint/accessKeyId/secret/bucket）与百炼、硅基流动 API Key，提交前必须脱敏 |
| rag | `rag/src/main/resources/application.yaml` | M | <span style="color:#c62828">风险较高</span>：含真实百炼、硅基流动 API Key，提交前必须脱敏 |

### untracked 变更

当前无 untracked 变更。

## 5. 已完成功能

- **AI 基础设施**：LLM、Embedding、Rerank、多模型路由、模型健康状态、OpenAI 风格 SSE 解析、流式取消句柄等能力齐备。
- **首包探测机制**：`FirstPacketAwaiter` 使用 `CompletableFuture<Result>` 表达一次性探测结果，`markContent`/`markComplete`/`markError` 通过 `complete` 固化首个决定性事件（first-wins）；改造已提交并经用户对话验证。
- **统一聊天客户端与降级兜底**：移除 `BaiLianChatClient`/`SiliconFlowChatClient`，由 `OpenAIStyleChatClient` 统一承载 OpenAI 风格调用；`RoutingLLMService` 的降级不再局限当前平台配置，改用各平台兜底模型构建兜底列表。
- **RAG 回答质量优化**：优化模型调用提示词缓解上下文干扰，调整检索逻辑确保 rerank 被实际调用，修复配置解析异常与写死问题。
- **RAG 检索评测/可观测模块（待提交）**：`eval` 子包提供免 LLM 生成的检索全链路复跑与 JSONL 追踪，默认随 `app.rag-eval.enabled` 关闭，不影响线上链路。

## 6. 开发中功能

- **配置安全收敛**：`knowledge` 与 `rag` 的 `application.yaml` 含真实凭据，需在提交前恢复占位符或迁移环境变量。
- **评测模块落地**：`eval` 子包代码已就绪但未提交，且 yaml 中未写入 `app.rag-eval` 配置块；需补配置并作独立提交，启用后跑通一次评测。
- **测试基线修复**：`mvn -pl infrastructure-ai -am test` 仍会在既有 `FrameworkApplicationTests`（缺少 `@SpringBootConfiguration`）处失败，与功能改动无关。

## 7. 待确认事项与风险

| 风险 | 等级 | 说明 | 建议 |
| --- | --- | --- | --- |
| 明文凭据 | <span style="color:#c62828">风险较高</span> | `knowledge`/`rag` 的 `application.yaml` 当前包含真实 OSS 与 API Key。 | 禁止提交真实凭据；提交前改回占位符或环境变量；鉴于密钥已出现在本地工作区，建议轮换。 |
| 评测入口鉴权放行 | <span style="color:#616161">待确认</span> | `WebConfiguration` 放行 `/api/rag/eval/**`，且评测接口免登录。 | 仅在 `app.rag-eval.enabled=true` 时注册，生产环境务必保持关闭，避免检索内容外泄。 |
| 测试状态 | <span style="color:#616161">待确认</span> | 完整测试受既有空 `FrameworkApplicationTests` 阻塞。 | 修复或排除该测试后再运行完整测试。 |
| 首包探测并发语义 | <span style="color:#2e7d32">已完成</span> | `CompletableFuture.complete` 具备 first-wins 语义，已提交。 | 不要再拆回多个独立 `AtomicBoolean/AtomicReference` 组合状态。 |

## 8. 下一步建议

1. **提交前先处理凭据**：优先脱敏 `knowledge` 与 `rag` 的 `application.yaml`，并轮换已暴露的密钥。
2. **评测模块独立提交**：`eval` 子包 + `WebConfiguration` 放行可作为一次独立提交；如需启用补 `app.rag-eval.enabled`、`log-path` 等配置，并确认生产关闭。
3. **补降级/兜底验证**：借助评测模块或集成环境，核验统一聊天客户端与跨平台兜底链路、rerank 强制调用是否符合预期。
4. **补完整测试基线**：修复 `FrameworkApplicationTests` 的 Spring Boot 配置问题后再运行 `mvn -pl infrastructure-ai -am test`。
5. **提交前检查**：执行 `git status --short` 与 `git diff --check`，确认无误提交密钥、生成产物或临时调试文件。

## 9. 变更记录

| 时间 | 类型 | 内容 |
| --- | --- | --- |
| 2026-06-16 | <span style="color:#1565c0">文档更新</span> | 同步评测模块扩充（流式 chat-stream + chunk probe）、`ConversationMemorySummaryServiceImpl` 改动、外部 Python 评测工具与实测结论（§10）。 |
| 2026-06-03 | <span style="color:#1565c0">文档更新</span> | 同步至 `6901179` 与当前工作区：记录统一聊天客户端、降级兜底、rerank 强制调用、RAG 评测模块（待提交）、`docs/CLAUDE.md` 新增及凭据风险。 |
| 2026-05-27 | <span style="color:#1565c0">文档更新</span> | 根据当时工作区更新进度快照；记录首包探测 `CompletableFuture<Result>` 改造、对话验证结果、配置凭据风险与测试基线状态。 |
| 2026-05-26 | <span style="color:#1565c0">文档更新</span> | 创建 `codex_coding_guidance.md` 和 `codex_process.md`，纳入接口、类指引、开发约束、Git 进度与未提交状态。 |

## 10. RAG 评测实测结论（2026-06-16）

由外部 Python 评测工具（`D:\PythonProjects\aiapplication-rag-eval`）对真实历史问题与生成 chunk 集做检索/流式/生产对照评测，关键结论如下。**以下为评测发现，待 Java 侧后续优化，本轮未改业务逻辑。**

### 10.1 路由不稳定（最值得修）

用 `conversation_message` 中 23 条真实历史问题（6.2 前）分场景评测：

| 场景 | 样本 | 路由准确率 |
| --- | --- | --- |
| 闲聊 simple_chat | 3 | 100% |
| 简单通用 simple_general | 6 | 83% |
| 知识库事实 kb_fact | 11 | **54.5%** |

- 近义问题路由相反：「请介绍java面向对象三大特性」→ SYSTEM（跳检索），「详细介绍一下Java面向对象」→ KB。
- kb_fact 一半被误判为 SYSTEM 直答，未走知识库检索。
- **这解释了"简单问题体感快"**：被判 SYSTEM 的问题约 2.5s 返回，走 KB 的同类问题 6–9s。意图/路由判定的稳定性是后续优化重点。

### 10.2 首包延迟构成

- 真实问题首包（首个 SSE 事件）分布：min 2.9s / 中位 6.7s / p95 9.1s / max 17.2s，**无 1s 级**。
- 首字前 rewrite + intent 两个 LLM 串行调用占大头（中位约 5s），检索仅约 1.4s。压首包应优先优化这两步（更小模型 / 并行 / 缓存），而非检索。

### 10.3 检索质量

- 文档级召回健康（生成 chunk 集 doc 命中约 97%）；chunk 级命中低主要源于评测标尺（单 chunk 过严 + 机器出题错配），**非召回策略问题**。
- 现知识库 `JAVASE学习笔记.pdf` 以代码为主，分块后多为代码片段，对概念性问题召回相关性弱——知识库内容形态本身影响问答质量。
- `RetrievalEngine` 中 rerank 不裁剪（topN=候选全量），影响的是 Prompt 噪声与首包延迟，不影响命中率。可考虑通道化收缩 topK 以降噪提速。

### 10.4 评测链路与生产链路差异

- 评测端点 `/rag/eval/chat-stream` 缺会话记忆加载与 history 注入 Prompt，是生产 `/rag/v3/chat` 的单轮简化版。
- 单轮场景两者延迟接近（首包差值中位约 +0.6s，完成差值中位约 +1.5s，生产略慢）；多轮上下文差异评测端点暂不覆盖。
