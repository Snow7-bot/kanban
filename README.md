# 康伴智能医疗助手

康伴是一个前后端分离的家庭健康管理项目，提供健康指标、病历、用药、家庭成员、健康报告和 AI 智能问诊等功能。

> 本项目中的健康数据仅用于功能演示和健康信息参考，不替代医生诊断或处方。

## 项目结构

```text
.
├── kangban-web/       # React + Vite 前端
├── kangban-server/    # Spring Boot 后端
└── P0-测试结果表.md
```

## 技术栈

### 前端

- React
- Vite
- Lucide React

### 后端

- Java 17
- Spring Boot 3.2
- Spring Security + JWT
- MyBatis-Plus
- MySQL
- Redis
- Flyway
- MinIO
- SpringDoc OpenAPI
- Apache PDFBox
- 千问兼容模式 API

## 已实现功能

- 登录、注册、短信验证码模拟与密码重置
- 游客首页和登录后数据加载
- 个人资料与头像上传
- 家庭成员管理
- 健康指标录入、趋势与健康报告
- 病历上传、OCR 结果展示、病历详情、分享和 PDF
- 用药管理、服药记录与药物相互作用提示
- AI 智能问诊、患者切换、数据库健康上下文和 SSE 输出
- 前后端接口测试与数据库迁移

## 本地运行要求

- Node.js 18+
- npm
- JDK 17+
- Maven 3.9+
- MySQL
- Redis
- MinIO

## 环境变量

后端配置示例位于 [`kangban-server/.env.example`](kangban-server/.env.example)，前端配置示例位于 [`kangban-web/.env.example`](kangban-web/.env.example)。

复制示例文件后填写本机配置，禁止提交真实密码、JWT 密钥或 AI API Key。

后端主要变量：

```text
SPRING_PROFILES_ACTIVE
KANGBAN_DB_USERNAME
KANGBAN_DB_PASSWORD
KANGBAN_REDIS_PASSWORD
KANGBAN_MINIO_ENDPOINT
KANGBAN_MINIO_ACCESS_KEY
KANGBAN_MINIO_SECRET_KEY
KANGBAN_JWT_SECRET
KANGBAN_DEV_SMS_CODE
APP_BASE_URL
APP_CORS_ALLOWED_ORIGINS
APP_AI_PROVIDER
APP_AI_API_URL
APP_AI_API_KEY
APP_AI_AI_MODEL
APP_AI_OCR_MODEL
```

本地默认使用 `local` Profile。开发验证码只有在显式配置 `KANGBAN_DEV_SMS_CODE` 时才固定；未配置 `APP_AI_PROVIDER` 时使用 Mock，避免意外调用付费模型。`prod` Profile 默认关闭 Swagger，正式短信供应商接入前会返回“短信服务尚未配置”。

JWT 密钥可生成一行式 Base64 内容：

```bash
openssl rand -base64 64 | tr -d '\n'; echo
```

## 启动后端

```bash
cd kangban-server
mvn spring-boot:run
```

默认地址：

```text
后端 API：http://127.0.0.1:8080
Swagger：http://127.0.0.1:8080/swagger-ui/index.html
```

## 启动前端

```bash
cd kangban-web
npm install
npm run dev
```

默认预览地址：

```text
http://127.0.0.1:5173
```

## 测试与构建

前端：

```bash
cd kangban-web
npm test
npm run build
```

后端：

```bash
cd kangban-server
mvn test -Dspring.profiles.active=test
mvn package
```

## 数据库迁移

后端启动时由 Flyway 自动执行 `kangban-server/src/main/resources/db/migration/` 中的迁移文件。首次运行前请准备可用的 MySQL 数据库和对应环境变量。

## 后续计划

- 家庭创建、邀请与接受
- 家属健康数据共享授权
- 查看、录入、病历、用药、报告和 AI 权限控制
- 家庭操作审计与撤销授权
- OCR 服务的稳定性和真实场景验收

## 安全说明

- 不要提交 `.env`、IDEA 工作区配置、数据库文件或对象存储数据。
- 不要在 Issues、日志或截图中公开密码、JWT、API Key 和访问令牌。
- 已经暴露过的密钥应立即轮换，旧密钥不应继续使用。
