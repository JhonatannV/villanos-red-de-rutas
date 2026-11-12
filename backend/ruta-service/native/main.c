/*
 * main.c — Prueba de consola de SCRUM-5 [RN-01].
 *
 * Criterio (Jira y documento de Arquitectura): para los grafos de referencia
 * de 5 y de 7 paradas, el camino y el costo coinciden con los calculados a mano.
 *
 * Grafo guía de 5 paradas (el mismo de PruebaContratoJnaTest.java)
 *   A=0 B=1 C=2 D=3 E=4.  Tramos de ida y vuelta:
 *   A-B 4   A-C 2   B-C 3   B-D 5   C-D 8   C-E 15   D-E 2
 *   Esperado A -> E:  A -> B -> D -> E = 11  (4 + 5 + 2)
 *
 * Grafo de referencia de 7 paradas (traza a mano en la bitácora)
 *   Terminal=0 Centro=1 Hospital=2 Universidad=3 Estadio=4 Parque=5 Aeropuerto=6
 *   Terminal-Centro 4   Terminal-Hospital 2   Hospital-Centro 1   Centro-Universidad 5
 *   Hospital-Estadio 8  Universidad-Estadio 2 Universidad-Parque 6 Estadio-Parque 3
 *   Estadio-Aeropuerto 7  Parque-Aeropuerto 1
 *   Esperado Terminal -> Aeropuerto:
 *   Terminal -> Hospital -> Estadio -> Parque -> Aeropuerto = 14  (2 + 8 + 3 + 1)
 *
 * Compilar y correr:  make prueba
 */
#include <stdio.h>
#include "grafo.h"

#define MAX_PARADAS 8

typedef struct {
    const char *titulo;
    int num_paradas, num_tramos;
    const char *nombre[MAX_PARADAS];
    int origen[16], destino[16], peso[16];
    int inicio, fin;
    int esperado_paradas;
    int esperado_camino[MAX_PARADAS];
    int esperado_costo;
} Caso;

static const Caso casos[] = {
    { "Grafo guía de 5 paradas", 5, 7,
      { "A", "B", "C", "D", "E" },
      { 0, 0, 1, 1, 2, 2, 3 },
      { 1, 2, 2, 3, 3, 4, 4 },
      { 4, 2, 3, 5, 8, 15, 2 },
      0, 4, 4, { 0, 1, 3, 4 }, 11 },
    { "Grafo de referencia de 7 paradas", 7, 10,
      { "Terminal", "Centro", "Hospital", "Universidad", "Estadio", "Parque", "Aeropuerto" },
      { 0, 0, 2, 1, 2, 3, 3, 4, 4, 5 },
      { 1, 2, 1, 3, 4, 4, 5, 5, 6, 6 },
      { 4, 2, 1, 5, 8, 2, 6, 3, 7, 1 },
      0, 6, 5, { 0, 2, 4, 5, 6 }, 14 },
};

/* Corre un caso, imprime la ruta y dice si coincide con lo calculado a mano */
static int probar(const Caso *k) {
    int camino[MAX_PARADAS];   /* igual que Java: quien llama reserva la salida */
    int costo = 0;

    int paradas = dijkstra(k->num_paradas, k->num_tramos, k->origen, k->destino,
                           k->peso, k->inicio, k->fin, camino, &costo);

    printf("%s\n  ", k->titulo);
    if (paradas < 0) {
        printf("dijkstra devolvió el código %d\n  FALLO\n\n", paradas);
        return 0;
    }
    for (int i = 0; i < paradas; i++)
        printf("%s%s", k->nombre[camino[i]], i + 1 < paradas ? " -> " : "");
    printf("  (costo %d)\n", costo);

    int ok = (paradas == k->esperado_paradas && costo == k->esperado_costo);
    for (int i = 0; ok && i < paradas; i++)
        ok = (camino[i] == k->esperado_camino[i]);

    printf(ok ? "  OK: coincide con el cálculo a mano\n\n"
              : "  FALLO: no coincide con el cálculo a mano\n\n");
    return ok;
}

int main(void) {
    int total = (int)(sizeof casos / sizeof casos[0]);
    int bien = 0;
    for (int c = 0; c < total; c++)
        bien += probar(&casos[c]);
    printf("%d de %d casos correctos\n", bien, total);
    return bien == total ? 0 : 1;
}
