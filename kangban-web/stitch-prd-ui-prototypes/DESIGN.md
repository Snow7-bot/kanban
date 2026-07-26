## Brand & Style
The design system is defined by a "Warm Clinical" aesthetic. It balances the urgency and precision of healthcare with an approachable, patient-centric softness. It leverages **Minimalism** for clarity and **Glassmorphism** for modern depth, ensuring that complex medical data feels manageable and non-threatening.

The target audience ranges from tech-savvy individuals to elderly patients requiring high accessibility. The emotional response should be one of confidence, calm, and clarity. The UI uses stark white surfaces to evoke a sterile, professional environment, contrasted by high-impact crimson accents that denote vital energy and action.

## Layout & Spacing
The layout follows a **fixed-grid** philosophy for the dashboard to maintain data density control, while utilizing fluid components within cards. 

- **Desktop:** 12-column grid with 24px gutters. Content is centered with a max-width of 1440px.
- **Tablet:** 8-column grid. Cards reflow into a two-column stack.
- **Mobile:** Single-column layout. Margins reduce to 20px. 

All interactive elements must adhere to a minimum 48px touch target. In **Elderly Mode**, the `card-gap` should be increased to 32px to prevent accidental taps and reduce cognitive clutter.

## Elevation & Depth
Hierarchy is established through **Tonal Layering** and **Ambient Shadows**. 

1.  **Canvas:** The base background is `#F8FAFC`.
2.  **Cards:** Primary content containers are pure white (`#FFFFFF`) with a very soft, diffused shadow (Blur: 20px, Y: 4px, Opacity: 4% Black).
3.  **Floating Elements:** Modals and menus use a more pronounced shadow and a backdrop blur (Glassmorphism) of 12px to maintain context of the underlying data.
4.  **Active State:** Critical alerts may use a subtle inner glow or a 2px solid primary red border to draw immediate attention without relying solely on shadow depth.

## Components
- **Buttons:** Primary buttons are solid `#E63946` with white text. Secondary buttons use a ghost style with a `#1D3557` border.
- **Cards:** White surfaces with 24px padding. They should group related metrics (e.g., Heart Rate + Trend Graph).
- **Inputs:** Clean, outlined fields. On focus, the border transitions to Primary Red with a subtle outer glow.
- **Medical Chips:** Use light tinted backgrounds of the status colors (e.g., light red for "High Risk") with bold text labels.
- **Icons:** Use 2px stroke-based icons. Avoid filled icons unless they represent an active toggle state. In Elderly Mode, icons *must* be accompanied by a text label.
- **Health Charts:** Use ECharts 5 with a simplified color palette—red for data lines and soft gray for grid lines.
