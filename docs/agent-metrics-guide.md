# 康伴 Agent / RAG 真实指标获取指南

本文件说明如何得到可以写进简历或项目答辩的实测指标。所有指标必须来自一次实际运行，并保留测试集版本、模型版本、向量维度、网络环境和失败原因。

## 先区分三类结果

| 评测层级 | 是否调用 Qwen | 主要证明什么 | 能否称为线上真实指标 |
| --- | ---: | --- | ---: |
| 离线黄金集 | 否 | 排序器、引用判定、拒答逻辑 | 否，只能称离线基线 |
| JDBC 黄金集 | 否 | SQL、权限过滤、引用组装 | 否，只能称测试库指标 |
| Live Qwen | 是 | 真实文件入库、Qwen Embedding、检索和延迟 | 是，但只代表本次运行 |

## 推荐记录的 5 个指标

### 1. Hit@5：检索命中率

**定义**：有标准答案的知识问题中，预期文档是否出现在前 5 条检索结果里的比例。

```text
Hit@5 = 命中预期文档的问题数 / 有依据问题总数
```

**如何得到**：

1. 在黄金集为每条有依据问题标记 `expectedDocumentId`；
2. 运行检索；
3. 检查返回的前 5 条 `citation.documentId`；
4. 测试输出 `hitAt5=0.xxx`。

**运行命令**：

```bash
cd kangban-server
mvn -q -Dspring.profiles.active=test -Dtest=JdbcRagGoldenSetEvaluationTest test
```

真实 Qwen 版本：

```bash
cd ..
export KANGBAN_RUN_LIVE_RAG=true
export DASHSCOPE_API_KEY='仅在当前终端注入，不要写入文件'
bash scripts/run-live-rag-evaluation.sh
```

### 2. citationAccuracy：引用准确率

**定义**：命中结果的文档标题、版本、页码/章节和权限范围全部与黄金集预期一致的比例。

```text
citationAccuracy = 全部引用字段正确的问题数 / 有依据问题总数
```

它比“有命中”更严格。命中了错误文档、错误页码或错误家庭资料，都算失败。

**如何得到**：

- 检查 `citation.documentId`；
- 检查 `title`、`version`、`pageNumber`、`section`；
- 检查 `scope` 是否为 `PUBLIC` 或授权的私有家庭范围；
- 测试输出 `citationAccuracy=0.xxx`，同时输出 `citationFailures`。

### 3. noResultRecognition：无依据识别率

**定义**：黄金集中明确没有知识依据的问题，系统是否返回空检索结果并触发拒答。

```text
noResultRecognition = 正确返回空结果的问题数 / 无依据问题总数
```

**如何得到**：

1. 准备资料中不存在的测试问题；
2. 预期 `hits=[]` 或 `context` 为空；
3. Agent 最终回答必须说明资料不足，不得伪造引用；
4. 测试输出 `noResultRecognition=0.xxx`。

### 4. accessControlDenial：越权拒绝率

**定义**：跨用户或跨家庭查询时，系统没有返回不属于当前用户的私有资料的比例。

```text
accessControlDenial = 正确拒绝的越权问题数 / 越权问题总数
```

**如何得到**：

- 用用户 A 的身份检索用户 B 的病历关键词；
- 预期私有检索返回空结果；
- 同时检查引用中不能出现 B 的 `documentId`、患者名或病历内容；
- 测试输出 `accessControlDenial=0.xxx`。

这个指标是医疗场景的 P0 指标，不能只看回答是否自然。

### 5. p95 延迟和失败率

**定义**：

- `p95Ms`：95% 请求在该耗时内完成；
- `failureRate`：失败请求数 / 总请求数；
- `ingestionLatency`：文档上传到切片、Embedding、入库完成的耗时；
- `searchLatency`：从开始检索到返回结果的耗时。

**如何得到**：

Live 测试会输出：

```text
ingestionLatency={count=... success=... failures=... failureRate=... avgMs=... p50Ms=... p95Ms=... maxMs=...}
searchLatency={count=... success=... failures=... failureRate=... avgMs=... p50Ms=... p95Ms=... maxMs=...}
```

简历中建议写 `search p95` 和 `failureRate`，不要只写平均耗时。一次网络异常也要保留，不能只挑成功结果。

## 一次完整实测流程

### 1. 运行离线基线

```bash
cd kangban-server
mvn -q -Dspring.profiles.active=test -Dtest=RagGoldenSetEvaluationTest test
mvn -q -Dspring.profiles.active=test -Dtest=JdbcRagGoldenSetEvaluationTest test
```

确认输出包含：

```text
hitAt5=...
citationAccuracy=...
noResultRecognition=...
accessControlDenial=...
latency={...}
```

### 2. 运行真实 Qwen 评测

```bash
cd ..
export KANGBAN_RUN_LIVE_RAG=true
export APP_AI_API_KEY='仅在当前终端注入，不要写入仓库'
bash scripts/run-live-rag-evaluation.sh
```

必须看到：

```text
RAG_QWEN_LIVE_METRICS ...
```

并且命令退出码为 0。若出现 DNS、TLS、超时、429、模型或额度错误，本次只能记录为“真实评测失败”，不能用离线 1.000 替代。

### 3. 记录实验元数据

每次报告同时记录：

- Git commit；
- 黄金集版本和样本数；
- Embedding 模型、维度和批大小；
- Qwen Chat 模型；
- `topK`、`minScore` 和上下文 Token 上限；
- 运行时间、网络环境和失败原因。

不要记录 API Key、完整病历、原始问题正文或向量内容。

## 当前仓库已有结果

此前一次真实 Qwen 评测记录在 [`docs/rag-metrics-report.md`](rag-metrics-report.md)。该记录包含 `Hit@5`、引用准确率、无结果识别率、入库/检索失败率以及 p50/p95 延迟。若代码、资料、模型或网络环境发生变化，应重新执行 Live 评测后再更新结果。
