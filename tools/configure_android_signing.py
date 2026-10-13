"""Configure encrypted signing secrets using the selected existing Git credential.

Requires PyNaCl. Secret values arrive on stdin, and are never printed or saved.
"""
import argparse
import base64
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from nacl.public import PublicKey, SealedBox


def configure(repository, login, values):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid repository name')
    if not re.fullmatch(r'[A-Za-z0-9-]+', login):
        raise ValueError('Invalid selected GitHub login')
    credential = subprocess.run(['git', 'credential', 'fill'],
        input=f'protocol=https\nhost=github.com\nusername={login}\n\n', text=True,
        capture_output=True, check=True, timeout=25,
        env=dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never'))
    fields = dict(line.split('=', 1) for line in credential.stdout.splitlines() if '=' in line)
    token = fields.get('password')
    if not token:
        raise ValueError('No existing credential is available for the selected account')
    headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Kalam-signing-setup'}

    def request(path, method='GET', data=None):
        encoded = json.dumps(data).encode() if data is not None else None
        req = urllib.request.Request('https://api.github.com/' + path, data=encoded, headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=30) as response:
            body = response.read()
            return json.loads(body) if body else None

    if request('user')['login'].lower() != login.lower():
        raise ValueError('Authenticated GitHub account differs from the selected account')
    repo_prefix = f'repos/{repository}/actions/'
    environment = f'repos/{repository}/environments/android-signing'
    # A PR uses refs/pull/N/merge, which cannot match these signing branch rules.
    request(environment, 'PUT', {'deployment_branch_policy': {
        'protected_branches': False, 'custom_branch_policies': True}})
    rules = request(environment + '/deployment-branch-policies')['branch_policies']
    for name in ('main', 'codex/*'):
        if not any(rule['name'] == name and rule['type'] == 'branch' for rule in rules):
            request(environment + '/deployment-branch-policies', 'POST', {'name': name, 'type': 'branch'})
    prefix = environment + '/'
    expected_names = {'ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD'}
    if set(values) != expected_names | {'ANDROID_SIGNING_CERT_SHA256'} or any(not values[n] for n in expected_names):
        raise ValueError('Signing values are incomplete')
    certificate = values['ANDROID_SIGNING_CERT_SHA256']
    if not re.fullmatch('[0-9a-f]{64}', certificate):
        raise ValueError('Invalid certificate fingerprint')
    existing = request(prefix + 'secrets')['secrets']
    # Refuse to replace a signing identity that may already have been deployed.
    if any(secret['name'] in expected_names for secret in existing):
        raise ValueError('Protected environment signing secrets already exist; preserve the key')
    key = request(prefix + 'secrets/public-key')
    box = SealedBox(PublicKey(base64.b64decode(key['key'])))
    for name in sorted(expected_names):
        encrypted = base64.b64encode(box.encrypt(values[name].encode())).decode()
        request(prefix + 'secrets/' + name, 'PUT', {'encrypted_value': encrypted, 'key_id': key['key_id']})
        print('Configured protected Actions secret: ' + name)
    try:
        prior = request(repo_prefix + 'variables/ANDROID_SIGNING_CERT_SHA256')
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        prior = None
    if prior is not None and prior['value'] != certificate:
        raise ValueError('Existing certificate fingerprint differs; do not replace the signing key')
    if prior is None:
        request(repo_prefix + 'variables', 'POST', {'name': 'ANDROID_SIGNING_CERT_SHA256', 'value': certificate})
    actual = {item['name'] for item in request(prefix + 'secrets')['secrets']}
    if not expected_names.issubset(actual) or request(repo_prefix + 'variables/ANDROID_SIGNING_CERT_SHA256')['value'] != certificate:
        raise ValueError('GitHub signing settings could not be confirmed')
    # Remove broad repository exposure only after the same protected key is confirmed.
    broad = {item['name'] for item in request(repo_prefix + 'secrets')['secrets']}
    for name in sorted(expected_names & broad):
        request(repo_prefix + 'secrets/' + name, 'DELETE')
    if expected_names & {item['name'] for item in request(repo_prefix + 'secrets')['secrets']}:
        raise ValueError('Signing secrets still have repository-wide exposure')
    print('Verified signing configuration for ' + repository + ' under ' + login)
    print('Public signing certificate SHA-256: ' + certificate)


if __name__ == '__main__':
    args = argparse.ArgumentParser()
    args.add_argument('--repository', required=True)
    args.add_argument('--login', required=True)
    selected = args.parse_args()
    try:
        configure(selected.repository, selected.login, json.load(sys.stdin))
    except urllib.error.HTTPError as error:
        sys.exit(f'GitHub signing setup returned HTTP {error.code}. Check repository administration access.')
    except (ValueError, subprocess.SubprocessError) as error:
        sys.exit(str(error) if isinstance(error, ValueError) else 'Could not read the selected existing GitHub credential')
