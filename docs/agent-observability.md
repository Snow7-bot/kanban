# 康伴 Agent/RAG 可观测性

## 访问

本地开发服务启动后：

```text
GET http://localhost:8080/actuator/metrics
GET http://localhost:8080/actuator/metrics/kangban.agent.runs
GET http://localhost:8080/actuator/metrics/kangban.rag.searches
```

`/actuator/health` 保持公开；`/actuator/metrics` 必须登录后访问。生产环境应通过反向代理或内网访问，不应把该端点直接暴露到公网。

## 指标

- `kangban.agent.runs` / `kangban.agent.duration`：Agent 成功或失败次数、耗时；
- `kangban.rag.searches` / `kangban.rag.search.duration`：公共或私有检索次数、命中/空结果/失败和耗时；
- `kangban.rag.empty`：零结果次数；
- `kangban.rag.search.hits`：每次检索返回的命中数量；
- `kangban.agent.embedding.calls` / `kangban.agent.embedding.duration`：Embedding 服务调用结果和耗时；
- `kangban.agent.embedding.batch.size`：Embedding 批量大小；
- `kangban.agent.tools.calls` / `kangban.agent.tools.duration`：工具名称、状态和耗时。

指标标签只允许受控名称、状态和范围，禁止写入问题正文、病历内容、完整异常信息、向量、密钥或 Token。

## 运行测试

```bash
cd kangban-server
mvn -q -Dspring.profiles.active=test -Dtest=AgentMetricsTest,SecurityBoundaryIntegrationTest test
```
