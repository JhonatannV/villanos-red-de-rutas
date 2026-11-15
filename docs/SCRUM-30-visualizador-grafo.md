# SCRUM-30: Visualizador del Grafo con Paneo y Zoom (Canvas / SVG)

## 1. Información General del Requerimiento
* **ID Jira:** `SCRUM-30`
* **Código de Requerimiento:** `RF-02` (Visualización del Grafo de Transporte)
* **Sprint:** Sprint 4 - Frontend Angular (Standalone Components & Signals)
* **Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`)
* **Rama Git:** `feature/jhonatan-scrum-30-visualizador-grafo`

---

## 2. Descripción del Problema y Objetivos
El Requerimiento Funcional 02 (RF-02) exige la renderización gráfica e interactiva de la red de transporte en la interfaz web:
1. **Representación Visual de la Red:** Representación de paradas como nodos (círculos SVG con su identificador y nombre) y tramos como aristas (líneas vectoriales dirigidas/no dirigidas).
2. **Capacidades de Navegación Espacial:** Soporte completo de **Paneo** (arrastre con ratón sobre el lienzo) y **Zoom** (acercar, alejar y restablecer vista mediante controles en pantalla y rueda del ratón `wheel`).
3. **Legibilidad y Desplazamiento Anticolisión de Etiquetas de Peso:** En redes de alta densidad (p. ej., 40 paradas y 90 tramos), las etiquetas que indican el peso o distancia del tramo tienden a superponerse en vértices compartidos o tramos adyacentes. El requerimiento exige garantizar que no exista solapamiento visual entre etiquetas contiguas mediante un algoritmo de desplazamiento espacial.

---

## 3. Arquitectura y Solución Técnica

### 3.1. Selección Tecnológica: SVG Reactivo sobre HTML5 Canvas
Se implementó mediante gráficos vectoriales escalables (**SVG**) dentro de una plantilla de Angular Standalone Component:
* **Ventajas sobre Canvas nativo:**
  * Renderizado declarativo con enlace directo a reactividad basada en Angular Signals.
  * Escalabilidad matemática infinita sin pixelación ni pérdida de nitidez en zooms elevados.
  * Inspección nativa del DOM y estilos CSS modulares para efectos hover y animaciones.
  * Mapeo directo de coordenadas del grafo al espacio de usuario SVG mediante el atributo reactivo `viewBox`.

### 3.2. Espacio de Coordenadas y Paneo/Zoom Reactivo
El componente `GrafoVisorComponent` mantiene el estado de navegación en señales reactivas:
* `zoom`: Factor multiplicador de escala (restringido en el rango $[0.3, 4.0]$ con incrementos de $\pm 0.2$).
* `panX`, `panY`: Desplazamientos espaciales acumulados por el usuario.
* `viewBox`: Señal computada (`computed`) que proyecta el tamaño base del canvas ($1000 \times 666.7$) transformado por el nivel de zoom y paneo:
  $$\text{viewBox} = ( -panX \cdot \text{scale}, -panY \cdot \text{scale}, W \cdot \text{scale}, H \cdot \text{scale} )$$
  donde $\text{scale} = 1 / \text{zoom}$.

### 3.3. Algoritmo Anticolisión de Etiquetas de Peso
Para satisfacer el criterio de no superposición visual de etiquetas:
1. Cada tramo $(u, v)$ calcula su punto medio:
   $$M = \left( \frac{x_u + x_v}{2}, \frac{y_u + y_v}{2} \right)$$
2. Se extrae el vector director normalizado $\vec{u} = \frac{v - u}{\|v - u\|}$ y su normal perpendicular $\vec{n} = (-u_y, u_x)$.
3. Se aplica un desplazamiento inicial perpendicular $\vec{n} \cdot 10$ para distanciar la etiqueta de la línea del tramo.
4. Se ejecuta un bucle iterativo de resolución de colisiones: si la distancia euclidiana entre dos etiquetas $i$ y $j$ es menor a $14\text{px}$, se calcula la fuerza de repulsión relativa y se desplazan a lo largo de la dirección longitudinal de sus aristas y perpendiculares hasta garantizar una distancia mínima $\ge 16\text{px}$.

---

## 4. Archivos Implementados y Modificados
1. `frontend/red-rutas-web/src/app/features/grafo-visor/grafo-visor.component.ts`:
   * Lógica del visualizador, control de eventos del ratón (`onMouseDown`, `onMouseMove`, `onMouseUp`, `onWheel`), métodos de zoom y algoritmo anticolisión `etiquetasPeso()`.
2. `frontend/red-rutas-web/src/app/features/grafo-visor/grafo-visor.component.html`:
   * Marcado SVG con definición de marcadores de punta de flecha (`marker#arrow`), capas vectoriales de tramos (`<line>`), cápsulas para etiquetas con fondo blanco (`<rect>` y `<text>`), y nodos (`<circle>` y `<text>`).
3. `frontend/red-rutas-web/src/app/features/grafo-visor/grafo-visor.component.css`:
   * Estilos modernos del canvas: rejilla de fondo, cursores dinámicos (`grab`, `grabbing`), barra flotante de controles y badges informativos.
4. `frontend/red-rutas-web/src/app/features/grafo-visor/grafo-visor.component.spec.ts`:
   * 12 pruebas unitarias automatizadas que validan:
     * Renderizado de nodos y aristas con datos reactivos.
     * Restricciones de zoom (mínimo 0.3x, máximo 4.0x).
     * Paneo mediante interacción con el ratón.
     * Restablecimiento a vista por defecto (zoom 1.0x, offset 0, 0).
     * Verificación matemática de distancia euclidiana mínima entre todas las etiquetas ($\ge 14\text{px}$).
5. `frontend/red-rutas-web/src/app/app.ts` y `app.html`:
   * Inclusión de `<app-grafo-visor>` dentro del layout principal del frontend.

---

## 5. Resultados de Pruebas y Compilación
* **Suite de Pruebas de Angular (`ng test --watch=false`):**
  * Total de archivos de prueba: 7
  * Total de pruebas unitarias: 32 pruebas pasando exitosamente (0 errores, 0 fallos).
* **Compilación de Producción (`npm run build`):**
  * Salida limpia en `dist/red-rutas-web/browser/` sin advertencias de tipos.
