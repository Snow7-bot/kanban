# 康伴 RAG 黄金问答评测

## 用途

`kangban-server/src/test/resources/rag/golden-qa.json` 是离线评测集，包含 100 条脱敏中文演示问题：

- 40 条公共健康知识问题；
- 20 条用药知识问题；
- 10 条家庭私有病历问题；
- 20 条知识库没有依据的问题，应当返回空检索结果，由 Agent 统一拒答；
- 10 条跨家庭、无授权问题，应当在权限过滤阶段返回空检索结果；
- 每条有依据样例都带预期文档、页码和章节，用于验证结构化引用。

资料内容是脱敏演示数据，不构成医疗建议；本轮不包含扫描版 PDF OCR 评测。

## 指标

- `Hit@5`：有依据问题在前 5 条结果中命中预期文档的比例；
- `citationAccuracy`：命中文档的标题、页码和章节均正确的比例；
- `noResultRecognition`：无依据问题正确返回空结果的比例。
- `accessControlDenial`：跨家庭无授权问题未返回任何私有资料的比例；

每条样例还带有 `category` 和 `scope` 字段，用于按业务维度拆分指标：
`PUBLIC_HEALTH`、`MEDICATION`、`PRIVATE_RECORD`、`NO_EVIDENCE`、`ACCESS_CONTROL`，以及
`PUBLIC`、`PRIVATE_FAMILY`、`PRIVATE_FAMILY_OTHER`、`NONE`。

当前验收阈值：`Hit@5 >= 0.95`，引用准确率 `= 1.0`，无结果识别率 `= 1.0`。

## 运行

```bash
cd kangban-server
mvn -q -Dspring.profiles.active=test -Dtest=RagGoldenSetEvaluationTest test
```

上面的测试直接验证本地确定性排序器。若要验证从测试数据库到 JDBC 检索服务的完整链路，运行：

```bash
mvn -q -Dspring.profiles.active=test -Dtest=JdbcRagGoldenSetEvaluationTest test
```

JDBC 评测会在 H2 测试库中临时写入受控演示文档，并通过
`JdbcKnowledgeSearchService`、`JdbcPrivateKnowledgeSearchService` 和家庭权限过滤执行检索；
不会连接开发 MySQL、生产数据库、Qwen 或外部 Embedding 服务。

测试不会调用 Qwen、Embedding 服务或其他外部网络服务。输出示例：

```text
RAG_GOLDEN_METRICS cases=100 evidence=70 noEvidence=20 accessControl=10 hitAt5=1.000 citationAccuracy=1.000 noResultRecognition=1.000 accessControlDenial=1.000
```

JDBC 链路评测输出前缀为 `RAG_JDBC_GOLDEN_METRICS`，四项指标应与上面的离线评测一致。

当前测试使用本地确定性 Hash Embedding 和固定候选文档，只能验证评测器、排序器、引用字段和样例分类的一致性，不能代表 Qwen 或生产向量库的真实命中率。
真实知识库上线前，应使用经过审核的脱敏资料替换演示文档，并在真实向量库上按 Embedding 模型、版本、租户和权限分别记录结果。

## 真实 Embedding 评测

`kangban-server/src/test/resources/rag/live-documents/` 保存了用于验证真实文件入库链路的脱敏 Markdown 文件，
`QwenLiveRagGoldenSetEvaluationTest` 会依次执行文件上传、解析、切片、Qwen Embedding、审核发布和 JDBC 检索。
该测试默认关闭，避免普通构建因为外部网络或模型额度失败。显式运行：

```bash
export KANGBAN_RUN_LIVE_RAG=true
export APP_AI_API_KEY='仅在当前终端注入，不要写入仓库'
./scripts/run-live-rag-evaluation.sh
```

也可以使用当前终端已有的 `DASHSCOPE_API_KEY`。脚本和测试不会打印密钥或向量内容。
若 Qwen 域名 TLS、代理、额度或模型配置不可用，测试应视为“真实评测未完成”，不能把离线指标当作线上指标。
