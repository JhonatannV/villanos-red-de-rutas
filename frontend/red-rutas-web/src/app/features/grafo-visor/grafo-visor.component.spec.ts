import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { GrafoVisorComponent } from './grafo-visor.component';
import { RedService } from '../../core/services/red.service';
import { RedGrafo, Parada, Tramo } from '../../core/models/red.model';

describe('GrafoVisorComponent (Criterio de Aceptacion RF-02 Visualizador)', () => {
  let fixture: ComponentFixture<GrafoVisorComponent>;
  let component: GrafoVisorComponent;
  let redService: RedService;

  // Cargar datos de referencia del dataset oficial de 40 paradas y 90 tramos
  const mockParadas40: Parada[] = [
    { id: 0, nombre: 'Terminal de Transportes', x: 80, y: 82 },
    { id: 1, nombre: 'Estación Central', x: 242, y: 95 },
    { id: 2, nombre: 'Parque Boyacá', x: 383, y: 91 },
    { id: 3, nombre: 'UCEVA Campus', x: 524, y: 87 },
    { id: 4, nombre: 'Hospital Tomás Uribe', x: 665, y: 83 },
    { id: 5, nombre: 'Plaza Cívica', x: 806, y: 96 },
    { id: 6, nombre: 'Puente Río Tuluá', x: 968, y: 92 },
    { id: 7, nombre: 'Barrio Victoria', x: 1109, y: 88 },
    { id: 8, nombre: 'C.C. Herradura', x: 90, y: 239 },
    { id: 9, nombre: 'Estadio 12 de Octubre', x: 231, y: 252 },
    { id: 10, nombre: 'SENA Agropecuario', x: 372, y: 248 },
    { id: 11, nombre: 'Coliseo Benicio', x: 534, y: 244 },
    { id: 12, nombre: 'Galería Municipal', x: 675, y: 240 },
    { id: 13, nombre: 'Barrio Sajonia', x: 816, y: 253 },
    { id: 14, nombre: 'Parque Céspedes', x: 957, y: 249 },
    { id: 15, nombre: 'Clínica San Francisco', x: 1098, y: 245 },
    { id: 16, nombre: 'Barrio Salesianos', x: 100, y: 396 },
    { id: 17, nombre: 'Cruce La Variante', x: 241, y: 392 },
    { id: 18, nombre: 'Universidad del Valle', x: 382, y: 405 },
    { id: 19, nombre: 'Parque Carlos Sarmiento', x: 523, y: 401 },
    { id: 20, nombre: 'Barrio El Lago', x: 664, y: 397 },
    { id: 21, nombre: 'Centro Administrativo', x: 805, y: 393 },
    { id: 22, nombre: 'Barrio Farfán', x: 967, y: 406 },
    { id: 23, nombre: 'Zona Franca', x: 1108, y: 402 },
    { id: 24, nombre: 'Barrio La Esperanza', x: 89, y: 553 },
    { id: 25, nombre: 'Plaza de Mercado', x: 230, y: 549 },
    { id: 26, nombre: 'Estación Norte', x: 371, y: 562 },
    { id: 27, nombre: 'Barrio San Antonio', x: 533, y: 558 },
    { id: 28, nombre: 'Parque Guaduales', x: 674, y: 554 },
    { id: 29, nombre: 'Glorieta Las Flores', x: 815, y: 550 },
    { id: 30, nombre: 'Barrio El Príncipe', x: 956, y: 563 },
    { id: 31, nombre: 'C.C. Tuluá Plaza', x: 1097, y: 559 },
    { id: 32, nombre: 'Barrio Tomás Uribe', x: 99, y: 710 },
    { id: 33, nombre: 'Polideportivo Sur', x: 240, y: 706 },
    { id: 34, nombre: 'Barrio Alameda', x: 381, y: 702 },
    { id: 35, nombre: 'Barrio Rubén Cruz', x: 522, y: 715 },
    { id: 36, nombre: 'Salida Sur', x: 663, y: 711 },
    { id: 37, nombre: 'Peaje Aeropuerto', x: 825, y: 707 },
    { id: 38, nombre: 'Terminal Aérea', x: 966, y: 703 },
    { id: 39, nombre: 'Zona Rural La Marina', x: 1107, y: 716 }
  ];

  const mockTramos90: Tramo[] = [
    { id: 1, origen: 0, destino: 1, peso: 3, abierto: true },
    { id: 2, origen: 1, destino: 2, peso: 5, abierto: true },
    { id: 3, origen: 0, destino: 8, peso: 5, abierto: true },
    { id: 4, origen: 1, destino: 9, peso: 4, abierto: true },
    { id: 5, origen: 9, destino: 10, peso: 2, abierto: true },
    { id: 6, origen: 10, destino: 11, peso: 3, abierto: true },
    { id: 7, origen: 8, destino: 11, peso: 9, abierto: true },
    { id: 8, origen: 2, destino: 10, peso: 6, abierto: true },
    { id: 9, origen: 2, destino: 3, peso: 4, abierto: true },
    { id: 10, origen: 8, destino: 9, peso: 3, abierto: true },
    { id: 11, origen: 4, destino: 5, peso: 2, abierto: true },
    { id: 12, origen: 5, destino: 6, peso: 6, abierto: true },
    { id: 13, origen: 5, destino: 13, peso: 3, abierto: true },
    { id: 14, origen: 13, destino: 21, peso: 4, abierto: true },
    { id: 15, origen: 21, destino: 22, peso: 2, abierto: true },
    { id: 16, origen: 4, destino: 12, peso: 5, abierto: true },
    { id: 17, origen: 12, destino: 20, peso: 6, abierto: true },
    { id: 18, origen: 20, destino: 22, peso: 4, abierto: true },
    { id: 19, origen: 3, destino: 4, peso: 3, abierto: true },
    { id: 20, origen: 11, destino: 12, peso: 4, abierto: true },
    { id: 21, origen: 3, destino: 11, peso: 6, abierto: true },
    { id: 22, origen: 6, destino: 7, peso: 11, abierto: true },
    { id: 23, origen: 6, destino: 14, peso: 5, abierto: true },
    { id: 24, origen: 7, destino: 15, peso: 10, abierto: true },
    { id: 25, origen: 8, destino: 16, peso: 7, abierto: true },
    { id: 26, origen: 9, destino: 17, peso: 4, abierto: true },
    { id: 27, origen: 10, destino: 18, peso: 9, abierto: true },
    { id: 28, origen: 11, destino: 19, peso: 6, abierto: true },
    { id: 29, origen: 12, destino: 13, peso: 8, abierto: true },
    { id: 30, origen: 13, destino: 14, peso: 6, abierto: true },
    { id: 31, origen: 14, destino: 15, peso: 4, abierto: true },
    { id: 32, origen: 14, destino: 22, peso: 5, abierto: true },
    { id: 33, origen: 15, destino: 23, peso: 10, abierto: true },
    { id: 34, origen: 16, destino: 17, peso: 9, abierto: true },
    { id: 35, origen: 16, destino: 24, peso: 7, abierto: true },
    { id: 36, origen: 17, destino: 18, peso: 7, abierto: true },
    { id: 37, origen: 17, destino: 25, peso: 4, abierto: true },
    { id: 38, origen: 18, destino: 19, peso: 5, abierto: true },
    { id: 39, origen: 18, destino: 26, peso: 9, abierto: true },
    { id: 40, origen: 19, destino: 20, peso: 3, abierto: true },
    { id: 41, origen: 19, destino: 27, peso: 6, abierto: true },
    { id: 42, origen: 20, destino: 21, peso: 10, abierto: true },
    { id: 43, origen: 20, destino: 28, peso: 11, abierto: true },
    { id: 44, origen: 21, destino: 29, peso: 8, abierto: true },
    { id: 45, origen: 22, destino: 23, peso: 6, abierto: true },
    { id: 46, origen: 22, destino: 30, peso: 5, abierto: true },
    { id: 47, origen: 23, destino: 31, peso: 10, abierto: true },
    { id: 48, origen: 24, destino: 25, peso: 11, abierto: true },
    { id: 49, origen: 24, destino: 32, peso: 7, abierto: true },
    { id: 50, origen: 25, destino: 26, peso: 9, abierto: true },
    { id: 51, origen: 25, destino: 33, peso: 4, abierto: true },
    { id: 52, origen: 26, destino: 27, peso: 7, abierto: true },
    { id: 53, origen: 26, destino: 34, peso: 9, abierto: true },
    { id: 54, origen: 27, destino: 28, peso: 5, abierto: true },
    { id: 55, origen: 27, destino: 35, peso: 6, abierto: true },
    { id: 56, origen: 28, destino: 29, peso: 3, abierto: true },
    { id: 57, origen: 28, destino: 36, peso: 11, abierto: true },
    { id: 58, origen: 29, destino: 30, peso: 10, abierto: true },
    { id: 59, origen: 30, destino: 31, peso: 8, abierto: true },
    { id: 60, origen: 32, destino: 33, peso: 4, abierto: true },
    { id: 61, origen: 33, destino: 34, peso: 11, abierto: true },
    { id: 62, origen: 34, destino: 35, peso: 9, abierto: true },
    { id: 63, origen: 35, destino: 36, peso: 7, abierto: true },
    { id: 64, origen: 0, destino: 9, peso: 5, abierto: true },
    { id: 65, origen: 1, destino: 10, peso: 8, abierto: true },
    { id: 66, origen: 1, destino: 8, peso: 10, abierto: true },
    { id: 67, origen: 2, destino: 11, peso: 11, abierto: true },
    { id: 68, origen: 2, destino: 9, peso: 7, abierto: true },
    { id: 69, origen: 3, destino: 12, peso: 7, abierto: true },
    { id: 70, origen: 3, destino: 10, peso: 11, abierto: true },
    { id: 71, origen: 4, destino: 13, peso: 10, abierto: true },
    { id: 72, origen: 4, destino: 11, peso: 8, abierto: true },
    { id: 73, origen: 5, destino: 14, peso: 6, abierto: true },
    { id: 74, origen: 5, destino: 12, peso: 12, abierto: true },
    { id: 75, origen: 6, destino: 15, peso: 9, abierto: true },
    { id: 76, origen: 6, destino: 13, peso: 9, abierto: true },
    { id: 77, origen: 7, destino: 14, peso: 6, abierto: true },
    { id: 78, origen: 8, destino: 17, peso: 8, abierto: true },
    { id: 79, origen: 9, destino: 18, peso: 11, abierto: true },
    { id: 80, origen: 9, destino: 16, peso: 7, abierto: true },
    { id: 81, origen: 10, destino: 19, peso: 7, abierto: true },
    { id: 82, origen: 10, destino: 17, peso: 11, abierto: true },
    { id: 83, origen: 11, destino: 20, peso: 10, abierto: true },
    { id: 84, origen: 11, destino: 18, peso: 8, abierto: true },
    { id: 85, origen: 12, destino: 21, peso: 6, abierto: true },
    { id: 86, origen: 12, destino: 19, peso: 12, abierto: true },
    { id: 87, origen: 37, destino: 38, peso: 4, abierto: true },
    { id: 88, origen: 38, destino: 39, peso: 5, abierto: true },
    { id: 89, origen: 37, destino: 39, peso: 7, abierto: true },
    { id: 90, origen: 36, destino: 37, peso: 6, abierto: true }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GrafoVisorComponent],
      providers: [
        RedService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(GrafoVisorComponent);
    component = fixture.componentInstance;
    redService = TestBed.inject(RedService);
  });

  it('debe crearse correctamente con valores iniciales de zoom y vista', () => {
    expect(component).toBeTruthy();
    expect(component.zoom()).toBe(1.0);
    expect(component.panX()).toBe(0);
    expect(component.panY()).toBe(0);
    expect(component.viewBox()).toBe('0 0 1200 800');
  });

  it('debe aumentar y disminuir el zoom con limites seguros', () => {
    component.zoomIn();
    expect(component.zoom()).toBe(1.2);
    expect(component.viewBox()).toBe('0 0 1000 666.7');

    component.zoomOut();
    expect(component.zoom()).toBe(1.0);

    component.resetVista();
    expect(component.zoom()).toBe(1.0);
    expect(component.panX()).toBe(0);
    expect(component.panY()).toBe(0);
  });

  it('debe soportar interacciones de paneo con el raton', () => {
    // Iniciar arrastre
    component.onMouseDown({ button: 0, clientX: 200, clientY: 200 } as MouseEvent);

    // Mover 50px a la izquierda y 30px arriba
    component.onMouseMove({ clientX: 150, clientY: 170 } as MouseEvent);

    expect(component.panX()).toBe(50);
    expect(component.panY()).toBe(30);

    // Soltar raton
    component.onMouseUp();
    // Mover despues de soltar no debe alterar el paneo
    component.onMouseMove({ clientX: 100, clientY: 100 } as MouseEvent);
    expect(component.panX()).toBe(50);
  });

  it('debe permitir seleccionar una parada e identificar sus tramos conectados', () => {
    redService.paradas.set(mockParadas40);
    redService.tramos.set(mockTramos90);

    component.seleccionarParada(mockParadas40[0]);
    expect(component.paradaSeleccionada()?.id).toBe(0);

    // Tramo 1 conecta origen 0 con destino 1 -> conectado = true
    expect(component.esTramoConectado(mockTramos90[0])).toBe(true);

    // Tramo 2 conecta origen 1 con destino 2 -> conectado = false
    expect(component.esTramoConectado(mockTramos90[1])).toBe(false);
  });

  it('Criterio RF-02: Con 40 paradas visibles, ninguna etiqueta de peso queda solapada a nivel de zoom por defecto', () => {
    // Cargar las 40 paradas y 90 tramos del grafo real
    redService.paradas.set(mockParadas40);
    redService.tramos.set(mockTramos90);

    const etiquetas = component.etiquetasPeso();
    expect(etiquetas.length).toBe(90);

    // Verificación matemática estricta: calcular distancia euclidiana entre cada par de etiquetas
    // Ninguna etiqueta debe solaparse a nivel de zoom por defecto (distancia >= 14px)
    let colisiones = 0;
    const distanciaMinimaSegura = 14.0; // px

    for (let i = 0; i < etiquetas.length; i++) {
      for (let j = i + 1; j < etiquetas.length; j++) {
        const dx = etiquetas[i].x - etiquetas[j].x;
        const dy = etiquetas[i].y - etiquetas[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < distanciaMinimaSegura) {
          colisiones++;
          console.warn(`Colisión detectada entre tramo ${etiquetas[i].tramoId} y ${etiquetas[j].tramoId}: dist=${dist}`);
        }
      }
    }

    expect(colisiones).toBe(0);
  });

  it('debe calcular la ruta optima con Dijkstra entre Terminal de Transportes (0) y Coliseo Benicio (11)', () => {
    redService.paradas.set(mockParadas40);
    redService.tramos.set(mockTramos90);

    const orig = mockParadas40[0]; // Terminal de Transportes (0)
    const dest = mockParadas40[11]; // Coliseo Benicio (11)

    component.marcarComoOrigen(orig);
    component.marcarComoDestino(dest);

    const ruta = component.rutaOptima();
    expect(ruta).toBeTruthy();
    expect(ruta?.encontrada).toBe(true);
    // Costo óptimo esperado según el caso de prueba 1: 10 minutos
    expect(ruta?.costoTotal).toBe(10);
    // Secuencia de paradas esperada: [0, 9, 10, 11]
    const ids = ruta?.paradas.map(p => p.id);
    expect(ids).toEqual([0, 9, 10, 11]);
  });

  it('debe detectar cuando un destino queda inalcanzable por contingencia vial (cierre de tramo 90)', () => {
    // Clonar tramos y cerrar el tramo 90 (Salida Sur [36] <-> Peaje Aeropuerto [37])
    const tramosConPuenteCerrado = mockTramos90.map(t => t.id === 90 ? { ...t, abierto: false } : t);
    redService.paradas.set(mockParadas40);
    redService.tramos.set(tramosConPuenteCerrado);

    const orig = mockParadas40[0]; // Terminal de Transportes (0)
    const dest = mockParadas40[39]; // Zona Rural La Marina (39)

    component.marcarComoOrigen(orig);
    component.marcarComoDestino(dest);

    const ruta = component.rutaOptima();
    expect(ruta).toBeTruthy();
    expect(ruta?.encontrada).toBe(false);
    expect(ruta?.mensaje).toContain('No existe camino vial disponible');
  });
});
