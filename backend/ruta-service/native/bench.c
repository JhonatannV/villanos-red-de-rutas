/*
 * bench.c — Medición de SCRUM-6 [RN-02]: montículo binario vs. búsqueda lineal.
 *
 * Pregunta que responde: ¿el tiempo de dijkstra() crece de forma cercana a
 * lineal en el número de tramos (O(E log V)), o crece como el cuadrado del
 * número de paradas (O(V²))? Si fuera lo segundo, la cola de prioridad no
 * estaría funcionando.
 *
 * Cómo mide:
 *   - Genera redes conexas con semilla fija (siempre las mismas): 50, 150 y
 *     400 paradas, con 2,75 tramos por parada (400 paradas -> 1.100 tramos,
 *     el tamaño que pide el documento de Arquitectura). Opcionalmente redes
 *     más grandes para que la tendencia se vea más clara.
 *   - Para cada red lanza las mismas CONSULTAS parejas origen-destino a dos
 *     versiones:
 *       1. dijkstra() de dijkstra.c (montículo binario, la versión real).
 *       2. dijkstra_lineal() de este archivo: idéntica, pero escoge la parada
 *          más barata recorriendo todas (O(V) por paso). Sirve de contraste.
 *     Las dos deben dar el mismo costo; si no, la medición se detiene.
 *   - Repite CORRIDAS veces, descarta la primera (calentamiento) y reporta la
 *     mediana del tiempo por consulta.
 *
 * Uso:   make medicion            (50, 150, 400 paradas)
 *        ./bench --grande         (agrega 1.000, 2.000 y 4.000 paradas)
 *        ./bench --csv archivo    (además guarda los resultados en CSV)
 */
#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <math.h>
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

#define CONSULTAS 1000
#define CORRIDAS  6          /* la primera se descarta: quedan 5 */
#define TRAMOS_POR_PARADA 2.75

/* ---------- Generador pseudoaleatorio con semilla fija (xorshift32) ---------- */
static unsigned semilla;
static unsigned aleatorio(void) {
    semilla ^= semilla << 13;
    semilla ^= semilla >> 17;
    semilla ^= semilla << 5;
    return semilla;
}
static int entre(int a, int b) { return a + (int)(aleatorio() % (unsigned)(b - a + 1)); }

typedef struct {
    int paradas, tramos;
    int *origen, *destino, *peso;
} Red;

/* Red conexa: primero un árbol (cada parada se une a una anterior) y luego
 * tramos extra al azar, sin repetir pares, hasta llegar a la cantidad pedida. */
static Red generar_red(int paradas, int tramos, unsigned s) {
    Red r = { paradas, tramos, malloc(tramos * sizeof(int)), malloc(tramos * sizeof(int)),
              malloc(tramos * sizeof(int)) };
    char *usado = calloc((size_t)paradas * paradas, 1);
    int k = 0;
    semilla = s;
    for (int v = 1; v < paradas; v++) {
        int u = entre(0, v - 1);
        r.origen[k] = u; r.destino[k] = v; r.peso[k] = entre(1, 15); k++;
        usado[(size_t)u * paradas + v] = usado[(size_t)v * paradas + u] = 1;
    }
    while (k < tramos) {
        int u = entre(0, paradas - 1), v = entre(0, paradas - 1);
        if (u == v || usado[(size_t)u * paradas + v]) continue;
        usado[(size_t)u * paradas + v] = usado[(size_t)v * paradas + u] = 1;
        r.origen[k] = u; r.destino[k] = v; r.peso[k] = entre(1, 15); k++;
    }
    free(usado);
    return r;
}

static void liberar_red(Red *r) { free(r->origen); free(r->destino); free(r->peso); }

/* ---------- Versión de contraste: misma lógica, selección lineal O(V) ---------- */
static int dijkstra_lineal(int n, int m, const int *origen, const int *destino, const int *peso,
                           int inicio, int fin, int *camino, int *costo) {
    int *ini = calloc((size_t)n + 1, sizeof(int)), *vec = malloc((2 * (size_t)m + 1) * sizeof(int));
    int *pv = malloc((2 * (size_t)m + 1) * sizeof(int)), *sig = malloc((size_t)n * sizeof(int));
    long long *dist = malloc((size_t)n * sizeof(long long));
    int *saltos = malloc((size_t)n * sizeof(int)), *ant = malloc((size_t)n * sizeof(int));
    char *cerrada = calloc((size_t)n, 1);
    int res = GRAFO_SIN_RUTA;

    for (int i = 0; i < m; i++) { ini[origen[i] + 1]++; ini[destino[i] + 1]++; }
    for (int p = 0; p < n; p++) ini[p + 1] += ini[p];
    for (int p = 0; p < n; p++) sig[p] = ini[p];
    for (int i = 0; i < m; i++) {
        vec[sig[origen[i]]] = destino[i]; pv[sig[origen[i]]++] = peso[i];
        vec[sig[destino[i]]] = origen[i]; pv[sig[destino[i]]++] = peso[i];
    }
    for (int p = 0; p < n; p++) { dist[p] = LLONG_MAX; saltos[p] = INT_MAX; ant[p] = -1; }
    dist[inicio] = 0; saltos[inicio] = 0;

    for (;;) {
        int u = -1;                                   /* búsqueda lineal: O(V) */
        for (int p = 0; p < n; p++)
            if (!cerrada[p] && dist[p] != LLONG_MAX &&
                (u == -1 || dist[p] < dist[u] || (dist[p] == dist[u] && saltos[p] < saltos[u])))
                u = p;
        if (u == -1) break;
        cerrada[u] = 1;
        if (u == fin) break;
        for (int k = ini[u]; k < ini[u + 1]; k++) {
            int v = vec[k];
            if (cerrada[v]) continue;
            long long nd = dist[u] + pv[k];
            if (nd < dist[v] || (nd == dist[v] && saltos[u] + 1 < saltos[v])) {
                dist[v] = nd; saltos[v] = saltos[u] + 1; ant[v] = u;
            }
        }
    }
    if (cerrada[fin]) {
        res = saltos[fin] + 1;
        int i = res - 1;
        for (int p = fin; p != -1; p = ant[p]) camino[i--] = p;
        *costo = (int)dist[fin];
    }
    free(ini); free(vec); free(pv); free(sig); free(dist); free(saltos); free(ant); free(cerrada);
    return res;
}

/* ---------- Medición ---------- */
typedef int (*FuncionRuta)(int, int, const int *, const int *, const int *, int, int, int *, int *);

static int comparar_double(const void *a, const void *b) {
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

/* Devuelve la mediana (en microsegundos) del tiempo promedio por consulta */
static double medir(FuncionRuta f, const Red *r, const int *pares, int *suma_costos) {
    int *camino = malloc((size_t)r->paradas * sizeof(int));
    double tiempos[CORRIDAS];
    for (int c = 0; c < CORRIDAS; c++) {
        int suma = 0, costo = 0;
        double t0 = ahora_us();
        for (int q = 0; q < CONSULTAS; q++) {
            int n = f(r->paradas, r->tramos, r->origen, r->destino, r->peso,
                      pares[2 * q], pares[2 * q + 1], camino, &costo);
            if (n > 0) suma += costo;
        }
        tiempos[c] = (ahora_us() - t0) / CONSULTAS;
        *suma_costos = suma;
    }
    free(camino);
    qsort(tiempos + 1, CORRIDAS - 1, sizeof(double), comparar_double);   /* sin la 1.ª */
    return tiempos[1 + (CORRIDAS - 1) / 2];
}

int main(int argc, char **argv) {
    int tamanos[8] = { 50, 150, 400 }, cuantos = 3;
    const char *csv = NULL;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--grande") == 0) { tamanos[3] = 1000; tamanos[4] = 2000; tamanos[5] = 4000; cuantos = 6; }
        else if (strcmp(argv[i], "--csv") == 0 && i + 1 < argc) csv = argv[++i];
    }

    FILE *salida = csv ? fopen(csv, "w") : NULL;
    if (salida) fprintf(salida, "paradas,tramos,us_monticulo,us_lineal\n");

    printf("SCRUM-6 [RN-02] - Montículo binario vs. búsqueda lineal\n");
    printf("%d consultas por red, %d corridas (se descarta la 1.ª), mediana por consulta\n\n",
           CONSULTAS, CORRIDAS);
    printf("%8s %8s %16s %16s %10s\n", "Paradas", "Tramos", "Montículo (us)", "Lineal (us)", "Lineal/Mont.");

    double base_m = 0, base_l = 0, base_e = 0, base_v = 0;
    for (int t = 0; t < cuantos; t++) {
        int v = tamanos[t], e = (int)lround(v * TRAMOS_POR_PARADA);
        Red r = generar_red(v, e, 20261111u + (unsigned)v);

        int *pares = malloc(2 * CONSULTAS * sizeof(int));
        semilla = 7u + (unsigned)v;
        for (int q = 0; q < CONSULTAS; q++) {
            pares[2 * q] = entre(0, v - 1);
            do pares[2 * q + 1] = entre(0, v - 1); while (pares[2 * q + 1] == pares[2 * q]);
        }

        int suma_m = 0, suma_l = 0;
        double us_m = medir(dijkstra, &r, pares, &suma_m);
        double us_l = medir(dijkstra_lineal, &r, pares, &suma_l);
        if (suma_m != suma_l) {
            fprintf(stderr, "ERROR: las dos versiones no dan los mismos costos (%d vs %d)\n", suma_m, suma_l);
            return 1;
        }
        printf("%8d %8d %16.2f %16.2f %9.1fx\n", v, e, us_m, us_l, us_l / us_m);
        if (salida) fprintf(salida, "%d,%d,%.3f,%.3f\n", v, e, us_m, us_l);
        if (t == 0) { base_m = us_m; base_l = us_l; base_e = e; base_v = v; }
        if (t == cuantos - 1) {
            printf("\nDe %d a %d paradas:\n", (int)base_v, v);
            printf("  los tramos crecen     x%.1f\n", e / base_e);
            printf("  V al cuadrado crece   x%.1f\n", (v / base_v) * (v / base_v));
            printf("  el montículo tardó    x%.1f\n", us_m / base_m);
            printf("  la búsqueda lineal    x%.1f\n", us_l / base_l);
        }
        free(pares);
        liberar_red(&r);
    }
    if (salida) fclose(salida);
    return 0;
}
