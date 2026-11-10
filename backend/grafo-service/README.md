# grafo-service (Spring Boot)
**Responsable:** Jhonatan Varón Henao

## Propósito
Microservicio dueño de los datos de la red de transporte.
- Almacenamiento y CRUD de paradas (id, nombre, x, y) y tramos (origen, destino, peso, estado).
- Auditoría forense: persiste la marca de tiempo y el claim `sub` del usuario autenticado en cada mutación.
- Autorización RBAC: solo el rol `planificador` puede crear/editar/eliminar paradas y tramos.
- No contiene lógica algorítmica de cálculo de rutas.
