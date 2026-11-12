/*
 * pruebas.c — Suite de pruebas del componente nativo (RQ-02 Testabilidad).
 *
 * Un solo comando la compila y la corre, sin Java, sin Docker y sin levantar
 * el sistema:   make test
 * Termina con código 0 si todo pasa y 1 si algo falla.
 *
 * Secciones:
 *   SCRUM-7 [RN-03] Destino inalcanzable
 *     - Si no hay camino, dijkstra() devuelve -1 (GRAFO_SIN_RUTA).
 *     - No escribe nada en camino_salida ni en costo_salida.
 *     - No se queda en un ciclo: termina, y en el mismo orden de tiempo que
 *       una consulta normal (se mide al final).
 *   SCRUM-8 [RN-04] Casos degenerados e índices fuera de rango
 *     - Origen igual a destino: camino de 1 parada y costo 0.
 *     - Red de una sola parada, tramos de costo 0, tramos repetidos y bucles.
 *     - Índices fuera de rango, punteros nulos, tamaños y pesos inválidos:
 *       devuelven el código de error de grafo.h, sin tocar la salida y sin
 *       que el programa aborte. Si algo produjera un segmentation fault, la
 *       suite se caería y `make test` fallaría.
 *   SCRUM-9 [RN-05] Arreglos del tamaño exacto, como los reserva Java
 *     - Todas las parejas de la red de 40 paradas, con la entrada y la
 *       salida en memoria dinámica del tamaño justo. Corrida con Valgrind
 *       (`make valgrind`), cualquier lectura o escritura de un solo int de
 *       más se reporta como error.
 */
#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
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

#define MAX_PARADAS 64
#define MAX_TRAMOS  128
#define CENTINELA   (-999)   /* valor de relleno para detectar escrituras indebidas */

static int pasadas = 0, fallidas = 0;

/* ---------- Red de 40 paradas y 90 tramos de grafo-service ----------
 * Copiada de backend/grafo-service/data/grafo_40_paradas.json.
 * Cada fila: { id del tramo, origen, destino, peso }. */
static const int RED40[90][4] = {
    { 1, 0, 1, 3 }, { 2, 1, 2, 5 }, { 3, 0, 8, 5 }, { 4, 1, 9, 4 }, { 5, 9, 10, 2 },
    { 6, 10, 11, 3 }, { 7, 8, 11, 9 }, { 8, 2, 10, 6 }, { 9, 2, 3, 4 }, { 10, 8, 9, 3 },
    { 11, 4, 5, 2 }, { 12, 5, 6, 6 }, { 13, 5, 13, 3 }, { 14, 13, 21, 4 }, { 15, 21, 22, 2 },
    { 16, 4, 12, 5 }, { 17, 12, 20, 6 }, { 18, 20, 22, 4 }, { 19, 3, 4, 3 }, { 20, 11, 12, 4 },
    { 21, 3, 11, 6 }, { 22, 6, 7, 11 }, { 23, 6, 14, 5 }, { 24, 7, 15, 10 }, { 25, 8, 16, 7 },
    { 26, 9, 17, 4 }, { 27, 10, 18, 9 }, { 28, 11, 19, 6 }, { 29, 12, 13, 8 }, { 30, 13, 14, 6 },
    { 31, 14, 15, 4 }, { 32, 14, 22, 5 }, { 33, 15, 23, 10 }, { 34, 16, 17, 9 }, { 35, 16, 24, 7 },
    { 36, 17, 18, 7 }, { 37, 17, 25, 4 }, { 38, 18, 19, 5 }, { 39, 18, 26, 9 }, { 40, 19, 20, 3 },
    { 41, 19, 27, 6 }, { 42, 20, 21, 10 }, { 43, 20, 28, 11 }, { 44, 21, 29, 8 }, { 45, 22, 23, 6 },
    { 46, 22, 30, 5 }, { 47, 23, 31, 10 }, { 48, 24, 25, 11 }, { 49, 24, 32, 7 }, { 50, 25, 26, 9 },
    { 51, 25, 33, 4 }, { 52, 26, 27, 7 }, { 53, 26, 34, 9 }, { 54, 27, 28, 5 }, { 55, 27, 35, 6 },
    { 56, 28, 29, 3 }, { 57, 28, 36, 11 }, { 58, 29, 30, 10 }, { 59, 30, 31, 8 }, { 60, 32, 33, 4 },
    { 61, 33, 34, 11 }, { 62, 34, 35, 9 }, { 63, 35, 36, 7 }, { 64, 0, 9, 5 }, { 65, 1, 10, 8 },
    { 66, 1, 8, 10 }, { 67, 2, 11, 11 }, { 68, 2, 9, 7 }, { 69, 3, 12, 7 }, { 70, 3, 10, 11 },
    { 71, 4, 13, 10 }, { 72, 4, 11, 8 }, { 73, 5, 14, 6 }, { 74, 5, 12, 12 }, { 75, 6, 15, 9 },
    { 76, 6, 13, 9 }, { 77, 7, 14, 6 }, { 78, 8, 17, 8 }, { 79, 9, 18, 11 }, { 80, 9, 16, 7 },
    { 81, 10, 19, 7 }, { 82, 10, 17, 11 }, { 83, 11, 20, 10 }, { 84, 11, 18, 8 }, { 85, 12, 21, 6 },
    { 86, 12, 19, 12 }, { 87, 37, 38, 4 }, { 88, 38, 39, 5 }, { 89, 37, 39, 7 }, { 90, 36, 37, 6 }
};

/* Arma los arreglos paralelos de la red de 40 paradas, dejando por fuera
 * el tramo `cerrado` (0 = ninguno). Devuelve cuántos tramos quedaron. */
static int red40(int cerrado, int *origen, int *destino, int *peso) {
    int k = 0;
    for (int i = 0; i < 90; i++) {
        if (RED40[i][0] == cerrado) continue;
        origen[k] = RED40[i][1]; destino[k] = RED40[i][2]; peso[k] = RED40[i][3]; k++;
    }
    return k;
}

/* ---------- Comprobación de un caso ---------- */
typedef struct {
    const char *nombre;
    int num_paradas, num_tramos;
    const int *origen, *destino, *peso;
    int inicio, fin;
    int esperado_retorno;           /* paradas del camino, o código negativo */
    const int *esperado_camino;     /* solo si esperado_retorno > 0 */
    int esperado_costo;
} Caso;

static void probar(const Caso *k) {
    int camino[MAX_PARADAS];
    int costo = CENTINELA;
    for (int i = 0; i < MAX_PARADAS; i++) camino[i] = CENTINELA;

    int r = dijkstra(k->num_paradas, k->num_tramos, k->origen, k->destino, k->peso,
                     k->inicio, k->fin, camino, &costo);

    int ok = (r == k->esperado_retorno);
    const char *motivo = ok ? "" : "retorno distinto al esperado";

    if (ok && r > 0) {
        if (costo != k->esperado_costo) { ok = 0; motivo = "costo distinto"; }
        for (int i = 0; ok && i < r; i++)
            if (camino[i] != k->esperado_camino[i]) { ok = 0; motivo = "camino distinto"; }
    }
    if (ok && r < 0) {
        /* Con error no debe tocar NADA de la salida */
        if (costo != CENTINELA) { ok = 0; motivo = "escribió en costo_salida"; }
        for (int i = 0; ok && i < MAX_PARADAS; i++)
            if (camino[i] != CENTINELA) { ok = 0; motivo = "escribió en camino_salida"; }
    }
    /* Nunca debe escribir más allá de num_paradas posiciones */
    for (int i = k->num_paradas > 0 ? k->num_paradas : 0; ok && i < MAX_PARADAS; i++)
        if (camino[i] != CENTINELA) { ok = 0; motivo = "escribió fuera del arreglo"; }

    if (ok) { pasadas++; printf("  OK     %s  (devolvió %d)\n", k->nombre, r); }
    else    { fallidas++; printf("  FALLA  %s  (devolvió %d, esperado %d: %s)\n",
                                 k->nombre, r, k->esperado_retorno, motivo); }
}

/* ---------- SCRUM-7: casos de destino inalcanzable ---------- */
static void casos_inalcanzables(void) {
    printf("SCRUM-7 [RN-03] Destino inalcanzable\n");

    /* Dos islas: 0-1 y 2-3, sin tramo entre ellas */
    static const int o2[] = { 0, 2 }, d2[] = { 1, 3 }, p2[] = { 5, 5 };
    probar(&(Caso){ "Grafo partido en dos: 0 -> 3", 4, 2, o2, d2, p2, 0, 3, GRAFO_SIN_RUTA, NULL, 0 });
    probar(&(Caso){ "Grafo partido en dos, al revés: 3 -> 0", 4, 2, o2, d2, p2, 3, 0, GRAFO_SIN_RUTA, NULL, 0 });

    /* Parada 4 sin ningún tramo */
    static const int o5[] = { 0, 1, 2, 3 }, d5[] = { 1, 2, 3, 0 }, p5[] = { 1, 2, 3, 4 };
    probar(&(Caso){ "Parada aislada como destino: 0 -> 4", 5, 4, o5, d5, p5, 0, 4, GRAFO_SIN_RUTA, NULL, 0 });
    probar(&(Caso){ "Parada aislada como origen: 4 -> 0", 5, 4, o5, d5, p5, 4, 0, GRAFO_SIN_RUTA, NULL, 0 });

    /* Red con paradas pero sin un solo tramo */
    probar(&(Caso){ "Red sin tramos: 0 -> 2", 3, 0, NULL, NULL, NULL, 0, 2, GRAFO_SIN_RUTA, NULL, 0 });

    /* Ciclo cerrado en una isla (para comprobar que no da vueltas sin fin) */
    static const int oc[] = { 0, 1, 2, 4 }, dc[] = { 1, 2, 0, 5 }, pc[] = { 1, 1, 1, 1 };
    probar(&(Caso){ "Isla con ciclo 0-1-2-0, destino en otra isla: 0 -> 5", 6, 4, oc, dc, pc, 0, 5, GRAFO_SIN_RUTA, NULL, 0 });

    /* Red real de 40 paradas: control con todo abierto y luego con el puente cerrado.
     * Casos esperados de docs/casos_prueba_rf03.md (Jhonatan). */
    static int o40[90], d40[90], p40[90], o89[90], d89[90], p89[90];
    int t40 = red40(0, o40, d40, p40);
    int t89 = red40(90, o89, d89, p89);
    static const int c1[] = { 0, 9, 10, 11 }, c2[] = { 4, 5, 13, 21, 22 };
    static const int c3[] = { 0, 9, 10, 19, 27, 35, 36, 37, 38 };
    probar(&(Caso){ "Red 40: Terminal -> Coliseo Benicio (control RF-03 caso 1)", 40, t40, o40, d40, p40, 0, 11, 4, c1, 10 });
    probar(&(Caso){ "Red 40: Hospital -> Barrio Farfán (control RF-03 caso 2)", 40, t40, o40, d40, p40, 4, 22, 5, c2, 11 });
    probar(&(Caso){ "Red 40: Terminal -> Terminal Aérea, todo abierto", 40, t40, o40, d40, p40, 0, 38, 9, c3, 43 });
    probar(&(Caso){ "Red 40 con el tramo 90 cerrado: Terminal -> Terminal Aérea", 40, t89, o89, d89, p89, 0, 38, GRAFO_SIN_RUTA, NULL, 0 });
    probar(&(Caso){ "Red 40 con el tramo 90 cerrado: Zona Rural La Marina -> Salida Sur", 40, t89, o89, d89, p89, 39, 36, GRAFO_SIN_RUTA, NULL, 0 });
    probar(&(Caso){ "Red 40 con el tramo 90 cerrado: Peaje -> Zona Rural (misma isla, sí hay ruta)", 40, t89, o89, d89, p89, 37, 39, 2, (const int[]){ 37, 39 }, 7 });
}

/* ---------- SCRUM-8: casos degenerados e índices fuera de rango ---------- */

/* Llamada con un puntero de salida nulo: debe devolver -3 sin abortar */
static void probar_salida_nula(const char *nombre, int sin_camino, int sin_costo) {
    static const int o[] = { 0, 1 }, d[] = { 1, 2 }, p[] = { 1, 1 };
    int camino[3] = { CENTINELA, CENTINELA, CENTINELA }, costo = CENTINELA;
    int r = dijkstra(3, 2, o, d, p, 0, 2, sin_camino ? NULL : camino, sin_costo ? NULL : &costo);
    int ok = r == GRAFO_ARGUMENTO_INVALIDO && costo == CENTINELA &&
             camino[0] == CENTINELA && camino[1] == CENTINELA && camino[2] == CENTINELA;
    if (ok) { pasadas++; printf("  OK     %s  (devolvió %d)\n", nombre, r); }
    else    { fallidas++; printf("  FALLA  %s  (devolvió %d, esperado %d)\n", nombre, r, GRAFO_ARGUMENTO_INVALIDO); }
}

static void casos_degenerados(void) {
    /* Grafo guía de 5 paradas (A=0 ... E=4) y red de 7 paradas de referencia */
    static const int og[] = { 0, 0, 1, 1, 2, 2, 3 }, dg[] = { 1, 2, 2, 3, 3, 4, 4 }, pg[] = { 4, 2, 3, 5, 8, 15, 2 };
    static const int o7[] = { 0, 0, 2, 1, 2, 3, 3, 4, 4, 5 }, d7[] = { 1, 2, 1, 3, 4, 4, 5, 5, 6, 6 },
                     p7[] = { 4, 2, 1, 5, 8, 2, 6, 3, 7, 1 };

    printf("\nSCRUM-8 [RN-04] Origen igual a destino y redes mínimas\n");
    probar(&(Caso){ "Origen igual a destino (Universidad -> Universidad)", 7, 10, o7, d7, p7, 3, 3, 1, (const int[]){ 3 }, 0 });
    probar(&(Caso){ "Origen igual a destino en una parada sin tramos", 5, 4, (const int[]){ 0, 1, 2, 3 },
                    (const int[]){ 1, 2, 3, 0 }, (const int[]){ 1, 2, 3, 4 }, 4, 4, 1, (const int[]){ 4 }, 0 });
    probar(&(Caso){ "Red de una sola parada: 0 -> 0", 1, 0, NULL, NULL, NULL, 0, 0, 1, (const int[]){ 0 }, 0 });
    probar(&(Caso){ "Red de una sola parada: 0 -> 1 (no existe)", 1, 0, NULL, NULL, NULL, 0, 1, GRAFO_FUERA_DE_RANGO, NULL, 0 });

    printf("\nSCRUM-8 [RN-04] Tramos de costo cero, repetidos y bucles\n");
    probar(&(Caso){ "Tramo de costo 0 en el camino: 0 -(0)- 1 -(4)- 2", 3, 2, (const int[]){ 0, 1 },
                    (const int[]){ 1, 2 }, (const int[]){ 0, 4 }, 0, 2, 3, (const int[]){ 0, 1, 2 }, 4 });
    probar(&(Caso){ "Todo cuesta 0: gana el camino con menos paradas", 3, 3, (const int[]){ 0, 1, 0 },
                    (const int[]){ 1, 2, 2 }, (const int[]){ 0, 0, 0 }, 0, 2, 2, (const int[]){ 0, 2 }, 0 });
    probar(&(Caso){ "Dos tramos entre las mismas paradas (9 y 2): usa el barato", 2, 2, (const int[]){ 0, 0 },
                    (const int[]){ 1, 1 }, (const int[]){ 9, 2 }, 0, 1, 2, (const int[]){ 0, 1 }, 2 });
    probar(&(Caso){ "Tramo que sale y llega a la misma parada (1-1)", 3, 3, (const int[]){ 0, 1, 1 },
                    (const int[]){ 1, 1, 2 }, (const int[]){ 3, 1, 4 }, 0, 2, 3, (const int[]){ 0, 1, 2 }, 7 });
    probar(&(Caso){ "Costo total que no cabe en un int", 3, 2, (const int[]){ 0, 1 }, (const int[]){ 1, 2 },
                    (const int[]){ 2000000000, 2000000000 }, 0, 2, GRAFO_COSTO_DESBORDADO, NULL, 0 });

    printf("\nSCRUM-8 [RN-04] Índices fuera de rango (código -2, sin segmentation fault)\n");
    probar(&(Caso){ "Destino 99 cuando hay 5 paradas", 5, 7, og, dg, pg, 0, 99, GRAFO_FUERA_DE_RANGO, NULL, 0 });
    probar(&(Caso){ "Origen 99 cuando hay 5 paradas", 5, 7, og, dg, pg, 99, 4, GRAFO_FUERA_DE_RANGO, NULL, 0 });
    probar(&(Caso){ "Destino igual a num_paradas (5 cuando van de 0 a 4)", 5, 7, og, dg, pg, 0, 5, GRAFO_FUERA_DE_RANGO, NULL, 0 });
    probar(&(Caso){ "Origen negativo (-1)", 5, 7, og, dg, pg, -1, 4, GRAFO_FUERA_DE_RANGO, NULL, 0 });
    probar(&(Caso){ "Origen 99 y destino 99 (iguales, pero no existen)", 5, 7, og, dg, pg, 99, 99, GRAFO_FUERA_DE_RANGO, NULL, 0 });
    probar(&(Caso){ "Un tramo apunta a la parada 7 en una red de 3", 3, 1, (const int[]){ 0 }, (const int[]){ 7 },
                    (const int[]){ 1 }, 0, 1, GRAFO_FUERA_DE_RANGO, NULL, 0 });
    probar(&(Caso){ "Un tramo sale de la parada -4", 3, 1, (const int[]){ -4 }, (const int[]){ 1 },
                    (const int[]){ 1 }, 0, 1, GRAFO_FUERA_DE_RANGO, NULL, 0 });

    printf("\nSCRUM-8 [RN-04] Argumentos inválidos (código -3, sin segmentation fault)\n");
    probar(&(Caso){ "Peso negativo", 2, 1, (const int[]){ 0 }, (const int[]){ 1 }, (const int[]){ -3 }, 0, 1,
                    GRAFO_ARGUMENTO_INVALIDO, NULL, 0 });
    probar(&(Caso){ "Red de 0 paradas", 0, 0, NULL, NULL, NULL, 0, 0, GRAFO_ARGUMENTO_INVALIDO, NULL, 0 });
    probar(&(Caso){ "Cantidad de paradas negativa", -5, 0, NULL, NULL, NULL, 0, 0, GRAFO_ARGUMENTO_INVALIDO, NULL, 0 });
    probar(&(Caso){ "Cantidad de tramos negativa", 5, -1, og, dg, pg, 0, 4, GRAFO_ARGUMENTO_INVALIDO, NULL, 0 });
    probar(&(Caso){ "Hay tramos pero los arreglos son nulos", 5, 7, NULL, NULL, NULL, 0, 4, GRAFO_ARGUMENTO_INVALIDO, NULL, 0 });
    probar_salida_nula("camino_salida nulo", 1, 0);
    probar_salida_nula("costo_salida nulo", 0, 1);
}

/* ---------- SCRUM-7: "en el mismo orden de tiempo" ----------
 * Red de 400 paradas partida en dos islas de 200 (1.100 tramos en total).
 * Se comparan 1.000 consultas SIN ruta (de una isla a la otra) contra 1.000
 * consultas CON ruta dentro de la misma isla. Sin ruta, Dijkstra recorre
 * toda la isla del origen y se detiene: debe tardar del mismo orden. */
static unsigned semilla = 20261112u;
static unsigned aleatorio(void) { semilla ^= semilla << 13; semilla ^= semilla >> 17; semilla ^= semilla << 5; return semilla; }
static int entre(int a, int b) { return a + (int)(aleatorio() % (unsigned)(b - a + 1)); }

static int isla(int base, int n, int tramos, int *o, int *d, int *p, int k) {
    for (int v = 1; v < n; v++) { o[k] = base + entre(0, v - 1); d[k] = base + v; p[k] = entre(1, 15); k++; }
    for (int i = n - 1; i < tramos; i++) {
        int u = entre(0, n - 1), v = entre(0, n - 1);
        if (u == v) v = (u + 1) % n;
        o[k] = base + u; d[k] = base + v; p[k] = entre(1, 15); k++;
    }
    return k;
}

static double mediana_us(int n, int m, const int *o, const int *d, const int *p,
                         const int *pares, int consultas, int esperado_negativo) {
    int *camino = malloc((size_t)n * sizeof(int));
    double t[5];
    int incoherente = 0;
    for (int c = 0; c < 5; c++) {
        double t0 = ahora_us();
        for (int q = 0; q < consultas; q++) {
            int costo;
            int r = dijkstra(n, m, o, d, p, pares[2 * q], pares[2 * q + 1], camino, &costo);
            if ((r < 0) != esperado_negativo) incoherente = 1;
        }
        t[c] = (ahora_us() - t0) / consultas;
    }
    free(camino);
    if (incoherente) return -1;
    for (int i = 0; i < 5; i++) for (int j = i + 1; j < 5; j++) if (t[j] < t[i]) { double x = t[i]; t[i] = t[j]; t[j] = x; }
    return t[2];
}

static void tiempo_inalcanzable(void) {
    enum { N = 400, M = 1100, Q = 1000 };
    static int o[M], d[M], p[M], sin_ruta[2 * Q], con_ruta[2 * Q];
    int k = isla(0, N / 2, M / 2, o, d, p, 0);
    k = isla(N / 2, N / 2, M / 2, o, d, p, k);
    for (int q = 0; q < Q; q++) {
        sin_ruta[2 * q] = entre(0, N / 2 - 1);  sin_ruta[2 * q + 1] = entre(N / 2, N - 1);
        con_ruta[2 * q] = entre(0, N / 2 - 1);
        do con_ruta[2 * q + 1] = entre(0, N / 2 - 1); while (con_ruta[2 * q + 1] == con_ruta[2 * q]);
    }
    double us_con = mediana_us(N, k, o, d, p, con_ruta, Q, 0);
    double us_sin = mediana_us(N, k, o, d, p, sin_ruta, Q, 1);

    printf("\nSCRUM-7 [RN-03] Tiempo de una consulta sin ruta (400 paradas en dos islas)\n");
    printf("  Con ruta (misma isla):     %7.2f us por consulta\n", us_con);
    printf("  Sin ruta (otra isla):      %7.2f us por consulta\n", us_sin);
    int ok = us_con > 0 && us_sin > 0 && us_sin < 5 * us_con;
    if (ok) {
        pasadas++;
        printf("  OK     Sin ruta tarda %.1f veces una consulta normal: mismo orden de tiempo\n", us_sin / us_con);
    } else {
        fallidas++;
        printf("  FALLA  Sin ruta no está en el mismo orden de tiempo (o un resultado no fue el esperado)\n");
    }
}

/* ---------- SCRUM-9: arreglos del tamaño exacto, como los reserva Java ----------
 * Java crea int[numParadas] para el camino e int[1] para el costo, y pasa
 * los tramos en int[numTramos]. Aquí se hace igual, pero con malloc: así, si
 * dijkstra() leyera o escribiera aunque sea una posición de más, Valgrind lo
 * detectaría (los arreglos en la pila no los vigila con esa precisión). */
static int *copia_exacta(const int *datos, int n) {
    int *c = malloc((size_t)n * sizeof(int));
    memcpy(c, datos, (size_t)n * sizeof(int));
    return c;
}

static void todas_las_parejas(const char *nombre, int cerrado) {
    static int o[90], d[90], p[90];
    int m = red40(cerrado, o, d, p);
    int *origen = copia_exacta(o, m), *destino = copia_exacta(d, m), *peso = copia_exacta(p, m);
    int consultas = 0, con_ruta = 0, incoherentes = 0;

    for (int a = 0; a < 40; a++) {
        for (int b = 0; b < 40; b++) {
            int *camino = malloc(40 * sizeof(int));   /* int[numParadas] */
            int *costo = malloc(sizeof(int));         /* int[1] */
            int r = dijkstra(40, m, origen, destino, peso, a, b, camino, costo);
            consultas++;
            if (r > 0) {
                con_ruta++;
                if (camino[0] != a || camino[r - 1] != b || *costo < 0 || (r == 1) != (a == b)) incoherentes++;
            } else if (r != GRAFO_SIN_RUTA) {
                incoherentes++;
            }
            free(camino);
            free(costo);
        }
    }
    free(origen); free(destino); free(peso);

    if (incoherentes == 0) {
        pasadas++;
        printf("  OK     %s: %d consultas, %d con ruta\n", nombre, consultas, con_ruta);
    } else {
        fallidas++;
        printf("  FALLA  %s: %d resultados incoherentes\n", nombre, incoherentes);
    }
}

static void casos_memoria_exacta(void) {
    printf("\nSCRUM-9 [RN-05] Arreglos del tamaño exacto, como los reserva Java\n");
    todas_las_parejas("Red 40, todas las parejas origen-destino", 0);
    todas_las_parejas("Red 40 con el tramo 90 cerrado, todas las parejas", 90);
}

int main(void) {
    double t0 = ahora_us();
    casos_inalcanzables();
    tiempo_inalcanzable();
    casos_degenerados();
    casos_memoria_exacta();
    printf("\n%d pruebas pasaron, %d fallaron  (%.2f s)\n", pasadas, fallidas, (ahora_us() - t0) / 1e6);
    return fallidas == 0 ? 0 : 1;
}
