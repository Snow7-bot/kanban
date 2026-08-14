# 康伴医疗 Agent 架构图

## 总体架构

```mermaid
flowchart TB
    USER[用户 / 家庭成员]
    WEB[React + Vite
    首页 / 健康 / 用药 / 病历 / AI问诊]
    NGINX[Nginx
    生产同源代理]
    API[Spring Boot API
    Controller / Security]
    AUTH[JWT 身份与家庭授权]
    DOMAIN[领域服务
    健康 / 用药 / 病历 / 家庭]
    AGENT[AgentOrchestrator
    上下文、工具、记忆、RAG、模型]
    TOOLS[只读 Agent 工具
    健康指标 / 用药 / 病历]
    RAG[KnowledgeSearchService
    公共资料 + 私有病历权限过滤]
    INGEST[KnowledgeDocumentService
    解析 / 切片 / Embedding / 审核发布]
    QWEN_EMB[Qwen text-embedding-v4]
    QWEN_CHAT[Qwen Chat / Tool Calling]
    MYSQL[(MySQL
    业务数据、文档元数据、切片、引用、审计)]
    REDIS[(Redis
    验证码、缓存、短期状态)]
    MINIO[(MinIO
    头像、病历和对象文件)]

    USER --> WEB
    WEB -->|本地 REST / SSE| API
    WEB -->|生产同源| NGINX --> API
    API --> AUTH
    API --> DOMAIN
    DOMAIN --> MYSQL
    DOMAIN --> REDIS
    DOMAIN --> MINIO
    API --> AGENT
    AGENT --> TOOLS --> DOMAIN
    AGENT --> RAG --> MYSQL
    RAG --> QWEN_EMB
    AGENT --> QWEN_CHAT
    QWEN_CHAT --> AGENT
    AGENT -->|token / thinking / agent_tool / citation / done| API
    API -->|SSE| WEB
    WEB -->|管理员上传资料| API
    API --> INGEST --> MYSQL
    INGEST --> QWEN_EMB
```

## AI 问诊请求时序

```mermaid
sequenceDiagram
    participant U as 用户
    participant W as kangban-web
    participant C as ConsultationController
    participant S as ConsultationService
    participant A as AgentOrchestrator
    participant T as 只读工具 / RAG
    participant Q as Qwen
    participant DB as MySQL

    U->>W: 选择当前患者并发送问题
    W->>C: POST /consultation/sessions/{id}/messages
    C->>S: 保存用户消息并校验会话权限
    S-->>W: 返回 messageId
    W->>C: GET /stream?messageId=...
    C->>S: 创建 SSE 流
    S->>A: 构造签名 AgentExecutionContext
    A->>T: 读取患者健康、用药、病历或知识库
    T->>DB: 仅按服务端身份查询
    DB-->>T: 结构化数据 / 引用
    T-->>A: 工具结果和检索证据
    A->>Q: 系统提示 + 患者上下文 + 证据 + 对话历史
    Q-->>A: 最终答案或下一轮工具调用
    A-->>S: 答案、引用、工具轨迹、指标
    S-->>W: thinking / agent_tool / citation / token / done
    W-->>U: 丝滑展示最终回答和依据
```

## 关键模块职责

| 模块 | 职责 | 关键边界 |
| --- | --- | --- |
| `AgentOrchestrator` | 管理 Agent 运行、上下文、工具循环、RAG 合并和最终生成 | 不直接写业务表 |
| `AgentExecutionContext` | 携带用户、患者、成员、家庭和运行 ID | 由服务端签名，不能信任客户端身份字段 |
| `AgentToolRegistry` | 注册当前允许使用的工具 | 当前只允许只读工具 |
| `AgentToolExecutor` | 校验上下文、执行工具、记录状态和耗时 | 越权或写工具直接阻断 |
| `KnowledgeDocumentService` | 文档上传、解析、切片、审核、发布和重建索引 | 只有发布且有切片的文档可被公共检索 |
| `JdbcKnowledgeSearchService` | 过滤已发布文档、生成 Query Embedding、混合排序和引用 | 先做文档状态与 Embedding 模型过滤 |
| `ConsultationService` | 保存会话消息并把 Agent 事件转为 SSE | 处理超时、重试和断线收尾 |
| `QwenAiConsultationClient` | Qwen Chat / Tool Calling 适配 | API Key 只从环境变量读取 |

## 数据隔离

- 公共知识库只返回 `PUBLISHED` 且未删除的文档；
- 私有病历检索同时限制 owner、subject、family 和 member 范围；
- Agent 上下文由认证用户和服务端当前患者选择共同生成；
- 前端传入的用户 ID 不能覆盖服务端身份；
- 医疗写操作不由 Agent 工具直接执行，需要用户确认后进入领域接口。

## 故障策略

- Qwen Chat 超时：SSE 返回可重试错误，并保留会话状态；
- Qwen Embedding 不可用：RAG 返回 503，不静默切换为无依据医疗回答；
- 无检索证据：Agent 明确说明资料不足，并建议咨询专业人员；
- 工具异常：记录安全的错误类型和指标，不把病历正文写入日志；
- SSE 断线：服务端收尾，前端可以基于已保存的 messageId 重试。
