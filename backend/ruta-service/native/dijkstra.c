/*
 * dijkstra.c — Dijkstra con cola de prioridad (montículo binario).
 *
 * Pasos:
 *   1. Validar la entrada (nada de leer fuera de los arreglos).
 *   2. Armar la lista de adyacencia a partir de los tres arreglos paralelos.
 *   3. Dijkstra: sacar siempre la parada más barata del montículo y relajar
 *      sus vecinos. Desempate: a igual costo, gana el camino de menos paradas.
 *   4. Reconstruir el camino hacia atrás con anterior[] y escribirlo en orden.
 */
#include <stdlib.h>
#include <limits.h>
#include "grafo.h"

/* ---------- Montículo binario (mínimo) ---------- */

typedef struct {
    long long costo;  /* distancia acumulada desde el inicio */
    int saltos;       /* cuántos tramos lleva el camino (para el desempate) */
    int parada;
} Entrada;

typedef struct {
    Entrada *datos;
    int tam;
} Monticulo;

/* a va antes que b si cuesta menos; a igual costo, si tiene menos saltos */
static int va_antes(Entrada a, Entrada b) {
    if (a.costo != b.costo) return a.costo < b.costo;
    return a.saltos < b.saltos;
}

static void intercambiar(Entrada *a, Entrada *b) {
    Entrada t = *a;
    *a = *b;
    *b = t;
}

static void meter(Monticulo *m, Entrada e) {
    int i = m->tam++;
    m->datos[i] = e;
    /* subir mientras sea más barato que su padre */
    while (i > 0) {
        int padre = (i - 1) / 2;
        if (!va_antes(m->datos[i], m->datos[padre])) break;
        intercambiar(&m->datos[i], &m->datos[padre]);
        i = padre;
    }
}

static Entrada sacar(Monticulo *m) {
    Entrada raiz = m->datos[0];
    m->datos[0] = m->datos[--m->tam];
    /* bajar mientras algún hijo sea más barato */
    int i = 0;
    for (;;) {
        int izq = 2 * i + 1, der = 2 * i + 2, menor = i;
        if (izq < m->tam && va_antes(m->datos[izq], m->datos[menor])) menor = izq;
        if (der < m->tam && va_antes(m->datos[der], m->datos[menor])) menor = der;
        if (menor == i) break;
        intercambiar(&m->datos[i], &m->datos[menor]);
        i = menor;
    }
    return raiz;
}

/* ---------- Dijkstra ---------- */

int dijkstra(int num_paradas, int num_tramos,
             const int *origen, const int *destino, const int *peso,
             int parada_inicio, int parada_fin,
             int *camino_salida, int *costo_salida) {
    /* 1. Validación */
    if (num_paradas <= 0 || num_tramos < 0) return GRAFO_ARGUMENTO_INVALIDO;
    if (camino_salida == NULL || costo_salida == NULL) return GRAFO_ARGUMENTO_INVALIDO;
    if (num_tramos > 0 && (origen == NULL || destino == NULL || peso == NULL))
        return GRAFO_ARGUMENTO_INVALIDO;
    if (parada_inicio < 0 || parada_inicio >= num_paradas ||
        parada_fin < 0 || parada_fin >= num_paradas)
        return GRAFO_FUERA_DE_RANGO;
    for (int i = 0; i < num_tramos; i++) {
        if (origen[i] < 0 || origen[i] >= num_paradas ||
            destino[i] < 0 || destino[i] >= num_paradas)
            return GRAFO_FUERA_DE_RANGO;
        if (peso[i] < 0) return GRAFO_ARGUMENTO_INVALIDO;
    }

    /* Origen igual a destino: camino de una parada, costo cero */
    if (parada_inicio == parada_fin) {
        camino_salida[0] = parada_inicio;
        *costo_salida = 0;
        return 1;
    }

    /* Toda la memoria de trabajo se pide aquí y se libera al final */
    int arcos = 2 * num_tramos;                        /* cada tramo va y vuelve */
    int *inicio_vecinos = calloc((size_t)num_paradas + 1, sizeof(int));
    int *vecino = malloc(((size_t)arcos + 1) * sizeof(int));
    int *peso_vecino = malloc(((size_t)arcos + 1) * sizeof(int));
    long long *dist = malloc((size_t)num_paradas * sizeof(long long));
    int *saltos = malloc((size_t)num_paradas * sizeof(int));
    int *anterior = malloc((size_t)num_paradas * sizeof(int));
    char *cerrada = calloc((size_t)num_paradas, 1);
    Monticulo cola = { malloc(((size_t)arcos + 1) * sizeof(Entrada)), 0 };
    int resultado;

    if (!inicio_vecinos || !vecino || !peso_vecino || !dist || !saltos ||
        !anterior || !cerrada || !cola.datos) {
        resultado = GRAFO_SIN_MEMORIA;
        goto liberar;
    }

    /* 2. Lista de adyacencia compacta: los vecinos de la parada p están en
     *    vecino[inicio_vecinos[p]] ... vecino[inicio_vecinos[p+1] - 1] */
    for (int i = 0; i < num_tramos; i++) {
        inicio_vecinos[origen[i] + 1]++;
        inicio_vecinos[destino[i] + 1]++;
    }
    for (int p = 0; p < num_paradas; p++)
        inicio_vecinos[p + 1] += inicio_vecinos[p];
    {
        int *siguiente = anterior; /* se reutiliza como contador temporal */
        for (int p = 0; p < num_paradas; p++) siguiente[p] = inicio_vecinos[p];
        for (int i = 0; i < num_tramos; i++) {
            int a = origen[i], b = destino[i];
            vecino[siguiente[a]] = b; peso_vecino[siguiente[a]++] = peso[i];
            vecino[siguiente[b]] = a; peso_vecino[siguiente[b]++] = peso[i];
        }
    }

    /* 3. Dijkstra */
    for (int p = 0; p < num_paradas; p++) {
        dist[p] = LLONG_MAX;
        saltos[p] = INT_MAX;
        anterior[p] = -1;
    }
    dist[parada_inicio] = 0;
    saltos[parada_inicio] = 0;
    meter(&cola, (Entrada){ 0, 0, parada_inicio });

    while (cola.tam > 0) {
        Entrada actual = sacar(&cola);
        int u = actual.parada;
        if (cerrada[u]) continue;          /* entrada vieja: ya se cerró */
        cerrada[u] = 1;
        if (u == parada_fin) break;        /* ya no puede mejorar */

        for (int k = inicio_vecinos[u]; k < inicio_vecinos[u + 1]; k++) {
            int v = vecino[k];
            if (cerrada[v]) continue;
            Entrada nueva = { dist[u] + peso_vecino[k], saltos[u] + 1, v };
            Entrada vieja = { dist[v], saltos[v], v };
            if (va_antes(nueva, vieja)) {
                dist[v] = nueva.costo;
                saltos[v] = nueva.saltos;
                anterior[v] = u;
                meter(&cola, nueva);
            }
        }
    }

    if (!cerrada[parada_fin]) {
        resultado = GRAFO_SIN_RUTA;
        goto liberar;
    }
    if (dist[parada_fin] > INT_MAX) {
        resultado = GRAFO_COSTO_DESBORDADO;
        goto liberar;
    }

    /* 4. El camino tiene saltos + 1 paradas; se llena de atrás hacia adelante */
    resultado = saltos[parada_fin] + 1;
    {
        int i = resultado - 1;
        for (int p = parada_fin; p != -1; p = anterior[p])
            camino_salida[i--] = p;
    }
    *costo_salida = (int)dist[parada_fin];

liberar:
    free(inicio_vecinos);
    free(vecino);
    free(peso_vecino);
    free(dist);
    free(saltos);
    free(anterior);
    free(cerrada);
    free(cola.datos);
    return resultado;
}
