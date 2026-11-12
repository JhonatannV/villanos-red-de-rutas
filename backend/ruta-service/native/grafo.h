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
 * Códigos de error de dijkstra() (siempre negativos).
 * Java solo necesita saber que "< 0" significa "no hay ruta válida";
 * el número exacto dice por qué.
 */
#define GRAFO_SIN_RUTA            (-1) /* no hay camino entre las dos paradas   */
#define GRAFO_FUERA_DE_RANGO      (-2) /* una parada no existe en la red        */
#define GRAFO_ARGUMENTO_INVALIDO  (-3) /* puntero nulo, tamaño o peso negativo  */
#define GRAFO_SIN_MEMORIA         (-4) /* malloc falló                          */
#define GRAFO_COSTO_DESBORDADO    (-5) /* el costo total no cabe en un int      */

/*
 * [RN-01 a RN-05]
 * La red llega como tres arreglos paralelos: el tramo i une origen[i] con
 * destino[i] y cuesta peso[i]. Paradas numeradas de 0 a num_paradas - 1.
 * Los tramos se recorren en los dos sentidos.
 *
 * Devuelve la cantidad de paradas del camino (>= 1) o un código negativo.
 * camino_salida y costo_salida los reserva quien llama (Java):
 * camino_salida debe tener espacio para num_paradas enteros y costo_salida
 * para 1. C solo los llena; nunca los libera. La memoria de trabajo que C
 * pide por dentro la libera antes de retornar.
 * Si hay empate de costo, gana el camino con menos paradas.
 * Si devuelve un código negativo, no escribe nada en la salida.
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
