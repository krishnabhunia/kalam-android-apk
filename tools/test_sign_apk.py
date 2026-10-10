import os
import pathlib
import tempfile
import unittest
from unittest.mock import patch
import sign_apk


class SigningTest(unittest.TestCase):
    def test_missing_key_never_falls_back_to_debug_signing(self):
        with patch.dict(os.environ, {}, clear=True), patch('subprocess.run') as run:
            with self.assertRaisesRegex(ValueError, 'Permanent Android signing is not configured'):
                sign_apk.sign(pathlib.Path('input.apk'), pathlib.Path('output.apk'))
            run.assert_not_called()

    def test_wrong_certificate_deletes_the_signed_output(self):
        env = dict(ANDROID_HOME='/sdk', ANDROID_KEYSTORE_BASE64='a2V5', ANDROID_KEYSTORE_PASSWORD='private',
                   ANDROID_KEY_ALIAS='release', ANDROID_KEY_PASSWORD='private', ANDROID_SIGNING_CERT_SHA256='a'*64)
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory)/'output.apk'
            output.write_bytes(b'apk')
            with patch.dict(os.environ, env), patch('subprocess.run'), patch('subprocess.check_output',
                    return_value='Signer #1 certificate SHA-256 digest: ' + 'b'*64):
                with self.assertRaisesRegex(ValueError, 'does not match'):
                    sign_apk.sign(pathlib.Path('input.apk'), output)
            self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
