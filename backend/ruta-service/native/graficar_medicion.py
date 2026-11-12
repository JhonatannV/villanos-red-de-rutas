"""Dibuja la gráfica de SCRUM-6 a partir del CSV que genera ./bench --csv.

Uso:  python graficar_medicion.py ../../../docs/mediciones/scrum6_monticulo.csv salida.svg
Solo usa la biblioteca estándar de Python (no necesita matplotlib).
"""
import csv
import sys

AZUL, ROJO, GRIS, TINTA, SUAVE = "#2050cc", "#cc3b30", "#9aa4b2", "#162030", "#5a6678"


def paso_redondo(bruto):
    """Paso de rejilla redondo (1, 2 o 5 por una potencia de 10)."""
    potencia = 10 ** len(str(int(bruto))) / 10 if bruto >= 1 else 1
    for k in (1, 2, 5, 10):
        if k * potencia >= bruto:
            return k * potencia
    return 10 * potencia


def panel(filas, x0, y0, ancho, alto, titulo):
    """Un panel: tiempo por consulta (us) contra número de tramos."""
    e = [f["tramos"] for f in filas]
    m = [f["us_monticulo"] for f in filas]
    lin = [f["us_lineal"] for f in filas]
    ref = [m[0] * t / e[0] for t in e]  # si el tiempo creciera exactamente igual que los tramos
    xmax = max(e) * 1.05
    paso = paso_redondo(max(lin) * 1.1 / 5)
    ymax = paso * (int(max(lin) * 1.1 / paso) + 1)
    izq, abajo = 58, 44
    w, h = ancho - izq - 16, alto - abajo - 40

    def px(x):
        return x0 + izq + x / xmax * w

    def py(y):
        return y0 + 40 + h - y / ymax * h

    s = [f'<text x="{x0 + izq}" y="{y0 + 22}" font-size="14" font-weight="700" fill="{TINTA}">{titulo}</text>']
    yv = 0
    while yv <= ymax + 1e-9:  # rejilla y eje Y con números redondos
        s.append(f'<line x1="{px(0):.1f}" y1="{py(yv):.1f}" x2="{px(xmax):.1f}" y2="{py(yv):.1f}" stroke="#e3e7ed"/>')
        s.append(f'<text x="{px(0) - 8:.1f}" y="{py(yv) + 4:.1f}" font-size="11" text-anchor="end" fill="{SUAVE}">{yv:,.0f}</text>')
        yv += paso
    ultimo_x = -1e9
    for t in reversed(e):  # marcas del eje X; se omiten las que se encimarían
        if ultimo_x - px(t) < 34:
            if ultimo_x != -1e9:
                continue
        s.append(f'<text x="{px(t):.1f}" y="{py(0) + 18:.1f}" font-size="11" text-anchor="middle" fill="{SUAVE}">{t:,}</text>')
        ultimo_x = px(t)
    s.append(f'<text x="{px(xmax / 2):.1f}" y="{y0 + alto - 4}" font-size="12" text-anchor="middle" fill="{SUAVE}">Tramos de la red</text>')
    s.append(f'<text x="{x0 + 14}" y="{py(ymax / 2):.1f}" font-size="12" text-anchor="middle" fill="{SUAVE}" transform="rotate(-90 {x0 + 14} {py(ymax / 2):.1f})">Microsegundos por consulta</text>')

    def serie(ys, color, guiones=""):
        pts = " ".join(f"{px(x):.1f},{py(y):.1f}" for x, y in zip(e, ys))
        extra = f' stroke-dasharray="{guiones}"' if guiones else ""
        out = [f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="2.5"{extra}/>']
        if not guiones:
            out += [f'<circle cx="{px(x):.1f}" cy="{py(y):.1f}" r="3.5" fill="{color}"/>' for x, y in zip(e, ys)]
        return out

    s += serie(ref, GRIS, "5 5") + serie(lin, ROJO) + serie(m, AZUL)
    s.append(f'<text x="{px(e[-1]) - 4:.1f}" y="{py(lin[-1]) - 8:.1f}" font-size="12" text-anchor="end" fill="{ROJO}" font-weight="700">Búsqueda lineal {lin[-1]:,.0f} us</text>')
    s.append(f'<text x="{px(e[-1]) - 4:.1f}" y="{py(m[-1]) - 8:.1f}" font-size="12" text-anchor="end" fill="{AZUL}" font-weight="700">Montículo {m[-1]:,.0f} us</text>')
    return s


def main():
    entrada, salida = sys.argv[1], sys.argv[2]
    with open(entrada, encoding="utf-8") as f:
        filas = [{k: float(v) for k, v in r.items()} for r in csv.DictReader(f)]
    for r in filas:
        r["tramos"] = int(r["tramos"])
    pedidas = [r for r in filas if r["paradas"] <= 400]

    W, H = 1000, 420
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" font-family="Segoe UI, Arial, sans-serif">',
         f'<rect width="{W}" height="{H}" fill="#ffffff"/>']
    s += panel(pedidas, 0, 0, W / 2, H - 40, "50, 150 y 400 paradas (lo que pide SCRUM-6)")
    if len(filas) > len(pedidas):
        s += panel(filas, W / 2, 0, W / 2, H - 40, "Hasta 4.000 paradas (para ver la tendencia)")
    ly = H - 14
    s.append(f'<line x1="60" y1="{ly - 4}" x2="84" y2="{ly - 4}" stroke="{AZUL}" stroke-width="3"/><text x="90" y="{ly}" font-size="12" fill="{TINTA}">dijkstra() con montículo binario, O(E log V)</text>')
    s.append(f'<line x1="380" y1="{ly - 4}" x2="404" y2="{ly - 4}" stroke="{ROJO}" stroke-width="3"/><text x="410" y="{ly}" font-size="12" fill="{TINTA}">Misma función con búsqueda lineal, O(V²)</text>')
    s.append(f'<line x1="690" y1="{ly - 4}" x2="714" y2="{ly - 4}" stroke="{GRIS}" stroke-width="2.5" stroke-dasharray="5 5"/><text x="720" y="{ly}" font-size="12" fill="{TINTA}">Si creciera igual que los tramos</text>')
    s.append("</svg>")
    with open(salida, "w", encoding="utf-8") as f:
        f.write("\n".join(s))


if __name__ == "__main__":
    main()
