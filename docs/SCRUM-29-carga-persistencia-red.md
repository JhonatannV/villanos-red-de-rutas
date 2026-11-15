# SCRUM-29 [RF-01] Carga, Creación y Persistencia de la Red de Transporte

**Sprint:** Sprint 4 (Frontend en Angular)  
**Ticket Jira:** [SCRUM-29](https://villanos.atlassian.net/browse/SCRUM-29)  
**Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`) - DevSecOps, Ciberseguridad & Backend de Datos  
**Equipo:** Villanos · Proyecto Integrador UCEVA (Semestre 6)

---

## 1. Objetivo del Incremento

Construir la pantalla y los servicios en **Angular 17+** (utilizando componentes *Standalone* y reactividad con *Signals*) para:
1. Cargar la red de transporte completa desde `grafo-service` a través del API Gateway (`GET /api/redes/1`).
2. Crear nuevas paradas indicando nombre y coordenadas espaciales ($x, y$) mediante `POST /api/redes/1/paradas`.
3. Crear nuevos tramos entre paradas con peso estrictamente positivo ($> 0$ minutos) mediante `POST /api/redes/1/tramos`.
4. Habilitar la modificación en vivo del estado vial de los tramos (abierto / cerrado) mediante `PUT /api/redes/1/tramos/{id}/estado?abierto={boolean}`.
5. Garantizar la persistencia integral de la red de transporte de 40 paradas y 90 tramos.

---

## 2. Criterio de Aceptación

> **Criterio de Aceptación Jira [RF-01]:**  
> *"Se carga red de 40 paradas y 90 tramos, se recarga el navegador y aparece idéntica en posiciones y pesos."*

---

## 3. Arquitectura y Componentes Desarrollados

### 3.1 Modelos de Datos (`src/app/core/models/red.model.ts`)
- **`Parada`**: `id: number`, `nombre: string`, `x: number`, `y: number`.
- **`Tramo`**: `id: number`, `origen: number`, `destino: number`, `peso: number`, `abierto: boolean`.
- **`RedGrafo`**: `redId: number`, `nombre: string`, `paradas: Parada[]`, `tramos: Tramo[]`.

### 3.2 Servicio Reactivo de Red (`RedService`)
Ubicado en `src/app/core/services/red.service.ts`:
- **Señales Reactivas (`Signals`):**
  - `redActual`: estado global inmutable de la red.
  - `paradas`: lista reactiva de estaciones.
  - `tramos`: lista reactiva de aristas y vías.
  - `totalParadas` y `totalTramos`: señales calculadas (`computed`) automáticas.
  - `tramosAbiertos` y `tramosCerrados`: conteo en tiempo real según estado vial.
- **Métodos Principales:**
  - `cargarRed(redId = 1)`: Realiza `GET /api/redes/1`, sincroniza signals y respalda en almacenamiento persistente seguro.
  - `crearParada(parada)`: Envía `POST /api/redes/1/paradas` y actualiza la lista reactiva.
  - `crearTramo(origen, destino, peso)`: Valida que el peso sea estrictamente mayor a 0 y que origen $\neq$ destino antes de enviar `POST /api/redes/1/tramos`.
  - `cambiarEstadoTramo(tramoId, abierto)`: Envía `PUT /api/redes/1/tramos/{id}/estado` y actualiza el tramo en memoria.

### 3.3 Componente Gestor de Red (`RedGestorComponent`)
Ubicado en `src/app/features/red-gestor/`:
- **Panel de Métricas:** Tarjetas visuales de paradas totales (40), tramos totales (90), tramos abiertos y cerrados.
- **Formularios Reactivos Protegidos:**
  - Creación de paradas y tramos con validación de entradas.
  - Control de acceso por roles: si el usuario es `consultor`, se le informa que está en modo solo lectura y los botones se deshabilitan. Si es `planificador` o `admin`, los controles están completamente habilitados.
- **Tablas Dinámicas con Filtro:**
  - Pestaña de paradas con buscador reactivo por nombre o ID.
  - Pestaña de tramos con visualización de peso, paradas involucradas y botón toggle para cerrar o reabrir vías.

---

## 4. Pruebas Automatizadas y Validación

Se ejecutaron las pruebas unitarias e integradas con Vitest:
1. **Carga inicial:** Validación de carga de la red con exactamente 40 paradas y 90 tramos.
2. **Creación de parada:** Verificación de envío `POST` y actualización reactiva a 41 paradas.
3. **Validación de pesos:** Confirmación de rechazo inmediato de tramos con peso $\le 0$ y paradas origen/destino coincidentes.
4. **Modificación de tramos:** Verificación de llamada `PUT` y actualización de estado vial.
5. **Criterio RF-01 de Paridad:** Simulación de recarga de red verificando que cada una de las 40 paradas conserva exactamente sus coordenadas $(x, y)$ y cada uno de los 90 tramos mantiene exactamente su peso en minutos.

### Resultado de la Suite de Pruebas:
```text
 ✓  red-rutas-web  src/app/core/auth/auth.guard.spec.ts (3 tests)
 ✓  red-rutas-web  src/app/core/auth/auth.service.spec.ts (5 tests)
 ✓  red-rutas-web  src/app/core/services/red.service.spec.ts (8 tests)
 ✓  red-rutas-web  src/app/core/auth/auth.interceptor.spec.ts (5 tests)
 ✓  red-rutas-web  src/app/features/red-gestor/red-gestor.component.spec.ts (2 tests)
 ✓  red-rutas-web  src/app/app.spec.ts (4 tests)

 Test Files  6 passed (6)
      Tests  27 passed (27)
   Duration  4.76s
```
Total: **27 pruebas ejecutadas, 27 exitosas (0 fallos)**.  
Compilación de producción con `ng build`: **100% exitosa**.
