# Componente nativo de ruta-service (Santiago Ríos).
#   make             compila libgrafo.so (la que carga Java con JNA) y el programa de prueba
#   make prueba      corre la prueba de los grafos de referencia (SCRUM-5)
#   make medicion    mide montículo vs. búsqueda lineal con 50, 150 y 400 paradas (SCRUM-6)
#   make test        corre la suite de pruebas: destino inalcanzable (SCRUM-7)
#   make clean       borra lo compilado
CC     = gcc
CFLAGS = -Wall -Wextra -O2 -std=c11

all: libgrafo.so main

# La biblioteca compartida que Java carga con Native.load("grafo", ...)
libgrafo.so: dijkstra.c grafo.h
	$(CC) $(CFLAGS) -shared -fPIC -o $@ dijkstra.c

main: main.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ main.c dijkstra.c

bench: bench.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ bench.c dijkstra.c -lm

pruebas: pruebas.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ pruebas.c dijkstra.c

prueba: main
	./main

test: pruebas
	./pruebas

medicion: bench
	./bench

clean:
	rm -f libgrafo.so main main.exe bench bench.exe pruebas pruebas.exe grafo.dll

.PHONY: all prueba medicion test clean
