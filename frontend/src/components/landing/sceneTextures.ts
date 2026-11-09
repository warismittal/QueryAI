/**
 * Procedural canvas textures for the library scene: nothing is downloaded, everything is painted in code.
 * Each function returns a <canvas> that the scene wraps in a THREE.CanvasTexture.
 */

function canvas(w: number, h: number): [HTMLCanvasElement, CanvasRenderingContext2D] {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return [c, c.getContext('2d')!];
}

function rand(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

export function roundRect(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + w, y, x + w, y + h, r);
  g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r);
  g.arcTo(x, y, x + w, y, r);
  g.closePath();
}

/** A Persian-style rug: deep field, layered borders, a central medallion and scattered motifs. */
export function rugCanvas(field: string, border: string, ornament: string) {
  const [c, g] = canvas(1024, 1536);
  const W = c.width;
  const H = c.height;
  g.fillStyle = border;
  g.fillRect(0, 0, W, H);
  // layered borders
  const bands = [[24, ornament], [44, border], [96, field], [112, ornament], [128, border]] as const;
  for (const [inset, color] of bands) {
    g.fillStyle = color;
    g.fillRect(inset, inset, W - inset * 2, H - inset * 2);
  }
  g.fillStyle = field;
  g.fillRect(150, 150, W - 300, H - 300);
  // border motifs
  const r = rand(3);
  g.fillStyle = ornament;
  for (let x = 70; x < W - 40; x += 46) {
    for (const y of [70, H - 70]) diamond(g, x, y, 13);
  }
  for (let y = 70; y < H - 40; y += 46) {
    for (const x of [70, W - 70]) diamond(g, x, y, 13);
  }
  // field motifs
  g.globalAlpha = 0.55;
  for (let i = 0; i < 160; i++) {
    const x = 190 + r() * (W - 380);
    const y = 190 + r() * (H - 380);
    g.fillStyle = r() < 0.5 ? ornament : border;
    flower(g, x, y, 8 + r() * 10);
  }
  g.globalAlpha = 1;
  // central medallion
  const cx = W / 2;
  const cy = H / 2;
  for (const [rad, color] of [[300, border], [262, ornament], [220, field], [170, border], [120, ornament], [70, field], [34, border]] as const) {
    g.fillStyle = color;
    g.beginPath();
    for (let k = 0; k <= 16; k++) {
      const a = (k / 16) * Math.PI * 2;
      const rr = rad * (k % 2 === 0 ? 1 : 0.86);
      g.lineTo(cx + Math.cos(a) * rr * 0.8, cy + Math.sin(a) * rr);
    }
    g.closePath();
    g.fill();
  }
  // corner spandrels
  g.fillStyle = border;
  for (const [x, y] of [[150, 150], [W - 150, 150], [150, H - 150], [W - 150, H - 150]]) {
    g.beginPath();
    g.arc(x, y, 150, 0, Math.PI * 2);
    g.fill();
  }
  // wear and fibre noise
  const img = g.getImageData(0, 0, W, H);
  for (let i = 0; i < img.data.length; i += 4) {
    const n = (r() - 0.5) * 26;
    img.data[i] += n;
    img.data[i + 1] += n;
    img.data[i + 2] += n;
  }
  g.putImageData(img, 0, 0);
  return c;
}

function diamond(g: CanvasRenderingContext2D, x: number, y: number, s: number) {
  g.beginPath();
  g.moveTo(x, y - s);
  g.lineTo(x + s, y);
  g.lineTo(x, y + s);
  g.lineTo(x - s, y);
  g.closePath();
  g.fill();
}

function flower(g: CanvasRenderingContext2D, x: number, y: number, s: number) {
  for (let k = 0; k < 4; k++) {
    g.beginPath();
    g.ellipse(x + Math.cos((k * Math.PI) / 2) * s * 0.6, y + Math.sin((k * Math.PI) / 2) * s * 0.6, s * 0.45, s * 0.25, (k * Math.PI) / 2, 0, Math.PI * 2);
    g.fill();
  }
}

/** Dark polished wooden floor boards. */
export function planksCanvas(base: string) {
  const [c, g] = canvas(1024, 1024);
  const r = rand(5);
  g.fillStyle = base;
  g.fillRect(0, 0, 1024, 1024);
  const plank = 64;
  for (let x = 0; x < 1024; x += plank) {
    let y = -r() * 400;
    while (y < 1024) {
      const len = 260 + r() * 360;
      const shade = (r() - 0.5) * 0.35;
      g.fillStyle = shade > 0 ? `rgba(255,220,180,${shade * 0.18})` : `rgba(0,0,0,${-shade * 0.6})`;
      g.fillRect(x + 1, y + 1, plank - 2, len - 2);
      // grain
      g.strokeStyle = 'rgba(0,0,0,0.25)';
      g.lineWidth = 1;
      for (let k = 0; k < 6; k++) {
        g.beginPath();
        const gx = x + 6 + r() * (plank - 12);
        g.moveTo(gx, y);
        g.bezierCurveTo(gx + (r() - 0.5) * 10, y + len * 0.3, gx + (r() - 0.5) * 10, y + len * 0.7, gx, y + len);
        g.stroke();
      }
      g.fillStyle = 'rgba(0,0,0,0.6)';
      g.fillRect(x, y, plank, 2);
      y += len;
    }
    g.fillStyle = 'rgba(0,0,0,0.65)';
    g.fillRect(x, 0, 2, 1024);
  }
  return c;
}

/** An old manuscript page: warm paper, faded handwritten lines and a drop capital. */
export function paperCanvas(ink = 'rgba(60,35,20,0.55)') {
  const [c, g] = canvas(256, 340);
  const r = rand(9);
  const grad = g.createRadialGradient(128, 170, 30, 128, 170, 220);
  grad.addColorStop(0, '#efe2c4');
  grad.addColorStop(1, '#c9b48b');
  g.fillStyle = grad;
  g.fillRect(0, 0, 256, 340);
  g.strokeStyle = ink;
  g.lineWidth = 2;
  for (let y = 46; y < 320; y += 15) {
    let x = 24;
    while (x < 230) {
      const w = 10 + r() * 34;
      g.beginPath();
      g.moveTo(x, y + (r() - 0.5) * 2);
      g.lineTo(Math.min(232, x + w), y + (r() - 0.5) * 2);
      g.stroke();
      x += w + 6;
    }
  }
  g.fillStyle = 'rgba(130,30,40,0.8)';
  g.font = 'italic 44px Georgia, serif';
  g.fillText('Q', 20, 44);
  return c;
}

/** An abstract oil painting: a stormy crimson sky over a dark landscape, in the spirit of an old master. */
export function paintingCanvas(accent: string) {
  const [c, g] = canvas(512, 384);
  const r = rand(21);
  const sky = g.createLinearGradient(0, 0, 0, 384);
  sky.addColorStop(0, '#120c0c');
  sky.addColorStop(0.45, accent);
  sky.addColorStop(0.7, '#3a1f12');
  sky.addColorStop(1, '#0d0907');
  g.fillStyle = sky;
  g.fillRect(0, 0, 512, 384);
  for (let i = 0; i < 900; i++) {
    g.fillStyle = `rgba(${200 + r() * 55},${120 + r() * 80},${80 + r() * 60},${r() * 0.05})`;
    g.beginPath();
    g.ellipse(r() * 512, r() * 260, 20 + r() * 60, 4 + r() * 10, r() * 3, 0, Math.PI * 2);
    g.fill();
  }
  g.fillStyle = '#0b0807';
  g.beginPath();
  g.moveTo(0, 300);
  for (let x = 0; x <= 512; x += 32) g.lineTo(x, 270 + Math.sin(x / 60) * 18 + r() * 14);
  g.lineTo(512, 384);
  g.lineTo(0, 384);
  g.fill();
  // a lone rider silhouette
  g.fillStyle = '#060404';
  g.beginPath();
  g.ellipse(300, 262, 34, 14, 0, 0, Math.PI * 2);
  g.fill();
  g.fillRect(272, 262, 5, 24);
  g.fillRect(320, 262, 5, 24);
  g.beginPath();
  g.ellipse(330, 246, 9, 14, 0.5, 0, Math.PI * 2);
  g.fill();
  g.fillRect(293, 228, 9, 26);
  const v = g.createRadialGradient(256, 192, 120, 256, 192, 330);
  v.addColorStop(0, 'rgba(0,0,0,0)');
  v.addColorStop(1, 'rgba(0,0,0,0.6)');
  g.fillStyle = v;
  g.fillRect(0, 0, 512, 384);
  return c;
}

/** A soft radial glow (used for the doorway light, lamp halos and the portal). */
export function glowCanvas(color: string, core = 'rgba(255,240,228,0.95)') {
  const [c, g] = canvas(256, 256);
  const grad = g.createRadialGradient(128, 128, 2, 128, 128, 128);
  grad.addColorStop(0, core);
  grad.addColorStop(0.15, hexA(color, 0.85));
  grad.addColorStop(0.5, hexA(color, 0.2));
  grad.addColorStop(1, hexA(color, 0));
  g.fillStyle = grad;
  g.fillRect(0, 0, 256, 256);
  return c;
}

/** The luminous floor grid of the digital world. */
export function gridCanvas(color: string) {
  const [c, g] = canvas(512, 512);
  g.fillStyle = 'rgba(0,0,0,0)';
  g.clearRect(0, 0, 512, 512);
  g.strokeStyle = hexA(color, 0.85);
  g.lineWidth = 3;
  g.strokeRect(0, 0, 512, 512);
  g.strokeStyle = hexA(color, 0.28);
  g.lineWidth = 1.5;
  for (let k = 64; k < 512; k += 64) {
    g.beginPath();
    g.moveTo(k, 0);
    g.lineTo(k, 512);
    g.moveTo(0, k);
    g.lineTo(512, k);
    g.stroke();
  }
  return c;
}

/** Lit windows for the data towers (used as an emissive map). */
export function windowsCanvas(color: string) {
  const [c, g] = canvas(128, 512);
  const r = rand(17);
  g.fillStyle = '#000';
  g.fillRect(0, 0, 128, 512);
  for (let y = 6; y < 512; y += 12) {
    for (let x = 6; x < 128; x += 14) {
      const v = r();
      if (v < 0.42) continue;
      g.fillStyle = v > 0.94 ? '#ffffff' : hexA(color, 0.35 + v * 0.5);
      g.fillRect(x, y, 8, 5);
    }
  }
  return c;
}

export function hexA(hex: string, alpha: number) {
  const h = hex.replace('#', '');
  const n = parseInt(h.length === 3 ? h.split('').map((x) => x + x).join('') : h, 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

export interface PanelInk {
  ink: string;
  muted: string;
  accent: string;
  fill: string;
}

export type PanelKind = 'sql' | 'chart' | 'insight' | 'nlp' | 'question';

/** Translucent holographic panels: the question, its SQL, a chart and the insight. */
export function drawPanel(c: HTMLCanvasElement, kind: PanelKind, p: PanelInk) {
  const g = c.getContext('2d')!;
  const W = c.width;
  const H = c.height;
  g.clearRect(0, 0, W, H);
  roundRect(g, 6, 6, W - 12, H - 12, 34);
  g.fillStyle = p.fill;
  g.fill();
  const edge = g.createLinearGradient(0, 0, W, H);
  edge.addColorStop(0, 'rgba(255,255,255,0.45)');
  edge.addColorStop(0.5, 'rgba(255,255,255,0.08)');
  edge.addColorStop(1, hexA(p.accent, 0.8));
  g.strokeStyle = edge;
  g.lineWidth = 4;
  g.stroke();
  const pad = 56;
  const label = (t: string) => {
    g.font = '500 22px Inter, system-ui, sans-serif';
    g.fillStyle = p.muted;
    g.fillText(t.toUpperCase().split('').join(String.fromCharCode(8202)), pad, 74);
    g.fillStyle = p.accent;
    g.beginPath();
    g.arc(W - pad, 66, 7, 0, Math.PI * 2);
    g.fill();
  };
  if (kind === 'question') {
    label('Question');
    g.font = 'italic 64px "Instrument Serif", Georgia, serif';
    g.fillStyle = p.ink;
    g.fillText('Which product is', pad, 190);
    g.fillText('loved the most?', pad, 262);
  } else if (kind === 'sql') {
    label('Generated SQL · validated');
    const lines: [string, string][][] = [
      [['SELECT', 'k'], [' p.name, ', 'i'], ['AVG', 'f'], ['(r.rating)', 'i']],
      [['FROM', 'k'], [' product_reviews r', 'i']],
      [['JOIN', 'k'], [' products p ', 'i'], ['ON', 'k'], [' p.id = r.product_id', 'i']],
      [['GROUP BY', 'k'], [' p.name', 'i']],
      [['ORDER BY', 'k'], [' 2 ', 'n'], ['DESC LIMIT', 'k'], [' 5', 'n']],
    ];
    g.font = '500 34px "JetBrains Mono", ui-monospace, monospace';
    lines.forEach((line, li) => {
      let x = pad;
      const y = 150 + li * 62;
      g.fillStyle = p.muted;
      g.globalAlpha = 0.5;
      g.fillText(String(li + 1), x, y);
      g.globalAlpha = 1;
      x += 48;
      for (const [txt, k] of line) {
        g.fillStyle = k === 'k' ? p.accent : k === 'f' ? '#d6b072' : k === 'n' ? '#9cc0e6' : p.ink;
        g.fillText(txt, x, y);
        x += g.measureText(txt).width;
      }
    });
  } else if (kind === 'chart') {
    label('Average rating · top products');
    const values = [4.62, 4.48, 4.41, 4.3, 4.21, 4.12];
    const base = H - 70;
    const top = 120;
    const bw = (W - pad * 2) / values.length;
    values.forEach((v, i) => {
      const h = ((v - 3.6) / 1.1) * (base - top);
      const x = pad + i * bw + 14;
      const grad = g.createLinearGradient(0, base - h, 0, base);
      grad.addColorStop(0, p.accent);
      grad.addColorStop(1, hexA(p.accent, 0.2));
      g.fillStyle = grad;
      roundRect(g, x, base - h, bw - 28, h, 10);
      g.fill();
    });
  } else if (kind === 'nlp') {
    label('Natural language → meaning');
    g.font = '400 40px Inter, system-ui, sans-serif';
    g.fillStyle = p.ink;
    const words = ['loved', '→', 'rating', '·', 'reviews', '·', 'products'];
    let x = pad;
    words.forEach((w, i) => {
      g.fillStyle = i === 0 ? p.accent : w === '→' || w === '·' ? p.muted : p.ink;
      g.fillText(w, x, 190);
      x += g.measureText(w).width + 18;
    });
    g.strokeStyle = hexA(p.accent, 0.5);
    g.lineWidth = 2;
    for (let i = 0; i < 7; i++) {
      g.beginPath();
      g.arc(pad + 40 + i * 120, H - 90, 12, 0, Math.PI * 2);
      g.stroke();
      if (i < 6) {
        g.beginPath();
        g.moveTo(pad + 52 + i * 120, H - 90);
        g.lineTo(pad + 148 + i * 120, H - 90);
        g.stroke();
      }
    }
  } else {
    label('Insight');
    g.font = '96px "Instrument Serif", Georgia, serif';
    g.fillStyle = p.accent;
    g.fillText('32%', pad, 210);
    g.font = '400 34px Inter, system-ui, sans-serif';
    g.fillStyle = p.ink;
    g.fillText('of total revenue came from', pad + 200, 172);
    g.fillText('your highest-performing product.', pad + 200, 222);
    g.font = '400 24px Inter, system-ui, sans-serif';
    g.fillStyle = p.muted;
    g.fillText('Example insight', pad, H - 54);
  }
}
