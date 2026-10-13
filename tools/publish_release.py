"""Retry-safe publication: reuse matching tags/assets, never overwrite a release."""
import hashlib
import json
import os
import pathlib
import re
import subprocess


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def publish(version, code, date, target, repository, archive, beta=False):
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-beta\.[0-9A-Za-z.]+)?', version) or not str(code).isdigit():
        raise ValueError('Invalid delivered version metadata')
    if not re.fullmatch(r'[0-9a-f]{40}', target) or not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid release target')
    archive = pathlib.Path(archive)
    if archive.name != f'Kalam_{version}.zip' or not archive.is_file():
        raise ValueError('Expected single versioned ZIP is missing')
    tag = 'v' + version
    remote = subprocess.run(['git', 'ls-remote', '--exit-code', '--tags', 'origin', 'refs/tags/' + tag],
                            capture_output=True, text=True)
    if remote.returncode not in (0, 2):
        raise ValueError('Could not verify the remote release tag')
    if remote.returncode == 0:
        run('git', 'fetch', 'origin', 'tag', tag)
        tagged = run('git', 'show', f'{tag}:android/app/build.gradle')
        tagged_badge = run('git', 'show', f'{tag}:android/app/src/main/java/com/krishna/kalam/AppVersion.kt')
        parents = run('git', 'show', '-s', '--format=%P', tag).split()
        if target not in parents or f'versionCode {code}; versionName "{version}"' not in tagged or f'NAME = "{version}"' not in tagged_badge or f'RELEASE_DATE = "{date}"' not in tagged_badge:
            raise ValueError('Existing tag differs from this delivered build; refusing to replace it')
    else:
        gradle = pathlib.Path('android/app/build.gradle')
        gradle.write_text(re.sub(r'versionCode \d+; versionName "[^"]+"',
            f'versionCode {code}; versionName "{version}"', gradle.read_text(encoding='utf-8')), encoding='utf-8')
        badge = pathlib.Path('android/app/src/main/java/com/krishna/kalam/AppVersion.kt')
        text = re.sub(r'const val NAME = "[^"]+"', f'const val NAME = "{version}"', badge.read_text(encoding='utf-8'))
        badge.write_text(re.sub(r'const val RELEASE_DATE = "[^"]+"', f'const val RELEASE_DATE = "{date}"', text), encoding='utf-8')
        run('git', 'config', 'user.name', 'github-actions[bot]')
        run('git', 'config', 'user.email', '41898282+github-actions[bot]@users.noreply.github.com')
        run('git', 'add', str(gradle), str(badge))
        run('git', 'commit', '--allow-empty', '-m', f'Record delivered Kalam {version} metadata')
        run('git', 'tag', tag)
        run('git', 'push', 'origin', 'refs/tags/' + tag)
    view = subprocess.run(['gh', 'api', f'repos/{repository}/releases/tags/{tag}'], capture_output=True, text=True)
    if view.returncode == 0:
        release = json.loads(view.stdout)
        if release['draft'] or release['prerelease'] != beta:
            raise ValueError('Existing release has a different channel')
        assets = [asset for asset in release['assets'] if asset['name'] == archive.name]
        if assets:
            digest = 'sha256:' + hashlib.sha256(archive.read_bytes()).hexdigest()
            if len(assets) != 1 or assets[0].get('digest') != digest:
                raise ValueError('Existing release asset differs; refusing to overwrite a published APK')
            print('Release and matching archive already published: ' + release['html_url'])
            return
        run('gh', 'release', 'upload', tag, str(archive))
    else:
        if 'HTTP 404' not in view.stderr:
            raise ValueError('Could not check the existing GitHub release')
        args = ['gh', 'release', 'create', tag, str(archive), '--verify-tag', '--title', f'Kalam v{version}', '--generate-notes']
        if beta:
            args += ['--prerelease', '--latest=false']
        run(*args)
    print('Published https://github.com/' + repository + '/releases/tag/' + tag)


if __name__ == '__main__':
    version = os.environ['VERSION']
    publish(version, os.environ['CODE'], os.environ['RELEASE_DATE'], os.environ['TARGET'], os.environ['GH_REPO'],
            pathlib.Path('release') / f'Kalam_{version}.zip', os.environ.get('BETA') == 'true')
