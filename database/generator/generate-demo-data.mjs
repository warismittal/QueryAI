#!/usr/bin/env node
/**
 * Deterministic generator for the QueryAI e-commerce demo dataset.
 *
 * Produces identical INSERT data for PostgreSQL and MySQL so that both supported
 * target dialects can be demonstrated against the same business facts.
 *
 *   node database/generator/generate-demo-data.mjs
 *
 * Output:
 *   database/demo/postgres/02-data.sql
 *   database/demo/mysql/02-data.sql
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

// ---------- deterministic PRNG (mulberry32) ----------
let seed = 20240101;
function rand() {
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const pick = (arr) => arr[Math.floor(rand() * arr.length)];
const between = (min, max) => min + rand() * (max - min);
const intBetween = (min, max) => Math.floor(between(min, max + 1));
function weighted(entries) {
  const total = entries.reduce((s, [, w]) => s + w, 0);
  let r = rand() * total;
  for (const [v, w] of entries) { if ((r -= w) <= 0) return v; }
  return entries[entries.length - 1][0];
}
function poisson(lambda) {
  const L = Math.exp(-lambda); let k = 0; let p = 1;
  do { k++; p *= rand(); } while (p > L);
  return k - 1;
}
const money = (n) => (Math.round(n * 100) / 100).toFixed(2);
const pad = (n) => String(n).padStart(2, '0');
const fmtDate = (d) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
const fmtTs = (d) => `${fmtDate(d)} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;
const q = (s) => `'${String(s).replace(/'/g, "''")}'`;

// ---------- reference data ----------
const START = Date.UTC(2024, 0, 1);
const END = Date.UTC(2026, 9, 10, 23, 59, 59); // 2026-10-10

const categories = [
  [1, 'Electronics', null], [2, 'Home & Kitchen', null], [3, 'Fashion', null], [4, 'Sports & Outdoors', null], [5, 'Books & Media', null],
  [6, 'Smartphones', 1], [7, 'Laptops', 1], [8, 'Audio', 1], [9, 'Accessories', 1],
  [10, 'Cookware', 2], [11, 'Furniture', 2], [12, 'Appliances', 2],
  [13, 'Menswear', 3], [14, 'Womenswear', 3], [15, 'Footwear', 3],
  [16, 'Fitness', 4], [17, 'Camping', 4], [18, 'Cycling', 4],
  [19, 'Fiction', 5], [20, 'Non-fiction', 5],
];

const productCatalog = {
  6: [['Nova X5 Smartphone', 649], ['Nova X5 Pro Smartphone', 899], ['Pixelate 8 Phone', 599], ['Orbit Lite Phone', 299], ['Orbit Max Phone', 749]],
  7: [['AeroBook 14 Laptop', 1099], ['AeroBook 16 Pro Laptop', 1899], ['Cortex Gaming Laptop', 1599], ['StudyMate 13 Laptop', 549], ['Vertex Ultrabook', 1349]],
  8: [['PulseBuds Wireless Earbuds', 129], ['Studio ANC Headphones', 299], ['BoomCube Speaker', 89], ['SoundBar 500', 249], ['PulseBuds Sport', 99], ['Vinyl Turntable Classic', 199]],
  9: [['USB-C Fast Charger 65W', 39], ['Braided Cable 2m', 15], ['Laptop Sleeve 14"', 29], ['Wireless Mouse Silent', 25], ['Mechanical Keyboard TKL', 119], ['Phone Case Armor', 19], ['Power Bank 20000mAh', 49]],
  10: [['Cast Iron Skillet 12"', 45], ['Nonstick Pan Set (3)', 89], ['Chef Knife 8"', 69], ['Stainless Stock Pot', 59], ['Ceramic Bakeware Set', 74]],
  11: [['Oak Dining Chair', 129], ['Standing Desk Frame', 349], ['Bookshelf 5-Tier', 159], ['Ergonomic Office Chair', 289], ['Coffee Table Walnut', 219]],
  12: [['Espresso Machine Barista', 449], ['Air Fryer 5L', 119], ['Robot Vacuum S2', 329], ['Blender Pro 1200W', 99], ['Electric Kettle Glass', 39]],
  13: [['Oxford Shirt Slim', 49], ['Chino Trousers', 59], ['Wool Overcoat', 229], ['Denim Jacket', 89], ['Merino Sweater', 99]],
  14: [['Linen Summer Dress', 79], ['Tailored Blazer', 149], ['Cashmere Scarf', 69], ['High-Rise Jeans', 85], ['Silk Blouse', 95]],
  15: [['Trail Running Shoes', 129], ['Leather Chelsea Boots', 179], ['Everyday Sneakers', 79], ['Hiking Boots Waterproof', 159]],
  16: [['Adjustable Dumbbells 24kg', 299], ['Yoga Mat Pro', 49], ['Resistance Band Set', 29], ['Smart Fitness Watch', 199], ['Foam Roller', 25]],
  17: [['2-Person Tent Ultralight', 249], ['Sleeping Bag 0°C', 139], ['Camping Stove Compact', 59], ['Headlamp 400lm', 35]],
  18: [['Road Bike Helmet', 89], ['Bike Repair Kit', 39], ['Cycling Jersey', 65], ['Smart Bike Trainer', 599]],
  19: [['The Silent Orbit (Novel)', 18], ['Harbor of Glass (Novel)', 16], ['Midnight Ledger (Thriller)', 15], ['Echoes of Ember (Fantasy)', 21]],
  20: [['Data Driven Decisions', 32], ['The Analytics Mindset', 28], ['Practical SQL Handbook', 39], ['History of Trade Routes', 25], ['Mindful Leadership', 22]],
};
const brands = {
  1: ['Novatek', 'Aero', 'Pulse', 'Cortex'], 2: ['HearthCo', 'Oakline', 'Brewster'], 3: ['Northwind Apparel', 'Linea', 'Strider'],
  4: ['Summit', 'Kinetic', 'Velo'], 5: ['Lantern Press', 'Quarry Books'],
};

const firstNames = ['Aarav', 'Ananya', 'Liam', 'Olivia', 'Noah', 'Emma', 'Mateo', 'Sofia', 'Kenji', 'Yuki', 'Lucas', 'Mia', 'Arjun', 'Diya', 'Ethan', 'Ava',
  'Omar', 'Layla', 'Chen', 'Mei', 'Hugo', 'Chloe', 'Felix', 'Hannah', 'Ravi', 'Priya', 'Daniel', 'Grace', 'Ivan', 'Elena', 'Kwame', 'Amara', 'Leo', 'Zoe',
  'Samuel', 'Isla', 'Rohan', 'Kavya', 'Jonas', 'Freya', 'Diego', 'Lucia', 'Aiden', 'Nora', 'Vikram', 'Sara', 'Tomas', 'Ines', 'Max', 'Alice'];
const lastNames = ['Sharma', 'Smith', 'Garcia', 'Tanaka', 'Muller', 'Rossi', 'Patel', 'Johnson', 'Kim', 'Nguyen', 'Silva', 'Brown', 'Mehta', 'Lopez',
  'Wilson', 'Chen', 'Ivanova', 'Okafor', 'Dubois', 'Andersen', 'Kapoor', 'Martin', 'Haddad', 'Novak', 'Reyes', 'Singh', 'Clarke', 'Fischer', 'Sato', 'Costa'];
const locations = [
  ['New York', 'NY', 'United States'], ['San Francisco', 'CA', 'United States'], ['Austin', 'TX', 'United States'], ['Chicago', 'IL', 'United States'],
  ['Seattle', 'WA', 'United States'], ['Boston', 'MA', 'United States'], ['Toronto', 'ON', 'Canada'], ['Vancouver', 'BC', 'Canada'],
  ['London', 'England', 'United Kingdom'], ['Manchester', 'England', 'United Kingdom'], ['Berlin', 'Berlin', 'Germany'], ['Munich', 'Bavaria', 'Germany'],
  ['Paris', 'Ile-de-France', 'France'], ['Mumbai', 'Maharashtra', 'India'], ['Bengaluru', 'Karnataka', 'India'], ['Delhi', 'Delhi', 'India'],
  ['Sydney', 'NSW', 'Australia'], ['Melbourne', 'VIC', 'Australia'], ['Tokyo', 'Tokyo', 'Japan'], ['Singapore', 'Singapore', 'Singapore'],
];
const warehouses = [[1, 'East Coast Fulfillment', 'Newark', 'United States'], [2, 'West Coast Fulfillment', 'Reno', 'United States'],
  [3, 'EU Central Hub', 'Rotterdam', 'Netherlands'], [4, 'APAC Hub', 'Singapore', 'Singapore']];
const warehouseFor = (country) => {
  if (['United States', 'Canada'].includes(country)) return rand() < 0.5 ? 1 : 2;
  if (['United Kingdom', 'Germany', 'France'].includes(country)) return 3;
  return 4;
};

// ---------- generate ----------
const rows = { categories, warehouses, customers: [], products: [], inventory: [], orders: [], order_items: [], payments: [], product_reviews: [] };

let productId = 0;
for (const [catId, items] of Object.entries(productCatalog)) {
  const parent = categories.find((c) => c[0] === Number(catId))[2];
  for (const [name, price] of items) {
    productId++;
    const cost = price * between(0.42, 0.68);
    const launched = new Date(START - intBetween(30, 900) * 86400000);
    rows.products.push({
      id: productId, sku: `SKU-${String(catId).padStart(2, '0')}${String(productId).padStart(4, '0')}`, name, category_id: Number(catId),
      brand: pick(brands[parent]), unit_price: price, unit_cost: cost, is_active: rand() > 0.05, launched_on: fmtDate(launched),
      quality: between(3.2, 4.9), popularity: between(0.3, 1.6),
    });
  }
}

const customerCount = 600;
const usedEmails = new Set();
for (let id = 1; id <= customerCount; id++) {
  const first = pick(firstNames); const last = pick(lastNames);
  let email = `${first}.${last}`.toLowerCase(); let n = 1;
  while (usedEmails.has(email)) { email = `${first}.${last}${++n}`.toLowerCase(); }
  usedEmails.add(email);
  const [city, state, country] = pick(locations);
  const signup = new Date(START - 400 * 86400000 + rand() * (END - START) * 0.85);
  rows.customers.push({
    id, first_name: first, last_name: last, email: `${email}@example.com`, city, state, country,
    segment: weighted([['Consumer', 70], ['Small Business', 20], ['Corporate', 10]]), signup_date: fmtDate(signup),
    // Hidden behavioural traits used to create realistic trends (not exported).
    rate: between(0.05, 0.55) * (rand() < 0.08 ? 3 : 1), yearlyTrend: weighted([[between(0.45, 0.8), 30], [between(0.9, 1.15), 40], [between(1.2, 1.7), 30]]),
    signupTs: signup.getTime(),
  });
}

for (const p of rows.products) {
  for (const [wid] of warehouses) {
    rows.inventory.push({ warehouse_id: wid, product_id: p.id, quantity_on_hand: intBetween(0, 450), reorder_level: intBetween(15, 60) });
  }
}

const seasonal = [0.85, 0.8, 0.95, 0.95, 1.0, 1.0, 0.95, 1.0, 1.05, 1.1, 1.45, 1.7];
const productWeights = rows.products.map((p) => [p, p.popularity * (p.unit_price > 1000 ? 0.35 : p.unit_price > 300 ? 0.7 : 1.2)]);
let orderId = 0; let itemId = 0; let paymentId = 0; let reviewId = 0;
const reviewed = new Set();

for (let y = 2024; y <= 2026; y++) {
  for (let m = 0; m < 12; m++) {
    const monthStart = Date.UTC(y, m, 1);
    if (monthStart > END) break;
    const monthEnd = Math.min(Date.UTC(y, m + 1, 1) - 1000, END);
    for (const c of rows.customers) {
      if (c.signupTs > monthEnd) continue;
      const yearsElapsed = (monthStart - START) / (365 * 86400000);
      const lambda = c.rate * seasonal[m] * Math.pow(c.yearlyTrend, yearsElapsed);
      const count = poisson(lambda);
      for (let k = 0; k < count; k++) {
        const ts = new Date(Math.max(monthStart, c.signupTs) + rand() * (monthEnd - Math.max(monthStart, c.signupTs)));
        orderId++;
        const ageDays = (END - ts.getTime()) / 86400000;
        let status;
        if (ageDays < 3) status = weighted([['processing', 60], ['shipped', 35], ['cancelled', 5]]);
        else if (ageDays < 9) status = weighted([['shipped', 55], ['delivered', 40], ['cancelled', 5]]);
        else status = weighted([['delivered', 89], ['cancelled', 5], ['returned', 6]]);
        const channel = weighted([['web', 50], ['mobile_app', 38], ['marketplace', 12]]);
        const lineCount = weighted([[1, 45], [2, 28], [3, 15], [4, 8], [5, 4]]);
        const chosen = new Set();
        let subtotal = 0;
        const lines = [];
        for (let li = 0; li < lineCount; li++) {
          const p = weighted(productWeights);
          if (chosen.has(p.id)) continue;
          chosen.add(p.id);
          const qty = p.unit_price > 300 ? 1 : weighted([[1, 70], [2, 20], [3, 7], [4, 3]]);
          // prices drift up ~4% per year
          const unitPrice = p.unit_price * (1 + 0.04 * yearsElapsed);
          const lineDiscount = rand() < 0.15 ? unitPrice * qty * between(0.05, 0.2) : 0;
          const lineTotal = unitPrice * qty - lineDiscount;
          subtotal += lineTotal;
          itemId++;
          lines.push({ id: itemId, order_id: orderId, product_id: p.id, quantity: qty, unit_price: unitPrice, line_discount: lineDiscount, line_total: lineTotal, p });
        }
        const discount = rand() < 0.1 ? subtotal * between(0.05, 0.12) : 0;
        const shipping = subtotal > 100 ? 0 : weighted([[4.99, 60], [9.99, 30], [14.99, 10]]);
        const total = subtotal - discount + shipping;
        rows.orders.push({
          id: orderId, customer_id: c.id, order_date: fmtTs(ts), status, channel, warehouse_id: warehouseFor(c.country),
          shipping_cost: shipping, discount_amount: discount, total_amount: total,
        });
        rows.order_items.push(...lines);
        if (status !== 'processing' || rand() < 0.7) {
          paymentId++;
          const payStatus = status === 'cancelled' || status === 'returned' ? 'refunded' : 'completed';
          rows.payments.push({
            id: paymentId, order_id: orderId, payment_method: weighted([['credit_card', 55], ['paypal', 20], ['debit_card', 15], ['bank_transfer', 5], ['gift_card', 5]]),
            amount: total, status: payStatus, paid_at: fmtTs(new Date(ts.getTime() + intBetween(30, 3600) * 1000)),
          });
        }
        if (status === 'delivered') {
          for (const l of lines) {
            const key = `${c.id}:${l.p.id}`;
            if (reviewed.has(key) || rand() > 0.22) continue;
            reviewed.add(key);
            reviewId++;
            const rating = Math.max(1, Math.min(5, Math.round(l.p.quality + between(-1.6, 1.2))));
            const reviewDate = new Date(Math.min(END, ts.getTime() + intBetween(5, 40) * 86400000));
            rows.product_reviews.push({ id: reviewId, product_id: l.p.id, customer_id: c.id, rating, review_date: fmtDate(reviewDate) });
          }
        }
      }
    }
  }
}

// ---------- SQL emitters ----------
function inserts(table, columns, values, batch = 400) {
  const out = [];
  for (let i = 0; i < values.length; i += batch) {
    out.push(`INSERT INTO ${table} (${columns.join(', ')}) VALUES\n` + values.slice(i, i + batch).map((v) => `(${v.join(', ')})`).join(',\n') + ';');
  }
  return out.join('\n');
}
const nul = (v) => (v === null || v === undefined ? 'NULL' : v);
function body() {
  const parts = [];
  parts.push(inserts('categories', ['id', 'name', 'parent_category_id'], rows.categories.map(([id, n, p]) => [id, q(n), nul(p)])));
  parts.push(inserts('warehouses', ['id', 'name', 'city', 'country'], rows.warehouses.map(([id, n, c, co]) => [id, q(n), q(c), q(co)])));
  parts.push(inserts('customers', ['id', 'first_name', 'last_name', 'email', 'city', 'state', 'country', 'segment', 'signup_date'],
    rows.customers.map((c) => [c.id, q(c.first_name), q(c.last_name), q(c.email), q(c.city), q(c.state), q(c.country), q(c.segment), q(c.signup_date)])));
  parts.push(inserts('products', ['id', 'sku', 'name', 'category_id', 'brand', 'unit_price', 'unit_cost', 'is_active', 'launched_on'],
    rows.products.map((p) => [p.id, q(p.sku), q(p.name), p.category_id, q(p.brand), money(p.unit_price), money(p.unit_cost), p.is_active ? 'TRUE' : 'FALSE', q(p.launched_on)])));
  parts.push(inserts('inventory', ['warehouse_id', 'product_id', 'quantity_on_hand', 'reorder_level'],
    rows.inventory.map((i) => [i.warehouse_id, i.product_id, i.quantity_on_hand, i.reorder_level])));
  parts.push(inserts('orders', ['id', 'customer_id', 'order_date', 'status', 'channel', 'warehouse_id', 'shipping_cost', 'discount_amount', 'total_amount'],
    rows.orders.map((o) => [o.id, o.customer_id, q(o.order_date), q(o.status), q(o.channel), o.warehouse_id, money(o.shipping_cost), money(o.discount_amount), money(o.total_amount)])));
  parts.push(inserts('order_items', ['id', 'order_id', 'product_id', 'quantity', 'unit_price', 'line_discount', 'line_total'],
    rows.order_items.map((l) => [l.id, l.order_id, l.product_id, l.quantity, money(l.unit_price), money(l.line_discount), money(l.line_total)])));
  parts.push(inserts('payments', ['id', 'order_id', 'payment_method', 'amount', 'status', 'paid_at'],
    rows.payments.map((p) => [p.id, p.order_id, q(p.payment_method), money(p.amount), q(p.status), q(p.paid_at)])));
  parts.push(inserts('product_reviews', ['id', 'product_id', 'customer_id', 'rating', 'review_date'],
    rows.product_reviews.map((r) => [r.id, r.product_id, r.customer_id, r.rating, q(r.review_date)])));
  return parts.join('\n\n');
}

const header = (dialect) => `-- QueryAI demo e-commerce dataset (${dialect}). Generated by database/generator/generate-demo-data.mjs — do not edit by hand.
-- customers=${rows.customers.length} products=${rows.products.length} orders=${rows.orders.length} order_items=${rows.order_items.length} payments=${rows.payments.length} reviews=${rows.product_reviews.length}
`;
const pg = `${header('PostgreSQL')}BEGIN;\n${body()}\nCOMMIT;\nANALYZE;\n`;
const my = `${header('MySQL')}SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\nSTART TRANSACTION;\n${body()}\nCOMMIT;\nSET FOREIGN_KEY_CHECKS = 1;\n`;

for (const [dir, content] of [['postgres', pg], ['mysql', my]]) {
  const file = join(root, 'demo', dir, '02-data.sql');
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, content);
  console.log(`wrote ${file} (${(content.length / 1024).toFixed(0)} KiB)`);
}
// copy bundled with the backend: loaded by WarehouseBootstrap on single-database hosts (e.g. Render free tier)
const bundled = join(root, '..', 'backend', 'src', 'main', 'resources', 'demo', 'postgres-data.sql');
mkdirSync(dirname(bundled), { recursive: true });
writeFileSync(bundled, pg);
console.log(`wrote ${bundled}`);
console.log(`customers=${rows.customers.length} products=${rows.products.length} orders=${rows.orders.length} items=${rows.order_items.length} payments=${rows.payments.length} reviews=${rows.product_reviews.length}`);
