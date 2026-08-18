"""Локальный сервер NOVA AI: раздаёт HTML и безопасно проксирует Ollama."""
from __future__ import annotations

import http.client
import json
import os
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parent
OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://127.0.0.1:11434").rstrip("/")
MAX_BODY = 16 * 1024 * 1024


class NovaHandler(SimpleHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def end_headers(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        super().end_headers()

    def do_POST(self):
        if self.path != "/api/chat":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_BODY:
                self._json_error(413, "Запрос слишком большой")
                return
            payload = self.rfile.read(length)
            json.loads(payload)
            target = urlsplit(OLLAMA_URL)
            connection_class = http.client.HTTPSConnection if target.scheme == "https" else http.client.HTTPConnection
            conn = connection_class(target.hostname, target.port, timeout=120)
            path = (target.path.rstrip("/") if target.path else "") + "/api/chat"
            conn.request("POST", path, body=payload, headers={"Content-Type": "application/json"})
            upstream = conn.getresponse()
            if upstream.status >= 400:
                raw = upstream.read(64 * 1024)
                try:
                    message = json.loads(raw).get("error", raw.decode("utf-8", "replace"))
                except Exception:
                    message = raw.decode("utf-8", "replace")
                self._json_error(upstream.status, message or "Ошибка Ollama")
                conn.close()
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "close")
            self.end_headers()
            while True:
                chunk = upstream.read(4096)
                if not chunk:
                    break
                self.wfile.write(chunk)
                self.wfile.flush()
            self.close_connection = True
            conn.close()
        except (ConnectionError, OSError, http.client.HTTPException) as exc:
            self._json_error(502, f"Ollama недоступна по адресу {OLLAMA_URL}: {exc}")
        except (ValueError, json.JSONDecodeError):
            self._json_error(400, "Некорректный запрос")
        except Exception as exc:
            self._json_error(500, f"Ошибка локального сервера: {exc}")

    def _json_error(self, status: int, message: str):
        body = json.dumps({"error": message}, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True


if __name__ == "__main__":
    address = ("127.0.0.1", 8080)
    print(f"NOVA AI: http://{address[0]}:{address[1]}")
    print(f"Ollama:  {OLLAMA_URL}")
    print("Для остановки нажмите Ctrl+C")
    ThreadingHTTPServer(address, NovaHandler).serve_forever()
