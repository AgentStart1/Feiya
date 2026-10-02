#!/usr/bin/env python3
"""Install a signed minified APK on a disposable emulator and verify HTTP/WebSocket.

Invoke through smoke-minified.sh, which owns the device lock. Existing app installs
are never replaced or cleared. Only this run's install and forwarding are removed.
"""
import base64
import http.client
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import sys
import time


def websocket_echo(port):
    with socket.create_connection(("127.0.0.1", port), timeout=5) as connection:
        key = base64.b64encode(os.urandom(16)).decode()
        request = (f"GET /chat HTTP/1.1\r\nHost: localhost:8080\r\nUpgrade: websocket\r\n"
                   f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n")
        connection.sendall(request.encode())
        stream = connection.makefile("rb")
        assert b" 101 " in stream.readline(), "WebSocket handshake failed"
        while True:
            header = stream.readline()
            assert header, "WebSocket closed during handshake"
            if header == b"\r\n":
                break
        message = b"minified-startup-smoke"
        mask = os.urandom(4)
        connection.sendall(bytes([0x81, 0x80 | len(message)]) + mask +
                           bytes(value ^ mask[i % 4] for i, value in enumerate(message)))
        for _ in range(10):
            header = stream.read(2)
            assert len(header) == 2, "WebSocket closed before echo"
            opcode, length = header[0] & 15, header[1] & 127
            if length == 126:
                length = struct.unpack("!H", stream.read(2))[0]
            elif length == 127:
                length = struct.unpack("!Q", stream.read(8))[0]
            payload = stream.read(length)
            if opcode == 1 and json.loads(payload).get("data") == message.decode():
                return
        raise AssertionError("WebSocket did not echo the test message")


def main():
    serial, apk, package, output_directory = sys.argv[1:]
    output = Path(output_directory)
    output.mkdir(parents=True, exist_ok=True)

    def adb(*args, check=True):
        return subprocess.run(["adb", "-s", serial, *args], text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              timeout=60, check=check).stdout.strip()

    installed = False
    forwarded = None
    try:
        existing = adb("shell", "pm", "list", "packages", package).splitlines()
        if f"package:{package}" in existing:
            raise RuntimeError(f"Refusing to replace existing {package}; use a disposable emulator")
        forwarded = int(adb("forward", "tcp:0", "tcp:8080"))
        try:
            probe = http.client.HTTPConnection("127.0.0.1", forwarded, timeout=2)
            probe.request("GET", "/")
            probe.getresponse()
        except (OSError, http.client.HTTPException):
            pass
        else:
            raise RuntimeError("Port 8080 already serves HTTP; refusing to test another service")
        finally:
            probe.close()
        result = adb("install", "-t", "-g", apk)
        if "Success" not in result:
            raise RuntimeError(result)
        installed = True
        adb("shell", "am", "start", "-W", "-n", f"{package}/com.storyteller_f.feiya.MainActivity")
        deadline = time.monotonic() + 60
        while True:
            try:
                connection = http.client.HTTPConnection("127.0.0.1", forwarded, timeout=2)
                connection.request("GET", "/")
                response = connection.getresponse()
                assert response.status == 302, f"Unexpected HTTP status {response.status}"
                assert response.getheader("Location") == "/login", "Wrong HTTP endpoint"
                websocket_echo(forwarded)
                # Repeat after startup's self-client has had time to fail, if it cannot connect.
                time.sleep(2)
                websocket_echo(forwarded)
                break
            except (OSError, http.client.HTTPException, AssertionError) as error:
                if time.monotonic() >= deadline:
                    raise RuntimeError("Minified service did not start") from error
                time.sleep(1)
            finally:
                connection.close()
        (output / "result.txt").write_text(f"{package}: HTTP 302 /login and WebSocket echo passed\n")
        print((output / "result.txt").read_text(), end="")
    finally:
        try:
            if installed:
                try:
                    (output / "runtime.log").write_text(adb("logcat", "-d", "-t", "2000", "-s", "AppServer", "AndroidRuntime", check=False))
                finally:
                    try:
                        adb("shell", "am", "force-stop", package, check=False)
                    finally:
                        adb("uninstall", package, check=False)
        finally:
            if forwarded is not None:
                adb("forward", "--remove", f"tcp:{forwarded}", check=False)


if __name__ == "__main__":
    main()
