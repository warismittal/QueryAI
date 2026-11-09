import * as THREE from 'three';
import { Reflector } from 'three/addons/objects/Reflector.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import {
  drawPanel, glowCanvas, gridCanvas, hexA, paintingCanvas, paperCanvas, planksCanvas, rugCanvas, windowsCanvas,
  type PanelKind,
} from './sceneTextures';

/**
 * "From the library to the data" — a procedural two-storey library (mezzanine, Persian rug, velvet sofa, armchair,
 * lamps, a rolling ladder and an old painting). An ancient book on a lectern releases glowing SQL; pages lift off the
 * shelves and stream towards an ornate door; the doors swing open and the camera passes through into a luminous
 * digital city of data towers, light roads and holographic panels. Scroll progress (0 → 1) drives the camera.
 * Everything is generated in code — no models or images are downloaded.
 */

export interface ScenePalette {
  background: string;
  fog: string;
  fogDensity: number;
  ambient: number;
  lamp: string;
  lampIntensity: number;
  accent: string;
  accentIntensity: number;
  wood: string;
  woodDark: string;
  floor: string;
  rug: [string, string, string];
  velvet: string;
  armchair: string;
  books: string[];
  gilt: string;
  digitalBg: string;
  digitalFog: string;
  panelInk: string;
  panelMuted: string;
  panelFill: string;
  exposure: number;
  bloom: number;
}

const LIB_BOOKS = ['#3d0f16', '#511620', '#6a1a26', '#1b1a1d', '#26252b', '#2f231d', '#121113', '#463a30', '#5a1420', '#232028', '#6e5a3c', '#14212b'];

export const PALETTES: Record<'midnight' | 'crimson' | 'graphite' | 'marine' | 'azure' | 'light', ScenePalette> = {
  midnight: {
    background: '#050405', fog: '#070506', fogDensity: 0.03, ambient: 0.32, lamp: '#ffbf85', lampIntensity: 30,
    accent: '#d83b53', accentIntensity: 120, wood: '#2a1a14', woodDark: '#120c0a', floor: '#1a110d',
    rug: ['#7d1622', '#14101a', '#c9a26a'], velvet: '#0d0c10', armchair: '#8c1a28', books: LIB_BOOKS, gilt: '#b08a4a',
    digitalBg: '#050206', digitalFog: '#12040a',
    panelInk: '#f3eee8', panelMuted: '#a49ea8', panelFill: 'rgba(14,10,14,0.55)', exposure: 1.05, bloom: 0.9,
  },
  crimson: {
    background: '#070203', fog: '#0d0305', fogDensity: 0.032, ambient: 0.3, lamp: '#ffae7a', lampIntensity: 28,
    accent: '#ff3352', accentIntensity: 170, wood: '#2b1512', woodDark: '#140909', floor: '#1c0d0c',
    rug: ['#951a2a', '#1a0a10', '#d4a76a'], velvet: '#120a0d', armchair: '#a61d30', books: ['#4a0d17', '#62121f', '#7d1726', '#24101a', '#1c1316', '#3a1414', '#140a0c', '#5c1a1f', '#8a1c2c', '#2b1519'],
    gilt: '#c09050', digitalBg: '#080103', digitalFog: '#1a0309',
    panelInk: '#fbeeea', panelMuted: '#b8979c', panelFill: 'rgba(24,8,11,0.55)', exposure: 1.1, bloom: 1.1,
  },
  graphite: {
    background: '#090909', fog: '#0c0c0d', fogDensity: 0.028, ambient: 0.36, lamp: '#f2ece4', lampIntensity: 28,
    accent: '#e6e6ee', accentIntensity: 90, wood: '#222224', woodDark: '#111112', floor: '#161617',
    rug: ['#3a3a40', '#121214', '#9a9aa2'], velvet: '#0e0e10', armchair: '#5a1a25', books: ['#1d1d20', '#2a2a2e', '#36363b', '#141416', '#44444a', '#232326', '#5a1a25', '#2f2f34', '#101012', '#3c3c42'],
    gilt: '#9a9aa2', digitalBg: '#060607', digitalFog: '#101013',
    panelInk: '#f2f2f4', panelMuted: '#9a9aa3', panelFill: 'rgba(18,18,20,0.55)', exposure: 1.0, bloom: 0.75,
  },
  marine: {
    background: '#020709', fog: '#03100f', fogDensity: 0.032, ambient: 0.34, lamp: '#bff3ea', lampIntensity: 26,
    accent: '#2ec4c0', accentIntensity: 140, wood: '#14211f', woodDark: '#081110', floor: '#0b1514',
    rug: ['#0e4a52', '#071216', '#d9b37a'], velvet: '#06100f', armchair: '#b0503d', books: ['#0f3a40', '#13505a', '#0b2a33', '#1b1a1d', '#24333a', '#3a2c22', '#0c1418', '#5a3a2a', '#1e5d63', '#132630'],
    gilt: '#b9a26a', digitalBg: '#01060a', digitalFog: '#031418',
    panelInk: '#e8f6f4', panelMuted: '#8fb0b2', panelFill: 'rgba(4,18,22,0.55)', exposure: 1.08, bloom: 1.0,
  },
  azure: {
    background: '#03040b', fog: '#050817', fogDensity: 0.03, ambient: 0.32, lamp: '#ffd9a8', lampIntensity: 28,
    accent: '#5b8cff', accentIntensity: 150, wood: '#1b1a22', woodDark: '#0b0b12', floor: '#12121a',
    rug: ['#1c2f78', '#0a0c1c', '#c9a26a'], velvet: '#0a0b14', armchair: '#2741a8', books: ['#1a2a6b', '#22378a', '#151b3a', '#1b1a1d', '#2a2f4a', '#3a2c22', '#0e1124', '#4b3a6b', '#2c4ca8', '#141a33'],
    gilt: '#b9a26a', digitalBg: '#02030c', digitalFog: '#060c26',
    panelInk: '#eef2fb', panelMuted: '#9aa6c8', panelFill: 'rgba(8,12,30,0.55)', exposure: 1.05, bloom: 1.0,
  },
  light: {
    background: '#e7e0d5', fog: '#e4ddd1', fogDensity: 0.026, ambient: 1.0, lamp: '#ffd7a8', lampIntensity: 22,
    accent: '#b3233c', accentIntensity: 80, wood: '#5a4030', woodDark: '#3a2a22', floor: '#8a6a50',
    rug: ['#8c1c2c', '#2a2430', '#d9c08a'], velvet: '#2a2830', armchair: '#a32a3a', books: ['#7a2230', '#8c2b38', '#5b4a3d', '#3f3a37', '#a08a72', '#6d1c28', '#4a4440', '#b8a68c', '#2f2b2a', '#93303f'],
    gilt: '#b08a4a', digitalBg: '#f3efe8', digitalFog: '#efe6dc',
    panelInk: '#1b171c', panelMuted: '#6d6670', panelFill: 'rgba(253,250,245,0.8)', exposure: 1.0, bloom: 0.35,
  },
};

export interface LibraryScene {
  setProgress(p: number): void;
  setPalette(p: ScenePalette): void;
  resize(width: number, height: number): void;
  start(): void;
  stop(): void;
  dispose(): void;
}

// ---------- layout (metres) ----------
const FACE = 6.5; // shelf faces at x = ±FACE
const HALL_FRONT = 8;
const DOOR_Z = -42; // far wall with the door
const LOWER_TOP = 6.2;
const MEZZ_Y = 6.45;
const UPPER_TOP = 11.6;
const CEIL = 12.3;
const DOOR_HALF = 1.8;
const DOOR_H = 5.6;

function rand(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}
const clamp01 = (x: number) => Math.min(1, Math.max(0, x));
const seg = (p: number, a: number, b: number) => clamp01((p - a) / (b - a));
const smooth = (t: number) => t * t * (3 - 2 * t);

export function createLibraryScene(canvas: HTMLCanvasElement, initial: ScenePalette, quality: 'high' | 'low'): LibraryScene {
  const high = quality === 'high';
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: high, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, high ? 1.5 : 1));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(55, 1, 0.1, 220);
  const disposables: { dispose(): void }[] = [];
  const track = <T extends { dispose(): void }>(x: T) => {
    disposables.push(x);
    return x;
  };
  const tex = (c: HTMLCanvasElement, repeat?: [number, number]) => {
    const t = track(new THREE.CanvasTexture(c));
    t.colorSpace = THREE.SRGBColorSpace;
    t.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy());
    if (repeat) {
      t.wrapS = t.wrapT = THREE.RepeatWrapping;
      t.repeat.set(repeat[0], repeat[1]);
    }
    return t;
  };
  let palette = initial;
  const dummy = new THREE.Object3D();
  const m4 = new THREE.Matrix4();

  // ---------- materials ----------
  const woodMat = track(new THREE.MeshStandardMaterial({ roughness: 0.62, metalness: 0.06 }));
  const woodDarkMat = track(new THREE.MeshStandardMaterial({ roughness: 0.7, metalness: 0.05 }));
  const bookMat = track(new THREE.MeshStandardMaterial({ roughness: 0.78, metalness: 0.03 }));
  const giltMat = track(new THREE.MeshStandardMaterial({ roughness: 0.35, metalness: 0.85 }));
  const velvetMat = track(high
    ? new THREE.MeshPhysicalMaterial({ roughness: 0.95, sheen: 1, sheenRoughness: 0.45 })
    : new THREE.MeshStandardMaterial({ roughness: 0.95 }));
  const armchairMat = track(high
    ? new THREE.MeshPhysicalMaterial({ roughness: 0.9, sheen: 1, sheenRoughness: 0.5 })
    : new THREE.MeshStandardMaterial({ roughness: 0.9 }));
  const lampGlowMat = track(new THREE.MeshBasicMaterial({ toneMapped: false }));
  const accentGlowMat = track(new THREE.MeshBasicMaterial({ toneMapped: false }));

  // ---------- floor: polished planks over a dim mirror ----------
  const floorGeo = track(new THREE.PlaneGeometry(FACE * 2 + 2, HALL_FRONT - DOOR_Z + 6));
  const floorZ = (HALL_FRONT + DOOR_Z) / 2;
  if (high) {
    const mirror = new Reflector(floorGeo, {
      clipBias: 0.003, textureWidth: Math.floor(window.innerWidth * 0.45), textureHeight: Math.floor(window.innerHeight * 0.45), color: 0x8a8a8a,
    });
    mirror.rotation.x = -Math.PI / 2;
    mirror.position.set(0, -0.003, floorZ);
    scene.add(mirror);
    disposables.push({ dispose: () => mirror.dispose() });
  }
  const floorMat = track(new THREE.MeshStandardMaterial({ roughness: 0.32, metalness: 0.1, transparent: high, opacity: high ? 0.82 : 1 }));
  const floor = new THREE.Mesh(floorGeo, floorMat);
  floor.rotation.x = -Math.PI / 2;
  floor.position.set(0, 0, floorZ);
  scene.add(floor);

  // ---------- shelving: uprights, boards and thousands of books (instanced) ----------
  const uprightM: THREE.Matrix4[] = [];
  const boardM: THREE.Matrix4[] = [];
  const bookM: THREE.Matrix4[] = [];
  const bookShade: number[] = [];
  const rb = rand(7);
  const bank = new THREE.Object3D();
  /** A bank of shelves. Local frame: runs from z=0 to z=-length, spines face +x at x=0, books extend into -x. */
  function addBank(x: number, z: number, rotY: number, length: number, y0: number, y1: number, levels: number) {
    bank.position.set(x, 0, z);
    bank.rotation.set(0, rotY, 0);
    bank.updateMatrix();
    const bays = Math.max(1, Math.round(length / 3.4));
    const bay = length / bays;
    const gap = (y1 - y0) / levels;
    const push = (list: THREE.Matrix4[], px: number, py: number, pz: number, sx: number, sy: number, sz: number, rx = 0) => {
      dummy.position.set(px, py, pz);
      dummy.rotation.set(rx, 0, 0);
      dummy.scale.set(sx, sy, sz);
      dummy.updateMatrix();
      list.push(new THREE.Matrix4().multiplyMatrices(bank.matrix, dummy.matrix));
    };
    for (let i = 0; i <= bays; i++) push(uprightM, -0.45, (y0 + y1) / 2, -i * bay, 0.2, y1 - y0, 0.95);
    for (let i = 0; i < bays; i++) {
      for (let l = 0; l <= levels; l++) push(boardM, -0.45, y0 + 0.06 + l * gap, -i * bay - bay / 2, 0.95, 0.08, bay - 0.18);
      for (let l = 0; l < levels; l++) {
        const yb = y0 + 0.1 + l * gap;
        let zz = -i * bay - 0.12;
        const zEnd = -(i + 1) * bay + 0.12;
        while (zz > zEnd) {
          if (rb() < 0.03) {
            zz -= 0.2 + rb() * 0.35;
            continue;
          }
          const w = 0.06 + rb() * 0.12;
          const h = gap * (0.55 + rb() * 0.32);
          const d = 0.5 + rb() * 0.22;
          if (zz - w < zEnd) break;
          const lean = rb() < 0.035 ? (rb() - 0.5) * 0.3 : 0;
          push(bookM, -d / 2 - 0.02, yb + h / 2, zz - w / 2, d, h, w, lean);
          bookShade.push(rb());
          zz -= w + 0.008;
        }
      }
    }
  }
  const hallLen = HALL_FRONT - DOOR_Z - 2;
  // left wall: lower bank split around the painting
  addBank(-FACE, HALL_FRONT, 0, 14, 0, LOWER_TOP, 5);
  addBank(-FACE, HALL_FRONT - 17.5, 0, hallLen - 17.5, 0, LOWER_TOP, 5);
  addBank(-FACE, HALL_FRONT, 0, hallLen, MEZZ_Y + 0.1, UPPER_TOP, 4);
  // right wall (rotated 180°: runs towards +z)
  addBank(FACE, DOOR_Z + 2, Math.PI, hallLen, 0, LOWER_TOP, 5);
  addBank(FACE, DOOR_Z + 2, Math.PI, hallLen, MEZZ_Y + 0.1, UPPER_TOP, 4);
  // far wall either side of the door, and above it
  addBank(-FACE - 0.5, DOOR_Z + 0.5, -Math.PI / 2, FACE - DOOR_HALF - 0.2, 0, UPPER_TOP, 9);
  addBank(DOOR_HALF + 0.7, DOOR_Z + 0.5, -Math.PI / 2, FACE - DOOR_HALF - 0.2, 0, UPPER_TOP, 9);

  const box = track(new THREE.BoxGeometry(1, 1, 1));
  const instanced = (list: THREE.Matrix4[], mat: THREE.Material) => {
    const mesh = new THREE.InstancedMesh(box, mat, list.length);
    list.forEach((m, i) => mesh.setMatrixAt(i, m));
    mesh.instanceMatrix.needsUpdate = true;
    disposables.push(mesh);
    scene.add(mesh);
    return mesh;
  };
  instanced(uprightM, woodMat);
  instanced(boardM, woodMat);
  const books = instanced(bookM, bookMat);

  // solid wall behind the far shelves with an opening for the door
  const wallMat = woodDarkMat;
  const addBox = (mat: THREE.Material, w: number, h: number, d: number, x: number, y: number, z: number, ry = 0) => {
    const m = new THREE.Mesh(box, mat);
    m.scale.set(w, h, d);
    m.position.set(x, y, z);
    m.rotation.y = ry;
    scene.add(m);
    return m;
  };
  addBox(wallMat, FACE - DOOR_HALF + 1, CEIL, 0.4, -(FACE + DOOR_HALF + 1) / 2, CEIL / 2, DOOR_Z - 0.3);
  addBox(wallMat, FACE - DOOR_HALF + 1, CEIL, 0.4, (FACE + DOOR_HALF + 1) / 2, CEIL / 2, DOOR_Z - 0.3);
  addBox(wallMat, DOOR_HALF * 2 + 0.2, CEIL - DOOR_H, 0.4, 0, DOOR_H + (CEIL - DOOR_H) / 2, DOOR_Z - 0.3);
  // side walls behind the shelves
  addBox(wallMat, 0.3, CEIL, hallLen + 4, -FACE - 1.1, CEIL / 2, floorZ);
  addBox(wallMat, 0.3, CEIL, hallLen + 4, FACE + 1.1, CEIL / 2, floorZ);

  // ---------- mezzanine balcony with balustrade ----------
  for (const side of [-1, 1]) {
    addBox(woodMat, 1.5, 0.28, hallLen, side * (FACE - 0.75), MEZZ_Y, floorZ + 1);
    addBox(woodMat, 0.12, 0.12, hallLen, side * (FACE - 1.45), MEZZ_Y + 1.05, floorZ + 1);
    addBox(woodMat, 0.08, 0.08, hallLen, side * (FACE - 1.45), MEZZ_Y + 0.25, floorZ + 1);
  }
  const balusterM: THREE.Matrix4[] = [];
  for (const side of [-1, 1]) {
    for (let z = HALL_FRONT - 1; z > DOOR_Z + 2; z -= 0.42) {
      dummy.position.set(side * (FACE - 1.45), MEZZ_Y + 0.65, z);
      dummy.rotation.set(0, 0, 0);
      dummy.scale.set(0.06, 0.8, 0.06);
      dummy.updateMatrix();
      balusterM.push(dummy.matrix.clone());
    }
  }
  instanced(balusterM, woodMat);

  // ---------- coffered wooden ceiling with recessed spotlights ----------
  addBox(woodDarkMat, FACE * 2 + 2.4, 0.2, hallLen + 4, 0, CEIL, floorZ);
  const beamM: THREE.Matrix4[] = [];
  for (let z = HALL_FRONT; z > DOOR_Z; z -= 3.6) {
    m4.compose(new THREE.Vector3(0, CEIL - 0.25, z), new THREE.Quaternion(), new THREE.Vector3(FACE * 2 + 2, 0.32, 0.25));
    beamM.push(m4.clone());
  }
  for (const x of [-3.2, 0, 3.2]) {
    m4.compose(new THREE.Vector3(x, CEIL - 0.25, floorZ), new THREE.Quaternion(), new THREE.Vector3(0.22, 0.3, hallLen + 4));
    beamM.push(m4.clone());
  }
  instanced(beamM, woodMat);
  const spotGeo = track(new THREE.CircleGeometry(0.11, 16));
  const spotM: THREE.Matrix4[] = [];
  const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(Math.PI / 2, 0, 0));
  for (let z = HALL_FRONT - 1.8; z > DOOR_Z + 1; z -= 3.6) {
    for (const x of [-4.8, -1.6, 1.6, 4.8]) {
      m4.compose(new THREE.Vector3(x, CEIL - 0.11, z), q, new THREE.Vector3(1, 1, 1));
      spotM.push(m4.clone());
    }
  }
  const spots = new THREE.InstancedMesh(spotGeo, lampGlowMat, spotM.length);
  spotM.forEach((m, i) => spots.setMatrixAt(i, m));
  disposables.push(spots);
  scene.add(spots);

  // ---------- the Persian rug ----------
  let rugTex = tex(rugCanvas(palette.rug[0], palette.rug[1], palette.rug[2]));
  const rugMat = track(new THREE.MeshStandardMaterial({ map: rugTex, roughness: 1 }));
  const rug = new THREE.Mesh(track(new THREE.PlaneGeometry(7.2, 11)), rugMat);
  rug.rotation.x = -Math.PI / 2;
  rug.position.set(0, 0.012, -9);
  scene.add(rug);

  // ---------- furniture ----------
  const furniture = new THREE.Group();
  scene.add(furniture);
  const part = (mat: THREE.Material, w: number, h: number, d: number, x: number, y: number, z: number, parent: THREE.Object3D) => {
    const m = new THREE.Mesh(box, mat);
    m.scale.set(w, h, d);
    m.position.set(x, y, z);
    parent.add(m);
    return m;
  };
  // black velvet sofa + ottoman (right side, facing the aisle)
  const sofa = new THREE.Group();
  part(velvetMat, 3.4, 0.45, 1.15, 0, 0.42, 0, sofa);
  part(velvetMat, 3.4, 0.85, 0.32, 0, 0.95, -0.45, sofa);
  part(velvetMat, 0.32, 0.6, 1.15, -1.7, 0.75, 0, sofa);
  part(velvetMat, 0.32, 0.6, 1.15, 1.7, 0.75, 0, sofa);
  part(woodDarkMat, 3.5, 0.18, 1.2, 0, 0.1, 0, sofa);
  const pillowMat = track(new THREE.MeshStandardMaterial({ roughness: 0.9 }));
  const pillowAlt = track(new THREE.MeshStandardMaterial({ roughness: 0.9 }));
  [-1.1, -0.4, 0.4, 1.1].forEach((x, i) => {
    const p = part(i % 2 ? pillowAlt : pillowMat, 0.5, 0.45, 0.14, x, 0.98, -0.18, sofa);
    p.rotation.set(-0.25, 0, (i - 1.5) * 0.08);
  });
  sofa.position.set(4.3, 0, -12);
  sofa.rotation.y = -Math.PI / 2;
  furniture.add(sofa);
  const ottoman = new THREE.Group();
  part(velvetMat, 1.6, 0.42, 1.1, 0, 0.32, 0, ottoman);
  ottoman.position.set(2.7, 0, -12);
  furniture.add(ottoman);
  // red armchair (left)
  const chair = new THREE.Group();
  part(armchairMat, 1.1, 0.42, 1.0, 0, 0.42, 0, chair);
  part(armchairMat, 1.1, 1.0, 0.24, 0, 1.0, -0.42, chair);
  part(armchairMat, 0.2, 0.5, 1.0, -0.55, 0.72, 0, chair);
  part(armchairMat, 0.2, 0.5, 1.0, 0.55, 0.72, 0, chair);
  for (const [x, z] of [[-0.45, 0.4], [0.45, 0.4], [-0.45, -0.4], [0.45, -0.4]]) part(woodDarkMat, 0.07, 0.22, 0.07, x, 0.1, z, chair);
  chair.position.set(-3.9, 0, -7.5);
  chair.rotation.y = Math.PI / 2 + 0.35;
  furniture.add(chair);
  // coffee table with a stack of books
  const table = new THREE.Group();
  part(woodMat, 1.6, 0.08, 0.9, 0, 0.52, 0, table);
  for (const [x, z] of [[-0.7, 0.35], [0.7, 0.35], [-0.7, -0.35], [0.7, -0.35]]) part(woodDarkMat, 0.08, 0.5, 0.08, x, 0.26, z, table);
  [0, 1, 2].forEach((i) => {
    const b = part(bookMat, 0.55, 0.08, 0.38, -0.25 + i * 0.03, 0.6 + i * 0.085, 0.05 * i, table);
    b.rotation.y = i * 0.2;
  });
  table.position.set(1.2, 0, -12);
  furniture.add(table);
  // table lamp beside the sofa and a floor lamp beside the armchair
  const lamps: THREE.PointLight[] = [];
  const shadeGeo = track(new THREE.CylinderGeometry(0.22, 0.38, 0.38, 24, 1, true));
  const lampAt = (x: number, y: number, z: number, stand: number) => {
    part(giltMat, 0.06, stand, 0.06, x, y - stand / 2 - 0.15, z, furniture);
    part(giltMat, 0.32, 0.04, 0.32, x, y - stand - 0.15, z, furniture);
    const shade = new THREE.Mesh(shadeGeo, lampGlowMat);
    shade.position.set(x, y, z);
    furniture.add(shade);
    const light = new THREE.PointLight('#ffffff', 1, 9, 2);
    light.position.set(x, y - 0.1, z);
    furniture.add(light);
    lamps.push(light);
  };
  part(woodMat, 0.6, 0.6, 0.6, 4.35, 0.3, -14.3, furniture);
  lampAt(4.35, 1.35, -14.3, 0.65);
  lampAt(-4.6, 1.85, -9.2, 1.7);

  // rolling ladder leaning on the right shelves
  const ladder = new THREE.Group();
  part(woodMat, 0.08, 7.4, 0.1, 0, 3.7, -0.38, ladder);
  part(woodMat, 0.08, 7.4, 0.1, 0, 3.7, 0.38, ladder);
  for (let y = 0.4; y < 7.2; y += 0.42) part(woodMat, 0.06, 0.05, 0.76, 0, y, 0, ladder);
  ladder.position.set(FACE - 1.0, 0, -21);
  ladder.rotation.z = 0.16;
  furniture.add(ladder);

  // painting in a gilt frame on the left wall (in the gap between the shelf banks)
  const paintTex = tex(paintingCanvas(palette.accent));
  const paintMat = track(new THREE.MeshStandardMaterial({ map: paintTex, roughness: 0.6 }));
  const painting = new THREE.Mesh(track(new THREE.PlaneGeometry(2.6, 1.95)), paintMat);
  painting.position.set(-FACE - 0.6, 3.1, HALL_FRONT - 15.75);
  painting.rotation.y = Math.PI / 2;
  scene.add(painting);
  addBox(giltMat, 0.12, 2.25, 2.9, -FACE - 0.68, 3.1, HALL_FRONT - 15.75);
  const paintLight = new THREE.SpotLight('#ffe2b8', 18, 7, 0.6, 0.6, 2);
  paintLight.position.set(-FACE + 0.6, 5, HALL_FRONT - 15.75);
  paintLight.target = painting;
  scene.add(paintLight);

  // ---------- the lectern and the ancient book that turns into SQL ----------
  const LECTERN_Z = -25;
  const LECTERN_X = -1.9;
  part(woodDarkMat, 0.5, 1.1, 0.5, LECTERN_X, 0.55, LECTERN_Z, scene);
  const top = part(woodMat, 1.0, 0.06, 0.75, LECTERN_X, 1.15, LECTERN_Z, scene);
  top.rotation.x = 0.35;
  const pageTex = tex(paperCanvas());
  const pageMat = track(new THREE.MeshStandardMaterial({ map: pageTex, roughness: 0.85, side: THREE.DoubleSide }));
  const pageGeo = track(new THREE.PlaneGeometry(0.42, 0.56));
  for (const s of [-1, 1]) {
    const pg = new THREE.Mesh(pageGeo, pageMat);
    pg.position.set(LECTERN_X + s * 0.22, 1.22, LECTERN_Z + 0.02);
    pg.rotation.set(-Math.PI / 2 + 0.35, s * 0.12, 0);
    scene.add(pg);
  }
  const sqlCanvas = document.createElement('canvas');
  sqlCanvas.width = 1024;
  sqlCanvas.height = 600;
  const sqlTex = tex(sqlCanvas);
  const sqlMat = track(new THREE.MeshBasicMaterial({ map: sqlTex, transparent: true, depthWrite: false, toneMapped: false, side: THREE.DoubleSide }));
  const sqlPanel = new THREE.Mesh(track(new THREE.PlaneGeometry(2.4, 1.4)), sqlMat);
  scene.add(sqlPanel);
  const bookHalo = new THREE.Mesh(track(new THREE.PlaneGeometry(2.2, 2.2)), track(new THREE.MeshBasicMaterial({
    map: tex(glowCanvas('#ffb066')), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false,
  })));
  bookHalo.position.set(LECTERN_X, 1.5, LECTERN_Z);
  scene.add(bookHalo);

  // ---------- the door: gilt arch, two leaves that swing open, light beyond ----------
  const doorFrameMat = giltMat;
  addBox(doorFrameMat, 0.22, DOOR_H, 0.35, -DOOR_HALF - 0.11, DOOR_H / 2, DOOR_Z);
  addBox(doorFrameMat, 0.22, DOOR_H, 0.35, DOOR_HALF + 0.11, DOOR_H / 2, DOOR_Z);
  const arch = new THREE.Mesh(track(new THREE.TorusGeometry(DOOR_HALF + 0.11, 0.14, 10, 48, Math.PI)), doorFrameMat);
  arch.position.set(0, DOOR_H, DOOR_Z);
  arch.scale.set(1, 0.55, 1);
  scene.add(arch);
  const leafMat = track(new THREE.MeshStandardMaterial({ roughness: 0.55, metalness: 0.15 }));
  const leaves: THREE.Object3D[] = [];
  for (const s of [-1, 1]) {
    const hinge = new THREE.Object3D();
    hinge.position.set(s * DOOR_HALF, 0, DOOR_Z);
    const leaf = new THREE.Mesh(box, leafMat);
    leaf.scale.set(DOOR_HALF, DOOR_H, 0.14);
    leaf.position.set(-s * DOOR_HALF / 2, DOOR_H / 2, 0);
    hinge.add(leaf);
    // gilt panel mouldings
    for (const y of [1.4, 3.6]) {
      const inset = new THREE.Mesh(box, giltMat);
      inset.scale.set(DOOR_HALF * 0.62, y === 1.4 ? 1.5 : 2.4, 0.03);
      inset.position.set(-s * DOOR_HALF / 2, y === 1.4 ? 1.3 : 3.75, 0.08);
      hinge.add(inset);
    }
    scene.add(hinge);
    leaves.push(hinge);
  }
  const doorLightTex = tex(glowCanvas(palette.accent));
  const doorGlowMat = track(new THREE.MeshBasicMaterial({ map: doorLightTex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }));
  const doorGlow = new THREE.Mesh(track(new THREE.PlaneGeometry(9, 9)), doorGlowMat);
  doorGlow.position.set(0, DOOR_H / 2, DOOR_Z - 0.6);
  scene.add(doorGlow);
  const doorLight = new THREE.PointLight('#ffffff', 0, 30, 2);
  doorLight.position.set(0, 3, DOOR_Z + 1.5);
  scene.add(doorLight);

  // magic ring of sparks around the door frame
  const sparkCount = high ? 700 : 260;
  const sparkPos = new Float32Array(sparkCount * 3);
  const sparkSeed = new Float32Array(sparkCount);
  const rs = rand(31);
  for (let i = 0; i < sparkCount; i++) sparkSeed[i] = rs();
  const sparkGeo = track(new THREE.BufferGeometry());
  sparkGeo.setAttribute('position', new THREE.BufferAttribute(sparkPos, 3));
  const sparkMat = track(new THREE.PointsMaterial({ size: 0.06, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }));
  const sparks = new THREE.Points(sparkGeo, sparkMat);
  scene.add(sparks);

  // ---------- flying pages: torn from the shelves, streaming to the door ----------
  const pageCount = high ? 110 : 50;
  const flyingGeo = track(new THREE.PlaneGeometry(0.36, 0.48));
  const flying = new THREE.InstancedMesh(flyingGeo, pageMat, pageCount);
  disposables.push(flying);
  flying.frustumCulled = false;
  scene.add(flying);
  const rp = rand(41);
  const pagesFrom = Array.from({ length: pageCount }, () => ({
    x: (rp() < 0.5 ? -1 : 1) * (FACE - 0.3),
    y: 0.6 + rp() * 10.5,
    z: -6 - rp() * 32,
    start: rp() * 0.22,
    spin: (rp() - 0.5) * 8,
    radius: 0.6 + rp() * 1.8,
    phase: rp() * Math.PI * 2,
  }));

  // ---------- the digital world beyond the door ----------
  const world = new THREE.Group();
  scene.add(world);
  const gridTex = tex(gridCanvas(palette.accent), [30, 40]);
  const gridMat = track(new THREE.MeshBasicMaterial({ map: gridTex, transparent: true, depthWrite: false, toneMapped: false, blending: THREE.AdditiveBlending }));
  const grid = new THREE.Mesh(track(new THREE.PlaneGeometry(240, 320)), gridMat);
  grid.rotation.x = -Math.PI / 2;
  grid.position.set(0, -0.02, DOOR_Z - 162);
  world.add(grid);
  const towerWindows = tex(windowsCanvas(palette.accent), [1, 1]);
  const towerMat = track(new THREE.MeshStandardMaterial({ roughness: 0.4, metalness: 0.6, emissiveMap: towerWindows, emissiveIntensity: 1.6 }));
  const towerM: THREE.Matrix4[] = [];
  const rt = rand(51);
  for (let i = 0; i < (high ? 170 : 90); i++) {
    const side = rt() < 0.5 ? -1 : 1;
    const x = side * (7 + rt() * 70);
    const z = DOOR_Z - 14 - rt() * 140;
    const h = 4 + Math.pow(rt(), 1.6) * 42;
    const w = 1.5 + rt() * 4;
    m4.compose(new THREE.Vector3(x, h / 2, z), new THREE.Quaternion(), new THREE.Vector3(w, h, w * (0.7 + rt() * 0.6)));
    towerM.push(m4.clone());
  }
  const towers = new THREE.InstancedMesh(box, towerMat, towerM.length);
  towerM.forEach((m, i) => towers.setMatrixAt(i, m));
  disposables.push(towers);
  world.add(towers);
  // a road of light flowing from the door into the city
  const roadCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(0, 0.05, DOOR_Z - 0.5), new THREE.Vector3(0.5, 0.4, DOOR_Z - 14), new THREE.Vector3(-3, 2.2, DOOR_Z - 32),
    new THREE.Vector3(3, 5, DOOR_Z - 55), new THREE.Vector3(-2, 9, DOOR_Z - 85), new THREE.Vector3(2, 14, DOOR_Z - 130),
  ]);
  const road = new THREE.Mesh(track(new THREE.TubeGeometry(roadCurve, 200, 0.09, 8, false)), accentGlowMat);
  world.add(road);
  const road2 = new THREE.Mesh(track(new THREE.TubeGeometry(roadCurve, 200, 0.6, 12, false)),
    track(new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.08, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false })));
  world.add(road2);
  const streamCount = high ? 600 : 240;
  const streamPos = new Float32Array(streamCount * 3);
  const streamSeed = new Float32Array(streamCount);
  for (let i = 0; i < streamCount; i++) streamSeed[i] = rt();
  const streamGeo = track(new THREE.BufferGeometry());
  streamGeo.setAttribute('position', new THREE.BufferAttribute(streamPos, 3));
  const streamMat = track(new THREE.PointsMaterial({ size: 0.09, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }));
  world.add(new THREE.Points(streamGeo, streamMat));
  // holographic rings over the road
  const ringGeo = track(new THREE.TorusGeometry(2.2, 0.03, 8, 80));
  [14, 30, 52].forEach((d, i) => {
    const ring = new THREE.Mesh(ringGeo, accentGlowMat);
    const at = roadCurve.getPoint(Math.min(1, d / 130));
    ring.position.copy(at).add(new THREE.Vector3(0, 1.6, 0));
    ring.rotation.y = i * 0.3;
    world.add(ring);
  });
  // stars
  const starCount = high ? 900 : 400;
  const starPos = new Float32Array(starCount * 3);
  for (let i = 0; i < starCount; i++) {
    starPos[i * 3] = (rt() - 0.5) * 260;
    starPos[i * 3 + 1] = 10 + rt() * 90;
    starPos[i * 3 + 2] = DOOR_Z - 20 - rt() * 200;
  }
  const starGeo = track(new THREE.BufferGeometry());
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3));
  const starMat = track(new THREE.PointsMaterial({ size: 0.25, transparent: true, opacity: 0.7, depthWrite: false, toneMapped: false }));
  world.add(new THREE.Points(starGeo, starMat));
  const worldLight = new THREE.PointLight('#ffffff', 0, 80, 1.6);
  worldLight.position.set(0, 12, DOOR_Z - 40);
  world.add(worldLight);

  // holographic panels in the digital world
  const panelDefs: { kind: PanelKind; x: number; y: number; z: number; w: number; h: number; ry: number }[] = [
    { kind: 'question', x: -4.2, y: 3.6, z: DOOR_Z - 12, w: 3.6, h: 1.5, ry: 0.45 },
    { kind: 'sql', x: 4.6, y: 4.4, z: DOOR_Z - 18, w: 4.2, h: 2.7, ry: -0.45 },
    { kind: 'nlp', x: -5.2, y: 6.5, z: DOOR_Z - 26, w: 4.0, h: 2.2, ry: 0.4 },
    { kind: 'chart', x: 5.6, y: 7.2, z: DOOR_Z - 32, w: 4.2, h: 2.8, ry: -0.4 },
    { kind: 'insight', x: -1.2, y: 9.5, z: DOOR_Z - 40, w: 4.6, h: 2.3, ry: 0 },
  ];
  const panels = panelDefs.map((d) => {
    const c = document.createElement('canvas');
    c.width = 1024;
    c.height = Math.round((1024 * d.h) / d.w);
    const t = tex(c);
    const mat = track(new THREE.MeshBasicMaterial({ map: t, transparent: true, depthWrite: false, side: THREE.DoubleSide, toneMapped: false }));
    const mesh = new THREE.Mesh(track(new THREE.PlaneGeometry(d.w, d.h)), mat);
    mesh.position.set(d.x, d.y, d.z);
    mesh.rotation.y = d.ry;
    world.add(mesh);
    return { ...d, canvas: c, tex: t, mat, mesh };
  });

  // dust motes in the lamplight
  const dustCount = high ? 500 : 200;
  const dustPos = new Float32Array(dustCount * 3);
  for (let i = 0; i < dustCount; i++) {
    dustPos[i * 3] = (rt() - 0.5) * FACE * 1.8;
    dustPos[i * 3 + 1] = 0.3 + rt() * 9;
    dustPos[i * 3 + 2] = HALL_FRONT - rt() * (HALL_FRONT - DOOR_Z);
  }
  const dustGeo = track(new THREE.BufferGeometry());
  dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3));
  const dustMat = track(new THREE.PointsMaterial({ size: 0.03, transparent: true, opacity: 0.55, depthWrite: false, blending: THREE.AdditiveBlending }));
  const dust = new THREE.Points(dustGeo, dustMat);
  scene.add(dust);

  // ---------- lights ----------
  const ambient = new THREE.AmbientLight('#ffffff', 0.3);
  const hemi = new THREE.HemisphereLight('#a39a8f', '#1a0f0c', 0.3);
  scene.add(ambient, hemi);
  const fills: THREE.PointLight[] = [];
  for (const [x, z] of [[0, 4], [0, -6], [0, -18], [0, -30], [-3.5, -38], [3.5, -38]]) {
    const l = new THREE.PointLight('#ffffff', 1, 16, 2);
    l.position.set(x, CEIL - 2.2, z);
    scene.add(l);
    fills.push(l);
  }

  // ---------- post-processing ----------
  let composer: EffectComposer | null = null;
  let bloom: UnrealBloomPass | null = null;
  if (high) {
    composer = new EffectComposer(renderer);
    composer.addPass(new RenderPass(scene, camera));
    bloom = new UnrealBloomPass(new THREE.Vector2(512, 512), initial.bloom, 0.7, 0.8);
    composer.addPass(bloom);
    composer.addPass(new OutputPass());
  }

  // ---------- palette ----------
  const bookColor = new THREE.Color();
  const libBg = new THREE.Color();
  const digBg = new THREE.Color();
  const libFog = new THREE.Color();
  const digFog = new THREE.Color();
  const fog = new THREE.FogExp2('#000000', 0.03);
  scene.fog = fog;
  scene.background = new THREE.Color();

  function drawSql() {
    const g = sqlCanvas.getContext('2d')!;
    g.clearRect(0, 0, 1024, 600);
    g.font = '500 52px "JetBrains Mono", ui-monospace, monospace';
    const lines: [string, string][][] = [
      [['SELECT', 'k'], [' name, ', 'i'], ['AVG', 'f'], ['(rating)', 'i']],
      [['FROM', 'k'], [' reviews', 'i']],
      [['GROUP BY', 'k'], [' name', 'i']],
      [['ORDER BY', 'k'], [' 2 ', 'n'], ['DESC', 'k']],
    ];
    lines.forEach((line, li) => {
      let x = 60;
      for (const [t, k] of line) {
        g.fillStyle = k === 'k' ? palette.accent : k === 'f' ? '#ffcf8a' : k === 'n' ? '#a9d0ff' : '#fff4ea';
        g.shadowColor = hexA(palette.accent, 0.9);
        g.shadowBlur = 24;
        g.fillText(t, x, 120 + li * 120);
        x += g.measureText(t).width;
      }
    });
    sqlTex.needsUpdate = true;
  }

  /** Text textures depend on web fonts: redrawn on palette change and once the fonts have loaded. */
  function drawTexts() {
    panels.forEach((pn) => {
      drawPanel(pn.canvas, pn.kind, { ink: palette.panelInk, muted: palette.panelMuted, accent: palette.accent, fill: palette.panelFill });
      pn.tex.needsUpdate = true;
    });
    drawSql();
  }

  function applyPalette(next: ScenePalette) {
    palette = next;
    libBg.set(next.background);
    digBg.set(next.digitalBg);
    libFog.set(next.fog);
    digFog.set(next.digitalFog);
    renderer.toneMappingExposure = next.exposure;
    woodMat.color.set(next.wood);
    woodDarkMat.color.set(next.woodDark);
    leafMat.color.set(next.woodDark);
    giltMat.color.set(next.gilt);
    floorMat.color.set('#ffffff');
    floorMat.map = tex(planksCanvas(next.floor), [3, 9]);
    floorMat.needsUpdate = true;
    velvetMat.color.set(next.velvet);
    if (velvetMat instanceof THREE.MeshPhysicalMaterial) velvetMat.sheenColor.set(next.velvet).offsetHSL(0, 0, 0.25);
    armchairMat.color.set(next.armchair);
    if (armchairMat instanceof THREE.MeshPhysicalMaterial) armchairMat.sheenColor.set(next.armchair).offsetHSL(0, 0, 0.2);
    pillowMat.color.set(next.armchair);
    pillowAlt.color.set(next.rug[2]);
    lampGlowMat.color.set(next.lamp).multiplyScalar(2.2);
    accentGlowMat.color.set(next.accent).multiplyScalar(2.4);
    sparkMat.color.set(next.accent).lerp(new THREE.Color('#ffd28a'), 0.35);
    streamMat.color.set(next.accent).multiplyScalar(1.6);
    starMat.color.set(next.accent).lerp(new THREE.Color('#ffffff'), 0.6);
    dustMat.color.set(next.lamp);
    gridMat.map = tex(gridCanvas(next.accent), [30, 40]);
    gridMat.needsUpdate = true;
    towerMat.color.set(next.digitalBg).lerp(new THREE.Color('#20202a'), 0.5);
    towerMat.emissive.set(next.accent);
    towerMat.emissiveMap = tex(windowsCanvas(next.accent));
    towerMat.needsUpdate = true;
    (road2.material as THREE.MeshBasicMaterial).color.set(next.accent);
    doorGlowMat.map = tex(glowCanvas(next.accent));
    doorGlowMat.needsUpdate = true;
    paintMat.map = tex(paintingCanvas(next.accent));
    paintMat.needsUpdate = true;
    rugTex = tex(rugCanvas(next.rug[0], next.rug[1], next.rug[2]));
    rugMat.map = rugTex;
    rugMat.needsUpdate = true;
    ambient.intensity = next.ambient;
    hemi.intensity = next.ambient * 0.9;
    [...lamps, ...fills].forEach((l) => {
      l.color.set(next.lamp);
      l.intensity = next.lampIntensity;
    });
    fills.forEach((l) => { l.intensity = next.lampIntensity * 0.8; });
    doorLight.color.set(next.accent);
    worldLight.color.set(next.accent);
    for (let i = 0; i < bookShade.length; i++) {
      bookColor.set(next.books[Math.floor(bookShade[i] * next.books.length) % next.books.length]);
      bookColor.multiplyScalar(0.7 + ((bookShade[i] * 7.3) % 1) * 0.6);
      books.setColorAt(i, bookColor);
    }
    if (books.instanceColor) books.instanceColor.needsUpdate = true;
    if (bloom) bloom.strength = next.bloom;
    drawTexts();
  }
  applyPalette(initial);
  document.fonts?.ready.then(() => drawTexts()).catch(() => undefined);

  // ---------- camera path ----------
  const keys: { p: number; pos: [number, number, number]; look: [number, number, number] }[] = [
    { p: 0.0, pos: [0, 2.7, 10], look: [0, 3.2, -10] },
    { p: 0.14, pos: [-1.4, 2.3, 1], look: [1.5, 2.2, -12] },
    { p: 0.3, pos: [1.6, 2.0, -13], look: [-1, 1.8, -24] },
    { p: 0.44, pos: [1.2, 2.3, -20], look: [-1.9, 2.4, -25] },
    { p: 0.58, pos: [1.0, 2.8, -30], look: [0, 3.0, -42] },
    { p: 0.72, pos: [0, 2.9, -36.8], look: [0, 3.0, -50] },
    { p: 0.84, pos: [0, 3.1, -46], look: [0, 4.2, -70] },
    { p: 1.0, pos: [0, 5.2, -62], look: [0, 8.5, -100] },
  ];
  const posCurve = new THREE.CatmullRomCurve3(keys.map((k) => new THREE.Vector3(...k.pos)), false, 'centripetal');
  const lookCurve = new THREE.CatmullRomCurve3(keys.map((k) => new THREE.Vector3(...k.look)), false, 'centripetal');
  const tmpPos = new THREE.Vector3();
  const tmpLook = new THREE.Vector3();
  const tmpRoad = new THREE.Vector3();
  function cameraAt(p: number) {
    let i = 0;
    while (i < keys.length - 2 && p > keys[i + 1].p) i++;
    const u = smooth(seg(p, keys[i].p, keys[i + 1].p));
    const t = (i + u) / (keys.length - 1);
    posCurve.getPoint(t, tmpPos);
    lookCurve.getPoint(t, tmpLook);
  }

  // ---------- animation ----------
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  let target = 0;
  let current = 0;
  let running = false;
  let raf = 0;
  const clock = new THREE.Clock();
  const col = new THREE.Color();

  function frame() {
    if (!running) return;
    raf = requestAnimationFrame(frame);
    const dt = Math.min(0.05, clock.getDelta()); // also advances clock.elapsedTime
    const t = clock.elapsedTime;
    current += (target - current) * (reduced ? 1 : 1 - Math.exp(-dt * 12));
    const p = clamp01(current);

    cameraAt(p);
    camera.position.copy(tmpPos);
    if (!reduced) camera.position.y += Math.sin(t * 0.5) * 0.03;
    camera.lookAt(tmpLook);

    // ancient book → SQL
    const rise = smooth(seg(p, 0.3, 0.5));
    sqlPanel.position.set(LECTERN_X - rise * 0.4, 1.5 + rise * 2.0, LECTERN_Z - 0.2);
    sqlPanel.scale.setScalar(0.35 + rise * 0.75);
    sqlPanel.lookAt(camera.position);
    sqlMat.opacity = rise * (1 - seg(p, 0.6, 0.68));
    sqlPanel.visible = sqlMat.opacity > 0.01;
    (bookHalo.material as THREE.MeshBasicMaterial).opacity = 0.35 + seg(p, 0.25, 0.45) * 0.65 * (1 - seg(p, 0.6, 0.7));
    bookHalo.lookAt(camera.position);

    // pages lift from the shelves and stream into the doorway
    pagesFrom.forEach((s, i) => {
      const f = seg(p, 0.4 + s.start, 0.72 + s.start * 0.5);
      if (f <= 0 || f >= 1) {
        dummy.scale.setScalar(0.0001);
      } else {
        const e = smooth(f);
        const r = s.radius * (1 - e);
        const a = s.phase + e * 6 + t * 0.6;
        dummy.position.set(
          THREE.MathUtils.lerp(s.x, 0, e) + Math.cos(a) * r,
          THREE.MathUtils.lerp(s.y, DOOR_H * 0.5, e) + Math.sin(a) * r * 0.6,
          THREE.MathUtils.lerp(s.z, DOOR_Z - 0.5, e),
        );
        dummy.rotation.set(t * s.spin * 0.3 + i, t * s.spin * 0.2, Math.sin(t * 2 + i) * 0.6);
        dummy.scale.setScalar(1 - e * 0.5);
      }
      dummy.updateMatrix();
      flying.setMatrixAt(i, dummy.matrix);
    });
    flying.instanceMatrix.needsUpdate = true;

    // the doors open
    const open = smooth(seg(p, 0.6, 0.76));
    leaves[0].rotation.y = open * 1.75; // both leaves swing away from the viewer, into the light
    leaves[1].rotation.y = -open * 1.75;
    const doorPower = 0.25 + open * 0.75;
    doorGlowMat.opacity = Math.min(1, 0.2 + seg(p, 0.45, 0.7) * 0.6 + open * 0.4);
    doorGlow.scale.setScalar(0.7 + open * 1.2 + (reduced ? 0 : Math.sin(t * 1.4) * 0.03));
    doorLight.intensity = palette.accentIntensity * doorPower * 0.6;

    // sparks swirl around the frame, brighter as the camera arrives
    const sparkPower = seg(p, 0.42, 0.7) * (1 - seg(p, 0.86, 0.95));
    for (let i = 0; i < sparkCount; i++) {
      const sd = sparkSeed[i];
      const a = sd * Math.PI * 2 + t * (0.3 + sd * 0.5);
      const r = DOOR_HALF + 0.3 + Math.sin(t * 0.8 + sd * 20) * 0.35 + sd * 0.4;
      sparkPos[i * 3] = Math.cos(a) * r;
      sparkPos[i * 3 + 1] = DOOR_H * 0.55 + Math.sin(a) * r * 1.35;
      sparkPos[i * 3 + 2] = DOOR_Z + 0.4 + Math.sin(sd * 40 + t) * 0.4;
    }
    sparkGeo.attributes.position.needsUpdate = true;
    sparkMat.opacity = sparkPower;
    sparks.visible = sparkPower > 0.01;

    // the digital world wakes up as the doors open
    const w = smooth(seg(p, 0.68, 0.86));
    world.visible = open > 0.02;
    col.copy(libBg).lerp(digBg, w);
    (scene.background as THREE.Color).copy(col);
    fog.color.copy(libFog).lerp(digFog, w);
    fog.density = THREE.MathUtils.lerp(palette.fogDensity, 0.012, w);
    worldLight.intensity = palette.accentIntensity * 0.9 * w;
    gridMat.opacity = 0.35 + w * 0.65;
    if (world.visible) {
      for (let i = 0; i < streamCount; i++) {
        const u = (streamSeed[i] + t * 0.04) % 1;
        roadCurve.getPoint(u, tmpRoad);
        const jitter = Math.sin(streamSeed[i] * 50) * 0.5;
        streamPos[i * 3] = tmpRoad.x + jitter;
        streamPos[i * 3 + 1] = tmpRoad.y + 0.3 + Math.cos(streamSeed[i] * 70) * 0.4;
        streamPos[i * 3 + 2] = tmpRoad.z;
      }
      streamGeo.attributes.position.needsUpdate = true;
      panels.forEach((pn, i) => {
        const dz = pn.z - camera.position.z;
        const vis = dz < 0 ? clamp01((40 + dz) / 18) : clamp01(1 - dz / 4);
        pn.mat.opacity = vis * w * 0.95;
        pn.mesh.position.y = pn.y + (reduced ? 0 : Math.sin(t * 0.7 + i * 1.3) * 0.12);
      });
    }
    if (!reduced) {
      dust.position.y = Math.sin(t * 0.2) * 0.15;
      dust.rotation.y = Math.sin(t * 0.05) * 0.02;
    }

    if (composer) composer.render();
    else renderer.render(scene, camera);
  }

  return {
    setProgress(p) {
      target = p;
    },
    setPalette(p) {
      applyPalette(p);
    },
    resize(width, height) {
      renderer.setSize(width, height, false);
      camera.aspect = width / Math.max(1, height);
      camera.fov = camera.aspect < 0.8 ? 70 : 55;
      camera.updateProjectionMatrix();
      composer?.setSize(width, height);
    },
    start() {
      if (running) return;
      running = true;
      clock.start();
      raf = requestAnimationFrame(frame);
    },
    stop() {
      running = false;
      cancelAnimationFrame(raf);
    },
    dispose() {
      running = false;
      cancelAnimationFrame(raf);
      disposables.forEach((d) => d.dispose());
      composer?.dispose();
      renderer.dispose();
    },
  };
}
