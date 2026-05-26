# Codex Process

> 本文档用于记录项目当前开发进度、Git 结构化日志、未提交变更、风险和下一步建议。**当前快照时间：2026-05-26。**

## 1. 当前状态摘要

| 项目项 | 当前状态 |
| --- | --- |
| 当前分支 | `rag` |
| 最新提交 | `0112f5a`，2026-04-20，`user-service_feature：修改用户登录、登出以及jwt生成逻辑` |
| 工作区状态 | 存在 staged、unstaged、untracked 变更，需按 **未提交** 处理 |
| 文档状态 | `docs/codex_coding_guidance.md` 与 `docs/codex_process.md` 已按当前规范生成 |
| 生成产物处理 | `generated-pets/` 属于未跟踪生成产物，未纳入代码/进度统计正文 |

### 状态颜色图例

| 状态 | 标识 | 含义 |
| --- | --- | --- |
| <span style="color:#2e7d32">已完成</span> | 已完成 | 用于标记当前进度。 |
| <span style="color:#ef6c00">开发中</span> | 开发中 | 用于标记当前进度。 |
| <span style="color:#6a1b9a">未提交</span> | 未提交 | 当前工作区已有变更，但尚未进入 Git 提交历史。 |
| <span style="color:#616161">待确认</span> | 待确认 | 代码无法完全确认业务意图，需要人工确认。 |
| <span style="color:#c62828">风险较高</span> | 风险较高 | 存在明显依赖、测试、接口或状态不一致风险。 |
| <span style="color:#1565c0">文档更新</span> | 文档更新 | 用于标记当前进度。 |

## 2. 模块开发进度

| 模块 | 状态 | 已完成 | 未完成/待确认 | 未提交内容 | 风险 | 下一步 |
| --- | --- | --- | --- | --- | --- | --- |
| user-service | <span style="color:#2e7d32">已完成</span> | 登录、登出、注册、JWT、权限字段返回、简历 CRUD 接口已具备 | 真实鉴权策略与前端 token 约定仍需联调确认 | 当前未见 user-service 未提交文件 | 中 | 联调登录态、管理员权限和简历接口 |
| agent | <span style="color:#6a1b9a">未提交</span> | 面试问题生成、答案评估、报告生成、记录查询链路已具备 | `AgentAskImpl` 体量大，报告生成和模型调用稳定性需验证 | `ReportGenerationReqDTO`、`AgentAskImpl` staged | 高 | 补充核心链路测试，确认报告生成输入输出结构 |
| framework | <span style="color:#6a1b9a">未提交</span> | 统一响应、异常、用户上下文、幂等切面、SSE 工具已具备 | staged 与 unstaged 同时存在，需确认本轮框架变更边界 | `IdempotentSubmitAspect` staged；`framework/pom.xml`、`UserContext` unstaged | 高 | 确认幂等 key、用户上下文依赖和新增依赖是否一致 |
| knowledge | <span style="color:#6a1b9a">未提交</span> | 知识库、文档、Chunk、解析、向量写入、异步分块链路基本成型 | 分块一致性、XML mapper、配置和异步消费仍需测试确认 | 多个 controller/service/mapper/config staged，新增 mapper XML 与 embedding 集成测试 | 高 | 运行 knowledge 相关测试，确认文档上传、分块、向量重建 |
| infrastructure-ai | <span style="color:#6a1b9a">未提交</span> | LLM、Embedding、Rerank、模型路由、健康状态、SSE 解析能力已具备 | 多模型配置、百炼 embedding 变更未提交，需验证实际调用 | `AIModelProperties`、`BaiLianEmbeddingClient` staged | 中 | 验证模型配置兼容性和 embedding 维度一致性 |
| rag | <span style="color:#6a1b9a">未提交</span> | RAG 聊天、检索、意图、记忆、提示词、会话和意图节点管理已具备 | staged/unstaged 交叉，检索通道、意图节点和提示词仍需联调 | 检索配置、`IntentNode`、检索通道 staged；`IntentNodeManageService`、`StreamChatEventHandler`、prompt unstaged | 高 | 分阶段提交或拆分验证 RAG 检索、意图和流式输出 |

## 3. Git 进度日志摘要

| 时间 | 提交/变更 | 涉及模块 | 进度类型 | 内容摘要 |
| --- | --- | --- | --- | --- |
| 2026-04-20 | `0112f5a` | user-service | <span style="color:#2e7d32">功能开发</span> | 修改用户登录、登出以及 JWT 生成逻辑。 |
| 2026-04-20 | `936f856` | rag/user-service/framework | <span style="color:#2e7d32">功能开发</span> | 完成管理员接口鉴权和用户信息权限字段返回。 |
| 2026-04-15 | `6578e80` | knowledge/rag | <span style="color:#c62828">问题修复</span> | 修复文件上传、知识库问答相关问题。 |
| 2026-04-15 | `dbd5ba4` | knowledge/rag/infrastructure-ai | <span style="color:#c62828">问题修复</span> | 修复配置类冲突并提升 LLM 对话响应性能。 |
| 2026-04-15 | `efb68c4` | rag | <span style="color:#2e7d32">功能开发</span> | 新增意图识别节点管理，并为模块增加 CORS 配置。 |
| 2026-04-14 | `d086d5f` | knowledge/rag | <span style="color:#c62828">问题修复</span> | 补齐配置、包扫描、mapper 扫描和拦截器，修复测试前问题。 |
| 2026-04-14 | `bb2b6db` | rag | <span style="color:#2e7d32">功能开发</span> | 完成 RAG 聊天基础控制层构建。 |
| 2026-04-14 | `419550d` | rag | <span style="color:#2e7d32">功能开发</span> | 完成引导式回答构建和 RAG 聊天服务实现。 |
| 2026-04-13 | `5dd4728` | rag/framework | <span style="color:#2e7d32">功能开发</span> | 完成流式返回逻辑构建。 |
| 2026-04-13 | `89e3aeb` | rag/knowledge | <span style="color:#2e7d32">功能开发</span> | 完成 RAG 检索引擎相关构建。 |
| 2026-04-12 | `cd640c4` | rag | <span style="color:#2e7d32">功能开发</span> | 完成 RAG 记忆链路构建。 |
| 2026-04-09 | `af821d5` | rag/knowledge | <span style="color:#2e7d32">功能开发</span> | 完成意图层级判断识别，增加知识库节点 Redis 缓存。 |
| 2026-04-09 | `ae343a5` | rag | <span style="color:#2e7d32">功能开发</span> | 完成 RAG 系统提示词构建服务。 |
| 2026-04-08 | `32cae33` | rag | <span style="color:#2e7d32">功能开发</span> | 完成用户问题重写链路。 |
| 2026-04-08 | `427c079` | rag | <span style="color:#00838f">结构调整</span> | 集中 RAG 配置，重命名配置解析类，添加提示词模板。 |
| 2026-04-07 | `0dad1bd` | infrastructure-ai/agent | <span style="color:#2e7d32">功能开发</span> | 完成聊天链路。 |
| 2026-04-07 | `4fab4f6` | knowledge | <span style="color:#c62828">问题修复</span> | 完善知识库删除逻辑，同步删除向量数据库 collection。 |
| 2026-04-07 | `4840b50` | knowledge | <span style="color:#2e7d32">功能开发</span> | 知识库创建时同步创建向量知识库 collection。 |
| 2026-04-07 | `6c49d4c` | infrastructure-ai | <span style="color:#00838f">结构调整</span> | 将大模型配置统一到 AI 基础架构模块。 |

## 4. 未提交变更

### staged 变更

| 模块 | 文件 | 变更类型 | 进度判断 |
| --- | --- | --- | --- |
| agent | `agent/src/main/java/com/ycy/aiapplication/dto/req/ReportGenerationReqDTO.java` | M | <span style="color:#6a1b9a">未提交</span>：报告生成请求结构调整 |
| agent | `agent/src/main/java/com/ycy/aiapplication/service/Impl/AgentAskImpl.java` | M | <span style="color:#6a1b9a">未提交</span>：面试 Agent 核心逻辑大幅调整 |
| framework | `framework/src/main/java/com/ycy/aiapplication/framework/idempotent/aspects/IdempotentSubmitAspect.java` | M | <span style="color:#6a1b9a">未提交</span>：幂等切面调整 |
| infrastructure-ai | `infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/config/AIModelProperties.java` | M | <span style="color:#6a1b9a">未提交</span>：AI 模型配置调整 |
| infrastructure-ai | `infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/impl/client/BaiLianEmbeddingClient.java` | M | <span style="color:#6a1b9a">未提交</span>：百炼 embedding 客户端调整 |
| knowledge | `knowledge/src/main/java/com/ycy/aiapplication/chunk/strategy/impl/FixedSizeTextChunker.java` | M | <span style="color:#6a1b9a">未提交</span>：固定长度分块策略调整 |
| knowledge | `knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/controller/KnowledgeBaseController.java` | M | <span style="color:#6a1b9a">未提交</span>：知识库接口调整 |
| knowledge | `knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/mapper/KnowledgeChunkDOMapper.java` | M | <span style="color:#6a1b9a">未提交</span>：Chunk mapper 调整 |
| knowledge | `knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/consumer/KnowledgeDocumentAsyncChunkEventConsumer.java` | M | <span style="color:#6a1b9a">未提交</span>：异步分块消费调整 |
| knowledge | `knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/impl/KnowledgeDocumentServiceImpl.java` | M | <span style="color:#6a1b9a">未提交</span>：文档服务调整 |
| knowledge | `knowledge/src/main/java/com/ycy/aiapplication/parse/toolkit/TextCleanupUtil.java` | M | <span style="color:#6a1b9a">未提交</span>：文本清洗调整 |
| knowledge | `knowledge/src/main/resources/application.yaml` | M | <span style="color:#6a1b9a">未提交</span>：知识库配置调整 |
| knowledge | `knowledge/src/main/resources/com/ycy/aiapplication/knowledge/dao.mapper/KnowledgeChunkDOMapper.xml` | A | <span style="color:#6a1b9a">未提交</span>：新增 Chunk XML mapper |
| knowledge | `knowledge/src/test/java/com/ycy/aiapplication/knowledge/embedding/EmbeddingChannelConsistencyIT.java` | A | <span style="color:#6a1b9a">未提交</span>：新增 embedding 通道一致性集成测试 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/config/RAGRetrieveProperties.java` | M | <span style="color:#6a1b9a">未提交</span>：RAG 检索配置调整 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/IntentNode.java` | M | <span style="color:#6a1b9a">未提交</span>：意图节点结构 staged 部分调整 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/AbstractVectorSearchChannel.java` | M | <span style="color:#6a1b9a">未提交</span>：向量检索通道基类调整 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/IntentDirectedSearchChannel.java` | M | <span style="color:#6a1b9a">未提交</span>：意图导向检索通道调整 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/VectorGlobalSearchChannel.java` | M | <span style="color:#6a1b9a">未提交</span>：全局向量检索通道调整 |
| rag | `rag/src/main/resources/application.yaml` | M | <span style="color:#6a1b9a">未提交</span>：RAG 配置调整 |

### unstaged 变更

| 模块 | 文件 | 变更类型 | 进度判断 |
| --- | --- | --- | --- |
| framework | `framework/pom.xml` | M | <span style="color:#6a1b9a">未提交</span>：框架依赖调整 |
| framework | `framework/src/main/java/com/ycy/aiapplication/framework/context/UserContext.java` | M | <span style="color:#6a1b9a">未提交</span>：用户上下文调整 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/IntentNode.java` | M | <span style="color:#6a1b9a">未提交</span>：意图节点另有未暂存变更，和 staged 交叉 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/service/impl/IntentNodeManageServiceImpl.java` | M | <span style="color:#6a1b9a">未提交</span>：意图节点管理服务调整 |
| rag | `rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamChatEventHandler.java` | M | <span style="color:#6a1b9a">未提交</span>：流式事件处理调整 |
| rag | `rag/src/main/resources/prompt/answer-chat-system.st` | M | <span style="color:#6a1b9a">未提交</span>：回答系统提示词调整 |

### untracked 变更

| 路径 | 处理建议 |
| --- | --- |
| `generated-pets/` | 生成产物目录，当前文档未纳入业务代码统计；提交前应确认是否需要忽略或单独管理。 |

## 5. 已完成功能

- **用户认证与简历管理**：已有用户注册、登录、登出、JWT、管理员权限拦截、简历表单 CRUD 与缓存列表接口。
- **AI 面试 Agent**：已有面试问题生成、答案评估、报告生成、面试记录查询和删除接口。
- **知识库管理**：已有知识库 CRUD、文档上传、文档分块、Chunk 管理、向量重建和分块日志查询接口。
- **AI 基础设施**：已有 LLM、Embedding、Rerank、多模型路由、模型健康状态、OpenAI 风格 SSE 解析、流式取消句柄等能力。
- **RAG 核心链路**：已有问题改写、意图识别、检索通道、重排、提示词构建、会话记忆、SSE 流式回答和任务停止。

## 6. 开发中功能

- **RAG 检索和意图节点链路**：当前存在 staged 与 unstaged 交叉变更，需要以一次完整联调确认最终行为。
- **Embedding 通道一致性**：新增集成测试说明正在处理模型通道/维度/配置一致性问题。
- **幂等与用户上下文**：framework 中幂等切面和 UserContext 均有未提交变更，需要确认跨模块影响。
- **面试报告生成**：`AgentAskImpl` 有大幅未提交变更，需要确认生成结构、调用成本和失败处理。

## 7. 待确认事项与风险

| 风险 | 等级 | 说明 | 建议 |
| --- | --- | --- | --- |
| staged/unstaged 交叉 | <span style="color:#c62828">风险较高</span> | `IntentNode.java` 同时存在 staged 和 unstaged 变更，容易提交遗漏。 | 提交前先拆分或统一暂存，避免部分逻辑丢失。 |
| 接口路径前缀不统一 | <span style="color:#616161">待确认</span> | 部分接口带 `/api`，部分接口直接以 `/knowledge-base`、`/conversations` 开头。 | 前端或网关应确认真实前缀规则。 |
| 中文注释显示 | <span style="color:#616161">待确认</span> | PowerShell 控制台可能乱码，但 Node 按 UTF-8 读取正常。 | 判断注释问题时以 UTF-8 文件读取为准。 |
| 测试状态 | <span style="color:#616161">待确认</span> | 本次仅生成文档，未执行 Maven 测试。 | 后续变更提交前运行相关模块测试。 |
| 大类复杂度 | <span style="color:#c62828">风险较高</span> | `AgentAskImpl`、RAG 检索/意图类职责较重。 | 后续修改前优先阅读调用链，并补充针对性测试。 |

## 8. 下一步建议

1. **先处理未提交变更**：按模块拆分 staged/unstaged，尤其是 framework、knowledge、rag。
2. **补验证链路**：优先验证 knowledge 文档上传/分块/向量重建、rag 流式问答、agent 报告生成。
3. **统一接口前缀**：确认 `/api`、`/knowledge-base`、`/conversations` 等路径是否由网关补前缀。
4. **更新文档机制**：接口、核心类或进度状态变化后，同步更新 `codex_coding_guidance.md` 和本文件。
5. **提交前检查**：执行 `git status --short`，确认没有误提交生成产物和未完成调试文件。

## 9. 变更记录

| 时间 | 类型 | 内容 |
| --- | --- | --- |
| 2026-05-26 | <span style="color:#1565c0">文档更新</span> | 创建 `codex_coding_guidance.md` 和 `codex_process.md`，纳入接口、类指引、开发约束、Git 进度和未提交状态。 |
