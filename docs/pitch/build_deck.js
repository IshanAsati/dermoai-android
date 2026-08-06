// DermoAI pitch deck generator (pptxgenjs) — "Pine & Cream" brand palette.
// Run: node prep_icons.js && node build_deck.js
const pptxgen = require('pptxgenjs');
const I = require('./icons.js');

const ICON = (k) => I[k] || (() => { throw new Error('missing icon ' + k); })();

// ---------- palette ----------
const C = {
  DARK: '123F33', PINE: '1E6E5C', CORAL: 'E0704F', PALEPINE: 'D9EDE4',
  CREAM: 'F4EFE7', SAND: 'EAE4DA', INK: '202B26', SLATE: '55645C',
  WHITE: 'FFFFFF', PALEONPINE: 'C8DBD2', MUTED: 'A8B8AF',
  CORALDARK: 'A64B2C', PALECORAL: 'F8DED2', LINECREAM: 'E3DCCF',
  LINEPINE: 'BFD3C9', GRID: 'C6D8CE', ROAD: 'EDF3EE', RING1: '274F43',
  RING2: '2F5B4E', PALECORALPILL: 'F7E2D8',
};
const F_DISPLAY = 'Cambria';
const F_BODY = 'Calibri';
const W = 10, H = 5.625, MX = 0.55, CW = W - 2 * MX;

// ---------- text-fit estimator (rough overflow guard) ----------
const warnLog = [];
function estLines(text, sizePt, wIn, factor) {
  const cap = (wIn * 72) / (sizePt * (factor || 0.5));
  const words = String(text).split(/\s+/);
  let lines = 1, cur = '';
  for (const w of words) {
    const cand = cur ? cur + ' ' + w : w;
    if (cand.length <= cap) cur = cand;
    else { lines++; cur = w; }
  }
  return lines;
}
function checkFit(tag, text, sizePt, wIn, hIn, factor, lineFactor) {
  const lines = estLines(text, sizePt, wIn, factor);
  const need = lines * sizePt * (lineFactor || 1.25) + 4;
  if (need > hIn * 72) {
    warnLog.push(`OVERFLOW? ${tag}: ${lines} lines x ${sizePt}pt needs ${(need / 72).toFixed(2)}in > ${hIn.toFixed(2)}in box — "${String(text).slice(0, 48)}..."`);
  }
  return lines;
}

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_16x9'; // 10 x 5.625 in
pptx.title = 'DermoAI — Pitch Deck';
pptx.subject = 'AI skin analysis, safety-first guidance, and a dermatologist in your pocket';
pptx.author = 'DermoAI';
pptx.company = 'DermoAI';

// fresh shadow object every call (pptxgenjs mutates options in place)
const softShadow = () => ({ type: 'outer', color: 'C4BAA8', blur: 10, angle: 90, offset: 4, opacity: 0.32 });
const softShadowStrong = () => ({ type: 'outer', color: 'B5AA96', blur: 14, angle: 90, offset: 6, opacity: 0.35 });

function bg(slide, color) {
  slide.addShape('rect', { x: 0, y: 0, w: W, h: H, fill: { color }, line: { type: 'none' } });
}
function card(slide, x, y, w, h, opts = {}) {
  slide.addShape('roundRect', {
    x, y, w, h, rectRadius: 0.12,
    fill: { color: opts.fill || C.WHITE, transparency: opts.transparency || 0 },
    line: opts.line ? { color: opts.line, width: 1 } : { type: 'none' },
    shadow: opts.shadow === false ? undefined : (opts.shadowStrong ? softShadowStrong() : softShadow()),
  });
}
function pill(slide, x, y, w, h, fill, lineColor, radius) {
  slide.addShape('roundRect', {
    x, y, w, h, rectRadius: radius || h / 2,
    fill: fill ? { color: fill } : { color: C.WHITE, transparency: 100 },
    line: lineColor ? { color: lineColor, width: 1.1 } : { type: 'none' },
  });
}
function chip(slide, x, y, d, iconKey, fillKey = 'pine', iconColorKey = 'cream') {
  const fill = { pine: C.PINE, coral: C.CORAL, palepine: C.PALEPINE, slate: C.SLATE, dark: C.DARK }[fillKey];
  slide.addShape('ellipse', { x, y, w: d, h: d, fill: { color: fill }, line: { type: 'none' } });
  slide.addImage({ data: ICON(`${iconKey}_${iconColorKey}`), x: x + d * 0.19, y: y + d * 0.19, w: d * 0.62, h: d * 0.62 });
}
function dot(slide, x, y, d, color, outline) {
  slide.addShape('ellipse', {
    x, y, w: d, h: d,
    fill: { color },
    line: outline ? { color: outline, width: 1 } : { type: 'none' },
  });
}
function ring(slide, x, y, d, lineColor, lineW) {
  slide.addShape('ellipse', { x, y, w: d, h: d, fill: { color: C.WHITE, transparency: 100 }, line: { color: lineColor, width: lineW } });
}
function text(slide, str, x, y, w, h, opts = {}) {
  const size = opts.fontSize || 12;
  const f = opts.fontFace || F_BODY;
  const factor = opts.fitFactor || (f === F_DISPLAY ? 0.53 : 0.5);
  const lines = opts.noFitCheck ? 0 : checkFit(opts.tag || str, str, size, w, h, factor, opts.lineFactor);
  slide.addText(str, {
    x, y, w, h,
    fontSize: size,
    fontFace: f,
    bold: !!opts.bold,
    italic: !!opts.italic,
    color: opts.color || C.INK,
    align: opts.align || 'left',
    valign: opts.valign || 'top',
    margin: opts.margin !== undefined ? opts.margin : 0,
    charSpacing: opts.charSpacing || 0,
    lineSpacing: opts.lineSpacing,
    paraSpaceAfter: opts.paraSpaceAfter,
    bullet: opts.bullet,
    breakLine: opts.breakLine,
    fit: opts.fit || undefined,
    ...(opts.underline ? { underline: true } : {}),
  });
  return lines;
}
function header(slide, kickerStr, titleStr, num) {
  text(slide, kickerStr, MX, 0.4, CW, 0.3, { fontSize: 11, bold: true, color: C.PINE, charSpacing: 3, tag: 'kicker:' + kickerStr, fitFactor: 0.55 });
  text(slide, titleStr, MX, 0.66, CW - 0.7, 0.55, { fontSize: 27, bold: true, color: C.INK, fontFace: F_DISPLAY, tag: 'title:' + titleStr, fitFactor: 0.52, lineFactor: 1.1 });
  if (num) {
    text(slide, String(num).padStart(2, '0'), 9.35, 0.45, 0.45, 0.3, { fontSize: 11, bold: true, color: C.SLATE, align: 'right', tag: 'pagenum' });
  }
}
function centerTextX(textStr, sizePt, wTotal) {
  const tw = textStr.length * sizePt * 0.5 / 72;
  return (wTotal - tw) / 2;
}

// ============================================================ S1 WELCOME (dark)
{
  const s = pptx.addSlide();
  s.addNotes('Welcome to the DermoAI pitch. DermoAI is an Android app that runs skin analysis on-device: private, instant, and offline. The aperture motif echoes the camera scan experience.');
  bg(s, C.DARK);
  // aperture rings motif (right)
  ring(s, 5.95, 1.25, 3.3, C.RING1, 1.5);
  ring(s, 6.45, 1.75, 2.3, C.RING2, 1.25);
  ring(s, 6.9, 2.2, 1.4, C.PALEONPINE, 1.25);
  dot(s, 7.41, 2.71, 0.38, C.PALEONPINE);
  dot(s, 7.5, 2.8, 0.2, C.PINE);
  // top pill
  pill(s, MX, 0.5, 3.3, 0.36, null, '3E6B5C');
  text(s, 'ANDROID · KOTLIN · ON-DEVICE AI', MX, 0.57, 3.3, 0.24, { fontSize: 9.5, bold: true, color: C.PALEONPINE, align: 'center', charSpacing: 2, tag: 's1pill' });
  // title block
  text(s, 'DermoAI', MX, 1.85, 6.5, 1.1, { fontSize: 58, bold: true, fontFace: F_DISPLAY, color: C.CREAM, tag: 's1title' });
  text(s, 'Your skin, understood.', MX, 3.0, 6.5, 0.55, { fontSize: 21, italic: true, fontFace: F_DISPLAY, color: C.PALEONPINE, tag: 's1tagline' });
  text(s, 'AI skin analysis, safety-first guidance, and a dermatologist in your pocket — private, instant, and offline.', MX, 3.72, 6.3, 0.9, { fontSize: 13, color: C.MUTED, tag: 's1sub' });
  text(s, 'PITCH DECK · OPEN SOURCE', MX, 5.15, 4, 0.25, { fontSize: 9, bold: true, color: '6F887E', charSpacing: 2, tag: 's1foot' });
}

// ============================================================ S2 ABOUT & WHY
{
  const s = pptx.addSlide();
  s.addNotes('Why DermoAI: skin concerns are common but expert access is slow and generic web advice is risky. DermoAI puts an on-device AI skin model, safety-first guidance, a local dermatologist finder, and progress tracking into one app.');
  bg(s, C.CREAM);
  header(s, 'ABOUT · WHY', 'Skin care shouldn’t be guesswork', 2);
  // left card — problem
  card(s, MX, 1.45, 4.35, 3.55);
  chip(s, 0.85, 1.75, 0.42, 'alert', 'coral');
  text(s, 'The problem', 1.42, 1.78, 3.3, 0.4, { fontSize: 17, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's2lhead' });
  const stats = [
    ['1 in 3', 'of us will face a skin concern that deserves expert eyes'],
    ['Weeks', 'of waiting to see a dermatologist, in many regions'],
    ['Risky', 'self-diagnosis — generic web answers can mislead'],
  ];
  let sy = 2.42;
  for (const [big, small] of stats) {
    text(s, big, 0.85, sy, 1.55, 0.48, { fontSize: 21, bold: true, fontFace: F_DISPLAY, color: C.CORAL, tag: 's2stat' });
    text(s, small, 2.4, sy + 0.03, 2.3, 0.8, { fontSize: 11, color: C.SLATE, tag: 's2statd:' + small });
    sy += 1.02;
  }
  // right card — the app
  card(s, 5.1, 1.45, 4.35, 3.55, { fill: C.PALEPINE, shadowStrong: true });
  chip(s, 5.4, 1.75, 0.42, 'sparkles', 'pine');
  text(s, 'The app — DermoAI', 5.97, 1.78, 3.4, 0.4, { fontSize: 17, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's2rhead' });
  text(s, 'Point your phone at a skin spot. DermoAI runs a skin model on-device, explains what it sees in plain language, and tells you when to see a professional — private, instant, offline.',
    5.4, 2.32, 3.8, 1.0, { fontSize: 12, color: C.INK, tag: 's2body' });
  const bullets = [
    'On-device AI — works fully offline',
    'Safety-first guidance, never alarmist',
    'Local dermatologist finder built in',
    'Track progress over time',
  ];
  let by = 3.28;
  for (const b of bullets) {
    dot(s, 5.4, by + 0.09, 0.13, C.PINE);
    text(s, b, 5.66, by, 3.6, 0.3, { fontSize: 11.5, color: C.INK, tag: 's2bullet:' + b });
    by += 0.42;
  }
  text(s, 'A screening aid, not a diagnosis — built for awareness and faster care.', 1.5, 5.13, 7, 0.3, {
    fontSize: 10.5, italic: true, color: C.SLATE, align: 'center', tag: 's2foot',
  });
}

// ============================================================ S3 FEATURE 01 — AI SKIN SCAN
{
  const s = pptx.addSlide();
  s.addNotes('Feature 1 — the core loop: capture a spot, analyze on-device, get a severity tier with a confidence score and plain-language guidance, always with a clear path to professional care.');
  bg(s, C.CREAM);
  header(s, 'FEATURE 01', 'Point. Scan. Understand your skin.', 3);
  const steps = [
    ['Capture', 'Camera or gallery, with thoughtful framing and a gentle crop flow'],
    ['Analyze on-device', 'A skin model scores the image privately — no internet needed'],
    ['Severity tier', 'Low, moderate, or high guidance with a confidence score'],
    ['Plain-language summary', 'Likely condition, runner-up possibilities, and next steps'],
  ];
  let sy = 1.58;
  for (let i = 0; i < steps.length; i++) {
    const [t, d] = steps[i];
    dot(s, 0.55, sy + 0.05, 0.5, C.PINE);
    text(s, String(i + 1), 0.55, sy + 0.14, 0.5, 0.34, { fontSize: 15, bold: true, color: C.CREAM, align: 'center', tag: 's3stepn' });
    text(s, t, 1.25, sy, 3.4, 0.3, { fontSize: 14, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's3stept:' + t });
    text(s, d, 1.25, sy + 0.33, 3.45, 0.52, { fontSize: 11, color: C.SLATE, tag: 's3stepd:' + d });
    sy += 0.91;
  }
  // result card mock
  card(s, 5.05, 1.5, 4.4, 3.5);
  text(s, 'YOUR SCAN RESULT', 5.35, 1.76, 2.6, 0.24, { fontSize: 9.5, bold: true, color: C.SLATE, charSpacing: 2, tag: 's3reshead' });
  pill(s, 5.35, 2.06, 1.55, 0.42, C.PALECORALPILL, null, 0.21);
  text(s, 'MODERATE', 5.35, 2.13, 1.55, 0.28, { fontSize: 10.5, bold: true, color: C.CORALDARK, align: 'center', tag: 's3pill' });
  text(s, '87% confidence', 7.15, 2.13, 2.1, 0.28, { fontSize: 12.5, bold: true, color: C.INK, align: 'right', tag: 's3conf' });
  s.addShape('line', { x: 5.35, y: 2.85, w: 3.8, h: 0, line: { color: C.LINECREAM, width: 1 } });
  text(s, 'WHAT IT LIKELY IS', 5.35, 3.0, 2.4, 0.24, { fontSize: 9.5, bold: true, color: C.SLATE, charSpacing: 2, tag: 's3l1' });
  text(s, 'Seborrheic keratosis', 5.35, 3.26, 3.9, 0.4, { fontSize: 16, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's3big' });
  text(s, 'ALSO POSSIBLE', 5.35, 3.78, 2.4, 0.24, { fontSize: 9.5, bold: true, color: C.SLATE, charSpacing: 2, tag: 's3l2' });
  text(s, 'Eczema · Psoriasis', 5.35, 4.02, 3.9, 0.3, { fontSize: 12.5, color: C.INK, tag: 's3also' });
  text(s, 'Screening aid — confirm with a professional before acting.', 5.35, 4.52, 3.9, 0.3, { fontSize: 9.5, italic: true, color: C.SLATE, tag: 's3disc' });
  text(s, 'Every result includes clear medical disclaimers and a clear path to professional care.', 1.5, 5.13, 7, 0.3, {
    fontSize: 10.5, italic: true, color: C.SLATE, align: 'center', tag: 's3foot',
  });
}

// ============================================================ S4 FEATURE 02 — SAFETY FILTER
{
  const s = pptx.addSlide();
  s.addNotes('Feature 2 — the safety filter. A rule-based engine applies 8 escalate-or-annotate rules (ABCDE warning signs, sudden change, irregular borders). Concerned patterns get escalated loudly; nothing is ever downgraded. Flagged scans get a consult-a-dermatologist CTA.');
  bg(s, C.CREAM);
  header(s, 'FEATURE 02', 'Built to escalate — never to dismiss.', 4);
  card(s, MX, 1.5, 4.35, 3.3);
  chip(s, 0.85, 1.8, 0.42, 'shield', 'pine');
  text(s, 'The safety filter', 1.42, 1.83, 3.3, 0.4, { fontSize: 16, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's4head' });
  text(s, 'A rule-based engine reviews every scan against 8 escalate-or-annotate rules — ABCDE signs, sudden change, irregular borders, and more. Concerned patterns are escalated loudly; safe ones are annotated — never downplayed, never dismissed.',
    0.85, 2.32, 3.75, 1.35, { fontSize: 12, color: C.INK, tag: 's4body' });
  text(s, 'EVERY SCAN CHECKED FOR', 0.85, 3.72, 3, 0.24, { fontSize: 8.5, bold: true, color: C.SLATE, charSpacing: 1.5, tag: 's4chklbl' });
  const checks = ['ABCDE signs', 'Sudden change', 'Irregular borders'];
  let cxx = 0.85;
  for (const chk of checks) {
    pill(s, cxx, 4.0, 1.24, 0.34, C.PALEPINE);
    text(s, chk, cxx, 4.09, 1.24, 0.24, { fontSize: 9.5, bold: true, color: C.PINE, align: 'center', tag: 's4chk:' + chk });
    cxx += 1.33;
  }
  const rows = [
    ['Escalate', 'alert', 'coral', 'Flagged scans get a “consult a dermatologist” call-to-action right on the result.'],
    ['Annotate', 'check', 'pine', 'Every adjustment is surfaced transparently — the reason is shown, not hidden.'],
    ['Never downgrades', 'shield', 'slate', 'Guidance only becomes more cautious — it never downplays a result.'],
  ];
  let ry = 1.5;
  for (const [t, ik, fk, d] of rows) {
    card(s, 5.05, ry, 4.4, 0.93);
    chip(s, 5.32, ry + 0.24, 0.44, ik, fk);
    text(s, t, 5.95, ry + 0.1, 3.4, 0.32, { fontSize: 13.5, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's4rt:' + t });
    text(s, d, 5.95, ry + 0.44, 3.35, 0.44, { fontSize: 10.5, color: C.SLATE, tag: 's4rd:' + d });
    ry += 1.06;
  }
  const callout = 'Flagged a scan? The finder suggests a dermatologist nearby — one tap away.';
  const pillW = 6.4, px = (W - pillW) / 2;
  pill(s, px, 4.88, pillW, 0.5, C.PALEPINE);
  const iconGap = 0.4;
  const tw = callout.length * 11.5 * 0.5 / 72;
  const startX = px + (pillW - (tw + iconGap)) / 2;
  s.addImage({ data: ICON('mappin_pine'), x: startX, y: 4.96, w: 0.34, h: 0.34 });
  text(s, callout, startX + iconGap, 4.96, tw + 0.3, 0.34, { fontSize: 11.5, bold: true, color: C.PINE, valign: 'middle', tag: 's4callout' });
}

// ============================================================ S5 FEATURE 03 — FIND A DERMATOLOGIST
{
  const s = pptx.addSlide();
  s.addNotes('Feature 3 — the dermatologist finder. OpenStreetMap-based, no account needed. Tap a clinic to call or get directions. When a scan is flagged, a consult CTA appears directly on the result.');
  bg(s, C.CREAM);
  header(s, 'FEATURE 03', '“What is this?” → “Who do I see?”', 5);
  // map card
  card(s, MX, 1.5, 4.6, 3.5, { fill: C.PALEPINE, shadowStrong: true });
  text(s, 'NEARBY CLINICS · 12 FOUND', 0.85, 1.68, 3, 0.24, { fontSize: 9, bold: true, color: C.SLATE, charSpacing: 1.5, tag: 's5maplabel' });
  for (let gx = 1.1; gx < 5.0; gx += 0.62) {
    s.addShape('line', { x: gx, y: 2.0, w: 0, h: 2.9, line: { color: C.GRID, width: 0.75 } });
  }
  for (let gy = 2.15; gy < 4.9; gy += 0.62) {
    s.addShape('line', { x: 0.8, y: gy, w: 4.1, h: 0, line: { color: C.GRID, width: 0.75 } });
  }
  s.addShape('line', { x: 0.75, y: 3.15, w: 4.2, h: 0, line: { color: C.ROAD, width: 5 } });
  s.addShape('line', { x: 2.95, y: 1.95, w: 0, h: 3.0, line: { color: C.ROAD, width: 4 } });
  // pins
  dot(s, 1.95, 2.45, 0.32, C.PINE); dot(s, 1.95 + 0.11, 2.56, 0.1, C.CREAM);
  dot(s, 3.55, 3.35, 0.32, C.PINE); dot(s, 3.66, 3.46, 0.1, C.CREAM);
  dot(s, 2.6, 3.95, 0.32, C.PINE); dot(s, 2.71, 4.06, 0.1, C.CREAM);
  dot(s, 3.35, 2.6, 0.42, C.CORAL); dot(s, 3.35 + 0.15, 2.75, 0.12, C.CREAM);
  // right column
  text(s, 'The finder maps real dermatology clinics around you — powered by OpenStreetMap, no account needed. Tap a clinic to call or get directions.',
    5.35, 1.52, 4.05, 0.85, { fontSize: 12, color: C.INK, tag: 's5copy' });
  card(s, 5.35, 2.5, 4.1, 1.6);
  dot(s, 5.62, 2.78, 0.16, C.PINE);
  text(s, 'DermaCare Clinic', 5.92, 2.68, 2.4, 0.3, { fontSize: 12.5, bold: true, color: C.INK, tag: 's5c1' });
  text(s, '0.4 mi · Open now', 5.92, 2.98, 2.4, 0.26, { fontSize: 10, color: C.SLATE, tag: 's5c1d' });
  chip(s, 9.0, 2.68, 0.36, 'phone', 'pine');
  s.addShape('line', { x: 5.62, y: 3.42, w: 3.6, h: 0, line: { color: C.LINECREAM, width: 1 } });
  dot(s, 5.62, 3.72, 0.16, C.PINE);
  text(s, 'City Skin & Laser', 5.92, 3.62, 2.4, 0.3, { fontSize: 12.5, bold: true, color: C.INK, tag: 's5c2' });
  text(s, '0.9 mi · Accepting new', 5.92, 3.92, 2.4, 0.26, { fontSize: 10, color: C.SLATE, tag: 's5c2d' });
  chip(s, 9.0, 3.62, 0.36, 'phone', 'pine');
  pill(s, 5.35, 4.32, 4.1, 0.62, C.PALECORALPILL);
  text(s, 'Flagged scan? A “Consult a dermatologist” action appears right on the result.', 5.35, 4.4, 4.1, 0.46, {
    fontSize: 11, bold: true, color: C.CORALDARK, align: 'center', valign: 'middle', tag: 's5flag',
  });
}

// ============================================================ S6 FEATURE 04 — TRACK & TREAT
{
  const s = pptx.addSlide();
  s.addNotes('Feature 4 — tracking. Timeline of every scan with photos and dates, step-by-step treatment plans with streak counters, analytics across scans, and one-tap shareable PDF reports.');
  bg(s, C.CREAM);
  header(s, 'FEATURE 04', 'Watch your skin change — week by week', 6);
  const grid = [
    ['Timeline', 'calendar', 'Every scan saved with its photo and date — see how a spot evolves over time.'],
    ['Treatment plans', 'check', 'Step-by-step plans with completions and a streak counter to stay consistent.'],
    ['Analytics', 'chart', 'Trends across scans — severity, frequency, and progress at a glance.'],
    ['PDF reports', 'file', 'One-tap, shareable reports to hand your doctor or pharmacist.'],
  ];
  const gw = 4.35, gh = 1.72, gx = [0.55, 5.1], gy = [1.5, 3.36];
  let gi = 0;
  for (const [t, ik, d] of grid) {
    const x = gx[gi % 2], y = gy[Math.floor(gi / 2)];
    card(s, x, y, gw, gh);
    chip(s, x + 0.22, y + 0.2, 0.4, ik, 'pine');
    text(s, t, x + 0.8, y + 0.22, 3.4, 0.34, { fontSize: 14, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's6t:' + t });
    text(s, d, x + 0.28, y + 0.72, 3.8, 0.9, { fontSize: 10.5, color: C.SLATE, tag: 's6d:' + d });
    gi++;
  }
  const callout = 'Consistency counts — streaks keep your care on track.';
  const pillW = 5.6, px = (W - pillW) / 2;
  pill(s, px, 4.95, pillW, 0.46, C.PALEPINE);
  const iconGap = 0.42;
  const tw = callout.length * 11.5 * 0.5 / 72;
  const startX = px + (pillW - (tw + iconGap)) / 2;
  s.addImage({ data: ICON('flame_pine'), x: startX, y: 5.06, w: 0.3, h: 0.3 });
  text(s, callout, startX + iconGap, 5.02, tw + 0.4, 0.34, { fontSize: 11.5, bold: true, color: C.PINE, valign: 'middle', tag: 's6streak' });
}

// ============================================================ S7 FEATURE 05 — MIND & SKIN
{
  const s = pptx.addSlide();
  s.addNotes('Feature 5 — mind and skin. SkinMind mood check-ins link feelings to skin, guided breathing sessions are capped and accessible, and a journal captures sleep, stress, and triggers beside the skin history.');
  bg(s, C.CREAM);
  header(s, 'FEATURE 05', 'Skin health is whole-body health', 7);
  const cols = [
    ['SkinMind', 'brain', 'Mood check-ins link how you feel to how your skin looks — stress shows up on skin.'],
    ['Guided breathing', 'wind', 'Short, capped sessions (~80 s) that calm you — with accessible, live announcements.'],
    ['Journal', 'book', 'Note sleep, stress, and triggers beside your skin history for fuller context.'],
  ];
  const cw3 = (CW - 2 * 0.16) / 3;
  let cx = MX;
  for (const [t, ik, d] of cols) {
    card(s, cx, 1.5, cw3, 3.0);
    chip(s, cx + cw3 / 2 - 0.25, 1.8, 0.5, ik, 'pine');
    text(s, t, cx, 2.48, cw3, 0.4, { fontSize: 15, bold: true, fontFace: F_DISPLAY, color: C.INK, align: 'center', tag: 's7t:' + t });
    text(s, d, cx + 0.18, 3.0, cw3 - 0.36, 1.35, { fontSize: 10.5, color: C.SLATE, align: 'center', tag: 's7d:' + d });
    cx += cw3 + 0.16;
  }
  // mood strip
  text(s, 'TODAY’S MOOD', MX, 4.82, 2.2, 0.28, { fontSize: 10.5, bold: true, color: C.SLATE, charSpacing: 1.5, tag: 's7moodlabel' });
  const moodColors = ['F1B8A4', 'EAD9BC', 'CBDCD2', 'A8C9BA', C.PINE];
  let mx = 5.7;
  for (let i = 0; i < 5; i++) {
    dot(s, mx, 4.84, 0.26, moodColors[i], i === 4 ? 'FFFFFF' : undefined);
    mx += 0.42;
  }
  text(s, 'Low', 5.7 - 0.1, 5.14, 0.5, 0.22, { fontSize: 8.5, color: C.SLATE, align: 'center', tag: 's7low' });
  text(s, 'Good', 7.25, 5.14, 0.5, 0.22, { fontSize: 8.5, color: C.SLATE, align: 'center', tag: 's7good' });
}

// ============================================================ S8 FEATURE 06 — KNOWLEDGE BASE
{
  const s = pptx.addSlide();
  s.addNotes('Feature 6 — knowledge base. 60 plain-language articles across 9 topics, with instant search. Sample Q&A: the ABCDE rule for moles, which DermoAI checks on scans.');
  bg(s, C.CREAM);
  header(s, 'FEATURE 06', 'Answers on demand — 60 articles, 9 topics', 8);
  // search pill
  pill(s, MX, 1.55, 3.7, 0.46, C.WHITE, C.LINEPINE, 0.23);
  s.addImage({ data: ICON('search_pine'), x: 0.8, y: 1.66, w: 0.24, h: 0.24 });
  text(s, 'Search “itchy red patch”…', 1.2, 1.63, 3.0, 0.3, { fontSize: 11, color: C.SLATE, tag: 's8search' });
  text(s, 'BROWSE BY TOPIC', MX, 2.25, 3, 0.26, { fontSize: 10, bold: true, color: C.SLATE, charSpacing: 1.5, tag: 's8browse' });
  const cats = ['Acne', 'Eczema', 'Psoriasis', 'Moles', 'Sun safety', 'Wounds'];
  const chipW = 1.32, chipGap = 0.12;
  let cxi = 0;
  cats.forEach((cat, i) => {
    const col = i % 3, row = Math.floor(i / 3);
    const x = MX + col * (chipW + chipGap), y = 2.6 + row * 0.52;
    const filled = cat === 'Acne';
    pill(s, x, y, chipW, 0.4, filled ? C.PINE : C.WHITE, filled ? null : C.LINEPINE, 0.2);
    text(s, cat, x, y + 0.08, chipW, 0.26, {
      fontSize: 10.5, bold: true, color: filled ? C.CREAM : C.PINE, align: 'center', tag: 's8cat:' + cat,
    });
  });
  text(s, 'Plain-language explanations, written with care and reviewed for safety.', MX, 3.78, 4.2, 0.6, {
    fontSize: 10, italic: true, color: C.SLATE, tag: 's8note',
  });
  // Q&A card
  card(s, 5.05, 1.55, 4.4, 3.45);
  chip(s, 5.32, 1.85, 0.4, 'book', 'pine');
  text(s, 'SAMPLE ARTICLE', 5.87, 1.9, 3, 0.26, { fontSize: 9.5, bold: true, color: C.SLATE, charSpacing: 2, tag: 's8sample' });
  text(s, 'Is that mole something to worry about?', 5.32, 2.42, 3.9, 0.75, {
    fontSize: 15, bold: true, fontFace: F_DISPLAY, color: C.INK, tag: 's8q', lineFactor: 1.15,
  });
  text(s, 'Most moles are harmless. The ABCDE rule — Asymmetry, Border, Colour, Diameter, Evolving — flags features that deserve a professional look. DermoAI checks these on your scans.',
    5.32, 3.25, 3.9, 1.35, { fontSize: 11.5, color: C.SLATE, tag: 's8a' });
  pill(s, 5.32, 4.62, 1.7, 0.3, C.PALEPINE);
  text(s, 'SAFETY-REVIEWED', 5.32, 4.67, 1.7, 0.2, { fontSize: 8, bold: true, color: C.PINE, align: 'center', charSpacing: 1, tag: 's8tag' });
}

// ============================================================ S9 CLOSING (dark)
{
  const s = pptx.addSlide();
  s.addNotes('Close. DermoAI — your skin, understood. Screening aid, not a medical device; always consult a professional for a diagnosis.');
  bg(s, C.DARK);
  chip(s, 4.77, 1.0, 0.46, 'scan', 'pine', 'cream');
  text(s, 'Your skin, understood.', 0, 1.75, W, 0.95, {
    fontSize: 40, bold: true, fontFace: F_DISPLAY, color: C.CREAM, align: 'center', tag: 's9title',
  });
  text(s, 'AI skin analysis, safety-first guidance, and a dermatologist in your pocket.', 0.5, 2.85, 9, 0.45, {
    fontSize: 14, color: C.PALEONPINE, align: 'center', tag: 's9sub',
  });
  const b1 = { t: 'Get DermoAI', w: 1.7, fill: C.PINE, tc: C.CREAM, outline: false };
  const b2 = { t: 'Open source on GitHub', w: 2.45, fill: null, tc: C.PALEONPINE, outline: true };
  const gap = 0.3;
  const total = b1.w + gap + b2.w;
  const startX = (W - total) / 2;
  pill(s, startX, 3.75, b1.w, 0.5, b1.fill, null, 0.25);
  text(s, b1.t, startX, 3.86, b1.w, 0.3, { fontSize: 12.5, bold: true, color: b1.tc, align: 'center', tag: 's9cta1' });
  pill(s, startX + b1.w + gap, 3.75, b2.w, 0.5, null, C.PALEONPINE, 0.25);
  text(s, b2.t, startX + b1.w + gap, 3.86, b2.w, 0.3, { fontSize: 12.5, bold: true, color: b2.tc, align: 'center', tag: 's9cta2' });
  text(s, 'DermoAI is a screening aid, not a medical device — always consult a professional for a diagnosis.', 0.5, 4.9, 9, 0.3, {
    fontSize: 9.5, color: '6F887E', align: 'center', tag: 's9foot',
  });
}

// ---------- write ----------
pptx.writeFile({ fileName: '../DermoAI_Pitch_Deck.pptx' }).then(() => {
  console.log('Deck written: ../DermoAI_Pitch_Deck.pptx');
  if (warnLog.length) {
    console.log('\n--- TEXT FIT WARNINGS ---');
    warnLog.forEach((w) => console.log(w));
  } else {
    console.log('No text-fit warnings.');
  }
}).catch((e) => { console.error(e); process.exit(1); });
