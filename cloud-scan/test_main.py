import base64
import os
import unittest

os.environ.setdefault("FIREBASE_PROJECT_ID", "factorybill-test")

from main import clean_suggestion, decode_bill_image


class BillRequestTests(unittest.TestCase):
    def test_decodes_supported_image(self):
        image = b"bill image"
        mime_type, decoded = decode_bill_image({
            "mime_type": "image/jpeg",
            "image_base64": base64.b64encode(image).decode("ascii"),
        })
        self.assertEqual("image/jpeg", mime_type)
        self.assertEqual(image, decoded)

    def test_rejects_unsupported_mime_and_invalid_base64(self):
        with self.assertRaises(ValueError):
            decode_bill_image({"mime_type": "text/plain", "image_base64": "YmlsbA=="})
        with self.assertRaises(ValueError):
            decode_bill_image({"mime_type": "image/jpeg", "image_base64": "not-base64"})

    def test_cleans_model_output_to_supported_fields(self):
        result = clean_suggestion(
            '{"party_name":" ACME ","unknown":"ignored","items":['
            '{"name":" Bolts ","quantity":2,"unit_price_rupees":12.5,"gst_rate_percent":18},'
            '{"name":" "},null]}'
        )
        self.assertEqual("ACME", result["party_name"])
        self.assertEqual(1, len(result["items"]))
        self.assertEqual("Bolts", result["items"][0]["name"])
        self.assertNotIn("unknown", result)

    def test_rejects_output_without_bill_details(self):
        with self.assertRaises(ValueError):
            clean_suggestion('{"items":[]}')


if __name__ == "__main__":
    unittest.main()
