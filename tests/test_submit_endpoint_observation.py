import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from submit_endpoint_observation import build_request


class AndroidEndpointPublisherTests(unittest.TestCase):
    def setUp(self):
        self.event = {
            "protocol": "nanlm.endpoint-observation",
            "version": 1,
            "event_id": "a" * 64,
            "base_generation": 12,
            "active_heads": [5, 2, 5],
            "outcome_verified": False,
            "extra_private_field": "must be dropped",
        }

    def test_request_is_hardware_neutral_and_allowlisted(self):
        request = build_request(self.event, "https://gateway.example", "scoped-device-token")
        self.assertEqual(request.full_url, "https://gateway.example/v1/observations")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Authorization"), "Bearer scoped-device-token")
        self.assertNotIn(b"extra_private_field", request.data)
        self.assertIn(b'"active_heads":[2,5]', request.data)

    def test_non_local_http_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "HTTPS is required"):
            build_request(self.event, "http://gateway.example", "scoped-device-token")

    def test_client_cannot_claim_outcome(self):
        bad = dict(self.event, outcome_verified=True, correct=True)
        with self.assertRaisesRegex(ValueError, "cannot assert correctness"):
            build_request(bad, "https://gateway.example", "scoped-device-token")

    def test_invalid_head_index_is_rejected(self):
        bad = dict(self.event, active_heads=[256])
        with self.assertRaisesRegex(ValueError, "active_heads"):
            build_request(bad, "https://gateway.example", "scoped-device-token")


if __name__ == "__main__":
    unittest.main()
