# SCRUM-8 [RN-04] · Casos degenerados e índices fuera de rango

**Criterio de aceptación:** origen igual a destino devuelve costo 0 y un camino de 1 parada. Un índice fuera de rango devuelve un código de error documentado, sin lectura fuera del arreglo y sin que el programa aborte (0 segmentation fault).

**Resultado:** ✅ las 23 pruebas nuevas pasan. Con las de SCRUM-7, la suite completa queda en **36 de 36** (`make test`, 0,3 s). Salida completa: [`scrum8_salida.txt`](scrum8_salida.txt).

## Códigos de error (definidos en `native/grafo.h`)

`dijkstra()` devuelve la cantidad de paradas del camino (≥ 1) o uno de estos códigos. Con un código negativo **no escribe nada** en `camino_salida` ni en `costo_salida`.

| Código | Constante | Cuándo |
|---:|---|---|
| -1 | `GRAFO_SIN_RUTA` | No hay camino entre las dos paradas (SCRUM-7) |
| -2 | `GRAFO_FUERA_DE_RANGO` | El origen, el destino o un tramo nombran una parada que no existe (< 0 o ≥ `num_paradas`) |
| -3 | `GRAFO_ARGUMENTO_INVALIDO` | `num_paradas` ≤ 0, `num_tramos` < 0, un peso negativo o un puntero nulo |
| -4 | `GRAFO_SIN_MEMORIA` | `malloc` falló |
| -5 | `GRAFO_COSTO_DESBORDADO` | El costo total no cabe en un `int` |

Del lado de Java, `PuenteRutas` trata cualquier valor `< 0` como "sin ruta válida". `ruta-service` puede traducir cada código a un mensaje o a una respuesta HTTP distinta.

## Qué se prueba

| Grupo | Casos | Esperado |
|---|---|---|
| **Origen igual a destino** | En la red de 7 paradas; en una parada sin tramos; en una red de una sola parada | `1`, camino `[origen]`, costo `0` |
| **Red de una sola parada** | Pedir la parada 1, que no existe | `-2` |
| **Tramos de costo 0** | Un tramo de costo 0 dentro del camino; una red donde todo cuesta 0 | Camino correcto; si todo empata, gana el de menos paradas |
| **Tramos raros** | Dos tramos entre las mismas paradas (9 y 2); un tramo que sale y llega a la misma parada | Usa el más barato; el bucle no afecta |
| **Costo enorme** | Dos tramos de 2.000.000.000 | `-5` (internamente suma en 64 bits, así que no se desborda) |
| **Índices fuera de rango** | Destino 99 y origen 99 con 5 paradas; destino igual a `num_paradas`; origen -1; tramo que apunta a la parada 7 en una red de 3; tramo que sale de -4 | `-2` |
| **Argumentos inválidos** | Peso negativo; 0 paradas; paradas negativas; tramos negativos; arreglos nulos; `camino_salida` nulo; `costo_salida` nulo | `-3` |

En todos los casos la prueba llena antes la salida con el centinela `-999`:
- si el resultado es un error, comprueba que la salida siga intacta;
- en cualquier caso, comprueba que nada se haya escrito más allá de `num_paradas` posiciones.

## Por qué no hay segmentation fault

`dijkstra()` **valida todo antes de tocar memoria**, en este orden:
1. tamaños (`num_paradas`, `num_tramos`);
2. punteros de salida y de entrada;
3. origen y destino dentro de `0 … num_paradas - 1`;
4. cada tramo: sus dos paradas dentro del rango y su peso ≥ 0.

Solo si todo pasa empieza a calcular. Por eso un índice malo nunca se usa para leer ni escribir un arreglo.

Las 36 pruebas corren en el mismo proceso. Si alguna provocara un *segmentation fault*, el programa se caería antes de imprimir el resumen y `make test` fallaría. La confirmación con herramienta, sin lecturas ni escrituras inválidas, la dará Valgrind en SCRUM-9.

## Cómo repetirlo

```bash
cd backend/ruta-service/native
make test
```
