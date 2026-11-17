# Componente nativo de ruta-service (Santiago Ríos).
#   make             compila libgrafo.so (la que carga Java con JNA) y el programa de prueba
#   make prueba      corre la prueba de los grafos de referencia (SCRUM-5)
#   make medicion    mide montículo vs. búsqueda lineal con 50, 150 y 400 paradas (SCRUM-6)
#   make banco_c     compila el medidor de C puro que usa el banco C vs. Java (SCRUM-37)
#   make test        corre la suite de pruebas (SCRUM-7, SCRUM-8 y SCRUM-9)
#   make valgrind    corre la suite y la prueba dentro de Valgrind, solo en Linux (SCRUM-9)
#   make clean       borra lo compilado
#
# Desde Windows, Valgrind se corre en un contenedor Linux (ver Dockerfile.valgrind):
#   docker build -t villanos-valgrind -f Dockerfile.valgrind .
#   docker run --rm villanos-valgrind
CC     = gcc
CFLAGS = -Wall -Wextra -O2 -g -std=c11

# Valgrind termina con error si encuentra un acceso inválido o cualquier fuga
VALGRIND = valgrind --leak-check=full --show-leak-kinds=all --errors-for-leak-kinds=all --error-exitcode=1

all: libgrafo.so main

# La biblioteca compartida que Java carga con Native.load("grafo", ...)
libgrafo.so: dijkstra.c grafo.h
	$(CC) $(CFLAGS) -shared -fPIC -o $@ dijkstra.c

main: main.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ main.c dijkstra.c

bench: bench.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ bench.c dijkstra.c -lm

banco_c: banco_c.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ banco_c.c dijkstra.c

pruebas: pruebas.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ pruebas.c dijkstra.c

prueba: main
	./main

test: pruebas
	./pruebas

medicion: bench
	./bench

valgrind: pruebas main
	$(VALGRIND) ./pruebas
	$(VALGRIND) ./main

clean:
	rm -f libgrafo.so main main.exe bench bench.exe banco_c banco_c.exe pruebas pruebas.exe grafo.dll

.PHONY: all prueba medicion test valgrind clean
