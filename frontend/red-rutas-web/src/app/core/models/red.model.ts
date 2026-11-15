export interface Parada {
  id: number;
  nombre: string;
  x: number;
  y: number;
}

export interface Tramo {
  id: number;
  origen: number;
  destino: number;
  peso: number;
  abierto: boolean;
}

export interface RedGrafo {
  redId: number;
  nombre: string;
  paradas: Parada[];
  tramos: Tramo[];
}
