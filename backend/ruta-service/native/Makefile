# Componente nativo de ruta-service (Santiago Ríos).
#   make           compila libgrafo.so (la que carga Java con JNA) y el programa de prueba
#   make prueba    corre la prueba del grafo guía (SCRUM-5)
#   make clean     borra lo compilado
CC     = gcc
CFLAGS = -Wall -Wextra -O2 -std=c11

all: libgrafo.so main

# La biblioteca compartida que Java carga con Native.load("grafo", ...)
libgrafo.so: dijkstra.c grafo.h
	$(CC) $(CFLAGS) -shared -fPIC -o $@ dijkstra.c

main: main.c dijkstra.c grafo.h
	$(CC) $(CFLAGS) -o $@ main.c dijkstra.c

prueba: main
	./main

clean:
	rm -f libgrafo.so main main.exe grafo.dll

.PHONY: all prueba clean
