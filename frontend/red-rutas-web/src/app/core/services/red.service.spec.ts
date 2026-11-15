import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RedService } from './red.service';
import { RedGrafo, Parada, Tramo } from '../models/red.model';

describe('RedService (Criterio de Aceptacion RF-01 Carga y Persistencia)', () => {
  let service: RedService;
  let httpTestingController: HttpTestingController;

  const mockParadas40: Parada[] = Array.from({ length: 40 }, (_, i) => ({
    id: i,
    nombre: `Estacion ${i}`,
    x: 4.08 + (i * 0.001),
    y: -76.19 - (i * 0.001)
  }));

  const mockTramos90: Tramo[] = Array.from({ length: 90 }, (_, i) => ({
    id: i,
    origen: i % 40,
    destino: (i + 1) % 40,
    peso: (i % 15) + 2,
    abierto: true
  }));

  const mockRed40x90: RedGrafo = {
    redId: 1,
    nombre: 'Red Troncal Principal',
    paradas: mockParadas40,
    tramos: mockTramos90
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        RedService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(RedService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('debe crearse correctamente', () => {
    expect(service).toBeTruthy();
    expect(service.totalParadas()).toBe(0);
    expect(service.totalTramos()).toBe(0);
  });

  it('debe cargar la red con 40 paradas y 90 tramos y actualizar señales reactivas', () => {
    service.cargarRed(1).subscribe(red => {
      expect(red.redId).toBe(1);
    });

    const req = httpTestingController.expectOne(req => req.url.includes('/api/redes/1'));
    expect(req.request.method).toBe('GET');
    req.flush(mockRed40x90);

    expect(service.totalParadas()).toBe(40);
    expect(service.totalTramos()).toBe(90);
    expect(service.tramosAbiertos()).toBe(90);
    expect(service.tramosCerrados()).toBe(0);
  });

  it('debe persistir una nueva parada en el servidor via POST', () => {
    // Cargar red base primero
    service.cargarRed(1).subscribe();
    httpTestingController.expectOne(req => req.url.includes('/api/redes/1')).flush(mockRed40x90);

    const nueva = { nombre: 'Terminal Intermodal', x: 4.095, y: -76.185 };
    service.crearParada(nueva, 1).subscribe();

    const postReq = httpTestingController.expectOne(req => req.url.includes('/api/redes/1/paradas'));
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body.nombre).toBe('Terminal Intermodal');

    postReq.flush({ id: 40, nombre: 'Terminal Intermodal', x: 4.095, y: -76.185 });

    expect(service.totalParadas()).toBe(41);
    expect(service.paradas().some(p => p.nombre === 'Terminal Intermodal')).toBe(true);
  });

  it('debe rechazar la creación de tramo con peso menor o igual a cero', () => {
    let errorRecibido: Error | null = null;
    service.crearTramo(0, 1, 0, 1).subscribe({
      error: err => errorRecibido = err
    });

    expect(errorRecibido).toBeTruthy();
    expect(service.mensajeError()).toContain('estrictamente positivo');
  });

  it('debe rechazar la creación de tramo con origen y destino idénticos', () => {
    let errorRecibido: Error | null = null;
    service.crearTramo(5, 5, 10, 1).subscribe({
      error: err => errorRecibido = err
    });

    expect(errorRecibido).toBeTruthy();
    expect(service.mensajeError()).toContain('origen y destino deben ser distintos');
  });

  it('debe persistir un nuevo tramo en el servidor via POST cuando es valido', () => {
    service.cargarRed(1).subscribe();
    httpTestingController.expectOne(req => req.url.includes('/api/redes/1')).flush(mockRed40x90);

    service.crearTramo(0, 15, 8, 1).subscribe();

    const postReq = httpTestingController.expectOne(req => req.url.includes('/api/redes/1/tramos'));
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body.peso).toBe(8);

    postReq.flush({ id: 90, origen: 0, destino: 15, peso: 8, abierto: true });

    expect(service.totalTramos()).toBe(91);
  });

  it('debe cambiar el estado de un tramo (cerrar/abrir) via PUT', () => {
    service.cargarRed(1).subscribe();
    httpTestingController.expectOne(req => req.url.includes('/api/redes/1')).flush(mockRed40x90);

    service.cambiarEstadoTramo(0, false, 1).subscribe();

    const putReq = httpTestingController.expectOne(req => req.url.includes('/api/redes/1/tramos/0/estado?abierto=false'));
    expect(putReq.request.method).toBe('PUT');

    putReq.flush({ id: 0, origen: 0, destino: 1, peso: 2, abierto: false });

    expect(service.tramosCerrados()).toBe(1);
    expect(service.tramosAbiertos()).toBe(89);
  });

  it('Criterio RF-01: Se carga red de 40 paradas y 90 tramos, se recarga y aparece identica en posiciones y pesos', () => {
    // 1. Carga inicial
    service.cargarRed(1).subscribe();
    httpTestingController.expectOne(req => req.url.includes('/api/redes/1')).flush(mockRed40x90);

    const paradasIniciales = service.paradas();
    const tramosIniciales = service.tramos();

    expect(paradasIniciales.length).toBe(40);
    expect(tramosIniciales.length).toBe(90);

    // 2. Simulación de recarga de página (segunda carga de la red)
    service.cargarRed(1).subscribe();
    httpTestingController.expectOne(req => req.url.includes('/api/redes/1')).flush(mockRed40x90);

    const paradasRecargadas = service.paradas();
    const tramosRecargados = service.tramos();

    // Verificación estricta de paridad idéntica en posiciones (x, y) y pesos
    for (let i = 0; i < 40; i++) {
      expect(paradasRecargadas[i].id).toBe(paradasIniciales[i].id);
      expect(paradasRecargadas[i].x).toBe(paradasIniciales[i].x);
      expect(paradasRecargadas[i].y).toBe(paradasIniciales[i].y);
      expect(paradasRecargadas[i].nombre).toBe(paradasIniciales[i].nombre);
    }

    for (let j = 0; j < 90; j++) {
      expect(tramosRecargados[j].id).toBe(tramosIniciales[j].id);
      expect(tramosRecargados[j].origen).toBe(tramosIniciales[j].origen);
      expect(tramosRecargados[j].destino).toBe(tramosIniciales[j].destino);
      expect(tramosRecargados[j].peso).toBe(tramosIniciales[j].peso);
    }
  });
});
