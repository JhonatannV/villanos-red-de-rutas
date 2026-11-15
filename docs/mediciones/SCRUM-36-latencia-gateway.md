# Evidencia de Medición: SCRUM-36 [W-RQ-03] Latencia de Consulta en API Gateway

* **Fecha de Ejecución:** 15 de Noviembre de 2026
* **Entorno de Prueba:** Windows 11 x64, Spring Cloud Gateway, Apache HttpClient 5 Connection Pool, Node.js v24.14.1.
* **Dataset:** Red metropolitana de 400 paradas y 1.100 tramos (`redId=2`).

---

## 1. Resumen de Criterios de Aceptación

| Criterio W-RQ-03 | Umbral Requerido | Medición Obtenida | Veredicto |
| :--- | :---: | :---: | :---: |
| **Latencia Percentil 95 (p95)** | $< 400.0\text{ ms}$ | **65.73 ms** (83.6% por debajo) | **APROBADO** |
| **Sobrecosto de Traza Paso a Paso** | Ratio $< 2.0\times$ | **1.00x** (sin sobrecosto apreciable) | **APROBADO** |
| **Tasa de Éxito en Ráfaga de 20 Consultas** | 100 % HTTP 200 | **20/20 exitosas (100 %)** | **APROBADO** |

---

## 2. Registro de las 20 Consultas Consecutivas
Extraído de `docs/mediciones/w_rq03_latencia_gateway.csv`:

```csv
NumeroPeticion,Origen,Destino,LatenciaRutaMs,LatenciaConTrazaMs,RatioTraza,StatusHttp,CostoTotal,NodosCamino,CumpleLimite400ms
1,0,399,65.73,6.27,0.10,200,120,38,SI
2,10,385,4.76,3.17,0.67,200,69,24,SI
3,25,370,3.05,3.41,1.12,200,75,26,SI
4,40,355,2.59,3.01,1.16,200,88,29,SI
5,65,340,4.13,3.82,0.92,200,68,23,SI
6,80,325,1.55,1.56,1.01,200,54,19,SI
7,100,310,1.43,1.36,0.95,200,60,21,SI
8,120,295,1.14,1.29,1.13,200,72,25,SI
9,145,280,1.10,1.80,1.64,200,41,15,SI
10,160,265,1.95,1.30,0.67,200,34,12,SI
11,180,250,1.44,1.92,1.33,200,48,17,SI
12,200,235,1.22,1.48,1.21,200,61,21,SI
13,220,220,0.95,1.06,1.12,200,0,1,SI
14,240,205,0.94,0.89,0.94,200,22,8,SI
15,260,190,1.10,1.08,0.99,200,47,16,SI
16,280,175,1.87,1.19,0.64,200,69,24,SI
17,300,160,0.96,0.90,0.94,200,31,11,SI
18,320,145,0.89,0.99,1.12,200,53,18,SI
19,350,100,1.13,1.36,1.20,200,84,28,SI
20,380,20,1.02,1.06,1.05,200,89,30,SI
```

---

## 3. Comandos de Reproducción
```powershell
powershell -ExecutionPolicy Bypass -File scripts/medir_rq03_gateway.ps1
```
