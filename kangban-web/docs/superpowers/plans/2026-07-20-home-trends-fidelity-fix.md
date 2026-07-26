# Home And Trends Fidelity Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the shared-shell implementations of the home and health-trends routes with page-specific structures that match their Stitch HTML and reference screenshots at the original source viewports.

**Architecture:** `App.jsx` will render `HomePage` and `HealthTrendsPage` outside the existing `AppShell`; consultation, records, and medications continue using `AppShell`. Each rebuilt page owns a scoped shell and scoped stylesheet so the correction cannot change the remaining routes.

**Tech Stack:** React 18, Vite, Lucide React icons, local Stitch JPG assets, CSS scoped by `.stitch-home` and `.stitch-trends`.

## Global Constraints

- Use `05-home-redesigned.html/png` and `02-health-trends.html/png` as the exact source of truth.
- Validate home at 2560×2224 and health trends at 2560×2416.
- Use local Stitch assets only for visible raster imagery.
- Preserve hash navigation and existing interactions.
- Do not modify the consultation, records, or medications page structures.
- Keep 390×844 responsive layouts free of horizontal overflow.

---

### Task 1: Add page-shell routing contract

**Files:**
- Modify: `src/App.jsx`
- Create: `src/shellMode.js`
- Create: `src/shellMode.test.js`

**Interfaces:**
- `shellModeForPage(pageId)` returns `"home"`, `"trends"`, or `"app"`.
- `App.jsx` renders direct page output for `home`/`trends` and wraps only `app` pages in `AppShell`.

- [ ] **Step 1: Write a failing Node test asserting that home and trends bypass the shared shell and all other known routes use it.**
- [ ] **Step 2: Run `npm test -- src/shellMode.test.js`; expect module-not-found failure for `src/shellMode.js`.**
- [ ] **Step 3: Implement `shellModeForPage` and update `App.jsx` to branch on it.**
- [ ] **Step 4: Run `npm test`; expect all navigation and shell-mode tests to pass.**

### Task 2: Rebuild the Stitch home page

**Files:**
- Replace: `src/pages/HomePage.jsx`
- Create: `src/styles/home.css`
- Modify: `src/main.jsx`

**Interfaces:**
- `HomePage({ onNavigate })` owns its desktop/mobile menu state and routes through `onNavigate(pageId)`.
- `.stitch-home` contains a 280px sidebar, fluid center column, and 360px right rail.

- [ ] **Step 1: Translate the exact semantic section order from `05-home-redesigned.html`: sidebar, center content, right rail, mobile navigation.**
- [ ] **Step 2: Use the local male avatar and Apple Watch image in the same card positions and crops as the reference.**
- [ ] **Step 3: Implement the 2×2 health-metric grid with matching card dimensions, dotted chart treatments, values, labels, and device card.**
- [ ] **Step 4: Add responsive rules for sidebar collapse, right-rail stacking, and 390px mobile navigation.**
- [ ] **Step 5: Verify AI CTA and navigation clicks still update the hash.**

### Task 3: Rebuild the Stitch health-trends page

**Files:**
- Replace: `src/pages/HealthTrendsPage.jsx`
- Replace: `src/components/HealthChart.jsx`
- Create: `src/styles/trends.css`
- Modify: `src/main.jsx`

**Interfaces:**
- `HealthTrendsPage({ onNavigate })` owns `range` and `selectedMetric` state.
- `HealthChart({ range })` renders the two line series and horizontal guides from the Stitch reference.
- `.stitch-trends` owns its top navigation, 12-column content grid, and footer.

- [ ] **Step 1: Translate the top navigation and title/range header from `02-health-trends.html`.**
- [ ] **Step 2: Recreate the 8-column chart card, two line series, axis labels, and three metric summary cards.**
- [ ] **Step 3: Recreate the 4-column patient card and four-row recent-records card with matching status colors and CTA.**
- [ ] **Step 4: Add the reference footer and responsive top navigation behavior.**
- [ ] **Step 5: Verify range tabs and metric cards update selected styling without affecting routing.**

### Task 4: Visual comparison and regression verification

**Files:**
- Modify: `design-qa.md`
- Modify: only `src/styles/home.css`, `src/styles/trends.css`, `src/pages/HomePage.jsx`, or `src/pages/HealthTrendsPage.jsx` for visual corrections

- [ ] **Step 1: Run `npm test` and `npm run build`; require exit code 0.**
- [ ] **Step 2: Capture home at 2560×2224 and trends at 2560×2416 in the in-app browser.**
- [ ] **Step 3: Place each local capture beside its matching Stitch reference and inspect shell geometry, padding, typography, card radii, imagery, and vertical rhythm.**
- [ ] **Step 4: Fix all P0/P1/P2 differences and recapture until both comparisons pass.**
- [ ] **Step 5: Check all five routes at 390×844 for horizontal overflow and verify consultation, records, and medications still load.**
- [ ] **Step 6: Update `design-qa.md` with the new evidence and set `final result: passed` only after all checks pass.**

