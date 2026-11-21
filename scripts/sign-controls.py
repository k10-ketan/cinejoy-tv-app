#!/usr/bin/env python3
"""Package the two TV scripts as a single signed update."""
import argparse
from pathlib import Path
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MAX_BYTES = 256 * 1024


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", type=int, help="Increasing positive integer for the update channel")
    parser.add_argument("output", type=Path)
    parser.add_argument("--key", type=Path, required=True, help="Private controls signing key")
    args = parser.parse_args()
    if not 0 < args.version <= 2**31 - 1:
        parser.error("version must be between 1 and 2147483647")

    remote = (ASSETS / "remote.js").read_bytes()
    media = (ASSETS / "media-controls.js").read_bytes()
    payload = struct.pack(">4sIII", b"CJT1", args.version, len(remote), len(media)) + remote + media
    if not remote or not media or len(payload) + 256 > MAX_BYTES:
        parser.error("scripts must be non-empty and the signed bundle must fit within 256 KiB")

    with tempfile.TemporaryDirectory() as directory:
        staging = Path(directory)
        data = staging / "payload"
        signature = staging / "signature"
        public_key = staging / "public.pem"
        data.write_bytes(payload)
        subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(args.key), "-out", str(signature), str(data)],
            check=True,
        )
        subprocess.run(
            ["openssl", "pkey", "-pubin", "-inform", "DER", "-in", str(ASSETS / "controls-public.der"),
             "-pubout", "-out", str(public_key)],
            check=True,
        )
        subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(public_key), "-signature", str(signature), str(data)],
            check=True,
        )
        signed = signature.read_bytes()
        if len(signed) != 256:
            parser.error("the controls channel requires a 2048-bit RSA key")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(signed + payload)


if __name__ == "__main__":
    main()
