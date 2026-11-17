/*
 * banco_c.c — SCRUM-37 [RN-BENCH]: tiempo de dijkstra() en C puro, sin JNA.
 *
 * Lo invoca el banco Java (BancoCvsJava) con la MISMA red y las MISMAS
 * consultas que mide en Java y en C por JNA, para separar cuánto cuesta el
 * algoritmo y cuánto cuesta cruzar de Java a C.
 *
 * Archivo de entrada (texto):
 *   <paradas> <tramos>
 *   <origen> <destino> <peso>      (una línea por tramo)
 *   <consultas>
 *   <desde> <hasta>                (una línea por consulta)
 *
 * Uso:    ./banco_c entrada.txt [corridas]
 * Salida: una línea por corrida "corrida,us_por_consulta,suma_costos".
 *         La suma de costos sirve para comprobar que C y Java dan lo mismo.
 */
#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include "grafo.h"

#ifdef _WIN32
#include <windows.h>
static double ahora_us(void) {
    static LARGE_INTEGER frec;
    LARGE_INTEGER t;
    if (frec.QuadPart == 0) QueryPerformanceFrequency(&frec);
    QueryPerformanceCounter(&t);
    return (double)t.QuadPart * 1e6 / (double)frec.QuadPart;
}
#else
#include <time.h>
static double ahora_us(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1e6 + t.tv_nsec / 1e3;
}
#endif

static int leer_entero(FILE *f, int *x) { return fscanf(f, "%d", x) == 1; }

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "uso: %s entrada.txt [corridas]\n", argv[0]);
        return 2;
    }
    int corridas = argc > 2 ? atoi(argv[2]) : 6;
    FILE *f = fopen(argv[1], "r");
    if (!f) { perror(argv[1]); return 2; }

    int n, m, q;
    if (!leer_entero(f, &n) || !leer_entero(f, &m) || n <= 0 || m < 0) {
        fprintf(stderr, "encabezado de red inválido\n"); fclose(f); return 2;
    }
    int *origen = malloc((size_t)m * sizeof(int)), *destino = malloc((size_t)m * sizeof(int));
    int *peso = malloc((size_t)m * sizeof(int));
    for (int i = 0; i < m; i++) {
        if (!leer_entero(f, &origen[i]) || !leer_entero(f, &destino[i]) || !leer_entero(f, &peso[i])) {
            fprintf(stderr, "tramo %d inválido\n", i); fclose(f); return 2;
        }
    }
    if (!leer_entero(f, &q) || q <= 0) { fprintf(stderr, "número de consultas inválido\n"); fclose(f); return 2; }
    int *pares = malloc(2 * (size_t)q * sizeof(int));
    for (int i = 0; i < 2 * q; i++) {
        if (!leer_entero(f, &pares[i])) { fprintf(stderr, "consulta %d inválida\n", i / 2); fclose(f); return 2; }
    }
    fclose(f);

    int *camino = malloc((size_t)n * sizeof(int));
    for (int c = 1; c <= corridas; c++) {
        long long suma = 0;
        int costo = 0;
        double t0 = ahora_us();
        for (int k = 0; k < q; k++) {
            int r = dijkstra(n, m, origen, destino, peso, pares[2 * k], pares[2 * k + 1], camino, &costo);
            if (r > 0) suma += costo;
        }
        double us = (ahora_us() - t0) / q;
        printf("%d,%.4f,%lld\n", c, us, suma);
    }
    free(origen); free(destino); free(peso); free(pares); free(camino);
    return 0;
}
