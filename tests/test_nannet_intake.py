import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

from scripts import intake_client, route_diff


class AndroidNannetIntakeTests(unittest.TestCase):
    def test_route_diff_digest_is_canonical(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "route.json"
            with patch.object(sys, "argv", ["route_diff.py", "--out", str(output),
                                           "--base", "4", "--epoch", "99"]):
                self.assertEqual(route_diff.main(), 0)
            payload = json.loads(output.read_text())
            digest = payload.pop("digest")
            canonical = json.dumps(payload, sort_keys=True,
                                   separators=(",", ":")).encode()
            import hashlib
            self.assertEqual(digest, hashlib.sha256(canonical).hexdigest())
            self.assertEqual(payload["node"], 2)
            self.assertEqual(payload["control_point"], 1)

    def test_client_sends_bound_https_route_diff(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "route.json"
            path.write_text('{"universe":0,"node":2,"layer":0,"control_point":1,'
                            '"base_generation":0,"epoch":1,"entries":[],"digest":"' +
                            "a" * 64 + '"}')
            response = MagicMock()
            response.status = 202
            response.read.return_value = b'{"state":"pending_unverified"}'
            response.__enter__.return_value = response
            with patch.dict(os.environ, {
                "NANLM_DEVICE_TOKEN": "nnt_test_secret",
                "NANLM_DEVICE_ADDRESS": "nan://GM0:SC0:MC0:BS0:HW1",
            }), patch.object(sys, "argv", ["intake_client.py", str(path),
                                          "--url", "https://nannet.example"]),                  patch("urllib.request.urlopen", return_value=response) as open_url,                  contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(intake_client.main(), 0)
            request = open_url.call_args.args[0]
            self.assertEqual(request.full_url,
                             "https://nannet.example/v1/intake/route")
            headers = {k.lower(): v for k, v in request.header_items()}
            self.assertEqual(headers["x-nan-address"],
                             "nan://GM0:SC0:MC0:BS0:HW1")
            self.assertEqual(headers["authorization"], "Bearer nnt_test_secret")

    def test_client_rejects_plain_http(self):
        with patch.dict(os.environ, {"NANLM_DEVICE_TOKEN": "nnt_test_secret"}), \
             patch.object(sys, "argv", ["intake_client.py", "missing.json",
                                       "--url", "http://nannet.example"]), \
             patch("urllib.request.urlopen") as open_url, \
             contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(intake_client.main(), 2)
            open_url.assert_not_called()


if __name__ == "__main__":
    unittest.main()
