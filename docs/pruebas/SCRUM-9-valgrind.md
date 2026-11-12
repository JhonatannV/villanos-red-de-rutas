# SCRUM-9 [RN-05] · Validación de memoria con Valgrind

**Criterio de aceptación:** ejecutar la suite de pruebas con Valgrind en Linux, sin fugas de memoria (0 memory leaks) ni accesos inválidos.

**Resultado:** ✅ **0 errores y 0 bytes perdidos** en las dos ejecuciones (`pruebas` y `main`). Salida completa: [`scrum9_valgrind.txt`](scrum9_valgrind.txt).

```
pruebas  (38 de 38 pruebas pasaron)
==25==   total heap usage: 111,505 allocs, 111,505 frees, 628,959,421 bytes allocated
==25== All heap blocks were freed -- no leaks are possible
==25== ERROR SUMMARY: 0 errors from 0 contexts (suppressed: 0 from 0)

main  (2 de 2 casos correctos)
==26==   total heap usage: 17 allocs, 17 frees, 5,220 bytes allocated
==26== All heap blocks were freed -- no leaks are possible
==26== ERROR SUMMARY: 0 errors from 0 contexts (suppressed: 0 from 0)
```

Cada `malloc` que hace `dijkstra()` tiene su `free`: más de 111.000 reservas en la suite y el mismo número de liberaciones.

## Qué corre dentro de Valgrind

- **Las 38 pruebas de la suite:** SCRUM-7 (destino inalcanzable, incluida la medición con 10.000 consultas en una red de 400 paradas), SCRUM-8 (casos degenerados y errores) y una prueba nueva de SCRUM-9.
- **La prueba nueva, "arreglos del tamaño exacto":** consulta **todas las parejas** de la red de 40 paradas (1.600 consultas) con todo abierto y otras 1.600 con el tramo 90 cerrado. La entrada (`origen`, `destino`, `peso`) y la salida (`camino_salida` de 40 enteros, `costo_salida` de 1) se reservan con `malloc` del tamaño **justo**, como lo hace Java con `new int[numParadas]` e `int[1]`. Si `dijkstra()` leyera o escribiera una sola posición de más, Valgrind lo reportaría. Con el tramo 90 cerrado salen 1.378 consultas con ruta (37 × 37 + 3 × 3), las demás dan `-1`.
- **`main`:** los grafos de referencia de 5 y 7 paradas.

Valgrind se ejecuta con `--leak-check=full --show-leak-kinds=all --errors-for-leak-kinds=all --error-exitcode=1`. Así, cualquier acceso inválido o fuga de cualquier tipo hace fallar el comando.

## Prueba de control: Valgrind sí detecta el error

Para confirmar que el "0 errores" no es porque Valgrind no esté mirando, en una **copia aparte** de `dijkstra.c` (no en el repositorio) se agregó a propósito una escritura una posición después del final del arreglo de salida:

```c
camino_salida[num_paradas] = 0;   /* ERROR A PROPÓSITO */
```

Valgrind lo detectó de inmediato y el comando terminó con error:

```
==16== Invalid write of size 4
==16==    at 0x10B796: dijkstra (dijkstra.c:180)
==16== ERROR SUMMARY: 7898 errors from 3 contexts
```

## Sobre el "arreglo de salida deliberadamente corto"

El documento de Arquitectura (RN-05) pide que, con un arreglo de salida más corto de lo debido, la biblioteca devuelva un código de error. Con la firma congelada del contrato eso **no se puede detectar desde C**: `dijkstra()` no recibe el tamaño del arreglo, solo un puntero.

El contrato resuelve el riesgo de otra forma, y eso queda para la bitácora (SCRUM-13):
- `grafo.h` y `PuenteRutas.java` fijan que Java reserva `camino_salida` con **`num_paradas` posiciones**.
- Un camino nunca puede tener más paradas que la red, porque cada parada se cierra una sola vez. Así que C nunca escribe más de `num_paradas` posiciones; la prueba de tamaño exacto lo confirma con Valgrind.
- La alternativa, agregar un parámetro `tam_camino` a la firma, se descartó porque cambia el contrato JNA ya probado con Java.

## Cómo repetirlo

En Linux:
```bash
cd backend/ruta-service/native
make valgrind
```

En Windows o macOS, con Docker:
```bash
cd backend/ruta-service/native
docker build -t villanos-valgrind -f Dockerfile.valgrind .
docker run --rm villanos-valgrind
```

Entorno de la evidencia: contenedor `debian:bookworm-slim`, gcc 12.2.0, valgrind 3.19.0, compilado con `-O2 -g`. 12 nov 2026.
