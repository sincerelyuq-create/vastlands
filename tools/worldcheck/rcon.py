"""Minimal RCON client (Source RCON protocol, as implemented by Minecraft)."""
import socket
import struct


class Rcon:
    def __init__(self, host, port, password, timeout=600):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self._id = 0
        if self._send(3, password) == -1:
            raise PermissionError("RCON authentication failed")

    def _send(self, kind, body):
        self._id += 1
        payload = struct.pack("<ii", self._id, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        size = struct.unpack("<i", self._recv(4))[0]
        data = self._recv(size)
        request_id = struct.unpack("<i", data[:4])[0]
        self.last_body = data[8:-2].decode("utf-8", "replace")
        return request_id

    def _recv(self, n):
        buf = b""
        while len(buf) < n:
            part = self.sock.recv(n - len(buf))
            if not part:
                raise ConnectionError("RCON connection closed")
            buf += part
        return buf

    def command(self, cmd):
        self._send(2, cmd)
        return self.last_body

    def close(self):
        self.sock.close()
