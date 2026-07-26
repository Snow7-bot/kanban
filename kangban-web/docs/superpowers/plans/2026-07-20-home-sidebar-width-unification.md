# 首页左侧导航宽度统一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页左侧导航的桌面、窄桌面和移动端尺寸规则统一到 AI 问诊及病历记录共用侧栏的 `224px`、`198px` 和 `820px` 断点。

**Architecture:** 保留首页独立的 `HomeSidebar` 结构，只修改其 CSS 几何值和间距，不耦合共用 `Sidebar` 组件。布局测试锁定三个断点的宽度、内容偏移和右侧竖栏不变，再通过真实浏览器检查文字溢出和三栏位置。

**Tech Stack:** React、CSS、Node.js 内置测试运行器、Vite、Codex 内置浏览器

## Global Constraints

- 标准桌面左侧导航和首页内容左偏移必须为 `224px`。
- 视口宽度不超过 `1100px` 时，两者必须为 `198px`。
- 视口宽度不超过 `820px` 时，左侧导航必须使用现有移动端抽屉行为，首页内容左偏移必须为 `0`。
- 右侧竖栏继续保持 `385px`，不修改导航文案、图标、交互和颜色。
- 仅修改首页样式和首页布局回归测试，不改共用 `Sidebar` 组件。

---

### Task 1: 锁定并实现首页侧栏统一尺寸

**Files:**
- Modify: `src/homeRailLayout.test.js`
- Modify: `src/styles/home.css`

**Interfaces:**
- Consumes: `HomeSidebar` 使用的 `.home-sidebar`、`.home-stage`、`.home-brand`、`.home-nav`、`.home-user-card` CSS 类。
- Produces: 三档响应式几何契约：默认 `224px`、`max-width: 1100px` 为 `198px`、`max-width: 820px` 为移动抽屉。

- [ ] **Step 1: 先修改回归测试，使其表达新尺寸并失败**

在 `src/homeRailLayout.test.js` 的桌面几何测试中，把首页侧栏断言改为：

```js
assert.match(css, /\.home-sidebar\s*\{[^}]*width:\s*224px/s);
assert.match(css, /\.home-stage\s*\{[^}]*margin-left:\s*224px/s);
assert.match(css, /@media \(max-width:\s*1100px\)[\s\S]*?\.home-sidebar\s*\{[^}]*width:\s*198px/s);
assert.match(css, /@media \(max-width:\s*1100px\)[\s\S]*?\.home-stage\s*\{[^}]*margin-left:\s*198px/s);
assert.match(css, /@media \(max-width:\s*820px\)[\s\S]*?\.home-sidebar\s*\{[^}]*translateX\(-102%\)/s);
```

保留右侧竖栏 `385px` 的原有断言。

- [ ] **Step 2: 运行定向测试并确认失败原因正确**

Run: `node --test src/homeRailLayout.test.js`

Expected: FAIL，失败信息显示当前 CSS 仍为 `.home-sidebar width: 288px` 或缺少 `1100px`、`820px` 新规则。

- [ ] **Step 3: 最小化修改首页 CSS**

在 `src/styles/home.css` 中完成以下精确变化：

```css
.home-sidebar { width: 224px; }
.home-sidebar > .home-brand { padding: 28px 24px; }
.home-nav { padding: 12px; }
.home-user-card { margin: 0 16px 20px; padding: 14px 12px; }
.home-stage { margin-left: 224px; }

@media (max-width: 1100px) {
  .home-sidebar { width: 198px; }
  .home-stage { margin-left: 198px; }
  .home-sidebar > .home-brand { padding-inline: 20px; }
  .home-nav-item { gap: 10px; padding-inline: 12px; }
  .home-user-card { margin-inline: 12px; }
}

@media (max-width: 820px) {
  .home-sidebar { transform: translateX(-102%); transition: transform .2s ease; box-shadow: 0 20px 50px rgba(19,27,46,.14); }
  .home-sidebar.open { transform: translateX(0); }
  .home-sidebar-close { display: grid; place-items: center; }
  .home-stage { flex-wrap: wrap; margin-left: 0; }
  .home-center { padding: 28px 20px 96px; }
  .home-right-rail { width: auto; flex: 1 1 100%; min-height: 0; grid-template-columns: 1fr; padding: 0 20px 96px; border-left: 0; }
  .home-mobile-top { height: 72px; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; background: #fff; border-bottom: 1px solid rgba(228,190,188,.18); }
  .home-mobile-top > button { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: #f2f3ff; }
  .home-mobile-nav { position: fixed; inset: auto 0 0; z-index: 35; height: 72px; display: flex; justify-content: space-around; padding: 8px 12px; background: rgba(255,255,255,.94); border-top: 1px solid rgba(228,190,188,.25); backdrop-filter: blur(12px); }
}
```

删除原来首页 `max-width: 1180px` 和 `max-width: 900px` 中与侧栏宽度、内容偏移、移动抽屉有关的旧规则；保留与新断点不冲突的中间内容规则。

- [ ] **Step 4: 运行定向测试并确认通过**

Run: `node --test src/homeRailLayout.test.js`

Expected: 该文件全部测试通过，0 failures。

- [ ] **Step 5: 运行完整自动化测试和生产构建**

Run: `npm test`

Expected: 全部测试通过，0 failures。

Run: `npm run build`

Expected: Vite 构建成功，退出码为 0。

当前工作目录不是 Git 仓库，因此本任务不执行提交步骤。

### Task 2: 浏览器验收三档响应式布局

**Files:**
- Verify: `src/styles/home.css`
- Verify: `http://localhost:4173/#home`

**Interfaces:**
- Consumes: Task 1 产生的三档 CSS 几何规则。
- Produces: 桌面、窄桌面和移动端的实际浏览器验收证据。

- [ ] **Step 1: 验证标准桌面**

在 `1280×900` 视口打开首页，读取元素边界并确认：

```text
.home-sidebar width = 224
.home-sidebar x = 0
.home-stage x = 224
.home-right-rail width = 385
```

同时截图确认品牌、五个导航项和底部用户卡没有截断或重叠。

- [ ] **Step 2: 验证窄桌面**

在 `1100×900` 视口确认：

```text
.home-sidebar width = 198
.home-sidebar x = 0
.home-stage x = 198
.home-sidebar transform = none
```

截图确认导航文字和用户卡完整显示。

- [ ] **Step 3: 验证移动端**

在 `820×900` 视口确认：

```text
.home-stage x = 0
.home-sidebar transform != none
.home-mobile-top display = flex
```

验证菜单按钮可以打开和关闭左侧导航，且移动底部导航保持显示。

- [ ] **Step 4: 恢复默认视口并保留首页预览**

清除临时视口覆盖，保留 `http://localhost:4173/#home` 作为可交付预览标签页。
