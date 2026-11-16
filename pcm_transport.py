from __future__ import annotations

import os
import socket
import sys
import time


HOST = "172.16.250.248"
PORT = 23
USER = "root"
PASSWORD = os.environ["PCM_PASSWORD"]


class MinimalTelnet:
    IAC = 255
    DONT = 254
    DO = 253
    WONT = 252
    WILL = 251
    SB = 250
    SE = 240

    def __init__(self, host: str, port: int):
        self.sock = socket.create_connection((host, port), timeout=5)
        self.sock.settimeout(0.25)
        self.text = bytearray()
        self._state = "data"
        self._verb = 0

    def send_line(self, line: str) -> None:
        self.sock.sendall(line.encode("ascii") + b"\r\n")

    def _feed(self, data: bytes) -> None:
        for byte in data:
            if self._state == "data":
                if byte == self.IAC:
                    self._state = "iac"
                else:
                    self.text.append(byte)
            elif self._state == "iac":
                if byte == self.IAC:
                    self.text.append(byte)
                    self._state = "data"
                elif byte in (self.DO, self.DONT, self.WILL, self.WONT):
                    self._verb = byte
                    self._state = "option"
                elif byte == self.SB:
                    self._state = "sub"
                else:
                    self._state = "data"
            elif self._state == "option":
                response = self.WONT if self._verb in (self.DO, self.DONT) else self.DONT
                self.sock.sendall(bytes((self.IAC, response, byte)))
                self._state = "data"
            elif self._state == "sub":
                if byte == self.IAC:
                    self._state = "sub_iac"
            elif self._state == "sub_iac":
                self._state = "data" if byte == self.SE else "sub"

    def read_until(self, marker: bytes, timeout: float = 8) -> bytes:
        deadline = time.monotonic() + timeout
        start = len(self.text)
        while marker not in self.text[start:]:
            if time.monotonic() > deadline:
                tail = bytes(self.text[max(0, len(self.text) - 500) :])
                raise TimeoutError(f"waiting for {marker!r}; tail={tail!r}")
            try:
                data = self.sock.recv(4096)
            except socket.timeout:
                continue
            if not data:
                raise ConnectionError("telnet connection closed")
            self._feed(data)
        return bytes(self.text[start:])

    def command(self, command: str, timeout: float = 15) -> str:
        token = f"__DONE_{time.time_ns()}__"
        start = len(self.text)
        self.send_line(command)
        self.send_line(f"echo {token}:$?")
        self.read_until(token.encode("ascii") + b":", timeout)
        return bytes(self.text[start:]).decode("utf-8", "replace")
