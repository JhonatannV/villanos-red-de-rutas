"""Dibuja la gráfica de SCRUM-37 (banco C vs. Java) a partir de scrum37_banco.csv.

Uso:  python graficar_banco.py ../../../docs/mediciones/scrum37_banco.csv salida.svg
Solo usa la biblioteca estándar de Python (no necesita matplotlib).

Panel izquierdo: la red real de 400 paradas; barra = mediana de las 5 corridas,
línea vertical = mínimo y máximo, puntos = cada corrida.
Panel derecho: cómo crece el tiempo con el tamaño de la red (redes generadas).
"""
import csv
import sys
from collections import defaultdict

MOTORES = [("c_puro", "C puro", "#2050cc"), ("c_jna", "C por JNA", "#1a9a6c"), ("java", "Java", "#cc3b30")]
TINTA, SUAVE, REJILLA = "#162030", "#5a6678", "#e3e7ed"


def mediana(v):
    v = sorted(v)
    return v[len(v) // 2]


def paso_redondo(bruto):
    potencia = 1
    while potencia * 10 <= bruto:
        potencia *= 10
    for k in (1, 2, 5, 10):
        if k * potencia >= bruto:
            return k * potencia
    return 10 * potencia


def eje_y(s, x0, ancho, py, ymax, paso):
    yv = 0
    while yv <= ymax + 1e-9:
        s.append(f'<line x1="{x0:.1f}" y1="{py(yv):.1f}" x2="{x0 + ancho:.1f}" y2="{py(yv):.1f}" stroke="{REJILLA}"/>')
        s.append(f'<text x="{x0 - 8:.1f}" y="{py(yv) + 4:.1f}" font-size="11" text-anchor="end" fill="{SUAVE}">{yv:,.0f}</text>')
        yv += paso


def panel_barras(datos, x0, y0, ancho, alto):
    izq, abajo, arriba = 62, 52, 46
    w, h = ancho - izq - 20, alto - abajo - arriba
    ymax_bruto = max(max(v) for v in datos.values()) * 1.12
    paso = paso_redondo(ymax_bruto / 5)
    ymax = paso * (int(ymax_bruto / paso) + 1)

    def py(y):
        return y0 + arriba + h - y / ymax * h

    s = [f'<text x="{x0 + izq}" y="{y0 + 22}" font-size="14" font-weight="700" fill="{TINTA}">Red real de 400 paradas y 1.100 tramos · 1.000 rutas</text>']
    eje_y(s, x0 + izq, w, py, ymax, paso)
    banda = w / len(MOTORES)
    for i, (clave, nombre, color) in enumerate(MOTORES):
        v = datos[clave]
        m = mediana(v)
        cx = x0 + izq + banda * (i + 0.5)
        bw = banda * 0.46
        s.append(f'<rect x="{cx - bw / 2:.1f}" y="{py(m):.1f}" width="{bw:.1f}" height="{py(0) - py(m):.1f}" fill="{color}" fill-opacity="0.85" rx="3"/>')
        s.append(f'<line x1="{cx:.1f}" y1="{py(min(v)):.1f}" x2="{cx:.1f}" y2="{py(max(v)):.1f}" stroke="{TINTA}" stroke-width="1.5"/>')
        for y in (min(v), max(v)):
            s.append(f'<line x1="{cx - 8:.1f}" y1="{py(y):.1f}" x2="{cx + 8:.1f}" y2="{py(y):.1f}" stroke="{TINTA}" stroke-width="1.5"/>')
        for k, y in enumerate(v):
            s.append(f'<circle cx="{cx + bw / 2 + 10 + (k % 2) * 6:.1f}" cy="{py(y):.1f}" r="2.6" fill="{TINTA}" fill-opacity="0.55"/>')
        s.append(f'<text x="{cx:.1f}" y="{py(max(v)) - 8:.1f}" font-size="13" font-weight="700" text-anchor="middle" fill="{color}">{m:,.1f} µs</text>')
        s.append(f'<text x="{cx:.1f}" y="{py(0) + 20:.1f}" font-size="12.5" font-weight="600" text-anchor="middle" fill="{TINTA}">{nombre}</text>')
    s.append(f'<text x="{x0 + 14}" y="{py(ymax / 2):.1f}" font-size="12" text-anchor="middle" fill="{SUAVE}" transform="rotate(-90 {x0 + 14} {py(ymax / 2):.1f})">Microsegundos por ruta (mediana de 5)</text>')
    return s


def panel_lineas(series, x0, y0, ancho, alto):
    izq, abajo, arriba = 62, 52, 46
    w, h = ancho - izq - 20, alto - abajo - arriba
    tramos = sorted({t for pts in series.values() for t, _ in pts})
    xmax = max(tramos) * 1.05
    ymax_bruto = max(y for pts in series.values() for _, y in pts) * 1.1
    paso = paso_redondo(ymax_bruto / 5)
    ymax = paso * (int(ymax_bruto / paso) + 1)

    def px(x):
        return x0 + izq + x / xmax * w

    def py(y):
        return y0 + arriba + h - y / ymax * h

    s = [f'<text x="{x0 + izq}" y="{y0 + 22}" font-size="14" font-weight="700" fill="{TINTA}">Cómo crece con el tamaño (redes generadas de SCRUM-6)</text>']
    eje_y(s, x0 + izq, w, py, ymax, paso)
    ultimo = -1e9
    for t in reversed(tramos):
        if ultimo - px(t) < 34 and ultimo != -1e9:
            continue
        s.append(f'<text x="{px(t):.1f}" y="{py(0) + 18:.1f}" font-size="11" text-anchor="middle" fill="{SUAVE}">{t:,}</text>')
        ultimo = px(t)
    s.append(f'<text x="{px(xmax / 2):.1f}" y="{y0 + alto - 8}" font-size="12" text-anchor="middle" fill="{SUAVE}">Tramos de la red</text>')
    for clave, nombre, color in MOTORES:
        pts = sorted(series[clave])
        linea = " ".join(f"{px(x):.1f},{py(y):.1f}" for x, y in pts)
        s.append(f'<polyline points="{linea}" fill="none" stroke="{color}" stroke-width="2.5"/>')
        s += [f'<circle cx="{px(x):.1f}" cy="{py(y):.1f}" r="3.5" fill="{color}"/>' for x, y in pts]
        xl, yl = pts[-1]
        s.append(f'<text x="{px(xl) - 6:.1f}" y="{py(yl) - 9:.1f}" font-size="12" font-weight="700" text-anchor="end" fill="{color}">{nombre} {yl:,.0f} µs</text>')
    return s


def main():
    entrada, salida = sys.argv[1], sys.argv[2]
    corridas = defaultdict(list)
    tramos_de = {}
    with open(entrada, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["descartada"] == "si":
                continue
            corridas[(r["red"], r["motor"])].append(float(r["us_por_consulta"]))
            tramos_de[r["red"]] = int(r["tramos"])

    real = next(red for red, _ in corridas if red.startswith("Red real"))
    datos_real = {m: corridas[(real, m)] for m, _, _ in MOTORES}
    series = defaultdict(list)
    for (red, motor), v in corridas.items():
        if red.startswith("Generada"):
            series[motor].append((tramos_de[red], mediana(v)))

    W, H = 1040, 440
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" font-family="Segoe UI, Arial, sans-serif">',
         f'<rect width="{W}" height="{H}" fill="#ffffff"/>']
    s += panel_barras(datos_real, 0, 0, W * 0.44, H - 30)
    s += panel_lineas(series, W * 0.44, 0, W * 0.56, H - 30)
    s.append(f'<text x="20" y="{H - 10}" font-size="11.5" fill="{SUAVE}">Barra = mediana de 5 corridas (se descarta la 1.ª). Línea vertical = mínimo y máximo. Puntos = cada corrida. Docker, 1,5 CPU y 512 MB (límites de ruta-service).</text>')
    s.append("</svg>")
    with open(salida, "w", encoding="utf-8") as f:
        f.write("\n".join(s))


if __name__ == "__main__":
    main()
