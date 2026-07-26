# Stitch Medical UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local React single-page app that faithfully recreates the five Stitch healthcare prototype screens with shared components, local assets, responsive behavior, and working prototype interactions.

**Architecture:** Vite serves a React app from the project root. `AppShell` owns hash-based page selection and the shared navigation shell; each page module owns its Stitch-specific layout and local state. Design tokens and reusable primitives live in focused component/style files, while static patient/health/medication data lives in one data module.

**Tech Stack:** React 18, Vite, plain CSS, Material Symbols webfont, local Stitch HTML/PNG/JPG assets. No backend, authentication, persistence, or medical API calls.

## Global Constraints

- Use the local `stitch-prd-ui-prototypes/assets` images; do not replace them with placeholders.
- Use Plus Jakarta Sans for headings and Inter for body/data text.
- Use `#E63946` as the primary action color and preserve the Stitch light clinical palette.
- Keep interactive targets at least 48px and prevent horizontal overflow at 390px.
- Use Material Symbols for UI icons; do not use emoji or handcrafted SVG icons.
- Keep all visible application copy in Chinese, including navigation labels and aria-labels.
- Verify desktop visual parity against the five downloaded Stitch screenshots and mobile behavior at 390px.

---

### Task 1: Create the Vite app shell and testable navigation contract

**Files:**
- Create: `package.json`
- Create: `index.html`
- Create: `src/main.jsx`
- Create: `src/App.jsx`
- Create: `src/data.js`
- Create: `src/styles/tokens.css`
- Create: `src/styles/global.css`

**Interfaces:**
- `src/main.jsx` mounts `<App />` into `#root`.
- `src/data.js` exports `NAV_ITEMS`, `PAGE_IDS`, `patient`, `healthMetrics`, `medications`, and `medicalFiles`.
- `App.jsx` maps the current hash to one of the five page components and exposes `navigateTo(pageId)` to `AppShell`.

- [ ] **Step 1: Add the minimal Vite package metadata and root document.**

`package.json` must define `dev: "vite"`, `build: "vite build"`, and `preview: "vite preview"`; `index.html` must set the Chinese title and Material Symbols stylesheet link.

- [ ] **Step 2: Add shared data and page IDs.**

Use stable IDs `home`, `trends`, `consultation`, `records`, and `medications`; keep all labels and sample values in `src/data.js` so page components do not duplicate navigation copy.

- [ ] **Step 3: Add tokens and global layout rules.**

Define CSS variables for the Stitch palette, radii, spacing, shadows, typography, and desktop/mobile breakpoints. Add reset, button/input defaults, focus-visible styles, and `overflow-x: hidden` at the document level.

- [ ] **Step 4: Implement `App.jsx` hash navigation with a fallback.**

Read `window.location.hash`, default to `home`, listen for `hashchange`, and render the page component selected by `PAGE_IDS`; unknown hashes must return to `home` without throwing.

- [ ] **Step 5: Run the app build.**

Run `npm install` then `npm run build`; expected result is a successful Vite production build with no module resolution errors.

### Task 2: Build the shared shell and primitives

**Files:**
- Create: `src/components/AppShell.jsx`
- Create: `src/components/Sidebar.jsx`
- Create: `src/components/TopBar.jsx`
- Create: `src/components/UI.jsx`
- Modify: `src/App.jsx`
- Modify: `src/styles/global.css`

**Interfaces:**
- `AppShell({ pageId, children, onNavigate })` renders the page frame and mobile menu.
- `Sidebar({ pageId, onNavigate, mobileOpen, onClose })` renders navigation and footer actions.
- `TopBar({ title, eyebrow, actions })` renders page-level utility controls.
- `Card`, `Button`, `IconButton`, `StatusChip`, `MetricCard`, and `SectionHeading` are named reusable components.

- [ ] **Step 1: Implement desktop and mobile shell markup.**

Use a fixed-width desktop sidebar matching Stitch, a flexible content canvas, and a mobile menu button that opens an overlay sidebar below the 768px breakpoint.

- [ ] **Step 2: Implement navigation active states and accessibility.**

Each navigation item must be a button or link with Chinese text, `aria-current="page"` when active, and a visible active red background/white icon state. Close the mobile menu after navigation.

- [ ] **Step 3: Implement the shared primitives.**

Cards, pills, icon buttons, metric cards, and section headings must consume token variables rather than hard-coded one-off visual values.

- [ ] **Step 4: Wire the shell into `App.jsx`.**

Every page must render inside `AppShell`; changing the hash must update the selected navigation item without a full reload.

- [ ] **Step 5: Verify shell interactions.**

Run the dev server and manually confirm desktop navigation, mobile menu open/close, active page state, keyboard focus, and direct hash navigation.

### Task 3: Implement the redesigned home screen

**Files:**
- Create: `src/pages/HomePage.jsx`
- Create: `src/components/HealthChart.jsx`
- Modify: `src/App.jsx`
- Modify: `src/styles/global.css`

**Interfaces:**
- `HomePage()` renders the Stitch home layout using `patient`, `healthMetrics`, and local assets.
- `HealthChart({ points, color, label })` renders the lightweight dot/line chart treatment used in metric cards.

- [ ] **Step 1: Recreate the Stitch desktop hierarchy.**

Build the greeting header, red AI consultation CTA, 2x2 health metric grid, Apple Watch card, profile card, medication reminders, and recent report panel with matching proportions and spacing.

- [ ] **Step 2: Add home interactions.**

The AI consultation CTA navigates to `#consultation`; metric cards toggle a selected border; reminder/report buttons show a local feedback state.

- [ ] **Step 3: Add mobile layout rules.**

Collapse the content to one column, preserve the CTA near the top, and keep the profile/summary cards readable without horizontal scrolling.

- [ ] **Step 4: Capture and compare the home page.**

At the Stitch screenshot viewport, compare the local screenshot for canvas color, sidebar width, card placement, CTA height, and right-rail spacing; fix P0-P2 mismatches before continuing.

### Task 4: Implement health trends and AI consultation screens

**Files:**
- Create: `src/pages/HealthTrendsPage.jsx`
- Create: `src/pages/ConsultationPage.jsx`
- Modify: `src/data.js`
- Modify: `src/App.jsx`
- Modify: `src/styles/global.css`

**Interfaces:**
- `HealthTrendsPage()` owns selected metric and time range state.
- `ConsultationPage()` owns `messages`, `draft`, and `sendMessage()` state.
- `HealthChart({ series, labels, height })` is reused for the trend graph.

- [ ] **Step 1: Recreate health trends.**

Match the wide graph card, right patient/record rail, and three bottom metric cards from the Stitch reference; use CSS/SVG-free DOM chart primitives or a simple CSS plot based on the provided values, with no decorative substitutes for actual icons.

- [ ] **Step 2: Add trend selection interactions.**

Metric cards and the time range control update selected styling and the displayed chart label/value without leaving the page.

- [ ] **Step 3: Recreate consultation.**

Match the patient information column, health overview card, chat header, assistant/user bubbles, warning card, quick replies, and composer.

- [ ] **Step 4: Add message sending.**

Prevent blank sends, append the user message, clear the input, and append a deterministic local assistant response so the core interaction visibly works.

- [ ] **Step 5: Verify desktop and mobile layouts.**

Check the graph and chat panels at desktop width and ensure each collapses to readable stacked sections at 390px.

### Task 5: Implement medical records and medication timeline screens

**Files:**
- Create: `src/pages/MedicalRecordsPage.jsx`
- Create: `src/pages/MedicationsPage.jsx`
- Create: `src/components/FileList.jsx`
- Create: `src/components/Timeline.jsx`
- Modify: `src/data.js`
- Modify: `src/App.jsx`
- Modify: `src/styles/global.css`

**Interfaces:**
- `FileList({ files, selectedId, onSelect })` renders selectable file rows.
- `Timeline({ items, activeId })` renders medication or medical-record event markers.
- `MedicalRecordsPage()` owns selected file state.
- `MedicationsPage()` owns selected filter and expanded medication state.

- [ ] **Step 1: Recreate the records page.**

Implement upload card, recent file list, document preview, AI analysis panel, health value cards, and record timeline with the same three-column desktop composition as Stitch.

- [ ] **Step 2: Add file selection.**

Clicking a file row updates its selected style and preview title/content; upload remains a visual prototype control and must not attempt a backend upload.

- [ ] **Step 3: Recreate the medication page.**

Implement the red interaction warning, today timeline, current plans list, interaction checker card, and dark dosage summary card.

- [ ] **Step 4: Add medication interactions.**

Filter controls update selected state; a medication row expands/collapses its details; the interaction button reveals a local result panel.

- [ ] **Step 5: Verify records and medication screens.**

Compare all major panels at desktop width and verify stacked ordering, preserved primary actions, and no horizontal scrolling at 390px.

### Task 6: Visual QA, accessibility, and delivery

**Files:**
- Create: `design-qa.md`
- Modify: `src/styles/global.css`
- Modify: any page/component file needed for P0-P2 fixes

- [ ] **Step 1: Run production build and inspect output.**

Run `npm run build`; confirm exit code 0 and no unresolved imports.

- [ ] **Step 2: Run the local dev server and inspect every route.**

Use the browser to open the local app, capture all five page states, and compare against the downloaded Stitch screenshots at matching desktop dimensions.

- [ ] **Step 3: Exercise required interactions.**

Test sidebar navigation, mobile menu, consultation send, file selection, trend selection, medication filters, and medication expansion. Check the browser console for errors.

- [ ] **Step 4: Fix all P0-P2 visual or functional issues.**

Prioritize wrong page structure, missing primary actions, broken navigation, clipped content, unreadable contrast, and horizontal overflow; leave only documented P3 polish items if any.

- [ ] **Step 5: Write the QA report.**

Record viewport sizes, tested interactions, remaining issues, and `final result: passed` only after the browser verification is complete.

