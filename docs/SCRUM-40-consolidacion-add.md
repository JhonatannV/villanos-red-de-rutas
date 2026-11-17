# Consolidación del Documento de Arquitectura ADD y Bitácora · SCRUM-40

**Proyecto Integrador** · Semestre VI · UCEVA  
**Materias:** Programación en Ambiente Web 2 · Arquitectura de Computadores  
**Historia:** SCRUM-40 (`[DOC-01] Documento de diseno ADD completo y Bitacora consolidada`)  
**Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`)  
**Fecha:** 15 de noviembre de 2026  

---

## 1. Resumen Ejecutivo de la Entrega

En el marco del **Sprint 5 ("Medición y Deploy")**, se culminó exitosamente la tarea **SCRUM-40**, correspondiente a la consolidación metodológica integral del **Documento de Diseño de Arquitectura basado en Atributos (ADD - *Attribute-Driven Design*)**, la **Bitácora de Decisiones de Arquitectura** y el **README de Arranque** del sistema **Red de Rutas**.

Siguiendo rigurosamente el estándar formal de cinco pasos propuesto por Bass, Clements y Kazman (*Software Architecture in Practice*), el diseño del sistema quedó plenamente documentado, vinculando cada requerimiento de negocio y calidad con su respectiva táctica arquitectónica, módulo de software, contrato de interfaz y medición empírica en laboratorio.

---

## 2. Artefactos Entregados y Consolidados

1. **[Documento de Diseño de Arquitectura (ADD)](ADD_Diseño_Arquitectura.md):**
   - **Paso 1 (Revisar Entradas):** Propósito formal del sistema, 9 Requisitos Funcionales primarios (RF-01 a RF-09), Requisitos Nativos de C (RN-01 a RN-06), Requisitos de Seguridad (RA-01 a RA-07), 8 Restricciones innegociables (CON-01 a CON-08), 6 Escenarios de Atributos de Calidad formalizados en el estándar canónico de seis partes (W-RQ-01, W-RQ-02, W-RQ-03, A-RQ-01, A-RQ-02, A-RQ-03/DEPLOY-01) y 6 Preocupaciones arquitectónicas (CRN-01 a CRN-06).
   - **Paso 2 (Establecer Objetivos de Iteración):** Planificación de las 5 iteraciones del ciclo de vida del proyecto, asignando drivers prioritarios a cada subsistema.
   - **Paso 3 (Identificar Conceptos de Diseño):** Catálogo formal de tácticas de Disponibilidad (*Timeout*, *Circuit Breaker 503*, *Fallback* a Java, separación de prefijos), Desempeño (tres arreglos paralelos `int[]` sin serialización, montículo binario $O(E \log V)$, lista de adyacencia compacta, Spatial Hash Grid $O(M)$ en SVG, Connection Pooling en Gateway), Seguridad (Keycloak OIDC, PKCE en Angular SPA, tokens JWT firmados RS256, roles RBAC, auditoría `sub`), Usabilidad (línea de tiempo algorítmica reversible paso a paso, visualizador vectorial sin colisiones) y Desplegabilidad (imágenes multi-stage ligeras, límites de CPU/RAM).
   - **Paso 4 (Instanciar Elementos y Responsabilidades):** Diagrama de arquitectura física y lógica, catálogo de componentes (`api-gateway`, `grafo-service`, `ruta-service`, `libgrafo.so`, `red-rutas-web`, `keycloak`) y matriz de responsabilidades.
   - **Paso 5 (Definir Interfaces y Registrar Decisiones):** Contratos REST de ambos microservicios, contrato binario JNA en C (`grafo.h`), contrato de tokens JWT, matriz completa de decisiones arquitectónicas D-01 a D-16 y cuadro de verificación empírica de calidad.

2. **[Bitácora de Decisiones de Arquitectura](Bitacora_Decisiones.md):**
   - Actualización del índice maestro con las 16 decisiones de arquitectura tomadas, justificadas y medidas (D-01 a D-16).
   - Cierre formal y fundamentado de todas las "Decisiones Abiertas" iniciales (traza paso a paso en Java, carga de `.so` con `jna.library.path`, red consultada con Connection Pooling, orquestación dual Docker Compose/K8s con cuotas, y autorización multinivel Gateway/microservicios con `sub`).

3. **[README de Arranque](../../README.md):**
   - Documento maestro de bienvenida y puesta en marcha del proyecto en la raíz del repositorio.
   - Instrucciones precisas de arranque con un único comando (`docker compose up -d` y `kubectl apply -k k8s/`).
   - Tabla de credenciales preconfiguradas (`planificador` y `consultor`).
   - Mapeo de puertos y URLs de acceso al frontend, gateway y Keycloak.
   - Guía paso a paso para reproducir las suites de pruebas unitarias (C y Angular) y los benchmarks de calidad (W-RQ-01, W-RQ-02, W-RQ-03, A-RQ-02, A-RQ-03).

---

## 3. Matriz de Cumplimiento de Criterios de Calidad

| Escenario | Descripción | Meta / Criterio | Valor Medido | Estado |
|---|---|---|:---:|:---:|
| **W-RQ-01** | Disponibilidad ante caída de `ruta-service` | Corte Gateway a $2.0\text{ s}$ con 503; recuperación $< 30\text{ s}$ | **2.01 s** y **10.2 s** (SCRUM-32) | ✅ CUMPLE |
| **W-RQ-02** | Renderizado red 400x1100 en Angular | Dibujo $< 2.000\text{ ms}$; crecimiento lineal; tareas $< 100\text{ ms}$ | **702.81 ms**, $O(M)$ en **1.27 ms**, max **36.51 ms** | ✅ CUMPLE |
| **W-RQ-03** | Latencia Gateway en red 400x1100 | $p95 < 400\text{ ms}$ en 20 consultas; ratio traza $< 2.0\times$ | **$p95 = 77.14\text{ ms}$**, ratio **1.07x** | ✅ CUMPLE |
| **A-RQ-01** | Eficiencia algorítmica Dijkstra en C | Montículo binario $O(E \log V)$; $p95 < 400\text{ ms}$; paridad | C **42,9 µs**, JNA **59,7 µs**, Java **95,3 µs** (SCRUM-37); paridad 1.000/1.000 | ✅ CUMPLE |
| **A-RQ-02** | Testabilidad y sanidad de memoria en C | Suite desacoplada $< 30\text{ s}$; 0 fugas Valgrind | **38 pruebas en 0.30 s**; **0 leaks / 0 errors** | ✅ CUMPLE |
| **A-RQ-03** | Despliegue orquestado con límites de recursos | Arranque $< 90\text{ s}$; frontend $< 250\text{ MB}$; límites CPU/RAM | Arranque en **32.8 s**; imagen **28.4 MB**; límites 5.5 CPU/2.75 GB | ✅ CUMPLE |
