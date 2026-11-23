# Stand-in for Yahoo Finance's chart and search services, answering in their documented JSON shape.
import json, math, random, sys, time, datetime
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs, unquote
KNOWN = {"MSFT": ("Microsoft Corporation", 420.0), "AAPL": ("Apple Inc.", 230.0), "VTI": ("Vanguard Total Stock Market Index Fund ETF Shares", 300.0),
         "VXUS": ("Vanguard Total International Stock Index Fund ETF Shares", 65.0), "BND": ("Vanguard Total Bond Market Index Fund ETF Shares", 72.0), "BRK-B": ("Berkshire Hathaway Inc.", 470.0)}
LOG = []
def chart(sym):
    name, px = KNOWN[sym]; rnd = random.Random(sym)
    ts, close, adj = [], [], []
    d = datetime.date.today() - datetime.timedelta(days=5 * 365)
    p = px * 0.6
    while d <= datetime.date.today():
        if d.weekday() < 5:
            p *= math.exp(0.0004 + 0.015 * rnd.gauss(0, 1))
            t = int(datetime.datetime(d.year, d.month, d.day, 14, 30, tzinfo=datetime.timezone.utc).timestamp())
            ts.append(t); c = round(p, 2)
            if rnd.random() < 0.003: close.append(None); adj.append(None)
            else: close.append(c); adj.append(round(c * 0.99, 4))
        d += datetime.timedelta(days=1)
    return {"chart": {"result": [{"meta": {"currency": "USD", "symbol": sym, "exchangeName": "NMS", "fullExchangeName": "NasdaqGS", "gmtoffset": -18000,
        "longName": name, "shortName": name[:20], "regularMarketPrice": close[-1]}, "timestamp": ts,
        "indicators": {"quote": [{"close": close, "open": close, "high": close, "low": close, "volume": [1] * len(ts)}], "adjclose": [{"adjclose": adj}]}}], "error": None}}
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        u = urlparse(self.path); LOG.append(u.path); ua = self.headers.get("User-Agent", "")
        if not ua.startswith("Mozilla"): return self.send(403, {"error": "bad agent"})
        if u.path.startswith("/v8/finance/chart/"):
            sym = unquote(u.path.split("/")[-1])
            if sym not in KNOWN: return self.send(404, {"chart": {"result": None, "error": {"code": "Not Found", "description": "No data found, symbol may be delisted"}}})
            return self.send(200, chart(sym))
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
