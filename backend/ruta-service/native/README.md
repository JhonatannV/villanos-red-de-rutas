# native/ — Núcleo en C (libgrafo.so)

**Responsable:** Santiago Ríos Morales · Sprint 1 (SCRUM-5 a SCRUM-9)

| Archivo | Qué es |
|---|---|
| `grafo.h` | El contrato con Java: firma de `dijkstra()` y códigos de error |
| `dijkstra.c` | Dijkstra con cola de prioridad (montículo binario) sobre arreglos paralelos |
| `main.c` | Prueba de consola (SCRUM-5): grafo guía de 5 paradas (A → B → D → E = 11) y grafo de referencia de 7 paradas (Terminal → … → Aeropuerto = 14) |
| `bench.c` | Medición de SCRUM-6: montículo vs. búsqueda lineal con 50, 150 y 400 paradas (y hasta 4.000 con `--grande`) |
| `graficar_medicion.py` | Dibuja la gráfica SVG a partir del CSV de `bench` (solo biblioteca estándar de Python) |
| `Makefile` | `make` compila `libgrafo.so`; `make prueba` corre la prueba; `make medicion` corre la medición |

Resultados de la medición: [`docs/mediciones/SCRUM-6-monticulo.md`](../../../docs/mediciones/SCRUM-6-monticulo.md).

## Compilar

Linux / WSL / Docker:

```bash
make            # libgrafo.so + main
make prueba     # debe terminar con "2 de 2 casos correctos"
```

Windows (gcc de MSYS2), para probar JNA en el PC sin Docker:

```bash
gcc -Wall -Wextra -O2 -std=c11 -o main.exe main.c dijkstra.c && ./main.exe
gcc -Wall -Wextra -O2 -std=c11 -shared -o grafo.dll dijkstra.c
```

Con `grafo.dll` en esta carpeta, el test `PruebaContratoJnaTest` usa C de verdad
si se corre con `-Djna.library.path=<ruta a native>`:
imprime `Biblioteca nativa C disponible en este PC?: true` y `costo=11, camino=[0, 1, 3, 4]`.

## Reglas del contrato

- Java reserva `camino_salida` (num_paradas enteros) y `costo_salida` (1 entero). C solo los llena.
- C pide su propia memoria de trabajo (`malloc`) y la libera toda antes de retornar; nunca libera la de Java.
- Retorno: cantidad de paradas del camino, o negativo: `-1` sin ruta, `-2` parada fuera de rango,
  `-3` argumento inválido, `-4` sin memoria, `-5` costo desbordado.
