"""Sign with the permanent protected key; never fall back to a runner debug key."""
import base64
import os
import pathlib
import re
import subprocess
import sys
import tempfile


def sign(source, destination):
    names = ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD',
             'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD', 'ANDROID_SIGNING_CERT_SHA256')
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise ValueError('Permanent Android signing is not configured: ' + ', '.join(missing))
    expected = os.environ['ANDROID_SIGNING_CERT_SHA256'].lower().replace(':', '')
    if not re.fullmatch('[0-9a-f]{64}', expected):
        raise ValueError('ANDROID_SIGNING_CERT_SHA256 must be the permanent certificate fingerprint')
    suffix = '.bat' if os.name == 'nt' else ''
    tools = pathlib.Path(os.environ['ANDROID_HOME']) / 'build-tools' / '34.0.0'
    with tempfile.TemporaryDirectory(prefix='kalam-signing-') as directory:
        key = pathlib.Path(directory) / 'release.keystore'
        key.write_bytes(base64.b64decode(os.environ['ANDROID_KEYSTORE_BASE64'], validate=True))
        key.chmod(0o600)
        aligned = pathlib.Path(directory) / 'aligned.apk'
        subprocess.run([str(tools / ('zipalign' + ('.exe' if os.name == 'nt' else ''))),
                        '-p', '-f', '4', str(source), str(aligned)], check=True)
        pathlib.Path(destination).parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([str(tools / ('apksigner' + suffix)), 'sign', '--v4-signing-enabled', 'false', '--ks', str(key),
                        '--ks-key-alias', os.environ['ANDROID_KEY_ALIAS'],
                        '--ks-pass', 'env:ANDROID_KEYSTORE_PASSWORD',
                        '--key-pass', 'env:ANDROID_KEY_PASSWORD', '--out', str(destination),
                        str(aligned)], check=True, capture_output=True)
        result = subprocess.check_output([str(tools / ('apksigner' + suffix)),
                                          'verify', '--print-certs', str(destination)], text=True)
        fingerprints = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', result)
        if fingerprints != [expected]:
            pathlib.Path(destination).unlink(missing_ok=True)
            raise ValueError('APK signing certificate does not match the permanent fingerprint')
        print('Verified permanent signing certificate SHA-256: ' + expected)


if __name__ == '__main__':
    try:
        sign(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))
    except subprocess.CalledProcessError:
        # Do not echo commands/output containing secret-derived values.
        sys.exit('Android signing failed. Check the protected key and passwords.')
    except (ValueError, KeyError) as error:
        sys.exit(str(error))
