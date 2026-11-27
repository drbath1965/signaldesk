# Stand-in for Yahoo Finance's chart and search services, answering in their documented JSON shape.
import json, math, os, random, sys, time, datetime
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs, unquote
KNOWN = {"MSFT": ("Microsoft Corporation", 420.0), "AAPL": ("Apple Inc.", 230.0), "VTI": ("Vanguard Total Stock Market Index Fund ETF Shares", 300.0),
         "VXUS": ("Vanguard Total International Stock Index Fund ETF Shares", 65.0), "BND": ("Vanguard Total Bond Market Index Fund ETF Shares", 72.0), "BRK-B": ("Berkshire Hathaway Inc.", 470.0)}
LOG = []
DIV_FLAG = "/tmp/yahoo_mock_dividend"   # when this file exists, past adjusted closes shift (as after a dividend)
ETFS = {"VTI","VXUS","BND","SPY","VOO","QQQ","IWM","VEA","VWO","AGG","TLT","GLD","VNQ","SCHD","XLK"}
def kind(sym):
    if sym.startswith("^"): return "INDEX"
    if sym.endswith("-USD"): return "CRYPTOCURRENCY"
    if sym in ETFS: return "ETF"
    if len(sym) == 5 and sym.endswith("X"): return "MUTUALFUND"
    return "EQUITY"
def info(sym):
    if sym in KNOWN: return KNOWN[sym]
    rnd = random.Random("px" + sym); k = kind(sym)
    base = {"INDEX": {"^GSPC": 6050.0, "^IXIC": 19800.0, "^DJI": 44700.0, "^RUT": 2400.0}.get(sym, 5000.0), "CRYPTOCURRENCY": {"BTC-USD": 91000.0, "ETH-USD": 3200.0, "SOL-USD": 180.0, "XRP-USD": 2.2, "ADA-USD": 0.8, "DOGE-USD": 0.31, "AVAX-USD": 35.0, "LINK-USD": 21.0, "LTC-USD": 100.0, "DOT-USD": 7.0}.get(sym, 50.0), "MUTUALFUND": 120.0}.get(k, 40 + rnd.random() * 400)
    nm = {"INDEX": "Index " + sym[1:], "CRYPTOCURRENCY": sym[:-4] + " USD", "MUTUALFUND": sym + " Index Fund Admiral Shares", "ETF": sym + " Index ETF"}.get(k, sym + " Holdings Inc.")
    return nm, base
def series(sym):
    name, px = info(sym); rnd = random.Random(sym); k = kind(sym)
    crypto = k == "CRYPTOCURRENCY"; fund = k == "MUTUALFUND"
    sig = 0.035 if crypto else 0.004 if sym in ("BND", "AGG", "VBTLX") else 0.010 if k in ("INDEX", "MUTUALFUND", "ETF") else 0.017
    drift = rnd.gauss(0.0003, 0.0003) + (0.001 if crypto else 0)
    rows = []
    d = datetime.date(2014, 9, 17) if crypto else datetime.date(1990, 1, 2); p = px * (0.002 if crypto else 0.05)
    while d <= datetime.date.today():
        if crypto or d.weekday() < 5:
            o = p * (1 + 0.004 * rnd.gauss(0, 1))
            p *= math.exp(drift + sig * rnd.gauss(0, 1))
            c = p
            h = max(o, c) * (1 + abs(rnd.gauss(0, 1)) * 0.006); l = min(o, c) * (1 - abs(rnd.gauss(0, 1)) * 0.006)
            t = int(datetime.datetime(d.year, d.month, d.day, 14, 30, tzinfo=datetime.timezone.utc).timestamp())
            if fund: o = h = l = c
            rows.append([t, o, h, l, c, 0 if fund else int(1e6 * (1 + abs(rnd.gauss(0, 1))))])
        d += datetime.timedelta(days=1)
    # end near a realistic price: scale the whole walk so the last close lands on the base price
    f = px / rows[-1][4]
    rows = [[r[0], round(r[1] * f, 4), round(r[2] * f, 4), round(r[3] * f, 4), round(r[4] * f, 4), r[5]] for r in rows]
    return name, rows
def chart(sym, q):
    name, rows = series(sym)
    if "range" in q and q["range"][0] == "1mo": rows = rows[-31:]
    div = os.path.exists(DIV_FLAG)
    ts = [r[0] for r in rows]
    adj = [round(r[4] * (0.97 if div and i < len(rows) - 5 else 1.0) * 0.99, 4) for i, r in enumerate(rows)]
    q0 = {"open": [r[1] for r in rows], "high": [r[2] for r in rows], "low": [r[3] for r in rows], "close": [r[4] for r in rows], "volume": [r[5] for r in rows]}
    q0["close"][len(rows) // 2] = None; adj[len(rows) // 2] = None   # a holiday gap, as Yahoo sometimes sends
    return {"chart": {"result": [{"meta": {"currency": "USD", "symbol": sym, "exchangeName": "NMS", "fullExchangeName": "NasdaqGS", "gmtoffset": -18000,
        "dataGranularity": "1d", "instrumentType": kind(sym), "longName": name, "shortName": name[:20], "regularMarketPrice": rows[-1][4]}, "timestamp": ts,
        "indicators": {"quote": [q0], "adjclose": [{"adjclose": adj}]}}], "error": None}}
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        u = urlparse(self.path); LOG.append(u.path); ua = self.headers.get("User-Agent", "")
        if not ua.startswith("Mozilla"): return self.send(403, {"error": "bad agent"})
        if u.path.startswith("/v8/finance/chart/"):
            sym = unquote(u.path.split("/")[-1])
            if sym in ("ZZZZ", "NOPE"): return self.send(404, {"chart": {"result": None, "error": {"code": "Not Found", "description": "No data found, symbol may be delisted"}}})
            return self.send(200, chart(sym, parse_qs(u.query)))
        if u.path == "/v1/finance/search":
            q = parse_qs(u.query)["q"][0].lower()
            quotes = [{"symbol": s, "shortname": n[:20], "longname": n, "quoteType": "ETF" if s in ("VTI","VXUS","BND") else "EQUITY", "typeDisp": "ETF" if s in ("VTI","VXUS","BND") else "Equity", "exchDisp": "NASDAQ"}
                      for s, (n, _) in KNOWN.items() if q in s.lower() or q in n.lower()]
            quotes.append({"symbol": "MSFT.MX", "quoteType": "OPTION"})
            return self.send(200, {"quotes": quotes, "news": []})
        self.send(404, {})
    def send(self, code, obj):
        b = json.dumps(obj).encode(); self.send_response(code); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
