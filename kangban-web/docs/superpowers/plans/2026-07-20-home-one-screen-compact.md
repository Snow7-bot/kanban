# 首页一屏紧凑布局 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `1280×900` 桌面视口内完整展示首页，使用两列两行的正方形健康指标卡和更扁的 AI 问诊按钮。

**Architecture:** 保留 `HomePage.jsx` 的组件和数据结构，仅调整 `home.css` 的桌面几何值与 `640px` 移动覆盖规则。静态回归测试锁定关键尺寸，浏览器测试负责验证真实宽高、滚动高度和内容溢出。

**Tech Stack:** React、CSS、Node.js 内置测试运行器、Vite、Codex 内置浏览器

## Global Constraints

- `1280×900` 下页面滚动高度不超过 `900px`。
- 桌面健康指标网格最大宽度为 `480px`，间距为 `20px`，卡片使用 `aspect-ratio: 1`。
- AI 问诊按钮桌面高度为 `156px`。
- 左侧导航尺寸、右侧 `385px` 竖栏、文案、颜色、图标和交互保持不变。
- `640px` 及以下继续单列排列，并取消卡片强制正方形。

---

### Task 1: 首页紧凑一屏布局

**Files:**
- Modify: `src/homeRailLayout.test.js`
- Modify: `src/styles/home.css`

**Interfaces:**
- Consumes: `.home-center`、`.home-ai-hero`、`.home-metrics-section`、`.home-section-title`、`.home-metrics-grid`、`.home-metric-card` 和内部媒体元素。
- Produces: `1280×900` 一屏布局，以及 `640px` 以下非正方形单列回退。

- [ ] **Step 1: 写入失败的 CSS 几何回归测试**

把旧的 `222px` 和 `288px` 断言替换为：

```js
assert.match(css, /\.home-center\s*\{[^}]*padding:\s*32px 24px 24px 40px/s);
assert.match(css, /\.home-ai-hero\s*\{[^}]*min-height:\s*156px[^}]*margin-top:\s*20px/s);
assert.match(css, /\.home-metrics-section\s*\{[^}]*margin-top:\s*20px/s);
assert.match(css, /\.home-section-title\s*\{[^}]*margin-bottom:\s*16px/s);
assert.match(css, /\.home-metrics-grid\s*\{[^}]*max-width:\s*480px[^}]*gap:\s*20px[^}]*margin:\s*0 auto/s);
assert.match(css, /\.home-metric-card\s*\{[^}]*aspect-ratio:\s*1[^}]*min-height:\s*0[^}]*padding:\s*20px/s);
assert.match(css, /@media \(max-width:\s*640px\)[\s\S]*?\.home-metric-card\s*\{[^}]*aspect-ratio:\s*auto[^}]*min-height:\s*240px/s);
```

- [ ] **Step 2: 运行定向测试并确认红灯**

Run: `node --test src/homeRailLayout.test.js`

Expected: FAIL，失败原因显示现有 CSS 仍为 `71px` 顶部内边距、`222px` 问诊高度或 `288px` 卡片高度。

- [ ] **Step 3: 实现最小 CSS 修改**

在 `src/styles/home.css` 中应用以下桌面规则：

```css
.home-center { flex: 1 1 auto; max-width: 1024px; padding: 32px 24px 24px 40px; }
.home-ai-hero { min-height: 156px; margin-top: 20px; padding: 24px 32px; }
.home-ai-label { gap: 10px; font-size: 15px; }
.home-ai-label i { width: 32px; height: 32px; }
.home-ai-hero strong { margin-top: 10px; font-size: 30px; }
.home-ai-hero small { margin-top: 6px; font-size: 15px; }
.home-ai-arrow { width: 56px; height: 56px; flex-basis: 56px; }
.home-metrics-section { margin-top: 20px; }
.home-section-title { margin-bottom: 16px; }
.home-metrics-grid { max-width: 480px; margin: 0 auto; gap: 20px; }
.home-metric-card { aspect-ratio: 1; min-height: 0; padding: 20px; }
.home-reading strong { font-size: 42px; }
.home-dot-chart { height: 100px; }
.home-device-image { left: 20px; right: 20px; bottom: 18px; width: calc(100% - 40px); height: 96px; }
```

在已有 `@media (max-width: 640px)` 中把卡片覆盖改为：

```css
.home-metric-card { aspect-ratio: auto; min-height: 240px; }
```

- [ ] **Step 4: 运行定向测试并确认绿灯**

Run: `node --test src/homeRailLayout.test.js`

Expected: 全部测试通过，0 failures。

- [ ] **Step 5: 运行全量验证**

Run: `npm test`

Expected: 全部测试通过，0 failures。

Run: `npm run build`

Expected: Vite 构建成功，退出码为 0。

当前目录不是 Git 仓库，因此不执行提交步骤。

### Task 2: 浏览器视觉与尺寸验收

**Files:**
- Verify: `src/styles/home.css`
- Verify: `http://localhost:4173/#home`

**Interfaces:**
- Consumes: Task 1 产生的紧凑布局 CSS。
- Produces: `1280×900`、`1100×900` 和 `640×900` 的真实布局证据。

- [ ] **Step 1: 验证 1280×900 一屏布局**

确认 `document.documentElement.scrollHeight <= 900`，四张卡片宽高差不超过 `1px`，卡片边长约 `230px`，问诊按钮高度约 `156px`。

- [ ] **Step 2: 验证内容完整性**

检查红色问诊区、四张卡片的 `scrollWidth <= clientWidth` 且 `scrollHeight <= clientHeight`，截图确认所有读数、状态和 Apple Watch 图片无重叠。

- [ ] **Step 3: 验证响应式回退**

在 `1100×900` 下确认两列卡片仍为正方形且无横向溢出；在 `640×900` 下确认单列卡片 `aspect-ratio` 为 `auto`。

- [ ] **Step 4: 恢复默认视口并保留预览**

清除临时视口覆盖，检查控制台没有错误，保留首页预览标签页。
