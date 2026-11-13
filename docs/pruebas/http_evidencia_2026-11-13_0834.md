# Evidencia HTTP de seguridad - corrida 2026-11-13_0834

Usuarios de prueba del realm rutas-realm: consultor y planificador. Los tokens se omiten.

| # | Caso | Esperado | Obtenido | Cumple |
|---:|---|:---:|:---:|:---:|
| 1 | Sin token, gateway, consultar ruta | 401 | 401 | Si |
| 2 | Sin token, grafo-service directo, consultar red | 401 | 401 | Si |
| 3 | Sin token, ruta-service directo, consultar ruta | 401 | 401 | Si |
| 4 | Firma alterada, gateway, consultar ruta | 401 | 401 | Si |
| 5 | Firma alterada, grafo-service directo, consultar red | 401 | 401 | Si |
| 6 | Firma alterada, ruta-service directo, consultar ruta | 401 | 401 | Si |
| 7 | Consultor intenta crear una parada (nodo) | 403 | 403 | Si |
| 8 | Consultor intenta modificar un tramo (arista) | 403 | 403 | Si |
| 9 | Consultor intenta eliminar una parada (nodo) | 403 | 403 | Si |
| 10 | Consultor intenta crear una parada, grafo-service directo | 403 | 403 | Si |
| 11 | Consultor consulta la red | 200 | 200 | Si |
| 12 | Consultor calcula una ruta | 200 | 200 | Si |
| 13 | Planificador modifica un tramo (lo deja abierto) | 200 | 200 | Si |
| 14 | Planificador consulta la auditoria | 200 | 200 | Si |

## Comandos y respuestas

### 1. Sin token, gateway, consultar ruta
```
curl -X GET "http://localhost:8080/api/rutas/calcular?desde=0&hasta=11"
-> HTTP 401
```

### 2. Sin token, grafo-service directo, consultar red
```
curl -X GET "http://localhost:8081/api/redes/1"
-> HTTP 401
```

### 3. Sin token, ruta-service directo, consultar ruta
```
curl -X GET "http://localhost:8082/api/rutas/calcular?desde=0&hasta=11"
-> HTTP 401
```

### 4. Firma alterada, gateway, consultar ruta
```
curl -X GET -H "Authorization: Bearer <token alterado>" "http://localhost:8080/api/rutas/calcular?desde=0&hasta=11"
-> HTTP 401
```

### 5. Firma alterada, grafo-service directo, consultar red
```
curl -X GET -H "Authorization: Bearer <token alterado>" "http://localhost:8081/api/redes/1"
-> HTTP 401
```

### 6. Firma alterada, ruta-service directo, consultar ruta
```
curl -X GET -H "Authorization: Bearer <token alterado>" "http://localhost:8082/api/rutas/calcular?desde=0&hasta=11"
-> HTTP 401
```

### 7. Consultor intenta crear una parada (nodo)
```
curl -X POST -H "Authorization: Bearer <token consultor>" -H "Content-Type: application/json" -d '{"nombre":"Parada de prueba","x":10,"y":10}' "http://localhost:8080/api/redes/1/paradas"
-> HTTP 403
```

### 8. Consultor intenta modificar un tramo (arista)
```
curl -X PUT -H "Authorization: Bearer <token consultor>" "http://localhost:8080/api/redes/1/tramos/5/estado?abierto=true"
-> HTTP 403
```

### 9. Consultor intenta eliminar una parada (nodo)
```
curl -X DELETE -H "Authorization: Bearer <token consultor>" "http://localhost:8080/api/redes/1/paradas/39"
-> HTTP 403
```

### 10. Consultor intenta crear una parada, grafo-service directo
```
curl -X POST -H "Authorization: Bearer <token consultor>" -H "Content-Type: application/json" -d '{"nombre":"Parada de prueba","x":10,"y":10}' "http://localhost:8081/api/redes/1/paradas"
-> HTTP 403
```

### 11. Consultor consulta la red
```
curl -X GET -H "Authorization: Bearer <token consultor>" "http://localhost:8080/api/redes/1"
-> HTTP 200
{"redId":1,"nombre":"Red Urbana de Transporte (40 Paradas)","paradas":[{"id":0,"nombre":"Terminal de Transportes","x":80.0,"y":82.0},{"id":1,"nombre":"Estación ...
```

### 12. Consultor calcula una ruta
```
curl -X GET -H "Authorization: Bearer <token consultor>" "http://localhost:8080/api/rutas/calcular?desde=0&hasta=11"
-> HTTP 200
{"redId":1,"desde":0,"hasta":11,"costoTotal":10,"camino":[0,9,10,11],"nodosCerrados":[],"modoEfectivo":"NATIVE","tramoCerrado":null}
```

### 13. Planificador modifica un tramo (lo deja abierto)
```
curl -X PUT -H "Authorization: Bearer <token planificador>" "http://localhost:8080/api/redes/1/tramos/5/estado?abierto=true"
-> HTTP 200
{"id":5,"origen":9,"destino":10,"peso":2,"abierto":true}
```

### 14. Planificador consulta la auditoria
```
curl -X GET -H "Authorization: Bearer <token planificador>" "http://localhost:8080/api/redes/1/auditoria"
-> HTTP 200
[{"id":1,"usuarioSub":"4cfcb882-514f-453c-b857-2b3a2a0049a9","usuarioUsername":"planificador","fecha":"2026-11-13T13:34:10.844430Z","operacion":"CAMBIAR_ESTADO_...
```

