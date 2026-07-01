import base64
import unittest

from app.tts.client import extract_audio_data


class TtsClientTest(unittest.TestCase):
    def test_extract_audio_data(self) -> None:
        audio = base64.b64encode(b"wav-bytes").decode("ascii")

        result = extract_audio_data(
            {
                "choices": [
                    {
                        "message": {
                            "audio": {
                                "data": audio,
                            }
                        }
                    }
                ]
            }
        )

        self.assertEqual(result, audio)

    def test_extract_missing_audio_data_as_none(self) -> None:
        self.assertIsNone(extract_audio_data({"choices": [{"message": {}}]}))


if __name__ == "__main__":
    unittest.main()
