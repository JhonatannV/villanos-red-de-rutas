/*
 * grafo.h — Interfaz oficial del componente nativo en C
 * Proyecto Integrador: Red de Rutas · Equipo Villanos
 * Arquitectura de Computadores · UCEVA
 */

#ifndef GRAFO_H
#define GRAFO_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Estructura de arista para el grafo
 */
typedef struct {
    int origen;
    int destino;
    double peso;
} Arista;

/*
 * [RN-01 a RN-05]
 * Devuelve la cantidad de paradas del camino, o -1 si no hay ruta.
 * camino_salida y costo_salida los reserva quien llama (Java):
 * camino_salida debe tener espacio para num_paradas enteros.
 */
int dijkstra(int num_paradas,
             int num_tramos,
             const int *origen,
             const int *destino,
             const int *peso,
             int parada_inicio,
             int parada_fin,
             int *camino_salida,
             int *costo_salida);

/*
 * [RF-03 & RF-04]
 * Devuelve el costo total, o -1 si no hay camino.
 * orden_cierre y distancia_final traen el recorrido paso a paso del algoritmo.
 */
double ruta_minima(const Arista* aristas, int n_aristas, int n_nodos,
                   int origen, int destino,
                   int* camino, int* largo_camino,
                   int* orden_cierre, double* distancia_final);

/*
 * [RF-06]
 * Calcula la cantidad de componentes conexas en la red (grupos incomunicados).
 */
int componentes_conexas(const Arista* aristas, int n_aristas, int n_nodos,
                        int* etiqueta_por_nodo);

/*
 * [RF-05]
 * Recorridos en anchura (en_anchura = 1) o profundidad (en_anchura = 0).
 */
int recorrido(const Arista* aristas, int n_aristas, int n_nodos,
              int inicio, int en_anchura, int* orden);

#ifdef __cplusplus
}
#endif

#endif /* GRAFO_H */
