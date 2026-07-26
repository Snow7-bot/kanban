# Stitch Account Pages and Unified Sidebar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the three downloaded Stitch account screens and make one shared sidebar available on every route.

**Architecture:** Hash navigation owns the route contract. `Sidebar` reads one expanded navigation data list, while the three page modules own their local prototype interactions and share existing card/button primitives.

**Tech Stack:** React, Vite, node:test, lucide-react, existing CSS tokens, downloaded Stitch images.

## Global Constraints

- Match the downloaded family, profile and settings Stitch screens.
- Use one shared `Sidebar` on all routes.
- Keep all copy and accessibility labels in Chinese.
- Use no backend calls or new dependencies.

---

### Task 1: Route and navigation contract

**Files:**
- Modify: `src/navigation.test.js`
- Modify: `src/navigation.js`
- Modify: `src/data.js`
- Modify: `src/App.jsx`

- [ ] Add a failing test asserting `family`、`profile`、`settings` are supported page IDs and `#family` resolves correctly.
- [ ] Run `npm test -- src/navigation.test.js`; expect failure because the IDs do not yet exist.
- [ ] Add the three page IDs and unified nav entries, then register the three page modules in `App.jsx`.
- [ ] Run `npm test -- src/navigation.test.js`; expect pass.

### Task 2: Shared sidebar fidelity

**Files:**
- Modify: `src/appShell.test.js`
- Modify: `src/components/Sidebar.jsx`
- Modify: `src/styles/global.css`

- [ ] Add a failing source-level test for the unified navigation entries and active-page state.
- [ ] Run `npm test -- src/appShell.test.js`; expect failure because the new entries are absent.
- [ ] Map current and added route IDs to the existing icon system, preserve compact bottom actions and mobile drawer behavior, and tune spacing to the latest Stitch sidebars.
- [ ] Re-run `npm test -- src/appShell.test.js`; expect pass.

### Task 3: Three high-fidelity account pages

**Files:**
- Create: `src/pages/FamilyPage.jsx`
- Create: `src/pages/ProfilePage.jsx`
- Create: `src/pages/SettingsPage.jsx`
- Create: `src/styles/account.css`
- Modify: `src/main.jsx`

- [ ] Add failing route/module assertions for the three new page components.
- [ ] Run the route test; expect failure before modules are registered.
- [ ] Build the pages from the downloaded Stitch HTML structure, consuming only local avatar assets and existing UI primitives.
- [ ] Add local visible interactions: member selection/add state, profile save feedback, settings elderly mode/cache feedback.
- [ ] Run all node tests and `npm run build`; expect all tests and the build to pass.

### Task 4: Visual verification

**Files:**
- Modify: `design-qa.md`
- Modify: affected page/CSS files only for P0–P2 mismatches.

- [ ] Run the Vite dev server and capture `#family`、`#profile`、`#settings` in the in-app browser.
- [ ] Compare each capture against its downloaded Stitch reference at the same desktop state; check sidebar consistency across all three.
- [ ] Test mobile navigation, member selection, profile save, elderly mode, cache feedback and browser console output.
- [ ] Write the result as `final result: passed` only after fixes and a clean recheck.
