"""Safe development-only AcademicCloud connectivity probe; prints no secrets."""
from __future__ import annotations

import socket
import ssl
import time
import urllib.request

from contextual_xai_narrator import list_academiccloud_models

HOST = "chat-ai.academiccloud.de"
URL = f"https://{HOST}/v1/models"


def probe() -> int:
    started = time.monotonic()
    try:
        addresses = socket.getaddrinfo(HOST, 443, type=socket.SOCK_STREAM)
        print(f"dns: ok ({len(addresses)} addresses)")
    except OSError as exc:
        print(f"dns: failed ({type(exc).__name__})"); return 2
    try:
        with socket.create_connection((HOST, 443), timeout=10) as tcp:
            with ssl.create_default_context().wrap_socket(tcp, server_hostname=HOST) as tls:
                print(f"tls: ok ({tls.version()})")
    except OSError as exc:
        print(f"tls: failed ({type(exc).__name__})"); return 3
    try:
        with urllib.request.urlopen(URL, timeout=15) as response:
            print(f"https: {response.status}")
    except Exception as exc:
        print(f"https: {type(exc).__name__}")
    try:
        print(f"openai: ok ({len(list_academiccloud_models())} models)")
    except Exception as exc:
        print(f"openai: failed ({type(exc).__name__})")
    print(f"elapsed_seconds: {time.monotonic() - started:.2f}")
    return 0


if __name__ == "__main__":  # pragma: no cover - manual diagnostic
    raise SystemExit(probe())
