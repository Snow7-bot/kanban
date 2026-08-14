# 康伴 RAG 评测指标报告

## 指标口径

| 指标 | 口径 |
| --- | --- |
| `Hit@5` | 有依据问题的预期文档是否出现在前 5 条结果中 |
| `citationAccuracy` | 命中结果的文档标题、页码/章节和权限范围是否与预期一致 |
| `noResultRecognition` | 无依据问题是否正确返回空检索结果 |
| `accessControlDenial` | 无权限问题是否没有返回私有资料 |
| `avgMs` | 所有已记录请求的平均耗时，失败请求也计入 |
| `p50Ms` | 排序后位于 50% 位置的请求耗时 |
| `p95Ms` | 排序后位于 95% 位置的请求耗时 |
| `failureRate` | 失败请求数 / 总请求数 |

评测统计只记录数量、状态和耗时，不记录问题正文、病历内容、向量或密钥。

## 三种评测边界

### 离线评测

验证确定性 Hash Embedding、排序器、引用字段和黄金集分类。延迟只代表本机计算耗时，不能代表 Qwen 网络延迟。

```bash
cd kangban-server
mvn -q -Dspring.profiles.active=test -Dtest=RagGoldenSetEvaluationTest test
```

### JDBC 评测

使用 H2 测试库和真实 JDBC 检索服务，验证数据库查询、公共/私有权限过滤、引用组装和延迟。不会调用外部 Embedding。

```bash
mvn -q -Dspring.profiles.active=test -Dtest=JdbcRagGoldenSetEvaluationTest test
```

### 真实 Qwen 评测

使用脱敏 Markdown 文件，实际调用 Qwen `text-embedding-v4`，验证文件解析、切片、向量化、发布和 JDBC 检索。

```bash
cd ..
export KANGBAN_RUN_LIVE_RAG=true
bash scripts/run-live-rag-evaluation.sh
```

只有命令成功退出并输出 `RAG_QWEN_LIVE_METRICS`，才能把该次数字作为真实 Embedding 评测结果。网络、TLS、额度或模型失败时，不得用离线指标替代真实指标。

## 输出格式

真实评测会输出两类延迟：

- `ingestionLatency`：每份唯一文档从上传到解析、Embedding、审核发布完成的耗时；
- `searchLatency`：每条问题从检索开始到返回结果的耗时。

示例字段格式如下，具体数值以实际运行结果为准：

```text
ingestionLatency={count=4 success=4 failures=0 failureRate=0.000 avgMs=... p50Ms=... p95Ms=... maxMs=...}
searchLatency={count=14 success=14 failures=0 failureRate=0.000 avgMs=... p50Ms=... p95Ms=... maxMs=...}
```

## 当前验收要求

- 离线/JDBC：`Hit@5 >= 0.95`、引用准确率 `= 1.0`、无结果识别率 `= 1.0`、权限拒绝率 `= 1.0`；
- 真实 Qwen：`Hit@5 >= 0.80`、引用准确率 `>= 0.95`、无结果识别率 `>= 0.50`；
- 任何真实评测失败都必须保留失败原因和失败样例，不能只报告成功请求。

## 最近一次真实 Qwen 评测

评测日期：2026-08-13

执行入口：`scripts/run-live-rag-evaluation.sh`

| 指标 | 结果 | 验收结果 |
| --- | ---: | --- |
| 文档入库 | 4/4 成功 | 通过 |
| 测试用例 | 14/14 成功 | 通过 |
| `Hit@5` | 1.000 | 通过 |
| `citationAccuracy` | 1.000 | 通过 |
| `noResultRecognition` | 1.000 | 通过 |
| 入库失败率 | 0.000 | 通过 |
| 检索失败率 | 0.000 | 通过 |
| Embedding 模型 | `text-embedding-v4` | — |
| 向量维度 | 1024 | — |

延迟结果：

```text
ingestionLatency={count=4 success=4 failures=0 failureRate=0.000 avgMs=310.808 p50Ms=241.425 p95Ms=549.087 maxMs=549.087}
searchLatency={count=14 success=14 failures=0 failureRate=0.000 avgMs=313.372 p50Ms=271.320 p95Ms=515.365 maxMs=515.365}
```

本次评测输出 `hitFailures=[]`、`citationFailures=[]`，真实 Qwen Embedding、文档解析、切片、入库和 JDBC 检索链路均通过。该结果只代表本次网络和服务可用时的测量值；生产环境仍需单独验证服务器到 Qwen 服务的稳定出网能力。
