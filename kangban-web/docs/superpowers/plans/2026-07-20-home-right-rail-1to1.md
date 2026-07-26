# 首页右侧竖栏一比一复刻实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除首页中间宽度下右侧信息区横向铺开的状态，使个人信息、用药提醒、近期报告在桌面端始终组成右侧单列竖栏，并保持参考图桌面布局不变。

**Architecture:** 保留现有 `HomePage.jsx` 三栏 DOM，只修正 `home.css` 的响应式断点。基础桌面布局继续使用 280px 左栏和 360px 右栏；901–1180px 隐藏左栏、显示紧凑顶部栏，为竖直右栏让出空间；900px 以下才把右栏整体放到主内容后方，且仍为单列。

**Tech Stack:** React 19、Vite 8、CSS、Node.js `node:test`

## Global Constraints

- 1330 × 1146 参考图是桌面视觉验收基准。
- Stitch 灰色提示浮层和蓝色编辑器边框不复刻。
- 右栏不得出现 `repeat(3, ...)` 或其他三列排列。
- 390 × 844 不得横向滚动。
- 不修改健康趋势、AI 问诊、病历管理、用药管理页面。

---

### Task 1: 建立右栏单列回归测试

**Files:**
- Create: `src/homeRailLayout.test.js`
- Test: `src/homeRailLayout.test.js`

**Interfaces:**
- Consumes: `src/styles/home.css` 文本。
- Produces: 首页右栏单列和断点行为的静态契约测试。

- [ ] **Step 1: 写失败测试**

```js
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const cssUrl = new URL('./styles/home.css', import.meta.url);

test('home right rail never becomes a three-column strip', async () => {
  const css = await readFile(cssUrl, 'utf8');
  assert.doesNotMatch(css, /home-right-rail[^}]*repeat\(3/s);
  assert.match(css, /\.home-right-rail\s*\{[^}]*grid-template-columns:\s*1fr/s);
});

test('home only stacks the right rail at the mobile breakpoint', async () => {
  const css = await readFile(cssUrl, 'utf8');
  assert.match(css, /@media \(max-width:\s*900px\)[\s\S]*?\.home-stage\s*\{[^}]*flex-wrap:\s*wrap/s);
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `node --test src/homeRailLayout.test.js`

Expected: FAIL，因为现有 1180px 断点包含 `repeat(3, minmax(0,1fr))`，基础右栏也未显式声明单列。

### Task 2: 修正首页响应式布局

**Files:**
- Modify: `src/styles/home.css:66-116`
- Test: `src/homeRailLayout.test.js`

**Interfaces:**
- Consumes: 现有 `.home-sidebar`、`.home-stage`、`.home-mobile-top`、`.home-right-rail` 类。
- Produces: 桌面单列右栏、紧凑桌面双区布局和移动端单列堆叠。

- [ ] **Step 1: 明确基础右栏为单列**

将基础规则补充为：

```css
.home-right-rail {
  width: 360px;
  flex: 0 0 360px;
  display: grid;
  grid-template-columns: 1fr;
  align-content: start;
}
```

- [ ] **Step 2: 将 901–1180px 改为紧凑桌面布局**

将原来的横排规则替换为：

```css
@media (max-width: 1180px) {
  .home-sidebar { transform: translateX(-102%); transition: transform .2s ease; box-shadow: 0 20px 50px rgba(19,27,46,.14); }
  .home-sidebar.open { transform: translateX(0); }
  .home-sidebar-close { display: grid; place-items: center; }
  .home-stage { margin-left: 0; }
  .home-center { max-width: none; }
  .home-mobile-top { height: 72px; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; background: #fff; border-bottom: 1px solid rgba(228,190,188,.18); }
  .home-mobile-top > button { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: #f2f3ff; }
}
```

- [ ] **Step 3: 仅在 900px 以下堆叠右栏**

移动断点包含：

```css
@media (max-width: 900px) {
  .home-stage { flex-wrap: wrap; margin-left: 0; }
  .home-center { padding: 28px 20px 96px; }
  .home-right-rail { width: auto; flex: 1 1 100%; min-height: 0; grid-template-columns: 1fr; padding: 0 20px 96px; border-left: 0; }
}
```

- [ ] **Step 4: 运行定向与完整测试**

Run: `node --test src/homeRailLayout.test.js`

Expected: 2 tests pass.

Run: `npm test`

Expected: all tests pass, 0 failures.

### Task 3: 浏览器视觉验收与构建

**Files:**
- Modify: `design-qa.md`

**Interfaces:**
- Consumes: 用户参考截图和本地 `#home` 页面。
- Produces: 同尺寸截图证据、移动端无溢出证据和最终构建结果。

- [ ] **Step 1: 桌面对照**

在 1330 × 1146 打开 `http://localhost:4173/#home`，截图并与用户参考图并排检查：左栏、中间画布、右栏分界，以及右栏三组内容的纵向顺序。

- [ ] **Step 2: 中间宽度复现检查**

在 1100 × 900 检查：左侧固定栏隐藏、顶部栏显示、右侧 360px 信息栏仍在右边并保持单列。

- [ ] **Step 3: 移动端检查**

在 390 × 844 检查 `document.documentElement.scrollWidth <= window.innerWidth`，并确认右栏内容位于主内容后方且单列排列。

- [ ] **Step 4: 更新验收记录并构建**

在 `design-qa.md` 记录三种视口结果。

Run: `npm run build`

Expected: Vite build exits 0.
