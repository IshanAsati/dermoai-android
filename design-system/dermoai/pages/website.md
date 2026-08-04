# Website Page Overrides

> **PROJECT:** DermoAI
> **Generated:** 2026-08-05 01:35:00
> **Page Type:** General

> ⚠️ **IMPORTANT:** Rules in this file **override** the Master file (`design-system/MASTER.md`).
> Only deviations from the Master are documented here. For all other rules, refer to the Master.

---

## Page-Specific Rules

### Layout Overrides

- **Max Width:** 1200px (standard)
- **Layout:** Full-width sections, centered content
- **Sections:** 1. Hero with device mockup, 2. Screenshots carousel, 3. Features with icons, 4. Reviews/ratings, 5. Download CTAs

### Spacing Overrides

- No overrides — use Master spacing

### Typography Overrides

- No overrides — use Master typography

### Color Overrides

- **Strategy:** Dark/light matching app store feel. Star ratings in gold. Screenshots with device frames.

### Component Overrides

- No overrides — use Master component specs

---

## Page-Specific Components

- No unique components for this page

---

## Recommendations

- Effects: Rounded corners (16-24px), organic curves (border-radius variations), natural shadows, flowing SVG shapes
- CTA Placement: Download buttons prominent (App Store + Play Store) throughout

---

## FINAL DECISIONS (overrides the generated recommendations — brand brief wins)

The database suggestions (navy/gold and violet palettes, "device mockup + screenshots carousel" page template) conflict with DermoAI's established product identity (Pine & Cream neumorphism, violet deliberately removed). Per the brief-pins-visuals rule, the brand palette and the dermoscope signature take precedence; the database's **Scroll-Triggered Storytelling pattern** and **Lora + Raleway** typography are adopted.

- **Pattern:** Scroll-Triggered Storytelling — intro hook → problem → journey → solution → climax CTA; scroll progress indicator (top, 3px pine); mini CTA at each chapter end + final climax CTA.
- **Chapters (background intensity builds):** 0 Hero `Canvas #EAE4DA` → 1 Problem `TintSweep #E2DCD1` → 2 Journey `PinePale #D9EDE4` → 3 Solution (ABCDE+features on `CardWhite #F4EFE7`, privacy climax on `Pine #1E6E5C`) → 4 Climax CTA `Canvas`.
- **Palette (brand-pinned):** Canvas `#EAE4DA` · CardWhite `#F4EFE7` · TintSweep `#E2DCD1` · Pine `#1E6E5C` · PineDeep `#123F33` · PinePale `#D9EDE4` · Ink `#202B26` · Slate `#55645C` · Coral `#B33A24`. Shadows: hi `#FFFFFF` / lo `#C9C1B4` (neumorphic dual-shadow).
- **Typography:** Lora (display 500/600) + Raleway (body 400/500/600). Base 17px, line-height 1.6.
- **Signature:** interactive ABCDE dermoscope (hero). Scan sweep is ONE-SHOT on reveal — not an infinite decorative loop (per db motion rule).
- **Motion:** reveal-on-scroll via IntersectionObserver (fade + 14px rise, 250-300ms ease-out); all disabled under `prefers-reduced-motion`. No scroll-jacking.
- **Accessibility floor:** contrast ≥4.5:1 on all chapters, visible focus rings, keyboard-accessible letters, aria-live readout, cursor-pointer on interactive elements, responsive 375→1440px.
