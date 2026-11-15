# Evidencia de Medición: SCRUM-35 [W-RQ-02] Desempeño en Frontend Angular

* **Fecha de Ejecución:** 15 de Noviembre de 2026
* **Entorno de Prueba:** Windows 11 x64, Node.js v24.14.1, Angular 17+ Standalone Components, Vitest / JSDOM.
* **Dataset:** `backend/grafo-service/data/grafo_400_paradas.json` (400 paradas, 1.100 tramos).

---

## 1. Tabla Resumen de Criterios de Aceptación

| Criterio W-RQ-02 | Umbral Exigido | Medición Obtenida | Margen de Cumplimiento | Veredicto |
| :--- | :---: | :---: | :---: | :---: |
| **Tiempo de Dibujo Inicial (SVG DOM)** | $< 2.000\text{ ms}$ | **702.81 ms** | **64.8% por debajo del umbral** | **APROBADO** |
| **Crecimiento en Función de Tramos** | Lineal $O(M)$ | **10.96x tiempo para 12.22x tramos** | Relación $\approx 0.897$ ($O(M)$ comprobado) | **APROBADO** |
| **Tareas Síncronas Hilo Principal** | $< 100\text{ ms}$ | **36.51 ms** | **63.5% por debajo del umbral** | **APROBADO** |

---

## 2. Datos de Complejidad Temporal (Spatial Hash Grid)
Extraído de `docs/mediciones/w_rq02_desempeno_frontend.csv`:

```csv
Paradas,Tramos,TiempoPromedioMs,TiempoMaximoMs,ComplejidadObservada,CumpleWRQ02
40,90,0.1160,0.6520,O(M) Lineal,SI
100,250,0.1990,0.4940,O(M) Lineal,SI
200,500,0.3840,0.9570,O(M) Lineal,SI
300,800,0.7490,1.2370,O(M) Lineal,SI
400,1100,1.2710,1.9340,O(M) Lineal,SI
```

---

## 3. Comandos de Reproducción
Para verificar y reproducir las mediciones en cualquier momento:

```powershell
# Ejecución completa del benchmark y suite de pruebas unitarias
powershell -ExecutionPolicy Bypass -File scripts/medir_rq02_frontend.ps1
```
