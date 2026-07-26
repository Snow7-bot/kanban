# Health Trends Left Sidebar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the health-trends top navigation with the exact shared left sidebar used by consultation, medical-records, and medications pages, without showing a top utility bar.

**Architecture:** Route `trends` through the existing `AppShell`, add a `showTopBar` switch to that shell, and keep the existing `Sidebar` as the single navigation implementation. Remove the page-owned trends topbar and provide only a floating mobile menu trigger when the shared sidebar becomes a drawer.

**Tech Stack:** React, Vite, Lucide React, scoped CSS, Node test runner.

## Global Constraints

- Reuse `src/components/Sidebar.jsx`; do not copy or fork its markup or styles.
- Do not render `TopBar` on the health-trends route.
- Keep the trends title, metric tabs, chart, statistic cards, patient card, recent records, and footer.
- Keep consultation, medical-records, medications, and home structures unchanged.
- Desktop sidebar width remains `224px`; the existing compact width remains `198px`.
- At the drawer breakpoint, provide one standalone menu button and no top toolbar.
- The workspace is not a Git repository, so commit steps are intentionally omitted.

---

### Task 1: Route Health Trends Through The Shared Sidebar Shell

**Files:**
- Modify: `src/shellMode.test.js`
- Modify: `src/shellMode.js`
- Modify: `src/App.jsx`

**Interfaces:**
- `shellModeForPage(pageId)` returns `"home"` only for `home`; all other known routes, including `trends`, return `"app"`.
- `AppShell` receives `showTopBar={pageId !== 'trends'}` from `App`.

- [ ] **Step 1: Update the shell contract test before production code**

```js
test('home keeps its custom shell while trends uses the shared app shell', () => {
  assert.equal(shellModeForPage('home'), 'home');
  assert.equal(shellModeForPage('trends'), 'app');
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `node --test src/shellMode.test.js`

Expected: FAIL because `shellModeForPage('trends')` still returns `"trends"`.

- [ ] **Step 3: Implement the minimal shell-mode change**

```js
export function shellModeForPage(pageId) {
  if (pageId === 'home') return 'home';
  return 'app';
}
```

In `src/App.jsx`, change the shared-shell render to:

```jsx
<AppShell pageId={pageId} onNavigate={navigateTo} showTopBar={pageId !== 'trends'}>
  {page}
</AppShell>
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `node --test src/shellMode.test.js`

Expected: both shell-mode tests PASS.

---

### Task 2: Add The Sidebar-Only AppShell Variant

**Files:**
- Create: `src/appShell.test.js`
- Modify: `src/components/AppShell.jsx`
- Modify: `src/styles/global.css`

**Interfaces:**
- `AppShell({ pageId, onNavigate, children, showTopBar = true })` conditionally renders `TopBar`.
- `.shell-menu-fab` is hidden on desktop and shown only below the existing `820px` drawer breakpoint.
- `.app-shell-sidebar-only` identifies the no-topbar shell and reserves mobile space for the floating menu button.

- [ ] **Step 1: Write structural tests before changing AppShell**

```js
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('AppShell can omit TopBar and exposes a standalone mobile menu trigger', async () => {
  const source = await readFile(new URL('./components/AppShell.jsx', import.meta.url), 'utf8');
  assert.match(source, /showTopBar\s*=\s*true/);
  assert.match(source, /showTopBar\s*\?\s*<TopBar/);
  assert.match(source, /shell-menu-fab/);
  assert.match(source, /app-shell-sidebar-only/);
});

test('the sidebar-only menu trigger follows the shared 820px drawer breakpoint', async () => {
  const css = await readFile(new URL('./styles/global.css', import.meta.url), 'utf8');
  assert.match(css, /\.shell-menu-fab\s*\{[^}]*display:\s*none/s);
  assert.match(css, /@media \(max-width:\s*820px\)[\s\S]*?\.shell-menu-fab\s*\{[^}]*display:\s*grid/s);
  assert.match(css, /@media \(max-width:\s*820px\)[\s\S]*?\.app-shell-sidebar-only \.page-canvas\s*\{[^}]*padding-top:\s*64px/s);
});
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `node --test src/appShell.test.js`

Expected: FAIL because the conditional topbar, shell class, and floating menu button do not exist.

- [ ] **Step 3: Implement the minimal AppShell variant**

Replace `src/components/AppShell.jsx` with the same navigation logic plus this conditional structure:

```jsx
import { useState } from 'react';
import { Menu } from 'lucide-react';
import Sidebar from './Sidebar.jsx';
import TopBar from './TopBar.jsx';

export default function AppShell({ pageId, onNavigate, children, showTopBar = true }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = (nextPage) => { onNavigate(nextPage); setMenuOpen(false); };
  return <div className={`app-shell ${showTopBar ? '' : 'app-shell-sidebar-only'}`}>
    <Sidebar pageId={pageId} onNavigate={navigate} open={menuOpen} onClose={() => setMenuOpen(false)} />
    <div className="page-canvas">
      {showTopBar ? <TopBar onMenu={() => setMenuOpen(true)} compact={pageId === 'records'} /> : <button className="shell-menu-fab" aria-label="打开导航" onClick={() => setMenuOpen(true)}><Menu size={20} /></button>}
      {children}
    </div>
  </div>;
}
```

- [ ] **Step 4: Add only the required responsive CSS**

Add near the existing shell styles:

```css
.shell-menu-fab { display: none; }
```

Inside the existing `@media (max-width: 820px)` block add:

```css
.shell-menu-fab { position: fixed; top: 16px; left: 16px; z-index: 19; width: 42px; height: 42px; display: grid; place-items: center; color: var(--ink); background: var(--surface); border: 1px solid var(--line); border-radius: 12px; box-shadow: var(--shadow-card); }
.app-shell-sidebar-only .page-canvas { padding-top: 64px; }
```

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run: `node --test src/appShell.test.js src/shellMode.test.js`

Expected: all AppShell and shell-mode tests PASS.

---

### Task 3: Remove The Health Trends Top Navigation

**Files:**
- Modify: `src/trendsFidelity.test.js`
- Modify: `src/pages/HealthTrendsPage.jsx`
- Modify: `src/styles/trends.css`

**Interfaces:**
- `HealthTrendsPage()` owns only `metric` state.
- `.stitch-trends` remains the scoped content root inside the shared `page-canvas`.
- Existing landmarks `trends-left-column`, `trends-right-column`, and `trends-footer` remain unchanged.

- [ ] **Step 1: Replace the old topbar expectation with the new structure contract**

```js
test('health trends removes its top navigation and keeps dashboard landmarks', async () => {
  const source = await readFile(new URL('./pages/HealthTrendsPage.jsx', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /TrendsTopbar|trends-topbar|menuOpen|setMenuOpen/);
  for (const landmark of ['trends-left-column', 'trends-right-column', 'trends-footer']) {
    assert.match(source, new RegExp(landmark));
  }
  for (const reading of ['120/80', '118/78', '142/90', '瑞安·沃克']) {
    assert.match(source, new RegExp(reading.replace('/', '\\/')));
  }
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `node --test src/trendsFidelity.test.js`

Expected: FAIL because `TrendsTopbar` and its menu state still exist.

- [ ] **Step 3: Remove only the obsolete page-owned navigation**

In `src/pages/HealthTrendsPage.jsx`:

- Remove the `TrendsTopbar` function.
- Remove `Bell`, `Menu`, `Search`, and `Settings` from the Lucide imports.
- Remove the `menuOpen` state.
- Remove `<TrendsTopbar ... />` from `.stitch-trends`.
- Keep all content from `<main className="trends-main">` through `<footer className="trends-footer">` unchanged.

- [ ] **Step 4: Remove only CSS selectors that belonged to the deleted topbar**

Delete `.trends-topbar`, `.trends-topbar-inner`, `.trends-topbar-left`, `.trends-topbar-actions`, `.trends-nav-links`, `.trends-logo`, `.trends-search`, `.trends-settings`, and `.trends-menu` rules, including their references inside media queries. Preserve all `.trends-main`, dashboard, chart, card, footer, and responsive content rules.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run: `node --test src/trendsFidelity.test.js src/appShell.test.js src/shellMode.test.js`

Expected: all focused tests PASS.

---

### Task 4: Regression And Browser Verification

**Files:**
- Modify: `design-qa.md`

**Interfaces:**
- Health trends URL: `http://localhost:4173/#trends`
- Comparison routes: `http://localhost:4173/#records`, `http://localhost:4173/#consultation`, and `http://localhost:4173/#medications`.

- [ ] **Step 1: Run all automated tests**

Run: `npm test`

Expected: exit code `0`, including the new shell and trends structure tests.

- [ ] **Step 2: Build the production bundle**

Run: `npm run build`

Expected: exit code `0` and Vite reports a successful build.

- [ ] **Step 3: Verify the desktop layout at 1280×900 in the in-app browser**

Inspect `#trends` and require all of the following:

- one `.sidebar` exists and its computed width is `224px`;
- `.nav-item.active` contains `健康趋势`;
- `.top-utility` and `.trends-topbar` do not exist;
- `.trends-main`, `.trends-left-column`, `.trends-right-column`, and `.trends-footer` are visible;
- `document.documentElement.scrollWidth === window.innerWidth`;
- browser error log is empty.

- [ ] **Step 4: Compare the shared sidebar against another route**

Open `#records` at 1280×900 and confirm `.sidebar` has the same computed width, padding, background color, border, and navigation item geometry as `#trends`. Confirm `#records` still contains `.top-utility` while `#trends` does not.

- [ ] **Step 5: Verify the drawer behavior at 640×900**

Inspect `#trends` and require all of the following:

- `.shell-menu-fab` is visible;
- clicking it adds `.open` to `.sidebar`;
- clicking the close button removes `.open`;
- no `.top-utility` or `.trends-topbar` exists;
- `document.documentElement.scrollWidth === window.innerWidth`.

- [ ] **Step 6: Update the design QA record**

Append a dated health-trends sidebar section to `design-qa.md` with the tested viewports, shared-sidebar comparison, mobile drawer result, console result, and `final result: passed` only when every preceding check succeeds.

