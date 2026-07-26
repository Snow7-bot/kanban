# 首页右侧竖栏一比一复刻视觉验收

## 比较目标

- 视觉真值：`/var/folders/89/6l5p5pt17jscpc_csct0w5ph0000gn/T/codex-clipboard-3032d8f3-891f-4294-bf74-317b360ea184.png`
- 实现路由：`http://localhost:4173/#home`
- 最终桌面截图：`/private/tmp/kangban-home-right-rail-1332-final.png`
- 中间宽度截图：`/private/tmp/kangban-home-right-rail-1100.png`
- 移动端截图：`/private/tmp/kangban-home-right-rail-390.png`
- 桌面状态：浅色主题、首页默认状态、1332 × 1146。

## 全视图对照证据

参考图与最终桌面截图已在同一个原始分辨率比较输入中并排打开。最终实现的关键几何值：

- 左侧栏：x=0，w=288。
- AI 主卡：x=328，y≈190，w=595，h=222。
- 健康指标网格：x=328，y≈499，w=595，h=600。
- 右侧竖栏：x=947，w=385，单列轨道宽 285。
- 个人信息卡：x=988，y=40，w=285，h=260。
- 用药提醒列表：x=988，y=381，w=285，h=180。
- 近期报告卡：x=988，y=642，w=285，h=158。

参考图对应位置约为左栏 0–287、主内容 328–920、右栏起点 945、右栏卡片 986–1272。当前差异为 1–3px 的抗锯齿/边界误差，没有改变栏宽关系、信息层级或密度。

## 聚焦区域说明

本次任务只涉及右侧竖栏，完整参考图和实现图均以 1332px 原始宽度打开，右栏本身宽约 385px，头像、标题、两张提醒卡和报告卡均可直接读清；同时使用浏览器 DOM 几何值逐项核对，因此无需额外裁切图片。右栏单列轨道、卡片尺寸和纵向坐标均有上述数值证据。

## 必检表面

- 字体与排版：标题、正文、标签的字号、字重和换行层级与参考一致；没有新增截断或拥挤。
- 间距与布局：原先中间断点的三个区块横排状态已删除；桌面三栏和右栏纵向节奏与参考一致。
- 颜色与令牌：浅紫灰画布、白色卡片、红色主操作和深蓝设备卡保持不变；AI 卡主色像素与参考图一致。
- 图片质量：继续使用本地男性患者头像和 Apple Watch 原图，裁切、清晰度和圆形遮罩正常。
- 文案与内容：本次按已确认规格仅修正布局，保留项目当前业务数据；信息层级与参考一致。
- 图标与控件：沿用项目现有图标库，右栏加号、提醒状态和上传按钮对齐正常。
- 交互与无障碍：移动菜单可打开和关闭；按钮保留可访问名称，图片保留替代文本。

## 响应式证据

- 1100 × 900：固定左栏隐藏、顶部栏显示；主区宽 715，右栏 x=715、w=385，`grid-template-columns` 为 285px，三个区块保持竖直单列；页面宽 1100，无横向溢出。
- 390 × 844：主内容结束后才出现右栏，`railAfterCenter=true`；右栏宽 390、内容轨道 350，页面 `scrollWidth=390`，无横向溢出。
- 移动菜单实测：打开后侧栏 transform 为 x=0；关闭后恢复至屏幕左侧外。
- 浏览器控制台：最终桌面检查无 error/warning。

## 比较历史

### 第一次比较：blocked

- [P1] 右栏位置偏右、主画布偏宽：实现右栏 x=972，参考约 x=945；AI 卡宽 628，参考约 592。
- [P2] 提醒和报告卡高度偏矮：实现提醒列表 155、报告卡 140，参考约 180 和 158。
- 修复：左栏调整到 288px，右栏调整到 385px 并使用非对称内边距；AI 卡、指标卡、个人卡、提醒卡和报告卡按参考高度校准。

### 第二次比较：passed

- 修复后右栏 x=947、轨道宽 285；AI 卡 x=328、w=595；提醒列表 h=180；报告卡 h=158。
- 未发现仍需修复的 P0、P1 或 P2。

## Follow-up Polish

- [P3] Stitch 编辑器提示浮层和外侧蓝色选中边框按确认不属于产品页面，未复刻。
- [P3] 参考图与浏览器实现存在 1–3px 的边界和字体抗锯齿误差，可接受。

final result: passed

# 添加药品 Stitch 复刻验收（2026-07-21）

## 比较目标

- 原图：`stitch/PRD-UI-Prototype-Implementation/add-medication/reference.jpg`（512 × 410）。
- 实现：`http://localhost:4173/#medication-add`，浏览器截图保存为 `/private/tmp/kangban-medication-add-implementation.png`。
- 同屏对照：`/private/tmp/kangban-medication-add-comparison.png`，原图与等比例缩放后的实现截图置于同一张比较图。

**Findings**

- 无 P0/P1/P2 差异。已复刻桌面原型的返回页头、药品基础信息、儿童参数卡、剂量/库存、用药说明、频率/提醒时间和智能分析模块。
- 延续全站确认的统一左侧栏，因此导航密度与单页 Stitch 原图有轻微区别；为既定跨页面约束，不作为本页待修复项。

## 五项保真检查

- 字体与排版：标题、字段标签、说明文字、按钮和表单层级对应原图。
- 间距与布局：左侧宽表单、右侧频率与分析卡、底部操作区的两列桌面布局已做同屏对照。
- 色彩与视觉 token：使用暖灰背景、医疗红主操作、浅红儿童参数卡和细边框。
- 图片与资源：原始 HTML、参考图和头像资源已下载至 `add-medication/`；页面继续使用统一侧栏头像。
- 文案与内容：药品、儿童参数、剂量、库存、频率、提醒时间和分析文案均与原图对应。

## 交互与技术验证

- 儿童年龄/体重参数可填写并显示“需由医生或药师确认”的提示；页面不提供真实儿童剂量数值建议。
- 可添加提醒时间、切换用药说明、调整频率、保存药品并获得状态反馈。
- 浏览器控制台无 error；默认状态已重新加载。
- `npm test`：21 项通过；`npm run build`：成功。

**Implementation Checklist**

- [x] 下载 Stitch HTML、参考图和图片资源。
- [x] 新增 `#medication-add` 路由并保持用药管理侧栏激活。
- [x] 将用药管理“添加药物”接入添加药品页。
- [x] 完成同屏视觉对照、表单交互和浏览器验证。

final result: passed

# 病历详情 Stitch 复刻验收（2026-07-21）

## 比较目标

- 原图：`stitch/PRD-UI-Prototype-Implementation/medical-record-detail/reference.jpg`（512 × 410）。
- 实现：`http://localhost:4173/#record-detail`，浏览器截图保存为 `/private/tmp/kangban-record-detail-implementation.png`。
- 同屏对照：`/private/tmp/kangban-record-detail-comparison.png`，将原图与等比例缩放的实现截图置于同一输入中对比。

**Findings**

- 无 P0/P1/P2 差异。已复刻原图的详情标题与操作区、三项就诊元数据、AI 结构化诊断、处方用药、医嘱随访与原件扫描侧栏。
- 全站左侧栏沿用此前确认的统一版本；与本张 Stitch 原图的导航密度差异属于既定项目约束，不作为待修复项。

## 五项保真检查

- 字体与排版：标题、辅助日期、诊断分组、药品信息及小号说明文本层级对应原图。
- 间距与布局：采用左侧诊断内容、右侧扫描预览的桌面双列结构，并在窄屏单列显示。
- 色彩与视觉 token：使用暖白背景、医疗红告警、浅灰表面、细边框和弱阴影。
- 图片与资源：原始 HTML、参考图、头像和扫描件均下载到 `medical-record-detail/`；扫描件使用 `assets/02.jpg`。
- 文案与内容：医院、科室、主治医师、诊断、处方、随访提醒和 OCR 引擎提示均按原图实现。

## 交互与技术验证

- “重新解析”可显示状态反馈；“添加随访提醒”可显示已添加反馈；扫描件可切换放大状态；返回与删除均回到病历管理。
- 浏览器已验证上述主操作，并重新加载回默认状态。
- 浏览器控制台无 error。
- `npm test`：20 项通过；`npm run build`：成功。

**Implementation Checklist**

- [x] 下载 Stitch HTML、参考图和两张原始图片。
- [x] 新增 `#record-detail` 并复用统一侧栏。
- [x] 将病历管理的“查看完整分析”接入详情页。
- [x] 完成同屏视觉对照与交互验证。

final result: passed

---

# Stitch 健康录入、添加成员、登录与注册验收（2026-07-21）

## 比较目标

- `stitch/PRD-UI-Prototype-Implementation/health-metrics-entry/reference.jpg`
- `stitch/PRD-UI-Prototype-Implementation/add-family-member/reference.jpg`
- `stitch/PRD-UI-Prototype-Implementation/login-password/reference.jpg`
- `stitch/PRD-UI-Prototype-Implementation/register/reference.jpg`

## 已完成实现

- 新增路由：`#health-record`、`#family-add`、`#login`、`#register`；新增“记录指标”共享侧栏入口。
- 健康录入支持成员和指标切换、数值输入、日期/时间、备注与保存反馈；添加成员支持关系、性别与健康备注表单及保存反馈。
- 登录/注册实现原图对应的左右分栏、密码显隐、验证码状态、协议勾选、页面互跳和提交反馈。
- 所有页面复用已下载的 Stitch 本地图片与现有共享组件 token。

## 自动验证

- `npm test`：18 项通过，0 项失败。
- `npm run build`：Vite 构建成功。

## 阻塞项

- 本地浏览器此前明确拒绝访问用于原图与实现并排对照的本地预览地址，并禁止通过替代浏览器或间接方式绕过。按照该限制，本轮未重新打开本地页面、未捕获最终截图，也未声明像素级验收通过。

## 待完成视觉检查

- 使用相同桌面视口，分别将四张原图与 `#health-record`、`#family-add`、`#login`、`#register` 的最终截图并排比较，逐项修正布局、间距、字体和颜色差异。

final result: blocked

# 健康报告 Stitch 复刻验收（2026-07-21）

## 比较目标

- 原图：`stitch/PRD-UI-Prototype-Implementation/health-report/reference.jpg`（512 × 410）。
- 实现：`http://localhost:4173/#health-report`，浏览器截图保存为 `/private/tmp/kangban-health-report-implementation.png`（1280 × 897）。
- 对照图：`/private/tmp/kangban-health-report-comparison.png`。实现截图按相同内容区域缩放后，与原图放入同一张对照图；初始状态为“周”、自己、未查看预约。

**Findings**

- 无 P0/P1/P2 差异。主结构、AI 洞察卡、心率柱图、睡眠环图、步数柱图与预约提醒保持原图的两列信息层级和配色关系。
- 已保留项目既有的统一左侧栏宽度与标签样式；它与 Stitch 原图的导航密度略有差异，但符合此前确认的全站统一侧栏约束，不作为本页待修复项。

## 五项保真检查

- 字体与排版：使用项目既有的标题/正文 token；标题、辅助日期、数值和标签层级均与原图对应。
- 间距与布局：顶部控制区、全宽洞察区、左宽右窄的两行卡片网格及页脚均经同屏对照。
- 色彩与视觉 token：沿用原图的暖白底、医疗红、睡眠蓝、浅灰边框与弱阴影。
- 图片与资源：Stitch 头像资源已下载到 `health-report/assets/01.jpg` 并在成员选择器中使用；其余图标均使用项目图标库。
- 文案与内容：本周日期、72 bpm、7h 30m、8,432 步、AI 洞察与体检提醒均按原图实现。

## 交互与技术验证

- 周/月切换：已在浏览器验证，切换至“月”后标题、日期、心率、步数与洞察文案同步更新。
- 预约详情：已在浏览器验证，按钮切换为“已查看”。
- 默认预览：已重新加载并确认回到“本周健康总结”。
- 浏览器控制台：无 error。
- 自动验证：`npm test` 19 项通过；`npm run build` 成功。

**Implementation Checklist**

- [x] 下载 Stitch 原始 HTML、截图和头像资源。
- [x] 新增 `#health-report` 页面及首页“本周健康总结”入口。
- [x] 实现成员选择、周/月切换与预约状态。
- [x] 完成同屏视觉对照和浏览器交互检查。

final result: passed

---

# Stitch 家庭成员、个人资料、设置与统一左侧栏验收（2026-07-21）

## 比较目标

- 视觉真值：
  - `stitch/PRD-Based-UI-Prototypes/family-members/reference.jpg`
  - `stitch/PRD-Based-UI-Prototypes/profile/reference.jpg`
  - `stitch/PRD-Based-UI-Prototypes/settings/reference.jpg`
- 实现截图：
  - `qa-captures/family-implementation.png`
  - `qa-captures/profile-implementation.png`
  - `qa-captures/settings-implementation.png`
- 移动端截图：`qa-captures/family-mobile-menu.png`（390 × 844）。
- 页面状态：默认浅色主题；统一左侧栏按用户确认覆盖三个页面。

## 已捕获证据

- 家庭成员、个人资料、设置三个路由均在浏览器渲染并分别高亮对应左侧导航项；三个页面均未发现横向溢出。
- 个人资料的保存动作显示“已保存”；设置页的老年模式和清除缓存动作均显示对应状态；390px 下左侧抽屉可打开，且 `scrollWidth` 未超过视口宽度。
- 浏览器控制台在完成上述交互时没有 error、warn 或 warning。
- 自动测试与生产构建本轮重新执行：15 项测试通过，Vite 构建成功。

## 阻塞项

- 浏览器在打开用于“原图与实现并排”的本地对照页时，明确拒绝访问 `http://127.0.0.1:4173`，并禁止使用替代浏览器或间接方式绕过该限制。
- 因此无法完成本轮所需的同一比较输入与页脚加入后的最终浏览器截图。此前三张实现截图是在加入共享页脚之前捕获，不能作为最终视觉验收结论。

## 必检表面状态

- 字体与排版：已由渲染截图确认新增页面使用现有 Inter/Plus Jakarta Sans token；最终并排比对被阻塞。
- 间距与布局：共享左栏、三页卡片布局与移动抽屉已渲染；设置页采用用户确认的左栏覆盖，属于对原图顶部导航的有意替换。
- 颜色与令牌：主操作、活动导航和状态色均复用项目红、浅紫灰、绿色 token；最终像素级比对被阻塞。
- 图片质量：页面使用已下载的 Stitch 本地人物图片；最终对照被阻塞。
- 文案与内容：新增导航及三页可见文案已中文化，且主交互可触发本地反馈。

## 实施检查清单

- [x] 统一所有页面的左侧栏，并新增个人资料、家庭成员、设置入口。
- [x] 加入三张 Stitch 页面及其本地图片资源。
- [x] 验证导航、保存、开关、缓存反馈与移动抽屉。
- [x] 重新执行自动化测试和生产构建。
- [ ] 在允许访问本地预览的浏览器中完成原图与最终实现的并排视觉对照。

final result: blocked

---

# 健康趋势左侧导航统一验收（2026-07-20）

## 验收范围

- 实现路由：`http://localhost:4173/#trends`
- 对比路由：`http://localhost:4173/#records`
- 桌面视口：1280 × 900
- 移动视口：640 × 900

## 桌面结果

- 健康趋势页只存在一个共享 `.sidebar`，计算宽度为 224px。
- 左栏计算样式为 `padding: 25px 12px 16px`、白色背景、`1px solid rgba(230, 230, 240, 0.75)` 右边框。
- 导航项高度 42px、水平内边距 12px、间距 12px、圆角 10px；与病历记录页逐项相同。
- 当前选中项为“健康趋势”。
- 健康趋势页 `.top-utility` 数量为 0，旧 `.trends-topbar` 数量为 0；病历记录页仍保留一个 `.top-utility`。
- 趋势主内容、左右内容列和页脚均正常显示，页面宽度 1280px，无横向溢出。
- 首次截图发现三张统计卡的数值/单位行因共享侧栏占用宽度而拥挤；已在 1280px 断点压缩该行字号和间距，复验后卡片边界不再裁切文字。

## 移动端结果

- 640 × 900 下共享侧栏按现有 820px 断点进入抽屉状态。
- 独立“打开导航”按钮可见，点击后 `.sidebar` 获得 `.open`，点击“关闭菜单”后移除 `.open`。
- 页面没有共享顶部工具栏或旧健康趋势顶部导航。
- 页面 `scrollWidth` 为 640px，无横向溢出。

## 自动验证

- 浏览器控制台：0 条 error。
- `npm test`：12 项通过，0 项失败。
- `npm run build`：Vite 构建成功，1793 个模块完成转换。
- 未发现剩余 P0、P1 或 P2 问题。

final result: passed
