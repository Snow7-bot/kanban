# 康伴 Web 前端缺失页面与接口简表

版本：V1.0  
日期：2026-07-21  
文档用途：补齐当前原型的前端页面与接口联调范围  
文档范围：依据《智能医疗助手 Web 版前端产品需求文档》V1.1 与当前 8 个已实现路由核对；仅列未覆盖的页面或流程，不新增后端能力。

## 1. 当前覆盖与缺口判断

已实现：首页、问诊聊天、家庭成员列表、健康趋势、用药管理、病历管理、个人资料、设置。

优先补齐顺序：认证 → 成员上下文 → 健康录入 → 问诊闭环 → 病历详情。

页面之外的共性缺口：JWT 路由保护、当前成员切换、请求加载/失败/重试、删除二次确认。

## 2. P0 页面与流程（核心闭环）

| 页面 | 建议路由 | 主要功能按钮 | PRD 接口 |
|---|---|---|---|
| 登录 | `/login` | 发送验证码、登录、跳转注册 | `POST /api/user/send-code`<br>`POST /api/user/login` |
| 注册 | `/register` | 发送验证码、勾选协议、注册、返回登录 | `POST /api/user/send-code`<br>`POST /api/user/register` |
| 问诊入口 | `/consultation/index` | 选择问诊成员、查看问诊记录 | 无（前端导航） |
| 选择问诊成员 | `/consultation/select` | 选择成员、搜索、添加成员、返回 | `GET /api/family/list` |
| 问诊历史 | `/consultation/history` | 打开历史对话、筛选成员、重试 | `GET /api/consultation/history` |
| 添加家庭成员 | `/family/add` | 填写资料、保存、取消/返回 | `POST /api/family/add` |
| 成员健康档案 | `/family/detail/:id` | 编辑成员、上传病历、录入指标、查看报告 | `GET /api/family/detail/{id}`<br>`GET /api/health/records`<br>`GET /api/health/report` |
| 健康指标录入 | `/health/record` | 选成员、选指标、保存、返回 | `GET /api/family/list`<br>`POST /api/health/record` |
| 病历详情 | `/medical-records/:id` | 原件预览、改日期、重新解析、删除、返回 | `GET /api/medical-records/{id}`<br>`PUT /api/medical-records/{id}`<br>`DELETE /api/medical-records/{id}` |
| 病历上传与解析 | 病历管理内流程 | 选择文件、上传、查看进度、重试 | `POST /api/medical-records`<br>`POST /api/medical-records/upload`<br>`POST /api/medical-records/{id}/parse` |

## 3. P1/P2 页面与增强流程

| 级别 | 页面 | 建议路由 | 主要功能按钮 | PRD 接口 |
|---|---|---|---|---|
| P1 | 健康报告 | `/health/report` | 切换成员、周/月、刷新、重试 | `GET /api/family/list`<br>`GET /api/health/report?memberId={id}&period=week|month` |
| P1 | 添加药品 | `/medication/add` | 填写药品、保存、取消/返回 | `POST /api/medication/add` |
| P1 | 药品详情与相互作用 | `/medication/detail` | 选择检查范围、检查相互作用、删除、返回 | `GET /api/medication/list`<br>`POST /api/medication/check-interaction`<br>`DELETE /api/medication/delete/{id}` |
| P2 | 儿童剂量计算 | `/medication/children` | 填写年龄/体重/药品、计算、重置、返回 | `POST /api/medication/children-dosage` |
| P2 | 移动端药品扫码 | `/medication/scan` | 授权相机、扫码、重新扫描、手动输入 | `POST /api/medication/scan` |

## 4. 开发验收要点

- 登录成功恢复原目标路由；401 清理登录态并返回登录。
- 成员相关页以页面成员 ID 为准，切换成员后重新取数。
- 上传、OCR、AI 生成和报告均有加载、成功、失败、重试状态。
- 删除成员、药品、病历及退出登录必须二次确认。
- AI 问诊、健康报告、相互作用、儿童剂量结果固定显示“健康参考，不替代医生诊断/处方”。

## 5. 建议迭代批次

| 批次 | 交付范围 | 完成标志 |
|---|---|---|
| 第 1 批 | 登录/注册、成员添加、健康录入 | 用户能登录并为指定成员录入第一条指标。 |
| 第 2 批 | 问诊入口/选成员/历史、病历详情/上传解析 | 问诊与病历整理形成可恢复闭环。 |
| 第 3 批 | 健康报告、药品添加/详情/剂量计算、扫码 | 完成 P1，按排期加入 P2 移动端能力。 |

说明：本表是页面缺口清单，不替代原 PRD 的字段、错误状态、隐私与医疗安全要求；接口以联调时后端实际契约为准。
