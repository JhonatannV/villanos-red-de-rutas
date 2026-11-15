/**
 * Generador determinista de red grande: 400 paradas y 1.100 tramos.
 * Cumple con el escenario W-RQ-02 (Desempeño Web en Angular).
 */
const fs = require('fs');
const path = require('path');

// Generador pseudoaleatorio LCG con semilla fija
let seed = 20261115;
function random() {
  seed = (seed * 1664525 + 1013904223) % 4294967296;
  return seed / 4294967296;
}
function randInt(min, max) {
  return Math.floor(random() * (max - min + 1)) + min;
}

const BARRIOS = [
  "Terminal", "Centro", "UCEVA Campus", "SENA Agropecuario", "Hospital Tomás Uribe",
  "Parque Céspedes", "Plaza Cívica", "Estadio 12 de Octubre", "Galería Central", "Aeropuerto Heraguas",
  "Barrio Victoria", "Barrio Salesianos", "Barrio Fátima", "Barrio Farfán", "Barrio Sajonia",
  "Barrio Alvernia", "Barrio Alameda", "Barrio Maracaibo", "Barrio Rubén Cruz", "Barrio San Antonio",
  "Barrio Guaduales", "Glorieta Las Flores", "Barrio El Príncipe", "Barrio Lombardía", "Barrio Chiminangos",
  "Barrio La Esperanza", "Coliseo Benicio", "Zona Franca", "Cruce Variante", "Clínica San Francisco",
  "Polideportivo Sur", "Plaza de Mercado", "Puente Río Tuluá", "C.C. Herradura", "C.C. Tuluá Plaza",
  "Estación Ferrocarril", "Parque Boyacá", "Barrio Las Américas", "Barrio Nuevo Horizonte", "Ciudad Campestre"
];

// 1. Generar 400 paradas en rejilla espacial 20x20 con varianza geográfica
const paradas = [];
const COLS = 20;
const ROWS = 20;

for (let i = 0; i < 400; i++) {
  const col = i % COLS;
  const row = Math.floor(i / COLS);
  
  // Coordenadas en canvas espacial (80 a 2400 en X, 80 a 1600 en Y)
  const baseX = 80 + col * 115;
  const baseY = 80 + row * 76;
  const jitterX = randInt(-12, 12);
  const jitterY = randInt(-10, 10);
  
  const barrioBase = BARRIOS[i % BARRIOS.length];
  const sectorNum = Math.floor(i / BARRIOS.length) + 1;
  const nombre = `${barrioBase} - Sector ${sectorNum} (E-${i.toString().padStart(3, '0')})`;

  paradas.push({
    id: i,
    nombre: nombre,
    x: baseX + jitterX,
    y: baseY + jitterY
  });
}

// 2. Generar 1.100 tramos conectados
const tramos = [];
const edgeSet = new Set();
let tramoId = 1;

function agregarTramo(u, v, peso) {
  if (tramos.length >= 1100) return false;
  if (u === v) return false;
  const key = `${u}->${v}`;
  if (edgeSet.has(key)) return false;
  edgeSet.add(key);

  tramos.push({
    id: tramoId++,
    origen: u,
    destino: v,
    peso: peso || randInt(2, 12),
    abierto: true
  });
  return true;
}

// 2.1 Conexiones de malla ortogonal (garantiza conexidad total)
// Horizontales
for (let r = 0; r < ROWS; r++) {
  for (let c = 0; c < COLS - 1; c++) {
    const u = r * COLS + c;
    const v = r * COLS + (c + 1);
    agregarTramo(u, v, randInt(2, 6));
    if (tramos.length < 1100 && random() > 0.3) {
      agregarTramo(v, u, randInt(2, 6));
    }
  }
}

// Verticales
for (let c = 0; c < COLS; c++) {
  for (let r = 0; r < ROWS - 1; r++) {
    const u = r * COLS + c;
    const v = (r + 1) * COLS + c;
    agregarTramo(u, v, randInt(2, 7));
    if (tramos.length < 1100 && random() > 0.3) {
      agregarTramo(v, u, randInt(2, 7));
    }
  }
}

// 2.2 Conexiones diagonales y arterias exprés hasta completar exactamente 1.100 tramos
let attempts = 0;
while (tramos.length < 1100 && attempts < 100000) {
  attempts++;
  const u = randInt(0, 399);
  // Buscar un nodo espacialmente cercano (radio de 1 a 3 celdas)
  const uCol = u % COLS;
  const uRow = Math.floor(u / COLS);
  
  const deltaC = randInt(-2, 2);
  const deltaR = randInt(-2, 2);
  const vCol = uCol + deltaC;
  const vRow = uRow + deltaR;
  
  if (vCol >= 0 && vCol < COLS && vRow >= 0 && vRow < ROWS) {
    const v = vRow * COLS + vCol;
    if (u !== v) {
      const distManhattan = Math.abs(deltaC) + Math.abs(deltaR);
      const peso = Math.max(2, distManhattan * 3 + randInt(-1, 2));
      agregarTramo(u, v, peso);
    }
  }
}

// Si aún faltara alguno para 1100 exactos:
while (tramos.length < 1100) {
  const u = randInt(0, 399);
  const v = randInt(0, 399);
  agregarTramo(u, v, randInt(3, 15));
}

console.log(`Generación completada:`);
console.log(`- Paradas: ${paradas.length}`);
console.log(`- Tramos: ${tramos.length}`);
console.log(`- Primer tramo:`, tramos[0]);
console.log(`- Último tramo:`, tramos[tramos.length - 1]);

const redGrafo = {
  redId: 2,
  nombre: "Red Metropolitana de Transporte - Escenario W-RQ-02 (400 Paradas, 1.100 Tramos)",
  paradas: paradas,
  tramos: tramos
};

// Guardar en backend/grafo-service/data/grafo_400_paradas.json
const backendPath = path.join(__dirname, '..', 'backend', 'grafo-service', 'data', 'grafo_400_paradas.json');
fs.writeFileSync(backendPath, JSON.stringify(redGrafo, null, 2), 'utf8');
console.log(`Guardado en: ${backendPath}`);

// Guardar en frontend/red-rutas-web/src/app/core/models/red-400.data.ts
const frontendTsPath = path.join(__dirname, '..', 'frontend', 'red-rutas-web', 'src', 'app', 'core', 'models', 'red-400.data.ts');
const tsContent = `// Dataset predeterminado para pruebas de desempeño W-RQ-02 (400 paradas, 1.100 tramos)
import { RedGrafo } from './red.model';

export const RED_GRANDE_400: RedGrafo = ${JSON.stringify(redGrafo, null, 2)};
`;
fs.writeFileSync(frontendTsPath, tsContent, 'utf8');
console.log(`Guardado en: ${frontendTsPath}`);
