# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** DermoAI
**Generated:** 2026-08-01 16:33:02
**Category:** Yoga & Stretching Guide

---

## Global Rules

### Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#0891B2` | `--color-primary` |
| On Primary | `#FFFFFF` | `--color-on-primary` |
| Secondary | `#22D3EE` | `--color-secondary` |
| Accent/CTA | `#059669` | `--color-accent` |
| Background | `#ECFEFF` | `--color-background` |
| Foreground | `#164E63` | `--color-foreground` |
| Muted | `#E8F1F6` | `--color-muted` |
| Border | `#A5F3FC` | `--color-border` |
| Destructive | `#DC2626` | `--color-destructive` |
| Ring | `#0891B2` | `--color-ring` |

**Color Notes:** Calm cyan + health green

### Typography

- **Heading Font:** Lora
- **Body Font:** Raleway
- **Mood:** calm, wellness, health, relaxing, natural, organic
- **Google Fonts:** [Lora + Raleway](https://fonts.googleapis.com/css2?family=Lora:wght@400;500;600;700&family=Raleway:wght@300;400;500;600;700&display=swap)

**CSS Import:**
```css
@import url('https://fonts.googleapis.com/css2?family=Lora:wght@400;500;600;700&family=Raleway:wght@300;400;500;600;700&display=swap');
```

### Spacing Variables

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `4px` / `0.25rem` | Tight gaps |
| `--space-sm` | `8px` / `0.5rem` | Icon gaps, inline spacing |
| `--space-md` | `16px` / `1rem` | Standard padding |
| `--space-lg` | `24px` / `1.5rem` | Section padding |
| `--space-xl` | `32px` / `2rem` | Large gaps |
| `--space-2xl` | `48px` / `3rem` | Section margins |
| `--space-3xl` | `64px` / `4rem` | Hero padding |

### Shadow Depths

| Level | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle lift |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)` | Cards, buttons |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` | Modals, dropdowns |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero images, featured cards |

---

## Component Specs

### Buttons

```css
/* Primary Button */
.btn-primary {
  background: #059669;
  color: white;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}

.btn-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* Secondary Button */
.btn-secondary {
  background: transparent;
  color: #0891B2;
  border: 2px solid #0891B2;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}
```

### Cards

```css
.card {
  background: #ECFEFF;
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-md);
  transition: all 200ms ease;
  cursor: pointer;
}

.card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}
```

### Inputs

```css
.input {
  padding: 12px 16px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 200ms ease;
}

.input:focus {
  border-color: #0891B2;
  outline: none;
  box-shadow: 0 0 0 3px #0891B220;
}
```

### Modals

```css
.modal-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal {
  background: white;
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-xl);
  max-width: 500px;
  width: 90%;
}
```

---

## Style Guidelines

**Style:** Organic Biophilic

**Keywords:** Nature, organic shapes, green, sustainable, rounded, flowing, wellness, earthy, natural textures

**Best For:** Wellness apps, sustainability brands, eco products, health apps, meditation, organic food brands

**Key Effects:** Rounded corners (16-24px), organic curves (border-radius variations), natural shadows, flowing SVG shapes

### Page Pattern

**Pattern Name:** App Store Style Landing

- **Conversion Strategy:** Show real screenshots. Include ratings (4.5+ stars). QR code for mobile. Platform-specific CTAs.
- **CTA Placement:** Download buttons prominent (App Store + Play Store) throughout
- **Section Order:** 1. Hero with device mockup, 2. Screenshots carousel, 3. Features with icons, 4. Reviews/ratings, 5. Download CTAs

---

## Anti-Patterns (Do NOT Use)

- ❌ Inconsistent styling
- ❌ Poor contrast ratios

### Additional Forbidden Patterns

- ❌ **Emojis as icons** — Use SVG icons (Heroicons, Lucide, Simple Icons)
- ❌ **Missing cursor:pointer** — All clickable elements must have cursor:pointer
- ❌ **Layout-shifting hovers** — Avoid scale transforms that shift layout
- ❌ **Low contrast text** — Maintain 4.5:1 minimum contrast ratio
- ❌ **Instant state changes** — Always use transitions (150-300ms)
- ❌ **Invisible focus states** — Focus states must be visible for a11y

---

## Pre-Delivery Checklist

Before delivering any UI code, verify:

- [ ] No emojis used as icons (use SVG instead)
- [ ] All icons from consistent icon set (Heroicons/Lucide)
- [ ] `cursor-pointer` on all clickable elements
- [ ] Hover states with smooth transitions (150-300ms)
- [ ] Light mode: text contrast 4.5:1 minimum
- [ ] Focus states visible for keyboard navigation
- [ ] `prefers-reduced-motion` respected
- [ ] Responsive: 375px, 768px, 1024px, 1440px
- [ ] No content hidden behind fixed navbars
- [ ] No horizontal scroll on mobile

---

## ⚠️ FINAL DECISIONS (overrides the recommendations above — user-approved)

**Stack:** Android · Jetpack Compose (Material 3). **Style:** Neumorphism (Soft UI) — confirmed best-fit for health/wellness by this skill's DB; not flat.

**Palette — "Pine & Cream" (light):**
| Token | Hex |
|---|---|
| Base (background/surface) | `#EAE4DA` warm sand |
| Raised fill (cards) | `#F4EFE7` warm cream |
| Inset well (inputs, pressed) | `#E2DCD1` |
| Shadow highlight / shadow dark | `#FFFFFF` / `#C9C1B4` |
| Primary (pine) | `#1E6E5C` (6.1:1 on white) |
| Primary container (pale pine) | `#D9EDE4` |
| On-primary-container text | `#123F33` |
| Text primary / secondary | `#202B26` / `#55645C` (≥4.9:1) |
| Semantic: positive / watch / alert | `#65A58D` sage / `#D4953A` amber / `#E8634A` coral (text variants `SageText/AmberText/CoralText` ≥5:1) |

**Effects:** dual shadows on raised (hi top-left, lo bottom-right), inner shadow + inset fill on pressed (150ms), radius 12–16dp wells / 20–28dp cards.

**Typography:** serif display roles (display→headlineSmall, Noto Serif; future: bundle Lora variable font) + SansSerif body/UI. All M3 scale, no inline sp.

**Anti-patterns to keep honoring:** emoji as structural icons (🔥 only as text content in streak pill), contrast <4.5:1, hardcoded colors in feature code (use DermoColors / theme tokens).
