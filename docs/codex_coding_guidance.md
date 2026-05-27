# Codex Coding Guidance

> 本文档用于帮助智能体和开发者快速理解项目接口、模块、文件、类与方法边界。**开发前应先阅读本文件和 `docs/codex_process.md`**。

## 1. 项目概览

| 项目项 | 内容 |
| --- | --- |
| 项目类型 | Java 17、Spring Boot 3 多模块 Maven 项目 |
| 父工程 | `AIApplication`，根 `pom.xml` 聚合 6 个模块 |
| 文档快照 | 2026-05-26，基于当前工作区源码和 Git 状态生成 |
| 文档范围 | 生产源码、前后端交互接口、业务类、工具类、实体信息类、配置类、数据访问类 |
| 排除范围 | `target`、`.idea`、`generated-pets`、第三方依赖、构建缓存、getter/setter、构造器、Lombok 自动生成方法 |

### 类型颜色图例

| 类型 | 标识 | 说明 |
| --- | --- | --- |
| <span style="color:#1565c0">接口入口类</span> | 接口入口类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |
| <span style="color:#2e7d32">业务类</span> | 业务类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |
| <span style="color:#6a1b9a">工具类</span> | 工具类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |
| <span style="color:#ef6c00">实体信息类</span> | 实体信息类 | 仅概括位置、用途和核心字段，不展开 getter/setter。 |
| <span style="color:#455a64">配置类</span> | 配置类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |
| <span style="color:#00838f">数据访问类</span> | 数据访问类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |
| <span style="color:#5d4037">常量/枚举类</span> | 常量/枚举类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |
| <span style="color:#c62828">异常类</span> | 异常类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |
| <span style="color:#6d4c41">注解类</span> | 注解类 | 按职责说明，接口入口类、业务类、工具类展开主要方法。 |

## 2. 模块架构

| 模块路径 | 模块名称 | 主要职责 | 类数量 | 类型分布 |
| --- | --- | --- | --- | --- |
| `user-service` | 用户服务模块 | 用户账号注册、登录、登出、JWT 校验、管理员权限拦截、简历表单管理。 | 35 | 常量/枚举类:1<br>实体信息类:17<br>配置类:5<br>接口入口类:2<br>数据访问类:2<br>业务类:4<br>工具类:4 |
| `agent` | 面试 Agent 模块 | AI 面试题生成、答案评估、报告生成和面试记录访问。 | 38 | 配置类:5<br>常量/枚举类:4<br>实体信息类:22<br>接口入口类:2<br>数据访问类:1<br>业务类:4 |
| `framework` | 通用框架模块 | 统一响应、异常体系、用户上下文、幂等切面、SSE 发送和 MQ 基础对象。 | 26 | 配置类:1<br>实体信息类:13<br>工具类:2<br>异常类:4<br>注解类:2<br>业务类:2<br>常量/枚举类:1<br>接口入口类:1 |
| `knowledge` | 知识库模块 | 知识库、文档、Chunk 管理，文档解析、分块、向量写入与异步分块消息。 | 69 | 业务类:17<br>工具类:10<br>实体信息类:23<br>常量/枚举类:5<br>配置类:6<br>接口入口类:3<br>数据访问类:4<br>异常类:1 |
| `infrastructure-ai` | AI 基础设施模块 | LLM、Embedding、Rerank、模型路由、模型健康、OpenAI 风格 SSE 解析和 HTTP 调用能力。 | 41 | 常量/枚举类:3<br>业务类:16<br>实体信息类:7<br>工具类:10<br>配置类:4<br>异常类:1 |
| `rag` | RAG 问答模块 | RAG 流式问答、会话记忆、问题改写、意图识别、检索通道、提示词和会话管理。 | 103 | 配置类:11<br>常量/枚举类:3<br>接口入口类:3<br>实体信息类:32<br>业务类:42<br>工具类:7<br>数据访问类:5 |

## 3. 前后端交互接口指引

接口仅指**前后端交互 HTTP 接口**，不包括 Java 内部 interface。接口清单天然适合横向比较，因此这里保留表格。返回值外层通常为 `Result<T>`，RAG 流式问答接口返回 `SseEmitter`。

| 模块 | Controller | 请求方法 | 路径 | 参数 | 返回值 | 作用 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| user-service | `IntervieweeFormController` | POST | `api/interviewee/form/add` | CreateIntervieweeFormReqDTO requestParam | `Result<IntervieweeFormRespDTO>` | 新增简历 | 需要 Authorization |
| user-service | `IntervieweeFormController` | PUT | `api/interviewee/form/update` | UpdateIntervieweeFormReqDTO requestParam | `Result<Void>` | 修改简历 | 需要 Authorization |
| user-service | `IntervieweeFormController` | DELETE | `api/interviewee/form/delete` | DeleteIntervieweeFormReqDTO requestParam | `Result<Void>` | 删除简历 | 需要 Authorization |
| user-service | `IntervieweeFormController` | POST | `api/interviewee/form/search/by/id` | SearchIntervieweeFormByIdReqDTO requestParam | `Result<IntervieweeFormRespDTO>` | 根据 id 查询简历 | 需要 Authorization |
| user-service | `IntervieweeFormController` | POST | `api/interviewee/form/fuzzy/search` | FuzzySearchIntervieweeFormReqDTO requestParam | `Result<List<IntervieweeFormRespDTO>>` | 简历名称模糊查询 | 需要 Authorization |
| user-service | `IntervieweeFormController` | GET | `api/interviewee/form/search/name/list` | 无 | `Result<List<IntervieweeFormNameRespDTO>>` | 查询缓存中的简历名称和时间 | 需要 Authorization |
| user-service | `UserServiceController` | POST | `api/user/service/login` | LoginReqDTO requestParam | `Result<LoginRespDTO>` | 用户登录 | 公开接口 |
| user-service | `UserServiceController` | POST | `api/user/service/logout` | LogoutReqDTO requestParam | `Result<LogoutRespDTO>` | 用户登出 | 需要 bearerAuth |
| user-service | `UserServiceController` | POST | `api/user/service/sign/up` | SignUpReqDTO requestParam | `Result<Void>` | 用户注册 | 公开接口 |
| agent | `AgentController` | POST | `api/agent/questions` | InterviewQuestionAskReqDTO requestParam | `Result<InterviewQuestionAskRespDTO>` | 生成面试问题 | 需要 Authorization |
| agent | `AgentController` | POST | `api/agent/evaluations` | AnswerEvaluationReqDTO requestParam | `Result<List<AnswerEvaluationRespDTO>>` | 批量评估回答 | 需要 Authorization |
| agent | `AgentController` | POST | `api/agent/report` | ReportGenerationReqDTO requestParam | `Result<ReportGenerationRespDTO>` | 生成面试报告 | 需要 Authorization |
| agent | `RecordController` | POST | `/record/service/fuzzy/search` | FuzzySearchInterviewNameReqDTO requestParam | `Result<List<FuzzySearchInterviewRecordRespDTO>>` | 面试记录名称模糊搜索 | 需要 Authorization |
| agent | `RecordController` | GET | `/record/service/search/record` | 无 | `Result<List<SearchInterviewNameAndIdRespDTO>>` | 查询记录名称与 id 列表 | 需要 Authorization |
| agent | `RecordController` | POST | `/record/service/click/record` | SearchInterviewRecordByIdReqDTO requestParam | `Result<InterviewRecordRespDTO>` | 根据 id 查询面试记录详情 | 需要 Authorization |
| agent | `RecordController` | DELETE | `/record/service/delete` | DeleteInterviewRecordReqDTO requestParam | `Result<Void>` | 删除面试记录 | 需要 Authorization |
| knowledge | `KnowledgeBaseController` | POST | `/api/knowledge/knowledge-base` | KnowledgeBaseCreateRequest requestParam | `Result<String>` | 创建知识库 | 幂等控制 |
| knowledge | `KnowledgeBaseController` | PUT | `/api/knowledge/knowledge-base` | KnowledgeBaseUpdateRequest requestParam | `Result<Void>` | 更新知识库 | 幂等控制 |
| knowledge | `KnowledgeBaseController` | PUT | `/api/knowledge/knowledge-base/rename` | KnowledgeBaseUpdateRequest requestParam | `Result<Void>` | 重命名知识库 | 幂等控制 |
| knowledge | `KnowledgeBaseController` | DELETE | `/api/knowledge/knowledge-base/{kbId}` | kbId | `Result<Void>` | 删除知识库 | 幂等控制 |
| knowledge | `KnowledgeBaseController` | GET | `/api/knowledge/knowledge-base/{kbId}` | kbId | `Result<KnowledgeBaseVO>` | 查询知识库详情 | 幂等控制 |
| knowledge | `KnowledgeBaseController` | GET | `/api/knowledge/knowledge-base` | KnowledgeBasePageRequest requestParam | `Result<IPage<KnowledgeBaseVO>>` | 分页查询知识库 | 幂等控制 |
| knowledge | `KnowledgeBaseController` | GET | `/api/knowledge/embeddingModel` | 无 | `Result<Map<String,String>>` | 查询可选 embedding 模型 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | POST | `/knowledge-base/{kb-id}/docs/upload` | kb-id, file, KnowledgeDocumentUploadRequest | `Result<KnowledgeDocumentVO>` | 上传文档并登记入库 | multipart/form-data |
| knowledge | `KnowledgeDocumentController` | POST | `/knowledge-base/docs/{doc-id}/chunk` | doc-id | `Result<Void>` | 启动文档分块与向量化 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | DELETE | `/knowledge-base/docs/{doc-id}` | doc-id | `Result<Void>` | 删除文档 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | GET | `/knowledge-base/docs/{docId}` | docId | `Result<KnowledgeDocumentVO>` | 查询文档详情 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | PUT | `/knowledge-base/docs/{docId}` | docId, KnowledgeDocumentUpdateRequest | `Result<Void>` | 更新文档信息 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | GET | `/knowledge-base/{kb-id}/docs` | kb-id, KnowledgeDocumentPageRequest | `Result<IPage<KnowledgeDocumentVO>>` | 分页查询知识库文档 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | GET | `/knowledge-base/docs/search` | keyword, limit | `Result<List<KnowledgeDocumentSearchVO>>` | 全局搜索文档 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | PATCH | `/knowledge-base/docs/{docId}/enable` | docId, value | `Result<Void>` | 启用或禁用文档 | 幂等控制 |
| knowledge | `KnowledgeDocumentController` | GET | `/knowledge-base/docs/{docId}/chunk-logs` | docId, Page<KnowledgeDocumentChunkLogVO> | `Result<IPage<KnowledgeDocumentChunkLogVO>>` | 查询文档分块日志 | 幂等控制 |
| knowledge | `KnowledgeChunkController` | GET | `/knowledge-base/docs/{doc-id}/chunks` | doc-id, KnowledgeChunkPageRequest | `Result<IPage<KnowledgeChunkVO>>` | 分页查询 Chunk |  |
| knowledge | `KnowledgeChunkController` | DELETE | `/knowledge-base/docs/{doc-id}/chunks/{chunk-id}` | doc-id, chunk-id | `Result<Void>` | 删除 Chunk |  |
| knowledge | `KnowledgeChunkController` | POST | `/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable` | doc-id, chunk-id | `Result<Void>` | 启用单条 Chunk |  |
| knowledge | `KnowledgeChunkController` | POST | `/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/disable` | doc-id, chunk-id | `Result<Void>` | 禁用单条 Chunk |  |
| knowledge | `KnowledgeChunkController` | POST | `/knowledge-base/docs/{doc-id}/chunks/batch-enable` | doc-id, KnowledgeChunkBatchRequest | `Result<Void>` | 批量启用 Chunk |  |
| knowledge | `KnowledgeChunkController` | POST | `/knowledge-base/docs/{doc-id}/chunks/batch-disable` | doc-id, KnowledgeChunkBatchRequest | `Result<Void>` | 批量禁用 Chunk |  |
| knowledge | `KnowledgeChunkController` | POST | `/knowledge-base/docs/{doc-id}/chunks/rebuild` | doc-id | `Result<Void>` | 重建文档向量 |  |
| rag | `RAGChatController` | GET | `/rag/v3/chat` | question, conversationId?, deepThinking? | `SseEmitter` | 发起 SSE 流式 RAG 对话 | 幂等控制 |
| rag | `RAGChatController` | POST | `/rag/v3/stop` | taskId | `Result<Void>` | 停止指定流式任务 | 幂等控制 |
| rag | `ConversationController` | GET | `/conversations` | 无 | `Result<List<ConversationVO>>` | 查询当前用户会话列表 | 从 UserContext 取用户 |
| rag | `ConversationController` | PUT | `/conversations/{conversationId}` | conversationId, ConversationUpdateRequest | `Result<Void>` | 重命名会话 |  |
| rag | `ConversationController` | DELETE | `/conversations/{conversationId}` | conversationId | `Result<Void>` | 删除会话 |  |
| rag | `ConversationController` | GET | `/conversations/{conversationId}/messages` | conversationId | `Result<List<ConversationMessageVO>>` | 查询会话消息列表 | 默认 ASC |
| rag | `IntentNodeController` | GET | `/api/rag/intent-node` | 无 | `Result<List<IntentNodeVO>>` | 查询全部意图节点 |  |
| rag | `IntentNodeController` | POST | `/api/rag/intent-node` | IntentNodeCreateRequest requestParam | `Result<String>` | 创建意图节点 | 幂等控制 |
| rag | `IntentNodeController` | PUT | `/api/rag/intent-node/{id}` | id, IntentNodeUpdateRequest | `Result<Void>` | 更新意图节点 | 幂等控制 |
| rag | `IntentNodeController` | DELETE | `/api/rag/intent-node/{id}` | id | `Result<Void>` | 删除意图节点 | 幂等控制 |
| rag | `IntentNodeController` | POST | `/api/rag/intent-node/batch/enable` | IntentNodeBatchRequest requestParam | `Result<Void>` | 批量启用意图节点 | 幂等控制 |
| rag | `IntentNodeController` | POST | `/api/rag/intent-node/batch/disable` | IntentNodeBatchRequest requestParam | `Result<Void>` | 批量停用意图节点 | 幂等控制 |
| rag | `IntentNodeController` | POST | `/api/rag/intent-node/batch/delete` | IntentNodeBatchRequest requestParam | `Result<Void>` | 批量删除意图节点 | 幂等控制 |

## 4. 代码文件与类指引

本节按模块列出所有生产类。每个类使用结构化文本描述，避免在大表中堆叠长句。

### user-service

**<span style="color:#1565c0">接口入口类</span>**

- `IntervieweeFormController`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/controller/IntervieweeFormController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

- `UserServiceController`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/controller/UserServiceController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

**<span style="color:#2e7d32">业务类</span>**

- `IntervieweeFormServiceImpl`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/service/impl/IntervieweeFormServiceImpl.java`
  作用：处理简历表单增删改查、缓存和持久化。
  方法展开：是

- `UserServiceImpl`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/service/impl/UserServiceImpl.java`
  作用：处理用户登录、注册、登出、JWT 和权限校验。
  方法展开：是

- `IntervieweeFormService`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/service/IntervieweeFormService.java`
  作用：处理简历表单增删改查、缓存和持久化。
  方法展开：是

- `UserService`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/service/UserService.java`
  作用：处理用户登录、注册、登出、JWT 和权限校验。
  方法展开：是

**<span style="color:#6a1b9a">工具类</span>**

- `AdminPermissionInterceptor`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/interceptor/AdminPermissionInterceptor.java`
  作用：处理用户登录、注册、登出、JWT 和权限校验。
  方法展开：是

- `JWTInterceptor`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/interceptor/JWTInterceptor.java`
  作用：处理用户登录、注册、登出、JWT 和权限校验。
  方法展开：是

- `JWTUtil`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/JWTUtil.java`
  作用：处理用户登录、注册、登出、JWT 和权限校验。
  方法展开：是

- `UserContextNickNameResolver`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/UserContextNickNameResolver.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

**<span style="color:#ef6c00">实体信息类</span>**

- `EducationExperience`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/common/pojo/EducationExperience.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ProjectExperience`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/common/pojo/ProjectExperience.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `WorkExperience`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/common/pojo/WorkExperience.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntervieweeFormDO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dao/entity/IntervieweeFormDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `UserAccountDO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dao/entity/UserAccountDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `CreateIntervieweeFormReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/CreateIntervieweeFormReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `DeleteIntervieweeFormReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/DeleteIntervieweeFormReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `FuzzySearchIntervieweeFormReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/FuzzySearchIntervieweeFormReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `LoginReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/LoginReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `LogoutReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/LogoutReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `SearchIntervieweeFormByIdReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/SearchIntervieweeFormByIdReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `SignUpReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/SignUpReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `UpdateIntervieweeFormReqDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/UpdateIntervieweeFormReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntervieweeFormNameRespDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/IntervieweeFormNameRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntervieweeFormRespDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/IntervieweeFormRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `LoginRespDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/LoginRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `LogoutRespDTO`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/LogoutRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

**<span style="color:#455a64">配置类</span>**

- `DataBaseConfiguration`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/config/DataBaseConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `SwaggerConfiguration`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/config/SwaggerConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebConfiguration`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/config/WebConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebMvcConfiguration`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/config/WebMvcConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `UserServiceApplication`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/UserServiceApplication.java`
  作用：Spring Boot 应用入口类，仅作为模块入口记录，不提供启动指引。
  方法展开：否

**<span style="color:#00838f">数据访问类</span>**

- `IntervieweeFormDOMapper`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dao/mapper/IntervieweeFormDOMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `UserAccountDOMapper`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dao/mapper/UserAccountDOMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

**<span style="color:#5d4037">常量/枚举类</span>**

- `UserServiceRedisConstant`
  位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/common/constant/UserServiceRedisConstant.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

### agent

**<span style="color:#1565c0">接口入口类</span>**

- `AgentController`
  位置：`agent/src/main/java/com/ycy/aiapplication/controller/AgentController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

- `RecordController`
  位置：`agent/src/main/java/com/ycy/aiapplication/controller/RecordController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

**<span style="color:#2e7d32">业务类</span>**

- `AgentAsk`
  位置：`agent/src/main/java/com/ycy/aiapplication/service/AgentAsk.java`
  作用：处理 AI 面试问题生成、答案评估、报告生成或面试记录查询。
  方法展开：是

- `AgentAskImpl`
  位置：`agent/src/main/java/com/ycy/aiapplication/service/Impl/AgentAskImpl.java`
  作用：处理 AI 面试问题生成、答案评估、报告生成或面试记录查询。
  方法展开：是

- `RecordServiceImpl`
  位置：`agent/src/main/java/com/ycy/aiapplication/service/Impl/RecordServiceImpl.java`
  作用：处理 AI 面试问题生成、答案评估、报告生成或面试记录查询。
  方法展开：是

- `RecordService`
  位置：`agent/src/main/java/com/ycy/aiapplication/service/RecordService.java`
  作用：处理 AI 面试问题生成、答案评估、报告生成或面试记录查询。
  方法展开：是

**<span style="color:#ef6c00">实体信息类</span>**

- `ApiEvaluationResp`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/ApiEvaluationResp.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `EducationExperience`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/EducationExperience.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntervieweeForm`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/IntervieweeForm.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `InterviewQuestion`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/InterviewQuestion.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ProjectExperience`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/ProjectExperience.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `QuestionWithAnswer`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/QuestionWithAnswer.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `WorkExperience`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/WorkExperience.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `InterviewRecordDO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dao/entity/InterviewRecordDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `AgentInterviewReportDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/AgentInterviewReportDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `InterviewDimensionScoreDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/InterviewDimensionScoreDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `AnswerEvaluationReqDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/AnswerEvaluationReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `DeleteInterviewRecordReqDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/DeleteInterviewRecordReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `FuzzySearchInterviewNameReqDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/FuzzySearchInterviewNameReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `InterviewQuestionAskReqDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/InterviewQuestionAskReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ReportGenerationReqDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/ReportGenerationReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `SearchInterviewRecordByIdReqDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/SearchInterviewRecordByIdReqDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `AnswerEvaluationRespDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/AnswerEvaluationRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `FuzzySearchInterviewRecordRespDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/FuzzySearchInterviewRecordRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `InterviewQuestionAskRespDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/InterviewQuestionAskRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `InterviewRecordRespDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/InterviewRecordRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ReportGenerationRespDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/ReportGenerationRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `SearchInterviewNameAndIdRespDTO`
  位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/SearchInterviewNameAndIdRespDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

**<span style="color:#455a64">配置类</span>**

- `AgentApplication`
  位置：`agent/src/main/java/com/ycy/aiapplication/AgentApplication.java`
  作用：Spring Boot 应用入口类，仅作为模块入口记录，不提供启动指引。
  方法展开：否

- `DataBaseConfiguration`
  位置：`agent/src/main/java/com/ycy/aiapplication/config/DataBaseConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `SwaggerConfiguration`
  位置：`agent/src/main/java/com/ycy/aiapplication/config/SwaggerConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebConfiguration`
  位置：`agent/src/main/java/com/ycy/aiapplication/config/WebConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebMvcConfiguration`
  位置：`agent/src/main/java/com/ycy/aiapplication/config/WebMvcConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

**<span style="color:#00838f">数据访问类</span>**

- `InterviewRecordDOMapper`
  位置：`agent/src/main/java/com/ycy/aiapplication/dao/mapper/InterviewRecordDOMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

**<span style="color:#5d4037">常量/枚举类</span>**

- `AgentRedisConstant`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/constant/AgentRedisConstant.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `AIPromptConstant`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/constant/AIPromptConstant.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `AIModelEnum`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/enums/AIModelEnum.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `QuestionLevelEnum`
  位置：`agent/src/main/java/com/ycy/aiapplication/common/enums/QuestionLevelEnum.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

### framework

**<span style="color:#1565c0">接口入口类</span>**

- `GlobalExceptionHandler`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/GlobalExceptionHandler.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

**<span style="color:#2e7d32">业务类</span>**

- `IdempotentConsumeAspect`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/aspects/IdempotentConsumeAspect.java`
  作用：处理接口幂等提交或消息消费幂等。
  方法展开：是

- `IdempotentSubmitAspect`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/aspects/IdempotentSubmitAspect.java`
  作用：处理接口幂等提交或消息消费幂等。
  方法展开：是

**<span style="color:#6a1b9a">工具类</span>**

- `UserNickNameResolver`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/context/UserNickNameResolver.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `SpELUtil`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/utils/SpELUtil.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

**<span style="color:#ef6c00">实体信息类</span>**

- `UserContext`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/context/UserContext.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `UserInfoDTO`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/context/UserInfoDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ChatMessage`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/ChatMessage.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ChatRequest`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/ChatRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `Result`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/Result.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `RetrievedChunk`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/RetrievedChunk.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `BaseErrorCode`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/errorcode/BaseErrorCode.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IErrorCode`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/errorcode/IErrorCode.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `BaseSendExtendDTO`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/mq/base/BaseSendExtendDTO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `MessageWrapper`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/mq/base/MessageWrapper.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `Result`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/Result.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `Results`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/Results.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `SseEmitterSender`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/SseEmitterSender.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

**<span style="color:#455a64">配置类</span>**

- `WebAutoConfiguration`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/config/WebAutoConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

**<span style="color:#5d4037">常量/枚举类</span>**

- `IdempotentConsumeStatusEnum`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/enums/IdempotentConsumeStatusEnum.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

**<span style="color:#c62828">异常类</span>**

- `AbstractException`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/exception/AbstractException.java`
  作用：封装异常类型、错误码或远程调用错误信息。
  方法展开：否

- `ClientException`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/exception/ClientException.java`
  作用：封装异常类型、错误码或远程调用错误信息。
  方法展开：否

- `RemoteException`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/exception/RemoteException.java`
  作用：封装异常类型、错误码或远程调用错误信息。
  方法展开：否

- `ServiceException`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/exception/ServiceException.java`
  作用：封装异常类型、错误码或远程调用错误信息。
  方法展开：否

**<span style="color:#6d4c41">注解类</span>**

- `IdempotentConsume`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/annotations/IdempotentConsume.java`
  作用：定义项目内 AOP 或业务约束注解。
  方法展开：否

- `IdempotentSubmit`
  位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/annotations/IdempotentSubmit.java`
  作用：定义项目内 AOP 或业务约束注解。
  方法展开：否

### knowledge

**<span style="color:#1565c0">接口入口类</span>**

- `KnowledgeBaseController`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/controller/KnowledgeBaseController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

- `KnowledgeChunkController`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/controller/KnowledgeChunkController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

- `KnowledgeDocumentController`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/controller/KnowledgeDocumentController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

**<span style="color:#2e7d32">业务类</span>**

- `ChunkingMode`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/ChunkingMode.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `ChunkingOptions`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/records/ChunkingOptions.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `VectorChunk`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/VectorChunk.java`
  作用：封装向量空间管理、Milvus 写入、更新、删除和查询。
  方法展开：是

- `KnowledgeDocumentAsyncChunkEventConsumer`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/consumer/KnowledgeDocumentAsyncChunkEventConsumer.java`
  作用：处理知识文档上传、解析、分块、异步任务和状态管理。
  方法展开：是

- `KnowledgeDocumentAsyncChunkProducer`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/producer/KnowledgeDocumentAsyncChunkProducer.java`
  作用：处理知识文档上传、解析、分块、异步任务和状态管理。
  方法展开：是

- `KnowledgeBaseServiceImpl`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/impl/KnowledgeBaseServiceImpl.java`
  作用：处理知识库创建、更新、删除、查询和向量空间维护。
  方法展开：是

- `KnowledgeChunkServiceImpl`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/impl/KnowledgeChunkServiceImpl.java`
  作用：处理知识分块查询、启停、删除和向量重建。
  方法展开：是

- `KnowledgeDocumentServiceImpl`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/impl/KnowledgeDocumentServiceImpl.java`
  作用：处理知识文档上传、解析、分块、异步任务和状态管理。
  方法展开：是

- `KnowledgeBaseService`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/KnowledgeBaseService.java`
  作用：处理知识库创建、更新、删除、查询和向量空间维护。
  方法展开：是

- `KnowledgeChunkService`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/KnowledgeChunkService.java`
  作用：处理知识分块查询、启停、删除和向量重建。
  方法展开：是

- `KnowledgeDocumentService`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/KnowledgeDocumentService.java`
  作用：处理知识文档上传、解析、分块、异步任务和状态管理。
  方法展开：是

- `VectorSpaceId`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/common/VectorSpaceId.java`
  作用：封装向量空间管理、Milvus 写入、更新、删除和查询。
  方法展开：是

- `VectorSpaceSpec`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/common/VectorSpaceSpec.java`
  作用：封装向量空间管理、Milvus 写入、更新、删除和查询。
  方法展开：是

- `MilvusVectorStoreAdmin`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/impl/MilvusVectorStoreAdmin.java`
  作用：封装向量空间管理、Milvus 写入、更新、删除和查询。
  方法展开：是

- `MilvusVectorStoreService`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/impl/MilvusVectorStoreService.java`
  作用：封装向量空间管理、Milvus 写入、更新、删除和查询。
  方法展开：是

- `VectorStoreAdmin`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/VectorStoreAdmin.java`
  作用：封装向量空间管理、Milvus 写入、更新、删除和查询。
  方法展开：是

- `VectorStoreService`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/VectorStoreService.java`
  作用：封装向量空间管理、Milvus 写入、更新、删除和查询。
  方法展开：是

**<span style="color:#6a1b9a">工具类</span>**

- `ChunkingStrategyFactory`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/ChunkingStrategyFactory.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `ChunkingStrategy`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/strategy/ChunkingStrategy.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `FixedSizeTextChunker`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/strategy/impl/FixedSizeTextChunker.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `StructureAwareTextChunker`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/strategy/impl/StructureAwareTextChunker.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `AliOSSUtils`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/toolkit/AliOSSUtils.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `DocumentParserSelector`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/DocumentParserSelector.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `DocumentParser`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/parser/DocumentParser.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `MarkdownDocumentParser`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/parser/impl/MarkdownDocumentParser.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `TikaDocumentParser`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/parser/impl/TikaDocumentParser.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `TextCleanupUtil`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/toolkit/TextCleanupUtil.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

**<span style="color:#ef6c00">实体信息类</span>**

- `FixedSizeOptions`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/records/FixedSizeOptions.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `TextBoundaryOptions`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/records/TextBoundaryOptions.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeBaseCreateRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/base/KnowledgeBaseCreateRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeBasePageRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/base/KnowledgeBasePageRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeBaseUpdateRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/base/KnowledgeBaseUpdateRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeChunkBatchRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/chunk/KnowledgeChunkBatchRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeChunkPageRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/chunk/KnowledgeChunkPageRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentPageRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/doc/KnowledgeDocumentPageRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentUpdateRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/doc/KnowledgeDocumentUpdateRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentUploadRequest`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/doc/KnowledgeDocumentUploadRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeBaseVO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeBaseVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeChunkVO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeChunkVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentChunkLogVO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeDocumentChunkLogVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentSearchVO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeDocumentSearchVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentVO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeDocumentVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeBaseDO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeBaseDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeChunkDO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeChunkDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentChunkLogDO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeDocumentChunkLogDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentDO`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeDocumentDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `KnowledgeDocumentAsyncChunkEvent`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/event/KnowledgeDocumentAsyncChunkEvent.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `AbstractCommonSendProduceTemplate`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/producer/AbstractCommonSendProduceTemplate.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ParseResult`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/common/ParseResult.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ParserType`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/common/ParserType.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

**<span style="color:#455a64">配置类</span>**

- `DataBaseConfiguration`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/config/DataBaseConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `OSSConfiguration`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/config/OSSConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebConfiguration`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/config/WebConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebMvcConfiguration`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/config/WebMvcConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `KnowledgeApplication`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/KnowledgeApplication.java`
  作用：Spring Boot 应用入口类，仅作为模块入口记录，不提供启动指引。
  方法展开：否

- `MilvusConfig`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/config/MilvusConfig.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

**<span style="color:#00838f">数据访问类</span>**

- `KnowledgeBaseMapper`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/mapper/KnowledgeBaseMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `KnowledgeChunkDOMapper`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/mapper/KnowledgeChunkDOMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `KnowledgeDocumentChunkLogMapper`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/mapper/KnowledgeDocumentChunkLogMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `KnowledgeDocumentMapper`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/mapper/KnowledgeDocumentMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

**<span style="color:#5d4037">常量/枚举类</span>**

- `KnowledgeRocketMQConstant`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/common/constant/KnowledgeRocketMQConstant.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `DocumentStatus`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/common/enums/DocumentStatus.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `ProcessMode`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/common/enums/ProcessMode.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `SourceType`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/common/enums/SourceType.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `MilvusVectorServiceConstant`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/common/constant/MilvusVectorServiceConstant.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

**<span style="color:#c62828">异常类</span>**

- `VectorCollectionAlreadyExistsException`
  位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/exception/VectorCollectionAlreadyExistsException.java`
  作用：封装异常类型、错误码或远程调用错误信息。
  方法展开：否

### infrastructure-ai

**<span style="color:#2e7d32">业务类</span>**

- `AbstractOpenAIStyleChatClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/client/AbstractOpenAIStyleChatClient.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `BaiLianChatClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/client/BaiLianChatClient.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `SiliconFlowChatClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/client/SiliconFlowChatClient.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `RoutingLLMService`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/service/RoutingLLMService.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `ChatClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/interfaces/ChatClient.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `LLMService`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/interfaces/LLMService.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `EmbeddingClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/EmbeddingClient.java`
  作用：封装 embedding 模型路由、调用和向量维度管理。
  方法展开：是

- `EmbeddingService`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/EmbeddingService.java`
  作用：封装 embedding 模型路由、调用和向量维度管理。
  方法展开：是

- `BaiLianEmbeddingClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/impl/client/BaiLianEmbeddingClient.java`
  作用：封装 embedding 模型路由、调用和向量维度管理。
  方法展开：是

- `SiliconFlowEmbeddingClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/impl/client/SiliconFlowEmbeddingClient.java`
  作用：封装 embedding 模型路由、调用和向量维度管理。
  方法展开：是

- `RoutingEmbeddingService`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/impl/service/RoutingEmbeddingService.java`
  作用：封装 embedding 模型路由、调用和向量维度管理。
  方法展开：是

- `ModelHealthStore`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelHealthStore.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `BaiLianRerankClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/rerank/BaiLianRerankClient.java`
  作用：封装重排序模型调用。
  方法展开：是

- `RerankClient`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/rerank/RerankClient.java`
  作用：封装重排序模型调用。
  方法展开：是

- `LeightWeightTokenCounterService`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/token/LeightWeightTokenCounterService.java`
  作用：提供文本 token 估算能力。
  方法展开：是

- `TokenCounterService`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/token/TokenCounterService.java`
  作用：提供文本 token 估算能力。
  方法展开：是

**<span style="color:#6a1b9a">工具类</span>**

- `ChatModelRegistry`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/ChatModelRegistry.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `ChatModelSelector`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/ChatModelSelector.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `FirstPacketAwaiter`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/FirstPacketAwaiter.java`
  作用：流式路由首包探测等待器，使用 `CompletableFuture<Result>` 固化首个决定性事件，保证 first-wins 语义。
  方法展开：是

- `OpenAIStyleSSEParser`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/OpenAIStyleSSEParser.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

- `StreamAsyncExecutor`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/StreamAsyncExecutor.java`
  作用：封装 SSE 发送、流式回调、任务取消或异步流执行。
  方法展开：是

- `StreamCancellationHandles`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/StreamCancellationHandles.java`
  作用：封装 SSE 发送、流式回调、任务取消或异步流执行。
  方法展开：是

- `EmbeddingModelRegistry`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/EmbeddingModelRegistry.java`
  作用：封装 embedding 模型路由、调用和向量维度管理。
  方法展开：是

- `ModelURLResolver`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/http/ModelURLResolver.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `ModelRoutingExecutor`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelRoutingExecutor.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `LLMResponseCleaner`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/toolkit/LLMResponseCleaner.java`
  作用：封装大模型聊天客户端、模型选择、流式解析和回退调用。
  方法展开：是

**<span style="color:#ef6c00">实体信息类</span>**

- `StreamCallback`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/interfaces/StreamCallback.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `StreamCancellationHandle`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/interfaces/StreamCancellationHandle.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `EmbeddingRoute`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/EmbeddingRoute.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `HttpMediaTypes`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/http/HttpMediaTypes.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ModelClientErrorType`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/http/ModelClientErrorType.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ModelCaller`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelCaller.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ModelTarget`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelTarget.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

**<span style="color:#455a64">配置类</span>**

- `AIModelProperties`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/config/AIModelProperties.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `ChatExecutorConfig`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/config/ChatExecutorConfig.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `OkHttpConfig`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/config/OkHttpConfig.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `RAGCollectionProperties`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/config/RAGCollectionProperties.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

**<span style="color:#5d4037">常量/枚举类</span>**

- `ChatServiceMessageConstant`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/constat/ChatServiceMessageConstant.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `ModelCapability`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/enums/ModelCapability.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `ModelProvider`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/enums/ModelProvider.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

**<span style="color:#c62828">异常类</span>**

- `ModelClientException`
  位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/http/ModelClientException.java`
  作用：封装异常类型、错误码或远程调用错误信息。
  方法展开：否

### rag

**<span style="color:#1565c0">接口入口类</span>**

- `ConversationController`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/controller/ConversationController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

- `IntentNodeController`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/controller/IntentNodeController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

- `RAGChatController`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/controller/RAGChatController.java`
  作用：提供前后端 HTTP 接口入口，转发请求到业务服务。
  方法展开：是

**<span style="color:#2e7d32">业务类</span>**

- `GuidanceDecision`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/guidance/GuidanceDecision.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `IntentGuidanceService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/guidance/IntentGuidanceService.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `FirstLayerIntentClassifier`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/classifier/impl/FirstLayerIntentClassifier.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `SecondLayerIntentClassifier`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/classifier/impl/SecondLayerIntentClassifier.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `IntentClassifier`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/classifier/IntentClassifier.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `ConversationMemoryService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/ConversationMemoryService.java`
  作用：维护对话历史、摘要压缩和上下文加载。
  方法展开：是

- `ConversationMemoryStoreService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/ConversationMemoryStoreService.java`
  作用：维护对话历史、摘要压缩和上下文加载。
  方法展开：是

- `ConversationMemorySummaryService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/ConversationMemorySummaryService.java`
  作用：维护对话历史、摘要压缩和上下文加载。
  方法展开：是

- `ConversationMemoryServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/impl/ConversationMemoryServiceImpl.java`
  作用：维护对话历史、摘要压缩和上下文加载。
  方法展开：是

- `ConversationMemoryStoreServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/impl/ConversationMemoryStoreServiceImpl.java`
  作用：维护对话历史、摘要压缩和上下文加载。
  方法展开：是

- `ConversationMemorySummaryServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/impl/ConversationMemorySummaryServiceImpl.java`
  作用：维护对话历史、摘要压缩和上下文加载。
  方法展开：是

- `PromptBuildPlan`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/plan/PromptBuildPlan.java`
  作用：加载、清洗和构建 RAG 提示词。
  方法展开：是

- `PromptPlan`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/plan/PromptPlan.java`
  作用：加载、清洗和构建 RAG 提示词。
  方法展开：是

- `PromptContext`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/PromptContext.java`
  作用：加载、清洗和构建 RAG 提示词。
  方法展开：是

- `PromptScene`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/PromptScene.java`
  作用：加载、清洗和构建 RAG 提示词。
  方法展开：是

- `RAGPromptService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/RAGPromptService.java`
  作用：加载、清洗和构建 RAG 提示词。
  方法展开：是

- `AbstractVectorSearchChannel`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/AbstractVectorSearchChannel.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `IntentDirectedSearchChannel`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/IntentDirectedSearchChannel.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `VectorGlobalSearchChannel`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/VectorGlobalSearchChannel.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `AbstractParallelRetriever`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/retriver/AbstractParallelRetriever.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `CollectionParallelRetriever`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/retriver/CollectionParallelRetriever.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `SearchChannel`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/SearchChannel.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `SearchChannelResult`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/SearchChannelResult.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `SearchChannelType`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/SearchChannelType.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `RetrievalEngine`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/RetrievalEngine.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `MilvusRetrieverService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/service/MilvusRetrieverService.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `RetrieverService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/service/RetrieverService.java`
  作用：执行知识检索、向量召回、检索通道编排或重排前处理。
  方法展开：是

- `MultiQuestionRewriteService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/service/MultiQuestionRewriteService.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `QueryRewriteService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/service/QueryRewriteService.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `QueryTermMappingService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/service/QueryTermMappingService.java`
  作用：项目业务或通用支撑能力。
  方法展开：是

- `ConversationComplexQueryService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/ConversationComplexQueryService.java`
  作用：处理会话、消息查询和会话管理。
  方法展开：是

- `ConversationMessageService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/ConversationMessageService.java`
  作用：处理会话、消息查询和会话管理。
  方法展开：是

- `ConversationService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/ConversationService.java`
  作用：处理会话、消息查询和会话管理。
  方法展开：是

- `ConversationComplexQueryServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/ConversationComplexQueryServiceImpl.java`
  作用：处理会话、消息查询和会话管理。
  方法展开：是

- `ConversationMessageServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/ConversationMessageServiceImpl.java`
  作用：处理会话、消息查询和会话管理。
  方法展开：是

- `ConversationServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/ConversationServiceImpl.java`
  作用：处理会话、消息查询和会话管理。
  方法展开：是

- `IntentNodeManageServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/IntentNodeManageServiceImpl.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `IntentNodeServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/IntentNodeServiceImpl.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `RAGChatServiceImpl`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/RAGChatServiceImpl.java`
  作用：编排 RAG 流式问答、任务停止和对话处理。
  方法展开：是

- `IntentNodeManageService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/IntentNodeManageService.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `IntentNodeService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/IntentNodeService.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `RAGChatService`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/RAGChatService.java`
  作用：编排 RAG 流式问答、任务停止和对话处理。
  方法展开：是

**<span style="color:#6a1b9a">工具类</span>**

- `IntentResolver`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/IntentResolver.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `IntentNodeRegistry`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/management/IntentNodeRegistry.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `IntentTreeCacheManager`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/management/IntentTreeCacheManager.java`
  作用：处理意图节点、意图分类、意图缓存或引导决策。
  方法展开：是

- `PromptTemplateLoader`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/PromptTemplateLoader.java`
  作用：加载、清洗和构建 RAG 提示词。
  方法展开：是

- `PromptTemplateUtils`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/PromptTemplateUtils.java`
  作用：加载、清洗和构建 RAG 提示词。
  方法展开：是

- `StreamCallbackFactory`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamCallbackFactory.java`
  作用：封装 SSE 发送、流式回调、任务取消或异步流执行。
  方法展开：是

- `StreamTaskManager`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamTaskManager.java`
  作用：封装 SSE 发送、流式回调、任务取消或异步流执行。
  方法展开：是

**<span style="color:#ef6c00">实体信息类</span>**

- `ConversationCreateRequest`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/ConversationCreateRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationUpdateRequest`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/ConversationUpdateRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentNodeBatchRequest`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/IntentNodeBatchRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentNodeCreateRequest`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/IntentNodeCreateRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentNodeUpdateRequest`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/IntentNodeUpdateRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationMessageVO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/vo/ConversationMessageVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationVO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/vo/ConversationVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentNodeVO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/vo/IntentNodeVO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentKind`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/enums/IntentKind.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentLevel`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/enums/IntentLevel.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentGroup`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/IntentGroup.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentNode`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/IntentNode.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `NodeScore`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/NodeScore.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `SubQuestionIntent`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/SubQuestionIntent.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `FirstLayerIntentDecision`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/FirstLayerIntentDecision.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `RetrievalContext`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/common/RetrievalContext.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `RetrieveRequest`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/common/RetrieveRequest.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `SearchContext`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/common/SearchContext.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `QueryTermMappingUtil`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/common/QueryTermMappingUtil.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `RewriteResult`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/common/RewriteResult.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationDO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/ConversationDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationMessageDO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/ConversationMessageDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationSummaryDO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/ConversationSummaryDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `IntentNodeDO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/IntentNodeDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `QueryTermMappingDO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/QueryTermMappingDO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationMessageBO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/bo/ConversationMessageBO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `ConversationSummaryBO`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/bo/ConversationSummaryBO.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `CompletionPayload`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/common/CompletionPayload.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `MessageDelta`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/common/MessageDelta.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `MetaPayload`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/common/MetaPayload.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `StreamChatEventHandler`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamChatEventHandler.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

- `StreamChatHandlerParams`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamChatHandlerParams.java`
  作用：承载请求、响应、数据库实体、值对象或业务过程数据。
  方法展开：否

**<span style="color:#455a64">配置类</span>**

- `DataBaseConfiguration`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/DataBaseConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `GuidanceProperties`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/GuidanceProperties.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `MemoryProperties`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/MemoryProperties.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `MilvusConfig`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/MilvusConfig.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `RAGExecutorConfig`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/RAGExecutorConfig.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `RAGIntentProperties`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/RAGIntentProperties.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `RAGRetrieveProperties`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/RAGRetrieveProperties.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `RAGReWriteProperties`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/RAGReWriteProperties.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebConfiguration`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/WebConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `WebMvcConfiguration`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/config/WebMvcConfiguration.java`
  作用：承载 Spring Bean、跨域、拦截器、数据库、线程池或外部服务配置。
  方法展开：否

- `RagApplication`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/RagApplication.java`
  作用：Spring Boot 应用入口类，仅作为模块入口记录，不提供启动指引。
  方法展开：否

**<span style="color:#00838f">数据访问类</span>**

- `ConversationMapper`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/mapper/ConversationMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `ConversationMessageMapper`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/mapper/ConversationMessageMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `ConversationSummaryMapper`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/mapper/ConversationSummaryMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `IntentNodeMapper`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/mapper/IntentNodeMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

- `QueryTermMappingMapper`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/mapper/QueryTermMappingMapper.java`
  作用：MyBatis/MyBatis-Plus 数据访问接口，负责数据库表读写。
  方法展开：否

**<span style="color:#5d4037">常量/枚举类</span>**

- `RAGConstant`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/constant/RAGConstant.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `ConversationMessageOrder`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/enums/ConversationMessageOrder.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

- `SSEEventType`
  位置：`rag/src/main/java/com/ycy/aiapplication/rag/enums/SSEEventType.java`
  作用：定义常量、枚举值或状态类型。
  方法展开：否

## 5. 主要方法指引

本节仅展开**接口入口类、业务类、工具类**的非 getter/setter 方法。方法说明改为文本块，便于阅读参数、返回值和作用。实体/DTO/VO/DO 只在后续实体概览中说明。

### user-service 方法

#### IntervieweeFormController

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/controller/IntervieweeFormController.java`

- `public Result<IntervieweeFormRespDTO> addIntervieweeForm(@RequestBody CreateIntervieweeFormReqDTO requestParam)`
  参数：`@RequestBody CreateIntervieweeFormReqDTO requestParam`
  返回值：`Result<IntervieweeFormRespDTO>`
  作用：创建或新增业务数据。
- `public Result<Void> updateIntervieweeForm(@RequestBody UpdateIntervieweeFormReqDTO requestParam)`
  参数：`@RequestBody UpdateIntervieweeFormReqDTO requestParam`
  返回值：`Result<Void>`
  作用：更新业务数据或名称。
- `public Result<Void> deleteIntervieweeForm(@RequestBody DeleteIntervieweeFormReqDTO requestParam)`
  参数：`@RequestBody DeleteIntervieweeFormReqDTO requestParam`
  返回值：`Result<Void>`
  作用：删除业务数据或清理资源。
- `public Result<IntervieweeFormRespDTO> searchIntervieweeFormById(@RequestBody SearchIntervieweeFormByIdReqDTO requestParam)`
  参数：`@RequestBody SearchIntervieweeFormByIdReqDTO requestParam`
  返回值：`Result<IntervieweeFormRespDTO>`
  作用：查询并返回业务数据。
- `public Result<List<IntervieweeFormRespDTO>> fuzzySearchIntervieweeForm( @RequestBody FuzzySearchIntervieweeFormReqDTO requestParam)`
  参数：`@RequestBody FuzzySearchIntervieweeFormReqDTO requestParam`
  返回值：`Result<List<IntervieweeFormRespDTO>>`
  作用：执行检索召回或检索通道处理。
- `public Result<List<IntervieweeFormNameRespDTO>> searchIntervieweeFormNameList()`
  参数：无
  返回值：`Result<List<IntervieweeFormNameRespDTO>>`
  作用：查询并返回业务数据。

#### UserServiceController

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/controller/UserServiceController.java`

- `public Result<LoginRespDTO> login(@RequestBody LoginReqDTO requestParam)`
  参数：`@RequestBody LoginReqDTO requestParam`
  返回值：`Result<LoginRespDTO>`
  作用：处理账号认证相关流程。
- `public Result<LogoutRespDTO> logout(@RequestBody LogoutReqDTO requestParam)`
  参数：`@RequestBody LogoutReqDTO requestParam`
  返回值：`Result<LogoutRespDTO>`
  作用：处理账号认证相关流程。
- `public Result<Void> signUpNewAccount(@RequestBody SignUpReqDTO requestParam)`
  参数：`@RequestBody SignUpReqDTO requestParam`
  返回值：`Result<Void>`
  作用：处理账号认证相关流程。

#### IntervieweeFormServiceImpl

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/service/impl/IntervieweeFormServiceImpl.java`

- `public IntervieweeFormRespDTO addIntervieweeForm(CreateIntervieweeFormReqDTO requestParam)`
  参数：`CreateIntervieweeFormReqDTO requestParam`
  返回值：`IntervieweeFormRespDTO`
  作用：创建或新增业务数据。
- `public void updateIntervieweeForm(UpdateIntervieweeFormReqDTO requestParam)`
  参数：`UpdateIntervieweeFormReqDTO requestParam`
  返回值：`void`
  作用：更新业务数据或名称。
- `public void deleteIntervieweeForm(String id)`
  参数：`String id`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `public IntervieweeFormRespDTO searchIntervieweeFormById(String id)`
  参数：`String id`
  返回值：`IntervieweeFormRespDTO`
  作用：查询并返回业务数据。
- `public List<IntervieweeFormRespDTO> fuzzySearchIntervieweeForm(FuzzySearchIntervieweeFormReqDTO requestParam)`
  参数：`FuzzySearchIntervieweeFormReqDTO requestParam`
  返回值：`List<IntervieweeFormRespDTO>`
  作用：执行检索召回或检索通道处理。
- `public List<IntervieweeFormNameRespDTO> searchIntervieweeFormNameList()`
  参数：无
  返回值：`List<IntervieweeFormNameRespDTO>`
  作用：查询并返回业务数据。
- `private void validFormParam( String formName, String candidateName, String jobIntention, List<String> professionalSkills, List<EducationExperience> educationExperiences, List<WorkExperience> workExperiences, List<ProjectExperience> projectExperiences)`
  参数：`String formName`；`String candidateName`；`String jobIntention`；`List<String> professionalSkills`；`List<EducationExperience> educationExperiences`；`List<WorkExperience> workExperiences`；`List<ProjectExperience> projectExperiences`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private Long parseFormId(String id)`
  参数：`String id`
  返回值：`Long`
  作用：执行该类对应的核心业务动作。
- `private IntervieweeFormDO findIntervieweeFormByIdAndUserId(Long id, Long userId)`
  参数：`Long id`；`Long userId`
  返回值：`IntervieweeFormDO`
  作用：查询并返回业务数据。
- `private void addOrRefreshFormNameCache(Long id, String formName, Long userId, Date createTime)`
  参数：`Long id`；`String formName`；`Long userId`；`Date createTime`
  返回值：`void`
  作用：创建或新增业务数据。
- `private void removeFormNameCache(Long id, Long userId)`
  参数：`Long id`；`Long userId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private IntervieweeFormRespDTO toRespDTO(IntervieweeFormDO intervieweeFormDO)`
  参数：`IntervieweeFormDO intervieweeFormDO`
  返回值：`IntervieweeFormRespDTO`
  作用：执行该类对应的核心业务动作。
- `private List<WorkExperience> defaultIfNull(List<WorkExperience> workExperiences)`
  参数：`List<WorkExperience> workExperiences`
  返回值：`List<WorkExperience>`
  作用：执行该类对应的核心业务动作。
- `private String writeJson(Object value)`
  参数：`Object value`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private List<String> readStringList(String json)`
  参数：`String json`
  返回值：`List<String>`
  作用：执行该类对应的核心业务动作。
- `private List<EducationExperience> readEducationExperiences(String json)`
  参数：`String json`
  返回值：`List<EducationExperience>`
  作用：执行该类对应的核心业务动作。
- `private List<WorkExperience> readWorkExperiences(String json)`
  参数：`String json`
  返回值：`List<WorkExperience>`
  作用：执行该类对应的核心业务动作。
- `private List<ProjectExperience> readProjectExperiences(String json)`
  参数：`String json`
  返回值：`List<ProjectExperience>`
  作用：执行该类对应的核心业务动作。
- `private <T> List<T> readList(String json, TypeReference<List<T>> typeReference)`
  参数：`String json`；`TypeReference<List<T>> typeReference`
  返回值：`<T> List<T>`
  作用：执行该类对应的核心业务动作。

#### UserServiceImpl

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/service/impl/UserServiceImpl.java`

- `public Result<LoginRespDTO> login(String accountId, String password)`
  参数：`String accountId`；`String password`
  返回值：`Result<LoginRespDTO>`
  作用：处理账号认证相关流程。
- `public Result<LogoutRespDTO> logout(String accountId)`
  参数：`String accountId`
  返回值：`Result<LogoutRespDTO>`
  作用：处理账号认证相关流程。
- `public void signUpNewAccount(String accountId, String password, String nickName)`
  参数：`String accountId`；`String password`；`String nickName`
  返回值：`void`
  作用：处理账号认证相关流程。
- `private void cacheLoginInfo(UserAccountDO userAccountDO, String jti)`
  参数：`UserAccountDO userAccountDO`；`String jti`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void cacheNickName(String accountId, String nickName)`
  参数：`String accountId`；`String nickName`
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### AdminPermissionInterceptor

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/interceptor/AdminPermissionInterceptor.java`

- `public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception`
  参数：`HttpServletRequest request`；`HttpServletResponse response`；`Object handler`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private String buildRequestUri(HttpServletRequest request)`
  参数：`HttpServletRequest request`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### JWTInterceptor

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/interceptor/JWTInterceptor.java`

- `public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception`
  参数：`HttpServletRequest request`；`HttpServletResponse response`；`Object handler`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)`
  参数：`HttpServletRequest request`；`HttpServletResponse response`；`Object handler`；`Exception ex`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void writeUnauthorized(HttpServletResponse response, String message) throws IOException`
  参数：`HttpServletResponse response`；`String message`
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### JWTUtil

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/JWTUtil.java`

- `public String generateToken(Long userId, String accountId, String jti)`
  参数：`Long userId`；`String accountId`；`String jti`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public DecodedJWT verifyToken(String token) throws JWTVerificationException`
  参数：`String token`
  返回值：`DecodedJWT`
  作用：执行该类对应的核心业务动作。

#### UserContextNickNameResolver

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/toolkit/UserContextNickNameResolver.java`

- `public void register()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。

### agent 方法

#### AgentController

位置：`agent/src/main/java/com/ycy/aiapplication/controller/AgentController.java`

- `public Result<InterviewQuestionAskRespDTO> generateInterviewQuestion(@RequestBody InterviewQuestionAskReqDTO requestParam)`
  参数：`@RequestBody InterviewQuestionAskReqDTO requestParam`
  返回值：`Result<InterviewQuestionAskRespDTO>`
  作用：执行该类对应的核心业务动作。
- `public Result<List<AnswerEvaluationRespDTO>> generateAnswerEvaluation(@RequestBody AnswerEvaluationReqDTO requestParam)`
  参数：`@RequestBody AnswerEvaluationReqDTO requestParam`
  返回值：`Result<List<AnswerEvaluationRespDTO>>`
  作用：执行该类对应的核心业务动作。
- `public Result<ReportGenerationRespDTO> generateReport(@RequestBody ReportGenerationReqDTO requestParam)`
  参数：`@RequestBody ReportGenerationReqDTO requestParam`
  返回值：`Result<ReportGenerationRespDTO>`
  作用：执行该类对应的核心业务动作。

#### RecordController

位置：`agent/src/main/java/com/ycy/aiapplication/controller/RecordController.java`

- `public Result<List<FuzzySearchInterviewRecordRespDTO>> fuzzySearchInterviewName( @RequestBody FuzzySearchInterviewNameReqDTO requestParam)`
  参数：`@RequestBody FuzzySearchInterviewNameReqDTO requestParam`
  返回值：`Result<List<FuzzySearchInterviewRecordRespDTO>>`
  作用：执行检索召回或检索通道处理。
- `public Result<List<SearchInterviewNameAndIdRespDTO>> searchInterviewNameAndId()`
  参数：无
  返回值：`Result<List<SearchInterviewNameAndIdRespDTO>>`
  作用：查询并返回业务数据。
- `public Result<InterviewRecordRespDTO> searchInterviewRecordById( @RequestBody SearchInterviewRecordByIdReqDTO requestParam)`
  参数：`@RequestBody SearchInterviewRecordByIdReqDTO requestParam`
  返回值：`Result<InterviewRecordRespDTO>`
  作用：查询并返回业务数据。
- `public Result<Void> deleteInterviewRecord(@RequestBody DeleteInterviewRecordReqDTO requestParam)`
  参数：`@RequestBody DeleteInterviewRecordReqDTO requestParam`
  返回值：`Result<Void>`
  作用：删除业务数据或清理资源。

#### AgentAskImpl

位置：`agent/src/main/java/com/ycy/aiapplication/service/Impl/AgentAskImpl.java`

- `public InterviewQuestionAskRespDTO giveInterviewQuestions(InterviewQuestionAskReqDTO requestParam)`
  参数：`InterviewQuestionAskReqDTO requestParam`
  返回值：`InterviewQuestionAskRespDTO`
  作用：执行该类对应的核心业务动作。
- `public AnswerEvaluationRespDTO singleQuestionAnswerEvaluation(QuestionWithAnswer requestParam)`
  参数：`QuestionWithAnswer requestParam`
  返回值：`AnswerEvaluationRespDTO`
  作用：执行该类对应的核心业务动作。
- `public List<AnswerEvaluationRespDTO> answersEvaluationByAsync(List<QuestionWithAnswer> requestParams)`
  参数：`List<QuestionWithAnswer> requestParams`
  返回值：`List<AnswerEvaluationRespDTO>`
  作用：执行该类对应的核心业务动作。
- `public ReportGenerationRespDTO generateInterviewReportAndRecordName(ReportGenerationReqDTO requestParam)`
  参数：`ReportGenerationReqDTO requestParam`
  返回值：`ReportGenerationRespDTO`
  作用：执行该类对应的核心业务动作。
- `private AnswerEvaluationRespDTO evaluateSingleQuestionWithStructuredFlow(QuestionWithAnswer requestParam)`
  参数：`QuestionWithAnswer requestParam`
  返回值：`AnswerEvaluationRespDTO`
  作用：执行该类对应的核心业务动作。
- `private List<AnswerEvaluationRespDTO> evaluateAnswersWithInvokeAll(List<QuestionWithAnswer> requestParams)`
  参数：`List<QuestionWithAnswer> requestParams`
  返回值：`List<AnswerEvaluationRespDTO>`
  作用：执行该类对应的核心业务动作。
- `private AnswerEvaluationRespDTO evaluateQuestionSafely(QuestionWithAnswer requestParam)`
  参数：`QuestionWithAnswer requestParam`
  返回值：`AnswerEvaluationRespDTO`
  作用：执行该类对应的核心业务动作。
- `private void validateQuestionWithAnswer(QuestionWithAnswer requestParam)`
  参数：`QuestionWithAnswer requestParam`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private boolean shouldUseRuleBasedFallback(String answer)`
  参数：`String answer`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private String buildEvaluationInput(InterviewQuestion question, String answer)`
  参数：`InterviewQuestion question`；`String answer`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String requestEvaluationComment(int questionNum, String evaluationInput)`
  参数：`int questionNum`；`String evaluationInput`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private EvaluationScorePayload requestEvaluationScore(int questionNum, String evaluationInput, String answer)`
  参数：`int questionNum`；`String evaluationInput`；`String answer`
  返回值：`EvaluationScorePayload`
  作用：执行该类对应的核心业务动作。
- `private String callModelForMessage(String model, String userContent, int questionNum, String stage, int attempt) throws NoApiKeyException, ApiException, InputRequiredException`
  参数：`String model`；`String userContent`；`int questionNum`；`String stage`；`int attempt`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String parseCommentPayload(String rawContent)`
  参数：`String rawContent`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `private EvaluationScorePayload parseScorePayload(String rawContent)`
  参数：`String rawContent`
  返回值：`EvaluationScorePayload`
  作用：加载、渲染或构建提示词/上下文。
- `private int readRequiredScore(JSONObject jsonObject, String fieldName)`
  参数：`JSONObject jsonObject`；`String fieldName`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private String extractFirstJsonObject(String rawContent)`
  参数：`String rawContent`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String normalizeModelContent(String rawContent)`
  参数：`String rawContent`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private boolean isNonRetryableModelException(Exception ex)`
  参数：`Exception ex`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private EvaluationScorePayload buildHeuristicScorePayload(String answer)`
  参数：`String answer`
  返回值：`EvaluationScorePayload`
  作用：加载、渲染或构建提示词/上下文。
- `private int countTechnicalKeywordHits(String answer)`
  参数：`String answer`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private boolean containsAny(String text, String... fragments)`
  参数：`String text`；`String... fragments`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private int scoreByLength(int length, int shortScore, int mediumScore, int longScore, int longerScore, int richScore)`
  参数：`int length`；`int shortScore`；`int mediumScore`；`int longScore`；`int longerScore`；`int richScore`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private AnswerEvaluationRespDTO buildShortAnswerFallback(QuestionWithAnswer requestParam)`
  参数：`QuestionWithAnswer requestParam`
  返回值：`AnswerEvaluationRespDTO`
  作用：执行该类对应的核心业务动作。
- `private AnswerEvaluationRespDTO buildModelFailureFallback(QuestionWithAnswer requestParam, String reason)`
  参数：`QuestionWithAnswer requestParam`；`String reason`
  返回值：`AnswerEvaluationRespDTO`
  作用：执行该类对应的核心业务动作。
- `private AnswerEvaluationRespDTO buildFallbackEvaluation(QuestionWithAnswer requestParam, String comment, EvaluationScorePayload scorePayload)`
  参数：`QuestionWithAnswer requestParam`；`String comment`；`EvaluationScorePayload scorePayload`
  返回值：`AnswerEvaluationRespDTO`
  作用：执行该类对应的核心业务动作。
- `private List<AnswerEvaluationRespDTO> buildInterruptedBatchFallback(List<QuestionWithAnswer> requestParams)`
  参数：`List<QuestionWithAnswer> requestParams`
  返回值：`List<AnswerEvaluationRespDTO>`
  作用：执行该类对应的核心业务动作。
- `private boolean isFallbackEvaluation(AnswerEvaluationRespDTO dto)`
  参数：`AnswerEvaluationRespDTO dto`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private String truncateForLog(String content)`
  参数：`String content`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String buildFormDescription(IntervieweeForm form)`
  参数：`IntervieweeForm form`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String buildWorkDescription(List<WorkExperience> workExperiences)`
  参数：`List<WorkExperience> workExperiences`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String buildProjectDescription(ProjectExperience projectExperience)`
  参数：`ProjectExperience projectExperience`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private InterviewDimensionScoreDTO calculateDimensionScore(List<AnswerEvaluationRespDTO> list)`
  参数：`List<AnswerEvaluationRespDTO> list`
  返回值：`InterviewDimensionScoreDTO`
  作用：执行该类对应的核心业务动作。
- `private ReportGenerationRespDTO generateInterviewReportWithFutureTasks(ReportGenerationReqDTO requestParam)`
  参数：`ReportGenerationReqDTO requestParam`
  返回值：`ReportGenerationRespDTO`
  作用：执行该类对应的核心业务动作。
- `private JSONObject buildReportInput(List<AnswerEvaluationRespDTO> answerEvaluationRespList, IntervieweeForm form, InterviewDimensionScoreDTO dimensionScoreDTO)`
  参数：`List<AnswerEvaluationRespDTO> answerEvaluationRespList`；`IntervieweeForm form`；`InterviewDimensionScoreDTO dimensionScoreDTO`
  返回值：`JSONObject`
  作用：执行该类对应的核心业务动作。
- `private void sanitizeReportEvaluations(List<AnswerEvaluationRespDTO> answerEvaluationRespList)`
  参数：`List<AnswerEvaluationRespDTO> answerEvaluationRespList`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private AgentInterviewReportDTO waitForReportFuture(Future<AgentInterviewReportDTO> future, IntervieweeForm form, InterviewDimensionScoreDTO dimensionScoreDTO)`
  参数：`Future<AgentInterviewReportDTO> future`；`IntervieweeForm form`；`InterviewDimensionScoreDTO dimensionScoreDTO`
  返回值：`AgentInterviewReportDTO`
  作用：执行该类对应的核心业务动作。
- `private String waitForRecordNameFuture(Future<String> future, IntervieweeForm form)`
  参数：`Future<String> future`；`IntervieweeForm form`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private AgentInterviewReportDTO generateInterviewReportSafely(JSONObject jsonObject, InterviewDimensionScoreDTO dimensionScoreDTO, IntervieweeForm form)`
  参数：`JSONObject jsonObject`；`InterviewDimensionScoreDTO dimensionScoreDTO`；`IntervieweeForm form`
  返回值：`AgentInterviewReportDTO`
  作用：执行该类对应的核心业务动作。
- `private AgentInterviewReportDTO parseInterviewReport(String rawContent, InterviewDimensionScoreDTO dimensionScoreDTO)`
  参数：`String rawContent`；`InterviewDimensionScoreDTO dimensionScoreDTO`
  返回值：`AgentInterviewReportDTO`
  作用：执行该类对应的核心业务动作。
- `private AgentInterviewReportDTO buildFallbackReport(IntervieweeForm form, InterviewDimensionScoreDTO dimensionScoreDTO, String reason)`
  参数：`IntervieweeForm form`；`InterviewDimensionScoreDTO dimensionScoreDTO`；`String reason`
  返回值：`AgentInterviewReportDTO`
  作用：执行该类对应的核心业务动作。
- `private String generateRecordNameSafely(IntervieweeForm form)`
  参数：`IntervieweeForm form`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String normalizeRecordName(String rawContent)`
  参数：`String rawContent`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String buildFallbackRecordName(IntervieweeForm form)`
  参数：`IntervieweeForm form`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private void normalizeEvaluationResp(ApiEvaluationResp apiEvaluationResp)`
  参数：`ApiEvaluationResp apiEvaluationResp`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private int normalizeSingleScore(int score)`
  参数：`int score`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private int normalizeTotalScore(double score)`
  参数：`double score`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private Long parseFormId(String formId)`
  参数：`String formId`
  返回值：`Long`
  作用：执行该类对应的核心业务动作。
- `private List<String> readStringList(String json)`
  参数：`String json`
  返回值：`List<String>`
  作用：执行该类对应的核心业务动作。
- `private List<EducationExperience> readEducationExperiences(String json)`
  参数：`String json`
  返回值：`List<EducationExperience>`
  作用：执行该类对应的核心业务动作。
- `private List<WorkExperience> readWorkExperiences(String json)`
  参数：`String json`
  返回值：`List<WorkExperience>`
  作用：执行该类对应的核心业务动作。
- `private List<ProjectExperience> readProjectExperiences(String json)`
  参数：`String json`
  返回值：`List<ProjectExperience>`
  作用：执行该类对应的核心业务动作。
- `public Thread newThread(Runnable runnable)`
  参数：`Runnable runnable`
  返回值：`Thread`
  作用：执行该类对应的核心业务动作。

#### RecordServiceImpl

位置：`agent/src/main/java/com/ycy/aiapplication/service/Impl/RecordServiceImpl.java`

- `public List<FuzzySearchInterviewRecordRespDTO> fuzzySearchInterviewRecordName(FuzzySearchInterviewNameReqDTO requestParam)`
  参数：`FuzzySearchInterviewNameReqDTO requestParam`
  返回值：`List<FuzzySearchInterviewRecordRespDTO>`
  作用：执行检索召回或检索通道处理。
- `public List<SearchInterviewNameAndIdRespDTO> searchInterviewNameAndId()`
  参数：无
  返回值：`List<SearchInterviewNameAndIdRespDTO>`
  作用：查询并返回业务数据。
- `public InterviewRecordRespDTO searchInterviewRecordById(String id)`
  参数：`String id`
  返回值：`InterviewRecordRespDTO`
  作用：查询并返回业务数据。
- `public void deleteInterviewRecord(String id)`
  参数：`String id`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private InterviewRecordRespDTO toInterviewRecordRespDTO(InterviewRecordDO interviewRecordDO)`
  参数：`InterviewRecordDO interviewRecordDO`
  返回值：`InterviewRecordRespDTO`
  作用：执行该类对应的核心业务动作。
- `private Long parseRecordId(String id)`
  参数：`String id`
  返回值：`Long`
  作用：执行该类对应的核心业务动作。
- `private InterviewRecordDO findInterviewRecordByIdAndUserId(Long recordId, Long userId)`
  参数：`Long recordId`；`Long userId`
  返回值：`InterviewRecordDO`
  作用：查询并返回业务数据。
- `private void removeInterviewRecordCache(Long recordId, Long userId)`
  参数：`Long recordId`；`Long userId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private RecordCacheInfo parseRecordCache(String cacheValue, Double score)`
  参数：`String cacheValue`；`Double score`
  返回值：`RecordCacheInfo`
  作用：执行该类对应的核心业务动作。

### framework 方法

#### IdempotentConsumeAspect

位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/aspects/IdempotentConsumeAspect.java`

- `public Object idempotentConsume(ProceedingJoinPoint joinPoint) throws Throwable`
  参数：`ProceedingJoinPoint joinPoint`
  返回值：`Object`
  作用：执行该类对应的核心业务动作。

#### IdempotentSubmitAspect

位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/aspects/IdempotentSubmitAspect.java`

- `public Object noDuplicateSubmit(ProceedingJoinPoint joinPoint)throws Throwable`
  参数：`ProceedingJoinPoint joinPoint`
  返回值：`Object`
  作用：执行该类对应的核心业务动作。
- `private String calArgsMD5(ProceedingJoinPoint joinPoint)`
  参数：`ProceedingJoinPoint joinPoint`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private Object lightweightArg(Object arg)`
  参数：`Object arg`
  返回值：`Object`
  作用：执行该类对应的核心业务动作。
- `private Map<String, Object> lightweightMultipartFile(MultipartFile file)`
  参数：`MultipartFile file`
  返回值：`Map<String, Object>`
  作用：执行该类对应的核心业务动作。

#### SpELUtil

位置：`framework/src/main/java/com/ycy/aiapplication/framework/idempotent/utils/SpELUtil.java`

- `public static Object parseKey(String spEL, Method method,Object[] contextObj)`
  参数：`String spEL`；`Method method`；`Object[] contextObj`
  返回值：`Object`
  作用：执行该类对应的核心业务动作。
- `public static Object parse(String spEL, Method method ,Object[]contextObj)`
  参数：`String spEL`；`Method method`；`Object[]contextObj`
  返回值：`Object`
  作用：执行该类对应的核心业务动作。

#### GlobalExceptionHandler

位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/GlobalExceptionHandler.java`

- `public Result validExceptionHandler(HttpServletRequest request , MethodArgumentNotValidException ex)`
  参数：`HttpServletRequest request`；`MethodArgumentNotValidException ex`
  返回值：`Result`
  作用：执行该类对应的核心业务动作。
- `public Result abstractException(HttpServletRequest request,AbstractException ex)`
  参数：`HttpServletRequest request`；`AbstractException ex`
  返回值：`Result`
  作用：执行该类对应的核心业务动作。
- `public Result defaultErrorHandler(HttpServletRequest request,Throwable throwable)`
  参数：`HttpServletRequest request`；`Throwable throwable`
  返回值：`Result`
  作用：执行该类对应的核心业务动作。

### knowledge 方法

#### ChunkingMode

位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/ChunkingMode.java`

- `public ChunkingOptions createOptions(Map<String, Object> config)`
  参数：`Map<String`；`Object> config`
  返回值：`ChunkingOptions`
  作用：创建或新增业务数据。
- `public ChunkingOptions createDefaultOptions(Integer targetSize, Integer overlapSize)`
  参数：`Integer targetSize`；`Integer overlapSize`
  返回值：`ChunkingOptions`
  作用：创建或新增业务数据。
- `public ChunkingOptions createOptions(Map<String, Object> config)`
  参数：`Map<String`；`Object> config`
  返回值：`ChunkingOptions`
  作用：创建或新增业务数据。
- `public ChunkingOptions createDefaultOptions(Integer targetSize, Integer overlapSize)`
  参数：`Integer targetSize`；`Integer overlapSize`
  返回值：`ChunkingOptions`
  作用：创建或新增业务数据。
- `public abstract ChunkingOptions createOptions(Map<String, Object> config)`
  参数：`Map<String`；`Object> config`
  返回值：`abstract ChunkingOptions`
  作用：创建或新增业务数据。
- `public abstract ChunkingOptions createDefaultOptions(Integer targetSize, Integer overlapSize)`
  参数：`Integer targetSize`；`Integer overlapSize`
  返回值：`abstract ChunkingOptions`
  作用：创建或新增业务数据。
- `public static ChunkingMode fromValue(String value)`
  参数：`String value`
  返回值：`ChunkingMode`
  作用：执行该类对应的核心业务动作。
- `private static String normalize(String value)`
  参数：`String value`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### ChunkingStrategyFactory

位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/ChunkingStrategyFactory.java`

- `public Optional<ChunkingStrategy> findStrategy(ChunkingMode type)`
  参数：`ChunkingMode type`
  返回值：`Optional<ChunkingStrategy>`
  作用：查询并返回业务数据。
- `public ChunkingStrategy requireStrategy(ChunkingMode type)`
  参数：`ChunkingMode type`
  返回值：`ChunkingStrategy`
  作用：执行该类对应的核心业务动作。

#### FixedSizeTextChunker

位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/strategy/impl/FixedSizeTextChunker.java`

- `public List<VectorChunk> chunk(String text, ChunkingOptions config)`
  参数：`String text`；`ChunkingOptions config`
  返回值：`List<VectorChunk>`
  作用：执行该类对应的核心业务动作。
- `private int adjustToBoundary(String text, int start, int targetEnd, int overlap)`
  参数：`String text`；`int start`；`int targetEnd`；`int overlap`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private String normalizeText(String text)`
  参数：`String text`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private boolean shouldJoinBrokenUrl(char prev, char next, String s, int nextIndex)`
  参数：`char prev`；`char next`；`String s`；`int nextIndex`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private boolean isListItemStart(String s, int i)`
  参数：`String s`；`int i`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private boolean looksLikeUrlStart(String s, int i)`
  参数：`String s`；`int i`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private boolean isUrlChar(char c)`
  参数：`char c`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private boolean isCommonUrlPunct(char c)`
  参数：`char c`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private boolean isCjkWordChar(char c)`
  参数：`char c`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private boolean isCjkOrFullWidthLetterOrDigit(char c)`
  参数：`char c`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private boolean isCjkPunctuation(char c)`
  参数：`char c`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。

#### StructureAwareTextChunker

位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/strategy/impl/StructureAwareTextChunker.java`

- `public List<VectorChunk> chunk(String text, ChunkingOptions config)`
  参数：`String text`；`ChunkingOptions config`
  返回值：`List<VectorChunk>`
  作用：执行该类对应的核心业务动作。
- `private List<Block> segmentToBlocks(String text)`
  参数：`String text`
  返回值：`List<Block>`
  作用：执行该类对应的核心业务动作。
- `private List<Block> coalesceTrailingBlanks(List<Block> blocks, String text)`
  参数：`List<Block> blocks`；`String text`
  返回值：`List<Block>`
  作用：执行该类对应的核心业务动作。
- `private List<int[]> packBlocksToChunks(List<Block> blocks, int textLen, int min, int target, int max)`
  参数：`List<Block> blocks`；`int textLen`；`int min`；`int target`；`int max`
  返回值：`List<int[]>`
  作用：执行该类对应的核心业务动作。
- `private List<VectorChunk> materialize(String text, List<int[]> ranges, int overlap)`
  参数：`String text`；`List<int[]> ranges`；`int overlap`
  返回值：`List<VectorChunk>`
  作用：执行该类对应的核心业务动作。
- `private int indexOfNl(String s, int from)`
  参数：`String s`；`int from`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private String trimRightKeepLeft(String s)`
  参数：`String s`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private boolean isAllBlank(String s, int from, int to)`
  参数：`String s`；`int from`；`int to`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private String tailByChars(String s, int n)`
  参数：`String s`；`int n`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### KnowledgeBaseController

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/controller/KnowledgeBaseController.java`

- `public Result<String> createKnowledgeBase(@RequestBody KnowledgeBaseCreateRequest requestParam)`
  参数：`@RequestBody KnowledgeBaseCreateRequest requestParam`
  返回值：`Result<String>`
  作用：创建或新增业务数据。
- `public Result<Void> updateKnowledgeBase(@RequestBody KnowledgeBaseUpdateRequest requestParam)`
  参数：`@RequestBody KnowledgeBaseUpdateRequest requestParam`
  返回值：`Result<Void>`
  作用：更新业务数据或名称。
- `public Result<Void> renameKnowledgeBase(@RequestBody KnowledgeBaseUpdateRequest requestParam)`
  参数：`@RequestBody KnowledgeBaseUpdateRequest requestParam`
  返回值：`Result<Void>`
  作用：更新业务数据或名称。
- `public Result<IPage<KnowledgeBaseVO>> pageQuery(KnowledgeBasePageRequest requestParam)`
  参数：`KnowledgeBasePageRequest requestParam`
  返回值：`Result<IPage<KnowledgeBaseVO>>`
  作用：查询并返回业务数据。
- `public Result<Map<String,String>> embeddingModelConfigGet()`
  参数：无
  返回值：`Result<Map<String,String>>`
  作用：调用 embedding 能力生成向量。

#### KnowledgeDocumentController

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/controller/KnowledgeDocumentController.java`

- `public Result<Void> update(@PathVariable String docId, @RequestBody KnowledgeDocumentUpdateRequest requestParam)`
  参数：`@PathVariable String docId`；`@RequestBody KnowledgeDocumentUpdateRequest requestParam`
  返回值：`Result<Void>`
  作用：更新业务数据或名称。

#### KnowledgeDocumentAsyncChunkEventConsumer

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/consumer/KnowledgeDocumentAsyncChunkEventConsumer.java`

- `public void onMessage(MessageWrapper<KnowledgeDocumentAsyncChunkEvent> messageWrapper)`
  参数：`MessageWrapper<KnowledgeDocumentAsyncChunkEvent> messageWrapper`
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### KnowledgeDocumentAsyncChunkProducer

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/producer/KnowledgeDocumentAsyncChunkProducer.java`

- `protected BaseSendExtendDTO buildBaseSendExtendDTO(KnowledgeDocumentAsyncChunkEvent messageSendEvent)`
  参数：`KnowledgeDocumentAsyncChunkEvent messageSendEvent`
  返回值：`BaseSendExtendDTO`
  作用：执行该类对应的核心业务动作。
- `protected Message<?> buildMessage(KnowledgeDocumentAsyncChunkEvent messageSendEvent, BaseSendExtendDTO requestParam)`
  参数：`KnowledgeDocumentAsyncChunkEvent messageSendEvent`；`BaseSendExtendDTO requestParam`
  返回值：`Message<?>`
  作用：执行该类对应的核心业务动作。

#### KnowledgeBaseServiceImpl

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/impl/KnowledgeBaseServiceImpl.java`

- `public String create(KnowledgeBaseCreateRequest requestParam)`
  参数：`KnowledgeBaseCreateRequest requestParam`
  返回值：`String`
  作用：创建或新增业务数据。
- `public void update(KnowledgeBaseUpdateRequest requestParam)`
  参数：`KnowledgeBaseUpdateRequest requestParam`
  返回值：`void`
  作用：更新业务数据或名称。
- `public void rename(KnowledgeBaseUpdateRequest requestParam)`
  参数：`KnowledgeBaseUpdateRequest requestParam`
  返回值：`void`
  作用：更新业务数据或名称。
- `public void delete(String kbId)`
  参数：`String kbId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `public KnowledgeBaseVO queryById(String kbId)`
  参数：`String kbId`
  返回值：`KnowledgeBaseVO`
  作用：查询并返回业务数据。
- `public IPage<KnowledgeBaseVO> pageQuery(KnowledgeBasePageRequest requestParam)`
  参数：`KnowledgeBasePageRequest requestParam`
  返回值：`IPage<KnowledgeBaseVO>`
  作用：查询并返回业务数据。
- `private String normalizeName(String name)`
  参数：`String name`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private void checkNameUnique(String name, String excludeId)`
  参数：`String name`；`String excludeId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private boolean hasVectorizedDocument(String kbId)`
  参数：`String kbId`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private Long queryDocumentCount(String kbId)`
  参数：`String kbId`
  返回值：`Long`
  作用：查询并返回业务数据。
- `private Map<String, Long> countDocumentsByKbIds(List<KnowledgeBaseDO> knowledgeBases)`
  参数：`List<KnowledgeBaseDO> knowledgeBases`
  返回值：`Map<String, Long>`
  作用：执行该类对应的核心业务动作。
- `private String buildCollectionName()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String currentOperator()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### KnowledgeChunkServiceImpl

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/impl/KnowledgeChunkServiceImpl.java`

- `public Boolean existsByDocId(String docId)`
  参数：`String docId`
  返回值：`Boolean`
  作用：执行该类对应的核心业务动作。
- `public IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam)`
  参数：`String docId`；`KnowledgeChunkPageRequest requestParam`
  返回值：`IPage<KnowledgeChunkVO>`
  作用：查询并返回业务数据。
- `public void delete(String docId, String chunkId)`
  参数：`String docId`；`String chunkId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `public void enableChunk(String docId, String chunkId, boolean enabled)`
  参数：`String docId`；`String chunkId`；`boolean enabled`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `public void batchEnable(String docId, KnowledgeChunkBatchRequest requestParam)`
  参数：`String docId`；`KnowledgeChunkBatchRequest requestParam`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `public void batchDisable(String docId, KnowledgeChunkBatchRequest requestParam)`
  参数：`String docId`；`KnowledgeChunkBatchRequest requestParam`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `public void rebuildByDocId(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：触发异步处理或重建流程。
- `public void updateEnabledByDocId(String docId, boolean enabled)`
  参数：`String docId`；`boolean enabled`
  返回值：`void`
  作用：更新业务数据或名称。
- `public List<KnowledgeChunkVO> listByDocId(String docId)`
  参数：`String docId`
  返回值：`List<KnowledgeChunkVO>`
  作用：查询并返回业务数据。
- `public void deleteByDocId(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private void batchUpdateEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled)`
  参数：`String docId`；`KnowledgeChunkBatchRequest requestParam`；`boolean enabled`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `private void doRebuildByDocId(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private List<KnowledgeChunkDO> resolveTargetChunks(String docId, KnowledgeChunkBatchRequest requestParam)`
  参数：`String docId`；`KnowledgeChunkBatchRequest requestParam`
  返回值：`List<KnowledgeChunkDO>`
  作用：执行意图识别、解析或决策。
- `private void validateDocumentEnabledForChunkEnable(KnowledgeDocumentDO documentDO, boolean enableChunk)`
  参数：`KnowledgeDocumentDO documentDO`；`boolean enableChunk`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void syncChunkToVector(KnowledgeBaseDO kbDO, KnowledgeDocumentDO documentDO, KnowledgeChunkDO chunkDO)`
  参数：`KnowledgeBaseDO kbDO`；`KnowledgeDocumentDO documentDO`；`KnowledgeChunkDO chunkDO`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void deleteChunkVectorQuietly(String collectionName, String chunkId)`
  参数：`String collectionName`；`String chunkId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private void deleteDocumentVectorsQuietly(String collectionName, String docId)`
  参数：`String collectionName`；`String docId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private void updateDocumentChunkCount(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：更新业务数据或名称。
- `private KnowledgeChunkVO toChunkVO(KnowledgeChunkDO chunkDO)`
  参数：`KnowledgeChunkDO chunkDO`
  返回值：`KnowledgeChunkVO`
  作用：执行该类对应的核心业务动作。
- `private float[] toPrimitiveArray(List<Float> embedding)`
  参数：`List<Float> embedding`
  返回值：`float[]`
  作用：执行该类对应的核心业务动作。
- `private String currentOperator()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### KnowledgeDocumentServiceImpl

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/service/impl/KnowledgeDocumentServiceImpl.java`

- `public KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file)`
  参数：`String kbId`；`KnowledgeDocumentUploadRequest requestParam`；`MultipartFile file`
  返回值：`KnowledgeDocumentVO`
  作用：上传并登记文档或文件。
- `public void delete(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `public void startChunk(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：触发异步处理或重建流程。
- `public void executeChunk(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void update(String docId, KnowledgeDocumentUpdateRequest requestParam)`
  参数：`String docId`；`KnowledgeDocumentUpdateRequest requestParam`
  返回值：`void`
  作用：更新业务数据或名称。
- `public IPage<KnowledgeDocumentVO> page(String kbId, KnowledgeDocumentPageRequest requestParam)`
  参数：`String kbId`；`KnowledgeDocumentPageRequest requestParam`
  返回值：`IPage<KnowledgeDocumentVO>`
  作用：查询并返回业务数据。
- `public void enable(String docId, boolean enabled)`
  参数：`String docId`；`boolean enabled`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `public List<KnowledgeDocumentSearchVO> search(String keyword, int limit)`
  参数：`String keyword`；`int limit`
  返回值：`List<KnowledgeDocumentSearchVO>`
  作用：查询并返回业务数据。
- `private KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`KnowledgeDocumentVO`
  作用：执行该类对应的核心业务动作。
- `private KnowledgeDocumentChunkLogVO toChunkLogVO(KnowledgeDocumentChunkLogDO logDO)`
  参数：`KnowledgeDocumentChunkLogDO logDO`
  返回值：`KnowledgeDocumentChunkLogVO`
  作用：执行该类对应的核心业务动作。
- `private List<VectorChunk> doChunk(KnowledgeDocumentDO documentDO, String extractedText)`
  参数：`KnowledgeDocumentDO documentDO`；`String extractedText`
  返回值：`List<VectorChunk>`
  作用：执行该类对应的核心业务动作。
- `private void enrichEmbedding(KnowledgeBaseDO kbDO, List<VectorChunk> chunks)`
  参数：`KnowledgeBaseDO kbDO`；`List<VectorChunk> chunks`
  返回值：`void`
  作用：调用 embedding 能力生成向量。
- `private void replaceDocumentVectors(String collectionName, String docId, List<VectorChunk> chunks)`
  参数：`String collectionName`；`String docId`；`List<VectorChunk> chunks`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void deleteDocumentVectorsQuietly(String collectionName, String docId)`
  参数：`String collectionName`；`String docId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private void replaceDocumentChunks(KnowledgeBaseDO kbDO, KnowledgeDocumentDO documentDO, List<VectorChunk> chunks)`
  参数：`KnowledgeBaseDO kbDO`；`KnowledgeDocumentDO documentDO`；`List<VectorChunk> chunks`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `protected void deleteDocumentChunksQuietly(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private KnowledgeChunkDO buildChunkDO(KnowledgeBaseDO kbDO, KnowledgeDocumentDO documentDO, VectorChunk chunk, String operator)`
  参数：`KnowledgeBaseDO kbDO`；`KnowledgeDocumentDO documentDO`；`VectorChunk chunk`；`String operator`
  返回值：`KnowledgeChunkDO`
  作用：执行该类对应的核心业务动作。
- `private float[] toPrimitiveArray(List<Float> embedding)`
  参数：`List<Float> embedding`
  返回值：`float[]`
  作用：执行该类对应的核心业务动作。
- `private String extractDocumentText(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String extractTextWithTimeout(DocumentParser parser, Path tempFile, String fileName, String docId)`
  参数：`DocumentParser parser`；`Path tempFile`；`String fileName`；`String docId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private Path downloadSourceToTempFile(KnowledgeDocumentDO documentDO, String fileName) throws IOException`
  参数：`KnowledgeDocumentDO documentDO`；`String fileName`
  返回值：`Path`
  作用：加载、渲染或构建提示词/上下文。
- `private String resolveTempFileSuffix(String fileName)`
  参数：`String fileName`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private void deleteTempFileQuietly(Path tempFile, String docId, String fileName)`
  参数：`Path tempFile`；`String docId`；`String fileName`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private InputStream openSourceStream(KnowledgeDocumentDO documentDO) throws IOException`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`InputStream`
  作用：发起聊天或流式输出处理。
- `private DocumentParser selectParser(String fileName, String mimeType)`
  参数：`String fileName`；`String mimeType`
  返回值：`DocumentParser`
  作用：执行该类对应的核心业务动作。
- `private String probeMimeType(KnowledgeDocumentDO documentDO, String fileName)`
  参数：`KnowledgeDocumentDO documentDO`；`String fileName`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String resolveSourceFileName(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private String resolveObjectKey(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private void markDocumentRunning(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void markDocumentPending(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void markDocumentFailed(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private KnowledgeDocumentChunkLogDO createRunningChunkLog(KnowledgeDocumentDO documentDO)`
  参数：`KnowledgeDocumentDO documentDO`
  返回值：`KnowledgeDocumentChunkLogDO`
  作用：创建或新增业务数据。
- `private void fillSuccessLog(KnowledgeDocumentChunkLogDO chunkLogDO, long extractDuration, long chunkDuration, long embedDuration, long persistDuration, int chunkCount, long totalStart)`
  参数：`KnowledgeDocumentChunkLogDO chunkLogDO`；`long extractDuration`；`long chunkDuration`；`long embedDuration`；`long persistDuration`；`int chunkCount`；`long totalStart`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void fillFailedLog(KnowledgeDocumentChunkLogDO chunkLogDO, long extractDuration, long chunkDuration, long embedDuration, long persistDuration, long totalStart, Exception ex)`
  参数：`KnowledgeDocumentChunkLogDO chunkLogDO`；`long extractDuration`；`long chunkDuration`；`long embedDuration`；`long persistDuration`；`long totalStart`；`Exception ex`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private AliOSSUtils.StoredObject uploadFileIfNecessary(SourceType sourceType, String kbId, MultipartFile file)`
  参数：`SourceType sourceType`；`String kbId`；`MultipartFile file`
  返回值：`AliOSSUtils.StoredObject`
  作用：上传并登记文档或文件。
- `private Long resolveFileSize(SourceType sourceType, MultipartFile file)`
  参数：`SourceType sourceType`；`MultipartFile file`
  返回值：`Long`
  作用：执行意图识别、解析或决策。
- `private String resolveFileType(SourceType sourceType, KnowledgeDocumentUploadRequest requestParam, MultipartFile file)`
  参数：`SourceType sourceType`；`KnowledgeDocumentUploadRequest requestParam`；`MultipartFile file`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private String buildDocumentName(SourceType sourceType, KnowledgeDocumentUploadRequest requestParam, MultipartFile file)`
  参数：`SourceType sourceType`；`KnowledgeDocumentUploadRequest requestParam`；`MultipartFile file`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private SourceType normalizeSourceType(String sourceTypeValue, MultipartFile file)`
  参数：`String sourceTypeValue`；`MultipartFile file`
  返回值：`SourceType`
  作用：执行该类对应的核心业务动作。
- `private ProcessMode normalizeProcessMode(String processModeValue)`
  参数：`String processModeValue`
  返回值：`ProcessMode`
  作用：执行该类对应的核心业务动作。
- `private ChunkingMode normalizeChunkingMode(String chunkStrategyValue)`
  参数：`String chunkStrategyValue`
  返回值：`ChunkingMode`
  作用：执行该类对应的核心业务动作。
- `private String normalizeChunkConfig(String chunkConfig, ChunkingMode chunkingMode)`
  参数：`String chunkConfig`；`ChunkingMode chunkingMode`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private Map<String, Object> parseChunkConfig(String chunkConfig)`
  参数：`String chunkConfig`
  返回值：`Map<String, Object>`
  作用：执行该类对应的核心业务动作。
- `private String currentOperator()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private boolean docExists(String docId)`
  参数：`String docId`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。

#### AliOSSUtils

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/toolkit/AliOSSUtils.java`

- `public StoredObject upload(MultipartFile file, String kbId, long maxBytes)`
  参数：`MultipartFile file`；`String kbId`；`long maxBytes`
  返回值：`StoredObject`
  作用：上传并登记文档或文件。
- `public StoredObject doUpload(MultipartFile file, String kbId)`
  参数：`MultipartFile file`；`String kbId`
  返回值：`StoredObject`
  作用：加载、渲染或构建提示词/上下文。
- `public StoredObject doUploadWithLimit(MultipartFile file, String kbId, long maxBytes)`
  参数：`MultipartFile file`；`String kbId`；`long maxBytes`
  返回值：`StoredObject`
  作用：加载、渲染或构建提示词/上下文。
- `private StoredObject doUploadInternal(MultipartFile file, String kbId, Long maxBytes)`
  参数：`MultipartFile file`；`String kbId`；`Long maxBytes`
  返回值：`StoredObject`
  作用：加载、渲染或构建提示词/上下文。
- `private void validateFile(MultipartFile file, String kbId, Long maxBytes)`
  参数：`MultipartFile file`；`String kbId`；`Long maxBytes`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private String buildFileName(MultipartFile file, String kbId)`
  参数：`MultipartFile file`；`String kbId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String extractExtension(String originalFileName)`
  参数：`String originalFileName`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String buildFileUrl(String fileName)`
  参数：`String fileName`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### DocumentParserSelector

位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/DocumentParserSelector.java`

- `public DocumentParser select(String parserType)`
  参数：`String parserType`
  返回值：`DocumentParser`
  作用：执行该类对应的核心业务动作。
- `public DocumentParser selectByMimeType(String mimeType)`
  参数：`String mimeType`
  返回值：`DocumentParser`
  作用：执行该类对应的核心业务动作。

#### MarkdownDocumentParser

位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/parser/impl/MarkdownDocumentParser.java`

- `public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options)`
  参数：`byte[] content`；`String mimeType`；`Map<String`；`Object> options`
  返回值：`ParseResult`
  作用：执行该类对应的核心业务动作。
- `public String extractText(InputStream stream, String fileName)`
  参数：`InputStream stream`；`String fileName`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public boolean supports(String mimeType)`
  参数：`String mimeType`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。

#### TikaDocumentParser

位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/parser/impl/TikaDocumentParser.java`

- `public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options)`
  参数：`byte[] content`；`String mimeType`；`Map<String`；`Object> options`
  返回值：`ParseResult`
  作用：执行该类对应的核心业务动作。
- `public String extractText(InputStream stream, String fileName)`
  参数：`InputStream stream`；`String fileName`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public boolean supports(String mimeType)`
  参数：`String mimeType`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private String parseText(InputStream stream, String mimeType, String fileName) throws Exception`
  参数：`InputStream stream`；`String mimeType`；`String fileName`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### TextCleanupUtil

位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/toolkit/TextCleanupUtil.java`

- `public static String cleanup(String text)`
  参数：`String text`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public static String cleanup(String text, boolean removeBOM, boolean trimTrailingSpaces, boolean compressEmptyLines, int maxConsecutiveLines)`
  参数：`String text`；`boolean removeBOM`；`boolean trimTrailingSpaces`；`boolean compressEmptyLines`；`int maxConsecutiveLines`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### MilvusVectorStoreAdmin

位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/impl/MilvusVectorStoreAdmin.java`

- `public void ensureVectorSpace(VectorSpaceSpec spec)`
  参数：`VectorSpaceSpec spec`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public boolean vectorSpaceExists(VectorSpaceId spaceId)`
  参数：`VectorSpaceId spaceId`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public void deleteVectorSpace(VectorSpaceId spaceId)`
  参数：`VectorSpaceId spaceId`
  返回值：`void`
  作用：删除业务数据或清理资源。

#### MilvusVectorStoreService

位置：`knowledge/src/main/java/com/ycy/aiapplication/vector/impl/MilvusVectorStoreService.java`

- `public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks)`
  参数：`String collectionName`；`String docId`；`List<VectorChunk> chunks`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void updateChunk(String collectionName, String docId, VectorChunk chunk)`
  参数：`String collectionName`；`String docId`；`VectorChunk chunk`
  返回值：`void`
  作用：更新业务数据或名称。
- `public void deleteDocumentVectors(String collectionName, String docId)`
  参数：`String collectionName`；`String docId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `public void deleteChunkById(String collectionName, String chunkId)`
  参数：`String collectionName`；`String chunkId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private JsonObject buildRow(String collectionName, String docId, VectorChunk chunk)`
  参数：`String collectionName`；`String docId`；`VectorChunk chunk`
  返回值：`JsonObject`
  作用：执行该类对应的核心业务动作。
- `private float[] extractVector(VectorChunk chunk, int expectedDim)`
  参数：`VectorChunk chunk`；`int expectedDim`
  返回值：`float[]`
  作用：执行该类对应的核心业务动作。
- `private String normalizeContent(String content)`
  参数：`String content`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private JsonArray toJsonArray(float[] vector)`
  参数：`float[] vector`
  返回值：`JsonArray`
  作用：执行该类对应的核心业务动作。
- `private JsonObject buildMetadata(String collectionName, String docId, VectorChunk chunk)`
  参数：`String collectionName`；`String docId`；`VectorChunk chunk`
  返回值：`JsonObject`
  作用：执行该类对应的核心业务动作。
- `private String buildDocumentFilter(String docId)`
  参数：`String docId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String buildChunkFilter(String chunkId)`
  参数：`String chunkId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private void validateCollectionName(String collectionName)`
  参数：`String collectionName`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void validateDocId(String docId)`
  参数：`String docId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。

### infrastructure-ai 方法

#### AbstractOpenAIStyleChatClient

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/client/AbstractOpenAIStyleChatClient.java`

- `public String chat(ChatRequest request, ModelTarget target)`
  参数：`ChatRequest request`；`ModelTarget target`
  返回值：`String`
  作用：发起聊天或流式输出处理。
- `public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target)`
  参数：`ChatRequest request`；`StreamCallback callback`；`ModelTarget target`
  返回值：`StreamCancellationHandle`
  作用：发起聊天或流式输出处理。
- `protected void doStream(Call call, StreamCallback callback, AtomicBoolean cancelled, boolean reasoningEnabled)`
  参数：`Call call`；`StreamCallback callback`；`AtomicBoolean cancelled`；`boolean reasoningEnabled`
  返回值：`void`
  作用：发起聊天或流式输出处理。
- `protected Request buildChatRequest(ChatRequest request, ModelTarget target, boolean stream)`
  参数：`ChatRequest request`；`ModelTarget target`；`boolean stream`
  返回值：`Request`
  作用：发起聊天或流式输出处理。
- `protected JsonObject buildRequestBody(ChatRequest request, ModelTarget target, boolean stream)`
  参数：`ChatRequest request`；`ModelTarget target`；`boolean stream`
  返回值：`JsonObject`
  作用：执行该类对应的核心业务动作。
- `protected void applyThinking(JsonObject requestBody, ChatRequest request, boolean stream)`
  参数：`JsonObject requestBody`；`ChatRequest request`；`boolean stream`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `protected JsonArray buildMessages(List<ChatMessage> messages)`
  参数：`List<ChatMessage> messages`
  返回值：`JsonArray`
  作用：执行该类对应的核心业务动作。
- `protected String toRole(ChatMessage.Role role)`
  参数：`ChatMessage.Role role`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `protected String resolveUrl(ModelTarget target)`
  参数：`ModelTarget target`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `protected String resolveApiKey(String provider)`
  参数：`String provider`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `protected JsonObject parseJsonBody(ResponseBody body) throws IOException`
  参数：`ResponseBody body`
  返回值：`JsonObject`
  作用：执行该类对应的核心业务动作。
- `protected String readBody(ResponseBody body) throws IOException`
  参数：`ResponseBody body`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `protected String extractChatContent(JsonObject root)`
  参数：`JsonObject root`
  返回值：`String`
  作用：发起聊天或流式输出处理。
- `protected ModelClientErrorType classifyStatus(int statusCode)`
  参数：`int statusCode`
  返回值：`ModelClientErrorType`
  作用：执行意图识别、解析或决策。

#### BaiLianChatClient

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/client/BaiLianChatClient.java`

- `public String provider()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `protected void applyThinking(JsonObject requestBody, ChatRequest request, boolean stream)`
  参数：`JsonObject requestBody`；`ChatRequest request`；`boolean stream`
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### SiliconFlowChatClient

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/client/SiliconFlowChatClient.java`

- `public String provider()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### RoutingLLMService

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/impl/service/RoutingLLMService.java`

- `public String chat(ChatRequest request)`
  参数：`ChatRequest request`
  返回值：`String`
  作用：发起聊天或流式输出处理。
- `public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback)`
  参数：`ChatRequest request`；`StreamCallback callback`
  返回值：`StreamCancellationHandle`
  作用：发起聊天或流式输出处理。
- `private FirstPacketAwaiter.Result awaitFirstPacket( FirstPacketAwaiter awaiter, StreamCancellationHandle handle, StreamCallback callback)`
  参数：`FirstPacketAwaiter awaiter`；`StreamCancellationHandle handle`；`StreamCallback callback`
  返回值：`FirstPacketAwaiter.Result`
  作用：执行该类对应的核心业务动作。
- `private Throwable mapStreamFailure(FirstPacketAwaiter.Result result)`
  参数：`FirstPacketAwaiter.Result result`
  返回值：`Throwable`
  作用：发起聊天或流式输出处理。
- `public void onContent(String content)`
  参数：`String content`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void onThinking(String content)`
  参数：`String content`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void onComplete()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void onError(Throwable error)`
  参数：`Throwable error`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void commit()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void bufferOrDispatch(BufferedEvent event)`
  参数：`BufferedEvent event`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void dispatch(BufferedEvent event)`
  参数：`BufferedEvent event`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private record BufferedEvent(EventType type, String content, Throwable error)`
  参数：`EventType type`；`String content`；`Throwable error`
  返回值：`record`
  作用：执行该类对应的核心业务动作。
- `private static BufferedEvent content(String content)`
  参数：`String content`
  返回值：`BufferedEvent`
  作用：执行该类对应的核心业务动作。
- `private static BufferedEvent thinking(String content)`
  参数：`String content`
  返回值：`BufferedEvent`
  作用：执行该类对应的核心业务动作。
- `private static BufferedEvent complete()`
  参数：无
  返回值：`BufferedEvent`
  作用：发送 SSE 事件或结束流式响应。
- `private static BufferedEvent error(Throwable error)`
  参数：`Throwable error`
  返回值：`BufferedEvent`
  作用：执行该类对应的核心业务动作。

#### ChatModelRegistry

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/ChatModelRegistry.java`

- `public String resolveConfigName(String value)`
  参数：`String value`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `public String resolveDefaultConfigName(String provider)`
  参数：`String provider`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `public String resolveProvider(String alias)`
  参数：`String alias`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `public Map<String, String> allModels()`
  参数：无
  返回值：`Map<String, String>`
  作用：执行该类对应的核心业务动作。
- `public String normalize(String value)`
  参数：`String value`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### ChatModelSelector

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/ChatModelSelector.java`

- `public List<ModelTarget> select(ChatRequest request)`
  参数：`ChatRequest request`
  返回值：`List<ModelTarget>`
  作用：执行该类对应的核心业务动作。
- `private String resolvePrimaryConfigName(ChatRequest request)`
  参数：`ChatRequest request`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private void addFallbackTarget(List<ModelTarget> targets, Set<String> ids, String primaryConfigName)`
  参数：`List<ModelTarget> targets`；`Set<String> ids`；`String primaryConfigName`
  返回值：`void`
  作用：创建或新增业务数据。
- `private void addConfiguredTarget(List<ModelTarget> targets, Set<String> ids, String configName)`
  参数：`List<ModelTarget> targets`；`Set<String> ids`；`String configName`
  返回值：`void`
  作用：创建或新增业务数据。
- `private void addTarget(List<ModelTarget> targets, Set<String> ids, String configName, String provider, String model)`
  参数：`List<ModelTarget> targets`；`Set<String> ids`；`String configName`；`String provider`；`String model`
  返回值：`void`
  作用：创建或新增业务数据。

#### FirstPacketAwaiter

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/FirstPacketAwaiter.java`

维护指引：该类只负责首包探测结果等待，不负责缓冲事件回放；缓冲与提交逻辑仍在 `RoutingLLMService.ProbeBufferingCallback` 中。首包结果必须保持 first-wins：`markContent`、`markComplete`、`markError` 只能通过 `CompletableFuture.complete(Result.xxx())` 尝试完成同一个 `Result`，不要再拆成多个独立 `AtomicBoolean`/`AtomicReference` 组合状态，避免后续回调覆盖已确定的首包判定。

- `public void markContent()`
  参数：无
  返回值：`void`
  作用：收到正文或 thinking 增量时标记首包探测成功。
- `public void markComplete()`
  参数：无
  返回值：`void`
  作用：流在首个有效内容前完成时标记为无内容结果。
- `public void markError(Throwable throwable)`
  参数：`Throwable throwable`
  返回值：`void`
  作用：流在首包探测成功前失败时标记为错误结果。
- `public Result await(long timeout, TimeUnit unit) throws InterruptedException`
  参数：`long timeout`；`TimeUnit unit`
  返回值：`Result`
  作用：等待首个决定性探测结果；超时返回 `Result.timeout()`，中断继续向调用方抛出 `InterruptedException`。
- `public static Result success()`
  参数：无
  返回值：`Result`
  作用：创建首包成功结果。
- `public static Result error(Throwable throwable)`
  参数：`Throwable throwable`
  返回值：`Result`
  作用：创建首包错误结果。
- `public static Result timeout()`
  参数：无
  返回值：`Result`
  作用：创建首包超时结果。
- `public static Result noContent()`
  参数：无
  返回值：`Result`
  作用：创建流完成但没有首包内容的结果。
- `public boolean isSuccess()`
  参数：无
  返回值：`boolean`
  作用：判断首包探测是否成功。

#### OpenAIStyleSSEParser

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/OpenAIStyleSSEParser.java`

- `public static ParsedEvent parseLine(String line,boolean reasoningEnabled)`
  参数：`String line`；`boolean reasoningEnabled`
  返回值：`ParsedEvent`
  作用：执行该类对应的核心业务动作。
- `private static boolean hasFinishReason(JSONObject choice)`
  参数：`JSONObject choice`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private static String extractText(JSONObject choice,String fieldName)`
  参数：`JSONObject choice`；`String fieldName`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private static String extractReasoning(JSONObject choice)`
  参数：`JSONObject choice`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public boolean hasContent()`
  参数：无
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public boolean hasReasoning()`
  参数：无
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。

#### StreamAsyncExecutor

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/StreamAsyncExecutor.java`

- `public static StreamCancellationHandle submit( Executor executor, Call call, StreamCallback callback, Consumer<AtomicBoolean> streamTask)`
  参数：`Executor executor`；`Call call`；`StreamCallback callback`；`Consumer<AtomicBoolean> streamTask`
  返回值：`StreamCancellationHandle`
  作用：执行该类对应的核心业务动作。

#### StreamCancellationHandles

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/toolkit/StreamCancellationHandles.java`

- `public static StreamCancellationHandle noop()`
  参数：无
  返回值：`StreamCancellationHandle`
  作用：执行该类对应的核心业务动作。
- `public static StreamCancellationHandle fromOkHttp(Call call, AtomicBoolean cancelled)`
  参数：`Call call`；`AtomicBoolean cancelled`
  返回值：`StreamCancellationHandle`
  作用：执行该类对应的核心业务动作。
- `public void cancel()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### EmbeddingModelRegistry

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/EmbeddingModelRegistry.java`

- `public String resolveProvider(String modelId)`
  参数：`String modelId`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `public boolean supports(String clientProvider, String provider, String modelId)`
  参数：`String clientProvider`；`String provider`；`String modelId`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public Map<String, String> allModels()`
  参数：无
  返回值：`Map<String, String>`
  作用：执行该类对应的核心业务动作。
- `public String normalize(String provider)`
  参数：`String provider`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### BaiLianEmbeddingClient

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/impl/client/BaiLianEmbeddingClient.java`

- `public String provider()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public boolean supports(String provider, String modelId)`
  参数：`String provider`；`String modelId`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public int dimension(String modelId)`
  参数：`String modelId`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `public List<List<Float>> embedBatch(List<String> texts, String modelId, Integer dimension, Integer batchSize)`
  参数：`List<String> texts`；`String modelId`；`Integer dimension`；`Integer batchSize`
  返回值：`List<List<Float>>`
  作用：调用 embedding 能力生成向量。
- `private List<TextHolder> normalizeTexts(List<String> texts)`
  参数：`List<String> texts`
  返回值：`List<TextHolder>`
  作用：执行该类对应的核心业务动作。
- `private List<List<Float>> doEmbedOnce(List<TextHolder> slice, String modelId, int expectedDimension)`
  参数：`List<TextHolder> slice`；`String modelId`；`int expectedDimension`
  返回值：`List<List<Float>>`
  作用：调用 embedding 能力生成向量。
- `private Map<String, Object> buildInput(List<TextHolder> slice)`
  参数：`List<TextHolder> slice`
  返回值：`Map<String, Object>`
  作用：执行该类对应的核心业务动作。
- `private Map<String, Object> buildParameters(int expectedDimension)`
  参数：`int expectedDimension`
  返回值：`Map<String, Object>`
  作用：执行该类对应的核心业务动作。
- `private void ensureResults(List<List<Float>> results)`
  参数：`List<List<Float>> results`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private RuntimeException mapClientException(ModelClientException e)`
  参数：`ModelClientException e`
  返回值：`RuntimeException`
  作用：执行该类对应的核心业务动作。
- `private void validateBatchResult(List<List<Float>> vectors, int expectedSize, int expectedDimension)`
  参数：`List<List<Float>> vectors`；`int expectedSize`；`int expectedDimension`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private AIModelProperties.BaiLianProvider requireChannel()`
  参数：无
  返回值：`AIModelProperties.BaiLianProvider`
  作用：执行该类对应的核心业务动作。
- `private String requireModel(String modelId)`
  参数：`String modelId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String normalizeText(String text)`
  参数：`String text`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private JsonObject parseJsonBody(ResponseBody body) throws IOException`
  参数：`ResponseBody body`
  返回值：`JsonObject`
  作用：执行该类对应的核心业务动作。
- `private String readBody(ResponseBody body) throws IOException`
  参数：`ResponseBody body`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private boolean isSuccessCode(JsonElement codeElement)`
  参数：`JsonElement codeElement`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private String readMessage(JsonObject root)`
  参数：`JsonObject root`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private ModelClientErrorType classifyStatus(int status)`
  参数：`int status`
  返回值：`ModelClientErrorType`
  作用：执行意图识别、解析或决策。
- `private record TextHolder(int index, String text)`
  参数：`int index`；`String text`
  返回值：`record`
  作用：执行该类对应的核心业务动作。

#### SiliconFlowEmbeddingClient

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/impl/client/SiliconFlowEmbeddingClient.java`

- `public String provider()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public boolean supports(String provider, String modelId)`
  参数：`String provider`；`String modelId`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public int dimension(String modelId)`
  参数：`String modelId`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `public List<List<Float>> embedBatch(List<String> texts, String modelId, Integer dimension, Integer batchSize)`
  参数：`List<String> texts`；`String modelId`；`Integer dimension`；`Integer batchSize`
  返回值：`List<List<Float>>`
  作用：调用 embedding 能力生成向量。
- `private List<String> normalizeTexts(List<String> texts)`
  参数：`List<String> texts`
  返回值：`List<String>`
  作用：执行该类对应的核心业务动作。
- `private List<List<Float>> doEmbedOnce(List<String> slice, String modelId, int expectedDimension)`
  参数：`List<String> slice`；`String modelId`；`int expectedDimension`
  返回值：`List<List<Float>>`
  作用：调用 embedding 能力生成向量。
- `private void validateBatchResult(List<List<Float>> vectors, int expectedSize, int expectedDimension)`
  参数：`List<List<Float>> vectors`；`int expectedSize`；`int expectedDimension`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private AIModelProperties.SiliconFlowProvider requireChannel()`
  参数：无
  返回值：`AIModelProperties.SiliconFlowProvider`
  作用：执行该类对应的核心业务动作。
- `private String requireModel(String modelId)`
  参数：`String modelId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String normalizeText(String text)`
  参数：`String text`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private JsonObject parseJsonBody(ResponseBody body) throws IOException`
  参数：`ResponseBody body`
  返回值：`JsonObject`
  作用：执行该类对应的核心业务动作。
- `private String readBody(ResponseBody body) throws IOException`
  参数：`ResponseBody body`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private ModelClientErrorType classifyStatus(int status)`
  参数：`int status`
  返回值：`ModelClientErrorType`
  作用：执行意图识别、解析或决策。

#### RoutingEmbeddingService

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/impl/service/RoutingEmbeddingService.java`

- `public int dimension()`
  参数：无
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `public List<Float> embed(String text)`
  参数：`String text`
  返回值：`List<Float>`
  作用：调用 embedding 能力生成向量。
- `public List<Float> embed(String text, String modelId)`
  参数：`String text`；`String modelId`
  返回值：`List<Float>`
  作用：调用 embedding 能力生成向量。
- `public List<List<Float>> embedBatch(List<String> texts)`
  参数：`List<String> texts`
  返回值：`List<List<Float>>`
  作用：调用 embedding 能力生成向量。
- `public List<List<Float>> embedBatch(List<String> texts, String modelId)`
  参数：`List<String> texts`；`String modelId`
  返回值：`List<List<Float>>`
  作用：调用 embedding 能力生成向量。
- `private Map<String, EmbeddingClient> buildClientMap()`
  参数：无
  返回值：`Map<String, EmbeddingClient>`
  作用：执行该类对应的核心业务动作。
- `private EmbeddingRoute resolveRoute(String modelId)`
  参数：`String modelId`
  返回值：`EmbeddingRoute`
  作用：执行意图识别、解析或决策。
- `private String resolvePrimaryProvider(String modelId)`
  参数：`String modelId`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private String resolvePrimaryModel(String provider, String modelId)`
  参数：`String provider`；`String modelId`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private int resolveBatchSize()`
  参数：无
  返回值：`int`
  作用：执行意图识别、解析或决策。

#### ModelURLResolver

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/http/ModelURLResolver.java`

- `public static String resolveSiliconFlowUrl(AIModelProperties.SiliconFlowProvider channel, ModelCapability capability)`
  参数：`AIModelProperties.SiliconFlowProvider channel`；`ModelCapability capability`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `public static String resolveBaiLianUrl(AIModelProperties.BaiLianProvider channel, ModelCapability capability)`
  参数：`AIModelProperties.BaiLianProvider channel`；`ModelCapability capability`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private static String joinUrl(String baseUrl, String path)`
  参数：`String baseUrl`；`String path`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### ModelHealthStore

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelHealthStore.java`

- `public boolean isOpen(String id)`
  参数：`String id`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public boolean allowCall(String id)`
  参数：`String id`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public void markSuccess(String id)`
  参数：`String id`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void markFailure(String id)`
  参数：`String id`
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### ModelRoutingExecutor

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelRoutingExecutor.java`

- `public <C, T> T executeWithFallback( ModelCapability capability, List<ModelTarget> targets, Function<ModelTarget, C> clientResolver, ModelCaller<C, T> caller)`
  参数：`ModelCapability capability`；`List<ModelTarget> targets`；`Function<ModelTarget`；`C> clientResolver`；`ModelCaller<C`；`T> caller`
  返回值：`<C, T> T`
  作用：执行该类对应的核心业务动作。

#### BaiLianRerankClient

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/rerank/BaiLianRerankClient.java`

- `public String provider()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN)`
  参数：`String query`；`List<RetrievedChunk> candidates`；`int topN`
  返回值：`List<RetrievedChunk>`
  作用：执行该类对应的核心业务动作。
- `private List<RetrievedChunk> rerankByModel(String query, List<RetrievedChunk> candidates, int topN, String modelId)`
  参数：`String query`；`List<RetrievedChunk> candidates`；`int topN`；`String modelId`
  返回值：`List<RetrievedChunk>`
  作用：执行该类对应的核心业务动作。
- `private CurlResult executeCurl(String url, String apiKey, String requestJson)`
  参数：`String url`；`String apiKey`；`String requestJson`
  返回值：`CurlResult`
  作用：执行该类对应的核心业务动作。
- `private JsonObject parseResponse(String body, Integer httpStatus)`
  参数：`String body`；`Integer httpStatus`
  返回值：`JsonObject`
  作用：执行该类对应的核心业务动作。
- `private List<RetrievedChunk> normalizeCandidates(List<RetrievedChunk> candidates)`
  参数：`List<RetrievedChunk> candidates`
  返回值：`List<RetrievedChunk>`
  作用：执行该类对应的核心业务动作。
- `private Map<String, Object> buildInput(String query, List<RetrievedChunk> candidates)`
  参数：`String query`；`List<RetrievedChunk> candidates`
  返回值：`Map<String, Object>`
  作用：执行该类对应的核心业务动作。
- `private Map<String, Object> buildParameters(int topN)`
  参数：`int topN`
  返回值：`Map<String, Object>`
  作用：执行该类对应的核心业务动作。
- `private List<String> resolveModelChain()`
  参数：无
  返回值：`List<String>`
  作用：执行意图识别、解析或决策。
- `private AIModelProperties.BaiLianProvider requireChannel()`
  参数：无
  返回值：`AIModelProperties.BaiLianProvider`
  作用：执行该类对应的核心业务动作。
- `private int resolveTopN(int topN, int candidateSize)`
  参数：`int topN`；`int candidateSize`
  返回值：`int`
  作用：执行意图识别、解析或决策。
- `private long resolveMaxTimeoutMs()`
  参数：无
  返回值：`long`
  作用：执行意图识别、解析或决策。
- `private long toSeconds(Long timeoutMs, long defaultSeconds)`
  参数：`Long timeoutMs`；`long defaultSeconds`
  返回值：`long`
  作用：执行该类对应的核心业务动作。
- `private String readStream(InputStream stream) throws IOException`
  参数：`InputStream stream`
  返回值：`String`
  作用：发起聊天或流式输出处理。
- `private Integer parseHttpStatus(String statusText)`
  参数：`String statusText`
  返回值：`Integer`
  作用：执行该类对应的核心业务动作。
- `private int readIndex(JsonObject item, int candidateSize)`
  参数：`JsonObject item`；`int candidateSize`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private float readScore(JsonObject item)`
  参数：`JsonObject item`
  返回值：`float`
  作用：执行该类对应的核心业务动作。
- `private String readDocumentText(JsonObject item, String fallbackText)`
  参数：`JsonObject item`；`String fallbackText`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private boolean isSuccessCode(JsonElement codeElement)`
  参数：`JsonElement codeElement`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private Integer readStatusCode(JsonObject root)`
  参数：`JsonObject root`
  返回值：`Integer`
  作用：执行该类对应的核心业务动作。
- `private String readMessage(JsonObject root)`
  参数：`JsonObject root`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private ModelClientErrorType classifyStatus(int status)`
  参数：`int status`
  返回值：`ModelClientErrorType`
  作用：执行意图识别、解析或决策。
- `private RuntimeException mapClientException(ModelClientException e)`
  参数：`ModelClientException e`
  返回值：`RuntimeException`
  作用：执行该类对应的核心业务动作。
- `private String normalize(String text)`
  参数：`String text`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private record CurlResult(String body, Integer httpStatus)`
  参数：`String body`；`Integer httpStatus`
  返回值：`record`
  作用：执行该类对应的核心业务动作。

#### LeightWeightTokenCounterService

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/token/LeightWeightTokenCounterService.java`

- `public Integer countTokens(String text)`
  参数：`String text`
  返回值：`Integer`
  作用：执行该类对应的核心业务动作。
- `private boolean isCjk(char ch)`
  参数：`char ch`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。

#### LLMResponseCleaner

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/toolkit/LLMResponseCleaner.java`

- `public static String stripMarkdownCodeFence(String raw)`
  参数：`String raw`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

### rag 方法

#### ConversationController

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/controller/ConversationController.java`

- `public Result<List<ConversationVO>> listConversations()`
  参数：无
  返回值：`Result<List<ConversationVO>>`
  作用：查询并返回业务数据。
- `public Result<Void> rename(@PathVariable String conversationId, @RequestBody ConversationUpdateRequest request)`
  参数：`@PathVariable String conversationId`；`@RequestBody ConversationUpdateRequest request`
  返回值：`Result<Void>`
  作用：更新业务数据或名称。
- `public Result<Void> delete(@PathVariable String conversationId)`
  参数：`@PathVariable String conversationId`
  返回值：`Result<Void>`
  作用：删除业务数据或清理资源。
- `public Result<List<ConversationMessageVO>> listMessages(@PathVariable String conversationId)`
  参数：`@PathVariable String conversationId`
  返回值：`Result<List<ConversationMessageVO>>`
  作用：查询并返回业务数据。

#### IntentNodeController

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/controller/IntentNodeController.java`

- `public Result<List<IntentNodeVO>> listAllNodes()`
  参数：无
  返回值：`Result<List<IntentNodeVO>>`
  作用：查询并返回业务数据。
- `public Result<String> createNode(@RequestBody IntentNodeCreateRequest requestParam)`
  参数：`@RequestBody IntentNodeCreateRequest requestParam`
  返回值：`Result<String>`
  作用：创建或新增业务数据。
- `public Result<Void> updateNode(@PathVariable String id, @RequestBody IntentNodeUpdateRequest requestParam)`
  参数：`@PathVariable String id`；`@RequestBody IntentNodeUpdateRequest requestParam`
  返回值：`Result<Void>`
  作用：更新业务数据或名称。
- `public Result<Void> deleteNode(@PathVariable String id)`
  参数：`@PathVariable String id`
  返回值：`Result<Void>`
  作用：删除业务数据或清理资源。
- `public Result<Void> batchEnable(@RequestBody IntentNodeBatchRequest requestParam)`
  参数：`@RequestBody IntentNodeBatchRequest requestParam`
  返回值：`Result<Void>`
  作用：启停或批量处理业务对象。
- `public Result<Void> batchDisable(@RequestBody IntentNodeBatchRequest requestParam)`
  参数：`@RequestBody IntentNodeBatchRequest requestParam`
  返回值：`Result<Void>`
  作用：启停或批量处理业务对象。
- `public Result<Void> batchDelete(@RequestBody IntentNodeBatchRequest requestParam)`
  参数：`@RequestBody IntentNodeBatchRequest requestParam`
  返回值：`Result<Void>`
  作用：启停或批量处理业务对象。

#### RAGChatController

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/controller/RAGChatController.java`

- `public Result<Void> stop(@RequestParam String taskId)`
  参数：`@RequestParam String taskId`
  返回值：`Result<Void>`
  作用：执行该类对应的核心业务动作。

#### GuidanceDecision

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/guidance/GuidanceDecision.java`

- `public static GuidanceDecision none()`
  参数：无
  返回值：`GuidanceDecision`
  作用：执行该类对应的核心业务动作。
- `public static GuidanceDecision prompt(String prompt)`
  参数：`String prompt`
  返回值：`GuidanceDecision`
  作用：加载、渲染或构建提示词/上下文。
- `public boolean isPrompt()`
  参数：无
  返回值：`boolean`
  作用：加载、渲染或构建提示词/上下文。

#### IntentGuidanceService

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/guidance/IntentGuidanceService.java`

- `public GuidanceDecision detectAmbiguity(List<SubQuestionIntent> subIntents)`
  参数：`List<SubQuestionIntent> subIntents`
  返回值：`GuidanceDecision`
  作用：执行该类对应的核心业务动作。
- `private List<SubQuestionIntent> findGuidanceTargets(List<SubQuestionIntent> subIntents)`
  参数：`List<SubQuestionIntent> subIntents`
  返回值：`List<SubQuestionIntent>`
  作用：查询并返回业务数据。
- `private List<NodeScore> findQualifiedCandidates(List<NodeScore> scores)`
  参数：`List<NodeScore> scores`
  返回值：`List<NodeScore>`
  作用：查询并返回业务数据。
- `private List<String> collectCandidateNodeOptions(List<NodeScore> qualifiedCandidates)`
  参数：`List<NodeScore> qualifiedCandidates`
  返回值：`List<String>`
  作用：执行该类对应的核心业务动作。
- `private boolean shouldSkipGuidance(String question, List<String> optionNames)`
  参数：`String question`；`List<String> optionNames`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private List<String> resolveOptionNames(List<String> optionIds)`
  参数：`List<String> optionIds`
  返回值：`List<String>`
  作用：执行意图识别、解析或决策。
- `private String buildCandidatePrompt(String topicName, List<String> optionIds)`
  参数：`String topicName`；`List<String> optionIds`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `private String buildSupplementPrompt(String topicName)`
  参数：`String topicName`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `private String renderOptions(List<String> optionIds)`
  参数：`List<String> optionIds`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `private String normalizeName(String name)`
  参数：`String name`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### FirstLayerIntentClassifier

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/classifier/impl/FirstLayerIntentClassifier.java`

- `public List<NodeScore> classifyTargets(String question)`
  参数：`String question`
  返回值：`List<NodeScore>`
  作用：执行意图识别、解析或决策。
- `public FirstLayerIntentDecision decide(String question)`
  参数：`String question`
  返回值：`FirstLayerIntentDecision`
  作用：执行意图识别、解析或决策。
- `private List<NodeScore> parseNodeScores(String raw)`
  参数：`String raw`
  返回值：`List<NodeScore>`
  作用：执行该类对应的核心业务动作。
- `private IntentNode resolveFixedNode(String id)`
  参数：`String id`
  返回值：`IntentNode`
  作用：执行意图识别、解析或决策。
- `private double findScore(List<NodeScore> scores, String nodeId)`
  参数：`List<NodeScore> scores`；`String nodeId`
  返回值：`double`
  作用：查询并返回业务数据。
- `private double defaultScore(Double score)`
  参数：`Double score`
  返回值：`double`
  作用：执行该类对应的核心业务动作。

#### SecondLayerIntentClassifier

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/classifier/impl/SecondLayerIntentClassifier.java`

- `public List<NodeScore> classifyTargets(String question)`
  参数：`String question`
  返回值：`List<NodeScore>`
  作用：执行意图识别、解析或决策。
- `public List<IntentNode> listKnowledgeNodes()`
  参数：无
  返回值：`List<IntentNode>`
  作用：查询并返回业务数据。
- `private String buildIntentList(List<IntentNode> nodes)`
  参数：`List<IntentNode> nodes`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private List<NodeScore> parseNodeScores(String raw, List<IntentNode> nodes)`
  参数：`String raw`；`List<IntentNode> nodes`
  返回值：`List<NodeScore>`
  作用：执行该类对应的核心业务动作。

#### IntentResolver

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/IntentResolver.java`

- `public List<SubQuestionIntent> resolve(RewriteResult rewriteResult)`
  参数：`RewriteResult rewriteResult`
  返回值：`List<SubQuestionIntent>`
  作用：执行意图识别、解析或决策。
- `public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents)`
  参数：`List<SubQuestionIntent> subIntents`
  返回值：`IntentGroup`
  作用：执行该类对应的核心业务动作。
- `public boolean isSystemOnly(List<NodeScore> nodeScores)`
  参数：`List<NodeScore> nodeScores`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private SubQuestionIntent resolveSingleQuestion(String question)`
  参数：`String question`
  返回值：`SubQuestionIntent`
  作用：执行意图识别、解析或决策。
- `private int defaultTopN(Integer value)`
  参数：`Integer value`
  返回值：`int`
  作用：执行该类对应的核心业务动作。
- `private double defaultMinScore(Double value)`
  参数：`Double value`
  返回值：`double`
  作用：执行该类对应的核心业务动作。

#### IntentTreeCacheManager

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/management/IntentTreeCacheManager.java`

- `public void saveKnowledgeNodesToCache(List<IntentNode> nodes)`
  参数：`List<IntentNode> nodes`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void clearKnowledgeNodesCache()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### ConversationMemoryServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/impl/ConversationMemoryServiceImpl.java`

- `public List<ChatMessage> load(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`List<ChatMessage>`
  作用：加载、渲染或构建提示词/上下文。
- `public String append(String conversationId, String userId, ChatMessage message)`
  参数：`String conversationId`；`String userId`；`ChatMessage message`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private ChatMessage loadSummaryWithFallback(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`ChatMessage`
  作用：加载、渲染或构建提示词/上下文。
- `private List<ChatMessage> loadHistoryWithFallback(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`List<ChatMessage>`
  作用：加载、渲染或构建提示词/上下文。
- `private List<ChatMessage> attachSummary(ChatMessage summary, List<ChatMessage> messages)`
  参数：`ChatMessage summary`；`List<ChatMessage> messages`
  返回值：`List<ChatMessage>`
  作用：执行该类对应的核心业务动作。

#### ConversationMemoryStoreServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/impl/ConversationMemoryStoreServiceImpl.java`

- `public List<ChatMessage> loadHistory(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`List<ChatMessage>`
  作用：加载、渲染或构建提示词/上下文。
- `public String append(String conversationId, String userId, ChatMessage message)`
  参数：`String conversationId`；`String userId`；`ChatMessage message`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `public void refreshCache(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private ChatMessage toChatMessage(ConversationMessageVO record)`
  参数：`ConversationMessageVO record`
  返回值：`ChatMessage`
  作用：发起聊天或流式输出处理。
- `private List<ChatMessage> normalizeHistory(List<ChatMessage> messages)`
  参数：`List<ChatMessage> messages`
  返回值：`List<ChatMessage>`
  作用：执行该类对应的核心业务动作。
- `private boolean isHistoryMessage(ChatMessage message)`
  参数：`ChatMessage message`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private int resolveMaxHistoryMessages()`
  参数：无
  返回值：`int`
  作用：执行意图识别、解析或决策。

#### ConversationMemorySummaryServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/memory/impl/ConversationMemorySummaryServiceImpl.java`

- `public void compressIfNeeded(String conversationId, String userId, ChatMessage message)`
  参数：`String conversationId`；`String userId`；`ChatMessage message`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public ChatMessage loadLatestSummary(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`ChatMessage`
  作用：加载、渲染或构建提示词/上下文。
- `public ChatMessage decorateIfNeeded(ChatMessage summary)`
  参数：`ChatMessage summary`
  返回值：`ChatMessage`
  作用：执行该类对应的核心业务动作。
- `private void doCompressIfNeeded(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private boolean tryLock(RLock lock)`
  参数：`RLock lock`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private String summarizeMessages(List<ConversationMessageDO> messages, String existingSummary)`
  参数：`List<ConversationMessageDO> messages`；`String existingSummary`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private List<ChatMessage> toHistoryMessages(List<ConversationMessageDO> messages)`
  参数：`List<ConversationMessageDO> messages`
  返回值：`List<ChatMessage>`
  作用：执行该类对应的核心业务动作。
- `private ChatMessage toChatMessage(ConversationSummaryDO record)`
  参数：`ConversationSummaryDO record`
  返回值：`ChatMessage`
  作用：发起聊天或流式输出处理。
- `private String resolveSummaryStartId(String conversationId, String userId, ConversationSummaryDO summary)`
  参数：`String conversationId`；`String userId`；`ConversationSummaryDO summary`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private String resolveCutoffId(List<ConversationMessageDO> latestUserTurns)`
  参数：`List<ConversationMessageDO> latestUserTurns`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private String resolveLastMessageId(List<ConversationMessageDO> toSummarize)`
  参数：`List<ConversationMessageDO> toSummarize`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private void createSummary(String conversationId, String userId, String content, String lastMessageId)`
  参数：`String conversationId`；`String userId`；`String content`；`String lastMessageId`
  返回值：`void`
  作用：创建或新增业务数据。
- `private String buildLockKey(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### PromptContext

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/PromptContext.java`

- `public boolean hasKb()`
  参数：无
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。

#### PromptTemplateLoader

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/PromptTemplateLoader.java`

- `public String load(String path)`
  参数：`String path`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `public String render(String path, Map<String, String> slots)`
  参数：`String path`；`Map<String`；`String> slots`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `private String readResource(String path)`
  参数：`String path`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### PromptTemplateUtils

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/PromptTemplateUtils.java`

- `public static String cleanupPrompt(String prompt)`
  参数：`String prompt`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `public static String fillSlots(String template, Map<String, String> slots)`
  参数：`String template`；`Map<String`；`String> slots`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### RAGPromptService

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/prompt/RAGPromptService.java`

- `public String buildSystemPrompt(PromptContext context)`
  参数：`PromptContext context`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `public List<ChatMessage> buildStructuredMessages(PromptContext context, List<ChatMessage> history, String question, List<String> subQuestions)`
  参数：`PromptContext context`；`List<ChatMessage> history`；`String question`；`List<String> subQuestions`
  返回值：`List<ChatMessage>`
  作用：执行该类对应的核心业务动作。
- `private PromptPlan selectPromptPlan(List<NodeScore> intents, Map<String, List<RetrievedChunk>> intentChunks)`
  参数：`List<NodeScore> intents`；`Map<String`；`List<RetrievedChunk>> intentChunks`
  返回值：`PromptPlan`
  作用：加载、渲染或构建提示词/上下文。
- `private String resolveKbPromptTemplate(PromptContext context)`
  参数：`PromptContext context`
  返回值：`String`
  作用：执行意图识别、解析或决策。
- `private String loadDefaultTemplate(PromptScene scene)`
  参数：`PromptScene scene`
  返回值：`String`
  作用：加载、渲染或构建提示词/上下文。
- `private String formatKbEvidence(String header, String body)`
  参数：`String header`；`String body`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private static String resolveIntentChunkKey(IntentNode node)`
  参数：`IntentNode node`
  返回值：`String`
  作用：执行意图识别、解析或决策。

#### AbstractVectorSearchChannel

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/AbstractVectorSearchChannel.java`

- `public SearchChannelResult search(SearchContext context)`
  参数：`SearchContext context`
  返回值：`SearchChannelResult`
  作用：查询并返回业务数据。
- `private AbstractParallelRetriever.ParallelRetrievalResult<String> filterInvisibleChunks( AbstractParallelRetriever.ParallelRetrievalResult<String> result)`
  参数：`AbstractParallelRetriever.ParallelRetrievalResult<String> result`
  返回值：`AbstractParallelRetriever.ParallelRetrievalResult<String>`
  作用：执行该类对应的核心业务动作。
- `private List<RetrievedChunk> filterChunksByVisibleIds(List<RetrievedChunk> chunks, Set<String> visibleChunkIds)`
  参数：`List<RetrievedChunk> chunks`；`Set<String> visibleChunkIds`
  返回值：`List<RetrievedChunk>`
  作用：执行该类对应的核心业务动作。
- `private AbstractParallelRetriever.ParallelRetrievalResult<String> emptyRetrievalResult( AbstractParallelRetriever.ParallelRetrievalResult<String> source)`
  参数：`AbstractParallelRetriever.ParallelRetrievalResult<String> source`
  返回值：`AbstractParallelRetriever.ParallelRetrievalResult<String>`
  作用：执行该类对应的核心业务动作。
- `private SearchChannelResult buildResult(List<RetrievedChunk> chunks, Map<String, List<RetrievedChunk>> intentChunks, long latencyMs)`
  参数：`List<RetrievedChunk> chunks`；`Map<String`；`List<RetrievedChunk>> intentChunks`；`long latencyMs`
  返回值：`SearchChannelResult`
  作用：执行该类对应的核心业务动作。
- `private void mergeIntentChunks(Map<String, List<RetrievedChunk>> intentChunks, Map<String, List<RetrievedChunk>> collectionChunks, Map<String, List<String>> collectionIntentKeys)`
  参数：`Map<String`；`List<RetrievedChunk>> intentChunks`；`Map<String`；`List<RetrievedChunk>> collectionChunks`；`Map<String`；`List<String>> collectionIntentKeys`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `protected double resolveConfidence(List<RetrievedChunk> chunks, Map<String, List<RetrievedChunk>> intentChunks)`
  参数：`List<RetrievedChunk> chunks`；`Map<String`；`List<RetrievedChunk>> intentChunks`
  返回值：`double`
  作用：执行意图识别、解析或决策。
- `protected abstract List<SearchTask> buildTasks(SearchContext context)`
  参数：`SearchContext context`
  返回值：`abstract List<SearchTask>`
  作用：执行该类对应的核心业务动作。
- `protected record SearchTask( String question, List<String> collections, int topK, Map<String, List<String>> collectionIntentKeys )`
  参数：`String question`；`List<String> collections`；`int topK`；`Map<String`；`List<String>> collectionIntentKeys`
  返回值：`record`
  作用：执行检索召回或检索通道处理。

#### IntentDirectedSearchChannel

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/IntentDirectedSearchChannel.java`

- `public boolean isEnabled(SearchContext context)`
  参数：`SearchContext context`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `protected List<SearchTask> buildTasks(SearchContext context)`
  参数：`SearchContext context`
  返回值：`List<SearchTask>`
  作用：执行该类对应的核心业务动作。
- `private boolean isIntentDirected(SubQuestionIntent intent)`
  参数：`SubQuestionIntent intent`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private List<String> resolveCollections(SubQuestionIntent intent)`
  参数：`SubQuestionIntent intent`
  返回值：`List<String>`
  作用：执行意图识别、解析或决策。
- `private int resolveTopK(SearchContext context, SubQuestionIntent intent)`
  参数：`SearchContext context`；`SubQuestionIntent intent`
  返回值：`int`
  作用：执行意图识别、解析或决策。
- `private Map<String, List<String>> resolveCollectionIntentKeys(SubQuestionIntent intent)`
  参数：`SubQuestionIntent intent`
  返回值：`Map<String, List<String>>`
  作用：执行意图识别、解析或决策。

#### VectorGlobalSearchChannel

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/impls/VectorGlobalSearchChannel.java`

- `public boolean isEnabled(SearchContext context)`
  参数：`SearchContext context`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `protected List<SearchTask> buildTasks(SearchContext context)`
  参数：`SearchContext context`
  返回值：`List<SearchTask>`
  作用：执行该类对应的核心业务动作。
- `private boolean needsGlobalFallback(SubQuestionIntent intent)`
  参数：`SubQuestionIntent intent`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private Map<String, List<String>> buildIntentKeys(List<String> collections)`
  参数：`List<String> collections`
  返回值：`Map<String, List<String>>`
  作用：执行该类对应的核心业务动作。

#### AbstractParallelRetriever

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/retriver/AbstractParallelRetriever.java`

- `public final List<RetrievedChunk> executeParallelRetrieval(String question, List<T> targets, int topK)`
  参数：`String question`；`List<T> targets`；`int topK`
  返回值：`final List<RetrievedChunk>`
  作用：执行该类对应的核心业务动作。
- `public final ParallelRetrievalResult<T> executeParallelRetrievalWithTargets(String question, List<T> targets, int topK)`
  参数：`String question`；`List<T> targets`；`int topK`
  返回值：`final ParallelRetrievalResult<T>`
  作用：执行该类对应的核心业务动作。
- `protected abstract List<RetrievedChunk> createRetrievalTask(String question, T target, int topK)`
  参数：`String question`；`T target`；`int topK`
  返回值：`abstract List<RetrievedChunk>`
  作用：创建或新增业务数据。

#### CollectionParallelRetriever

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/channel/retriver/CollectionParallelRetriever.java`

- `protected List<RetrievedChunk> createRetrievalTask(String question, String collectionName, int topK)`
  参数：`String question`；`String collectionName`；`int topK`
  返回值：`List<RetrievedChunk>`
  作用：创建或新增业务数据。

#### RetrievalEngine

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/RetrievalEngine.java`

- `public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK)`
  参数：`List<SubQuestionIntent> subIntents`；`int topK`
  返回值：`RetrievalContext`
  作用：执行检索召回或检索通道处理。
- `private List<SearchChannelResult> rerankChannelResults(SearchContext context, List<SearchChannelResult> results)`
  参数：`SearchContext context`；`List<SearchChannelResult> results`
  返回值：`List<SearchChannelResult>`
  作用：执行该类对应的核心业务动作。
- `private SearchChannelResult rerankSingleChannelResult(String query, SearchChannelResult result)`
  参数：`String query`；`SearchChannelResult result`
  返回值：`SearchChannelResult`
  作用：执行该类对应的核心业务动作。
- `private Map<String, List<RetrievedChunk>> reorderIntentChunksByRerank(Map<?, ?> rawMap, List<RetrievedChunk> rerankedChunks)`
  参数：`Map<?`；`?> rawMap`；`List<RetrievedChunk> rerankedChunks`
  返回值：`Map<String, List<RetrievedChunk>>`
  作用：执行该类对应的核心业务动作。
- `private Map<String, List<RetrievedChunk>> mergeIntentChunks(List<SearchChannelResult> results)`
  参数：`List<SearchChannelResult> results`
  返回值：`Map<String, List<RetrievedChunk>>`
  作用：执行该类对应的核心业务动作。
- `private String buildKbContext(Map<String, List<RetrievedChunk>> intentChunks)`
  参数：`Map<String`；`List<RetrievedChunk>> intentChunks`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private List<RetrievedChunk> deduplicateAndSort(List<RetrievedChunk> chunks)`
  参数：`List<RetrievedChunk> chunks`
  返回值：`List<RetrievedChunk>`
  作用：执行该类对应的核心业务动作。
- `private String resolveChunkKey(RetrievedChunk chunk)`
  参数：`RetrievedChunk chunk`
  返回值：`String`
  作用：执行意图识别、解析或决策。

#### MilvusRetrieverService

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/service/MilvusRetrieverService.java`

- `public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam)`
  参数：`RetrieveRequest retrieveParam`
  返回值：`List<RetrievedChunk>`
  作用：执行检索召回或检索通道处理。
- `public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam)`
  参数：`float[] vector`；`RetrieveRequest retrieveParam`
  返回值：`List<RetrievedChunk>`
  作用：执行检索召回或检索通道处理。
- `private static float[] toArray(List<Float> list)`
  参数：`List<Float> list`
  返回值：`float[]`
  作用：执行该类对应的核心业务动作。
- `private static float[] normalize(float[] v)`
  参数：`float[] v`
  返回值：`float[]`
  作用：执行该类对应的核心业务动作。

#### MultiQuestionRewriteService

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/service/MultiQuestionRewriteService.java`

- `public String rewrite(String userQuestion)`
  参数：`String userQuestion`
  返回值：`String`
  作用：执行问题改写或拆分。
- `public RewriteResult rewriteWithSplit(String userQuestion)`
  参数：`String userQuestion`
  返回值：`RewriteResult`
  作用：执行问题改写或拆分。
- `public RewriteResult rewriteWithSplit(String userQuestion, List<ChatMessage> history)`
  参数：`String userQuestion`；`List<ChatMessage> history`
  返回值：`RewriteResult`
  作用：执行问题改写或拆分。
- `private RewriteResult rewriteAndSplit(String userQuestion)`
  参数：`String userQuestion`
  返回值：`RewriteResult`
  作用：执行问题改写或拆分。
- `private RewriteResult callLLMRewriteAndSplit(String normalizedQuestion, String originalQuestion, List<ChatMessage> history)`
  参数：`String normalizedQuestion`；`String originalQuestion`；`List<ChatMessage> history`
  返回值：`RewriteResult`
  作用：执行问题改写或拆分。
- `private ChatRequest buildRewriteRequest(String systemPrompt, String question, List<ChatMessage> history)`
  参数：`String systemPrompt`；`String question`；`List<ChatMessage> history`
  返回值：`ChatRequest`
  作用：执行问题改写或拆分。
- `private RewriteResult parseRewriteAndSplit(String raw)`
  参数：`String raw`
  返回值：`RewriteResult`
  作用：执行问题改写或拆分。
- `private List<String> ruleBasedSplit(String question)`
  参数：`String question`
  返回值：`List<String>`
  作用：执行该类对应的核心业务动作。

#### QueryTermMappingService

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/service/QueryTermMappingService.java`

- `public void loadMappings()`
  参数：无
  返回值：`void`
  作用：加载、渲染或构建提示词/上下文。
- `public String normalize(String text)`
  参数：`String text`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### ConversationComplexQueryServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/ConversationComplexQueryServiceImpl.java`

- `public List<ConversationMessageDO> listLatestUserOnlyMessages(String conversationId, String userId, int limit)`
  参数：`String conversationId`；`String userId`；`int limit`
  返回值：`List<ConversationMessageDO>`
  作用：查询并返回业务数据。
- `public List<ConversationMessageDO> listMessagesBetweenIds(String conversationId, String userId, String afterId, String beforeId)`
  参数：`String conversationId`；`String userId`；`String afterId`；`String beforeId`
  返回值：`List<ConversationMessageDO>`
  作用：查询并返回业务数据。
- `public String findMaxMessageIdAtOrBefore(String conversationId, String userId, java.util.Date at)`
  参数：`String conversationId`；`String userId`；`java.util.Date at`
  返回值：`String`
  作用：查询并返回业务数据。
- `public long countUserMessages(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`long`
  作用：执行该类对应的核心业务动作。
- `public ConversationSummaryDO findLatestSummary(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`ConversationSummaryDO`
  作用：查询并返回业务数据。
- `public ConversationDO findConversation(String conversationId, String userId)`
  参数：`String conversationId`；`String userId`
  返回值：`ConversationDO`
  作用：查询并返回业务数据。

#### ConversationMessageServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/ConversationMessageServiceImpl.java`

- `public String addMessage(ConversationMessageBO conversationMessage)`
  参数：`ConversationMessageBO conversationMessage`
  返回值：`String`
  作用：创建或新增业务数据。
- `public List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order)`
  参数：`String conversationId`；`String userId`；`Integer limit`；`ConversationMessageOrder order`
  返回值：`List<ConversationMessageVO>`
  作用：查询并返回业务数据。
- `public void addMessageSummary(ConversationSummaryBO conversationSummary)`
  参数：`ConversationSummaryBO conversationSummary`
  返回值：`void`
  作用：创建或新增业务数据。

#### ConversationServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/ConversationServiceImpl.java`

- `public List<ConversationVO> listByUserId(String userId)`
  参数：`String userId`
  返回值：`List<ConversationVO>`
  作用：查询并返回业务数据。
- `public void createOrUpdate(ConversationCreateRequest request)`
  参数：`ConversationCreateRequest request`
  返回值：`void`
  作用：创建或新增业务数据。
- `public void rename(String conversationId, ConversationUpdateRequest request)`
  参数：`String conversationId`；`ConversationUpdateRequest request`
  返回值：`void`
  作用：更新业务数据或名称。
- `public void delete(String conversationId)`
  参数：`String conversationId`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `private String generateTitleFromQuestion(String question)`
  参数：`String question`
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### IntentNodeManageServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/IntentNodeManageServiceImpl.java`

- `public List<IntentNode> listEnabledIntentNodes()`
  参数：无
  返回值：`List<IntentNode>`
  作用：查询并返回业务数据。
- `public void refreshIntentNodeCache()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void syncNodesFromKnowledgeBase()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private IntentNode toIntentNode(IntentNodeDO source, KnowledgeBaseDO knowledgeBase)`
  参数：`IntentNodeDO source`；`KnowledgeBaseDO knowledgeBase`
  返回值：`IntentNode`
  作用：执行该类对应的核心业务动作。
- `private List<String> parseExamples(String examples)`
  参数：`String examples`
  返回值：`List<String>`
  作用：执行该类对应的核心业务动作。
- `private String buildDefaultDescription(KnowledgeBaseDO knowledgeBase)`
  参数：`KnowledgeBaseDO knowledgeBase`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String currentOperator()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。

#### IntentNodeServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/IntentNodeServiceImpl.java`

- `public List<IntentNodeVO> listAllNodes()`
  参数：无
  返回值：`List<IntentNodeVO>`
  作用：查询并返回业务数据。
- `public String createNode(IntentNodeCreateRequest requestParam)`
  参数：`IntentNodeCreateRequest requestParam`
  返回值：`String`
  作用：创建或新增业务数据。
- `public void updateNode(String id, IntentNodeUpdateRequest requestParam)`
  参数：`String id`；`IntentNodeUpdateRequest requestParam`
  返回值：`void`
  作用：更新业务数据或名称。
- `public void deleteNode(String id)`
  参数：`String id`
  返回值：`void`
  作用：删除业务数据或清理资源。
- `public void batchEnableNodes(List<String> ids)`
  参数：`List<String> ids`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `public void batchDisableNodes(List<String> ids)`
  参数：`List<String> ids`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `public void batchDeleteNodes(List<String> ids)`
  参数：`List<String> ids`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `private void batchUpdateEnabled(List<String> ids, int enabled)`
  参数：`List<String> ids`；`int enabled`
  返回值：`void`
  作用：启停或批量处理业务对象。
- `private void validateCreateRequest(IntentKind kind, String kbId, String name)`
  参数：`IntentKind kind`；`String kbId`；`String name`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void validateUpdateRequest(String id, IntentKind kind, String kbId, String name)`
  参数：`String id`；`IntentKind kind`；`String kbId`；`String name`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void ensureKnowledgeBaseExists(String kbId)`
  参数：`String kbId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void ensureUniqueKbBinding(String kbId, String excludeId)`
  参数：`String kbId`；`String excludeId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private void ensureUniqueSystemNodeName(String name, String excludeId)`
  参数：`String name`；`String excludeId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private List<IntentNodeDO> listAndValidateTargetNodes(List<String> ids)`
  参数：`List<String> ids`
  返回值：`List<IntentNodeDO>`
  作用：查询并返回业务数据。
- `private Map<String, KnowledgeBaseDO> listKnowledgeBaseMap(Set<String> kbIds)`
  参数：`Set<String> kbIds`
  返回值：`Map<String, KnowledgeBaseDO>`
  作用：查询并返回业务数据。
- `private Set<String> extractKbIds(List<IntentNodeDO> nodes)`
  参数：`List<IntentNodeDO> nodes`
  返回值：`Set<String>`
  作用：执行该类对应的核心业务动作。
- `private IntentNodeVO toVO(IntentNodeDO source, Map<String, KnowledgeBaseDO> knowledgeBaseMap)`
  参数：`IntentNodeDO source`；`Map<String`；`KnowledgeBaseDO> knowledgeBaseMap`
  返回值：`IntentNodeVO`
  作用：执行该类对应的核心业务动作。
- `private IntentKind resolveKind(Integer kindCode, boolean allowNull)`
  参数：`Integer kindCode`；`boolean allowNull`
  返回值：`IntentKind`
  作用：执行意图识别、解析或决策。
- `private IntentKind inferKind(IntentNodeDO node)`
  参数：`IntentNodeDO node`
  返回值：`IntentKind`
  作用：执行该类对应的核心业务动作。
- `private String normalizeKbId(IntentKind kind, String kbId)`
  参数：`IntentKind kind`；`String kbId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private String normalizeRequiredName(String name)`
  参数：`String name`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private Integer normalizeEnabled(Integer enabled)`
  参数：`Integer enabled`
  返回值：`Integer`
  作用：执行该类对应的核心业务动作。
- `private String joinExamples(List<String> examples)`
  参数：`List<String> examples`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private List<String> splitExamples(String examples)`
  参数：`String examples`
  返回值：`List<String>`
  作用：执行该类对应的核心业务动作。
- `private String currentOperator()`
  参数：无
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private void clearKnowledgeNodeCache()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。

#### RAGChatServiceImpl

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/impl/RAGChatServiceImpl.java`

- `public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter)`
  参数：`String question`；`String conversationId`；`Boolean deepThinking`；`SseEmitter emitter`
  返回值：`void`
  作用：发起聊天或流式输出处理。
- `public void stopTask(String taskId)`
  参数：`String taskId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private StreamCancellationHandle streamSystemResponse(String question, List<ChatMessage> history, String customPrompt, StreamCallback callback)`
  参数：`String question`；`List<ChatMessage> history`；`String customPrompt`；`StreamCallback callback`
  返回值：`StreamCancellationHandle`
  作用：发起聊天或流式输出处理。
- `private StreamCancellationHandle streamLLMResponse(RewriteResult rewriteResult, RetrievalContext ctx, IntentGroup intentGroup, List<ChatMessage> history, boolean deepThinking, StreamCallback callback)`
  参数：`RewriteResult rewriteResult`；`RetrievalContext ctx`；`IntentGroup intentGroup`；`List<ChatMessage> history`；`boolean deepThinking`；`StreamCallback callback`
  返回值：`StreamCancellationHandle`
  作用：发起聊天或流式输出处理。

#### StreamCallbackFactory

位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamCallbackFactory.java`

- `public StreamCallback createChatEventHandler(SseEmitter emitter, String conversationId, String taskId)`
  参数：`SseEmitter emitter`；`String conversationId`；`String taskId`
  返回值：`StreamCallback`
  作用：创建或新增业务数据。

#### StreamTaskManager

位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamTaskManager.java`

- `public void subscribe()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void unsubscribe()`
  参数：无
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void register(String taskId, SseEmitterSender sender, Supplier<CompletionPayload> onCancelSupplier)`
  参数：`String taskId`；`SseEmitterSender sender`；`Supplier<CompletionPayload> onCancelSupplier`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void bindHandle(String taskId, StreamCancellationHandle handle)`
  参数：`String taskId`；`StreamCancellationHandle handle`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public boolean isCancelled(String taskId)`
  参数：`String taskId`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `public void cancel(String taskId)`
  参数：`String taskId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private boolean isTaskCancelledInRedis(String taskId, StreamTaskInfo taskInfo)`
  参数：`String taskId`；`StreamTaskInfo taskInfo`
  返回值：`boolean`
  作用：执行该类对应的核心业务动作。
- `private void cancelLocal(String taskId)`
  参数：`String taskId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `public void unregister(String taskId)`
  参数：`String taskId`
  返回值：`void`
  作用：执行该类对应的核心业务动作。
- `private String cancelKey(String taskId)`
  参数：`String taskId`
  返回值：`String`
  作用：执行该类对应的核心业务动作。
- `private void sendCancelAndDone(SseEmitterSender sender, CompletionPayload payload)`
  参数：`SseEmitterSender sender`；`CompletionPayload payload`
  返回值：`void`
  作用：发送 SSE 事件或结束流式响应。

## 6. 实体信息类概览

实体、DTO、VO、DO、record 等数据承载类不展开方法，只描述位置、用途和核心字段。

### user-service 实体信息

#### EducationExperience

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/common/pojo/EducationExperience.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`schoolName:String`、`startDate:String`、`endDate:String`、`major:String`、`degree:String`、`highlights:String`

#### ProjectExperience

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/common/pojo/ProjectExperience.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`projectName:String`、`projectRole:String`、`projectDescription:String`、`responsibility:String`、`achievement:String`

#### WorkExperience

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/common/pojo/WorkExperience.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`companyName:String`、`startDate:String`、`endDate:String`、`department:String`、`position:String`、`workContent:String`

#### IntervieweeFormDO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dao/entity/IntervieweeFormDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:Long`、`formName:String`、`userId:Long`、`candidateName:String`、`jobIntention:String`、`professionalSkills:String`、`educationExperiences:String`、`workExperiences:String`

#### UserAccountDO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dao/entity/UserAccountDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:Long`、`accountId:String`、`password:String`、`nickName:String`、`permission:Integer`、`deleted:boolean`

#### CreateIntervieweeFormReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/CreateIntervieweeFormReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`formName:String`、`candidateName:String`、`jobIntention:String`、`professionalSkills:List<String>`、`educationExperiences:List<EducationExperience>`、`workExperiences:List<WorkExperience>`、`projectExperiences:List<ProjectExperience>`

#### DeleteIntervieweeFormReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/DeleteIntervieweeFormReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`

#### FuzzySearchIntervieweeFormReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/FuzzySearchIntervieweeFormReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`formName:String`

#### LoginReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/LoginReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`accountId:String`、`password:String`

#### LogoutReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/LogoutReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`accountId:String`

#### SearchIntervieweeFormByIdReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/SearchIntervieweeFormByIdReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`

#### SignUpReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/SignUpReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`accountId:String`、`password:String`、`nickName:String`

#### UpdateIntervieweeFormReqDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/req/UpdateIntervieweeFormReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`formName:String`、`candidateName:String`、`jobIntention:String`、`professionalSkills:List<String>`、`educationExperiences:List<EducationExperience>`、`workExperiences:List<WorkExperience>`、`projectExperiences:List<ProjectExperience>`

#### IntervieweeFormNameRespDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/IntervieweeFormNameRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`formName:String`、`createTime:Date`

#### IntervieweeFormRespDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/IntervieweeFormRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`formName:String`、`userId:String`、`candidateName:String`、`jobIntention:String`、`professionalSkills:List<String>`、`educationExperiences:List<EducationExperience>`、`workExperiences:List<WorkExperience>`

#### LoginRespDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/LoginRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`accountId:String`、`nickName:String`、`permission:Integer`

#### LogoutRespDTO

位置：`user-service/src/main/java/com/ycy/aiapplication/user/service/dto/resp/LogoutRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`accountId:String`、`nickName:String`

### agent 实体信息

#### ApiEvaluationResp

位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/ApiEvaluationResp.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`comment:String`、`completeness:int`、`levelOfDetail:int`、`accuracy:int`、`logic:int`、`expressionAbility:int`

#### EducationExperience

位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/EducationExperience.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`schoolName:String`、`startDate:String`、`endDate:String`、`major:String`、`degree:String`、`highlights:String`

#### IntervieweeForm

位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/IntervieweeForm.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`candidateName:String`、`jobIntention:String`、`professionalSkills:List<String>`、`educationExperiences:List<EducationExperience>`、`workExperiences:List<WorkExperience>`、`projectExperiences:List<ProjectExperience>`

#### InterviewQuestion

位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/InterviewQuestion.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`num:int`、`level:int`、`questionDescription:String`

#### ProjectExperience

位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/ProjectExperience.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`projectName:String`、`projectRole:String`、`projectDescription:String`、`responsibility:String`、`achievement:String`

#### QuestionWithAnswer

位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/QuestionWithAnswer.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`question:InterviewQuestion`、`answer:String`

#### WorkExperience

位置：`agent/src/main/java/com/ycy/aiapplication/common/pojo/WorkExperience.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`companyName:String`、`startDate:String`、`endDate:String`、`department:String`、`position:String`、`workContent:String`

#### InterviewRecordDO

位置：`agent/src/main/java/com/ycy/aiapplication/dao/entity/InterviewRecordDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:Long`、`userId:Long`、`recordName:String`、`interviewProcessRecord:String`、`interviewKeywords:String`、`summaryReportRecord:String`、`adviceReportRecord:String`、`interviewPoint:Integer`

#### AgentInterviewReportDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/AgentInterviewReportDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`interviewPoint:int`、`accuracyScore:int`、`completenessScore:int`、`levelOfDetailScore:int`、`logicScore:int`、`expressionAbilityScore:int`、`summaryReport:String`、`adviceReport:String`

#### InterviewDimensionScoreDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/InterviewDimensionScoreDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`interviewPoint:int`、`accuracyScore:int`、`completenessScore:int`、`levelOfDetailScore:int`、`logicScore:int`、`expressionAbilityScore:int`

#### AnswerEvaluationReqDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/AnswerEvaluationReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`questionWithAnswers:List<QuestionWithAnswer>`

#### DeleteInterviewRecordReqDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/DeleteInterviewRecordReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`

#### FuzzySearchInterviewNameReqDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/FuzzySearchInterviewNameReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### InterviewQuestionAskReqDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/InterviewQuestionAskReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`formId:String`

#### ReportGenerationReqDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/ReportGenerationReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`answerEvaluationRespS:AnswerEvaluationRespDTO[]`、`formId:String`

#### SearchInterviewRecordByIdReqDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/req/SearchInterviewRecordByIdReqDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`

#### AnswerEvaluationRespDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/AnswerEvaluationRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`question:InterviewQuestion`、`answer:String`、`apiResp:ApiEvaluationResp`

#### FuzzySearchInterviewRecordRespDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/FuzzySearchInterviewRecordRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`recordName:String`、`id:String`

#### InterviewQuestionAskRespDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/InterviewQuestionAskRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`questions:List<InterviewQuestion>`

#### InterviewRecordRespDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/InterviewRecordRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`userId:String`、`recordName:String`、`interviewProcessRecord:String`、`interviewKeywords:String`、`summaryReportRecord:String`、`adviceReportRecord:String`、`interviewPoint:Integer`

#### ReportGenerationRespDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/ReportGenerationRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`date:Date`、`recordName:String`、`reportDTO:AgentInterviewReportDTO`

#### SearchInterviewNameAndIdRespDTO

位置：`agent/src/main/java/com/ycy/aiapplication/dto/resp/SearchInterviewNameAndIdRespDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`name:String`、`id:String`、`interviewPoint:Integer`、`createTime:Date`

### framework 实体信息

#### UserContext

位置：`framework/src/main/java/com/ycy/aiapplication/framework/context/UserContext.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`userNickNameResolver:static volatile UserNickNameResolver`

#### UserInfoDTO

位置：`framework/src/main/java/com/ycy/aiapplication/framework/context/UserInfoDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:Long`、`accountId:String`、`permission:Integer`、`jti:String`、`loginExpireTime:Long`

#### ChatMessage

位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/ChatMessage.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`role:Role`、`content:String`

#### ChatRequest

位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/ChatRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`messages:List<ChatMessage>`、`provider:String`、`modelId:String`、`temperature:Double`、`topP:Double`、`topK:Integer`、`maxTokens:Integer`、`thinking:Boolean`

#### Result

位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/Result.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`serialVersionUID:long`、`SUCCESS_CODE:String`、`code:String`、`message:String`、`data:T`、`requestId:String`

#### RetrievedChunk

位置：`framework/src/main/java/com/ycy/aiapplication/framework/convention/RetrievedChunk.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`text:String`、`score:Float`

#### BaseErrorCode

位置：`framework/src/main/java/com/ycy/aiapplication/framework/errorcode/BaseErrorCode.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`code:final String`、`message:final String`

#### IErrorCode

位置：`framework/src/main/java/com/ycy/aiapplication/framework/errorcode/IErrorCode.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### BaseSendExtendDTO

位置：`framework/src/main/java/com/ycy/aiapplication/framework/mq/base/BaseSendExtendDTO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`eventName:String`、`topic:String`、`tag:String`、`keys:String`、`sentTimeout:Long`、`delayTime:Long`

#### MessageWrapper

位置：`framework/src/main/java/com/ycy/aiapplication/framework/mq/base/MessageWrapper.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`serialVersionUID:long`、`keys:String`、`message:T`、`timestamp:long`

#### Result

位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/Result.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`serialVersionUID:long`、`SUCCESS_CODE:String`、`code:String`、`message:String`、`token:String`、`data:T`、`requestId:String`

#### Results

位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/Results.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### SseEmitterSender

位置：`framework/src/main/java/com/ycy/aiapplication/framework/web/SseEmitterSender.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`emitter:final SseEmitter`、`closed:final AtomicBoolean`

### knowledge 实体信息

#### FixedSizeOptions

位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/records/FixedSizeOptions.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### TextBoundaryOptions

位置：`knowledge/src/main/java/com/ycy/aiapplication/chunk/records/TextBoundaryOptions.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### KnowledgeBaseCreateRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/base/KnowledgeBaseCreateRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`name:String`、`embeddingModel:String`

#### KnowledgeBasePageRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/base/KnowledgeBasePageRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`name:String`

#### KnowledgeBaseUpdateRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/base/KnowledgeBaseUpdateRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`name:String`、`embeddingModel:String`

#### KnowledgeChunkBatchRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/chunk/KnowledgeChunkBatchRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`chunkIds:List<String>`

#### KnowledgeChunkPageRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/chunk/KnowledgeChunkPageRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`enabled:Integer`

#### KnowledgeDocumentPageRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/doc/KnowledgeDocumentPageRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`status:String`、`keyword:String`

#### KnowledgeDocumentUpdateRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/doc/KnowledgeDocumentUpdateRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`docName:String`、`processMode:String`、`chunkStrategy:String`、`chunkConfig:String`

#### KnowledgeDocumentUploadRequest

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/request/doc/KnowledgeDocumentUploadRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`sourceType:String`、`sourceLocation:String`、`scheduleEnabled:Boolean`、`scheduleCron:String`、`processMode:String`、`chunkStrategy:String`、`chunkConfig:String`

#### KnowledgeBaseVO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeBaseVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`name:String`、`embeddingModel:String`、`collectionName:String`、`documentCount:Long`、`createdBy:String`、`createTime:Date`、`updateTime:Date`

#### KnowledgeChunkVO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeChunkVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kbId:String`、`docId:String`、`chunkIndex:Integer`、`content:String`、`contentHash:String`、`charCount:Integer`、`tokenCount:Integer`

#### KnowledgeDocumentChunkLogVO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeDocumentChunkLogVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`docId:String`、`status:String`、`processMode:String`、`chunkStrategy:String`、`extractDuration:Long`、`chunkDuration:Long`、`embedDuration:Long`

#### KnowledgeDocumentSearchVO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeDocumentSearchVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kbId:String`、`docName:String`、`kbName:String`

#### KnowledgeDocumentVO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/control/vo/KnowledgeDocumentVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kbId:String`、`docName:String`、`sourceType:String`、`sourceLocation:String`、`scheduleEnabled:Integer`、`scheduleCron:String`、`enabled:Boolean`

#### KnowledgeBaseDO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeBaseDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`name:String`、`embeddingModel:String`、`collectionName:String`、`createdBy:String`、`updatedBy:String`、`createTime:Date`、`updateTime:Date`

#### KnowledgeChunkDO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeChunkDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kbId:String`、`docId:String`、`chunkIndex:Integer`、`content:String`、`contentHash:String`、`charCount:Integer`、`tokenCount:Integer`

#### KnowledgeDocumentChunkLogDO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeDocumentChunkLogDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`docId:String`、`status:String`、`processMode:String`、`chunkStrategy:String`、`extractDuration:Long`、`chunkDuration:Long`、`embedDuration:Long`

#### KnowledgeDocumentDO

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/dao/entity/KnowledgeDocumentDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kbId:String`、`docName:String`、`sourceType:String`、`sourceLocation:String`、`scheduleEnabled:Integer`、`scheduleCron:String`、`enabled:Integer`

#### KnowledgeDocumentAsyncChunkEvent

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/event/KnowledgeDocumentAsyncChunkEvent.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`docId:String`

#### AbstractCommonSendProduceTemplate

位置：`knowledge/src/main/java/com/ycy/aiapplication/knowledge/mq/producer/AbstractCommonSendProduceTemplate.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`rocketMQTemplate:final RocketMQTemplate`

#### ParseResult

位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/common/ParseResult.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### ParserType

位置：`knowledge/src/main/java/com/ycy/aiapplication/parse/common/ParserType.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`type:final String`

### infrastructure-ai 实体信息

#### StreamCallback

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/interfaces/StreamCallback.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### StreamCancellationHandle

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/chat/interfaces/StreamCancellationHandle.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### EmbeddingRoute

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/embedding/EmbeddingRoute.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### HttpMediaTypes

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/http/HttpMediaTypes.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`JSON:MediaType`、`JSON_UTF8_HEADER:String`

#### ModelClientErrorType

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/http/ModelClientErrorType.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### ModelCaller

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelCaller.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### ModelTarget

位置：`infrastructure-ai/src/main/java/com/ycy/aiapplication/infrastructure/ai/model/ModelTarget.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

### rag 实体信息

#### ConversationCreateRequest

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/ConversationCreateRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`conversationId:String`、`userId:String`、`question:String`、`lastTime:Date`

#### ConversationUpdateRequest

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/ConversationUpdateRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`title:String`

#### IntentNodeBatchRequest

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/IntentNodeBatchRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`ids:List<String>`

#### IntentNodeCreateRequest

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/IntentNodeCreateRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`kind:Integer`、`kbId:String`、`name:String`、`description:String`、`examples:List<String>`、`promptSnippet:String`、`promptTemplate:String`、`enabled:Integer`

#### IntentNodeUpdateRequest

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/request/IntentNodeUpdateRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`kind:Integer`、`kbId:String`、`name:String`、`description:String`、`examples:List<String>`、`promptSnippet:String`、`promptTemplate:String`、`enabled:Integer`

#### ConversationMessageVO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/vo/ConversationMessageVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`conversationId:String`、`role:String`、`content:String`、`createTime:Date`

#### ConversationVO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/vo/ConversationVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`conversationId:String`、`title:String`、`lastTime:Date`

#### IntentNodeVO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/control/vo/IntentNodeVO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kind:Integer`、`kbId:String`、`kbName:String`、`collectionName:String`、`name:String`、`description:String`、`examples:List<String>`

#### IntentKind

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/enums/IntentKind.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`code:final int`

#### IntentLevel

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/enums/IntentLevel.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`code:final int`

#### IntentGroup

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/IntentGroup.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### IntentNode

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/IntentNode.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kbId:String`、`name:String`、`description:String`、`level:IntentLevel`、`examples:List<String>`、`fullPath:String`、`kind:IntentKind`

#### NodeScore

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/NodeScore.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`node:IntentNode`、`score:double`

#### SubQuestionIntent

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/common/SubQuestionIntent.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### FirstLayerIntentDecision

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/intent/FirstLayerIntentDecision.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### RetrievalContext

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/common/RetrievalContext.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`kbContext:String`、`intentChunks:Map<String, List<RetrievedChunk>>`、`channelResults:List<SearchChannelResult>`

#### RetrieveRequest

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/common/RetrieveRequest.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`query:String`、`topK:int`、`collectionName:String`、`metadataFilters:Map<String, Object>`

#### SearchContext

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/retrieve/common/SearchContext.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`originalQuestion:String`、`rewrittenQuestion:String`、`subQuestions:List<String>`、`intents:List<SubQuestionIntent>`、`topK:int`、`metadata:Map<String, Object>`

#### QueryTermMappingUtil

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/common/QueryTermMappingUtil.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### RewriteResult

位置：`rag/src/main/java/com/ycy/aiapplication/rag/core/rewrite/common/RewriteResult.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### ConversationDO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/ConversationDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`conversationId:String`、`userId:String`、`title:String`、`lastTime:Date`、`createTime:Date`、`updateTime:Date`、`deleted:Integer`

#### ConversationMessageDO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/ConversationMessageDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`conversationId:String`、`userId:String`、`role:String`、`content:String`、`createTime:Date`、`updateTime:Date`、`deleted:Integer`

#### ConversationSummaryDO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/ConversationSummaryDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`conversationId:String`、`userId:String`、`content:String`、`lastMessageId:String`、`createTime:Date`、`updateTime:Date`、`deleted:Integer`

#### IntentNodeDO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/IntentNodeDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`kbId:String`、`name:String`、`description:String`、`examples:String`、`promptSnippet:String`、`promptTemplate:String`、`enabled:Integer`

#### QueryTermMappingDO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/dao/entity/QueryTermMappingDO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`id:String`、`domain:String`、`sourceTerm:String`、`targetTerm:String`、`matchType:Integer`、`priority:Integer`、`enabled:Integer`、`remark:String`

#### ConversationMessageBO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/bo/ConversationMessageBO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`conversationId:String`、`userId:String`、`role:String`、`content:String`

#### ConversationSummaryBO

位置：`rag/src/main/java/com/ycy/aiapplication/rag/service/bo/ConversationSummaryBO.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`conversationId:String`、`userId:String`、`content:String`、`lastMessageId:String`

#### CompletionPayload

位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/common/CompletionPayload.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### MessageDelta

位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/common/MessageDelta.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### MetaPayload

位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/common/MetaPayload.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：*未提取到显式字段或为 record/枚举式结构*

#### StreamChatEventHandler

位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamChatEventHandler.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`TYPE_THINK:String`、`TYPE_RESPONSE:String`、`conversationId:final String`、`taskId:final String`、`userId:final String`、`messageChunkSize:final int`、`sendTitleOnComplete:final boolean`、`answer:final StringBuilder`

#### StreamChatHandlerParams

位置：`rag/src/main/java/com/ycy/aiapplication/rag/stream/StreamChatHandlerParams.java`

用途：承载请求、响应、数据库实体、值对象或业务过程数据。

核心字段：`emitter:final SseEmitter`、`conversationId:final String`、`taskId:final String`、`modelProperties:final AIModelProperties`、`memoryService:final ConversationMemoryService`、`complexQueryService:final ConversationComplexQueryService`、`taskManager:final StreamTaskManager`

## 7. 智能体开发约束

- **开发前阅读**：必须先阅读 `docs/` 下 Markdown 文档，至少包括 `codex_coding_guidance.md` 与 `codex_process.md`。
- **Agent 约束**：如存在 `AGENTS.md`、`Agent.md`、`Agents.md` 等文件需优先阅读。当前扫描未发现该类文件，后续新增后应遵循。
- **修改范围**：限定在任务相关模块，避免无关重构、无关格式化和跨模块大范围改动。
- **未提交变更**：修改前识别并保留，不覆盖用户已有未提交内容；必要时在 `codex_process.md` 中记录。
- **接口变更**：同步更新本文档接口指引，确保前后端交互路径、参数、返回值可追踪。
- **实体类处理**：实体、DTO、VO、DO 不展开 getter/setter，使用用途、位置、核心字段进行概括。
- **中文注释**：按 UTF-8 读取源码；PowerShell 控制台可能显示乱码，不能直接据此判断文件内容损坏。
