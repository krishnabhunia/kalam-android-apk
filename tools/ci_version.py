"""Allocate a semantic version from unreleased commits and synchronize Android metadata."""
import datetime
import json
import os
import pathlib
import re
import subprocess


MAX_VERSION_CODE = 2_100_000_000


def latest_stable(tags):
    """Keep legacy two-part tags readable; newly allocated versions use x.y.z."""
    stable = []
    for tag in tags:
        match = re.fullmatch(r'v?(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?', tag)
        if match:
            stable.append((tuple(int(part or 0) for part in match.groups()), tag))
    return max(stable, default=((1, 21, 0), None), key=lambda entry: (entry[0], entry[1] or ''))


def infer_bump(messages):
    """Conventional Commit subjects/footers decide the largest release bump."""
    feature = False
    for message in messages:
        lines = message.strip().splitlines()
        subject = lines[0] if lines else ''
        # Required assistant PR titles remain meaningful when GitHub uses them
        # verbatim for a squash-merge commit subject.
        subject = re.sub(r'^(?:cgpt-|cld-)', '', subject)
        if (re.match(r'^[A-Za-z][A-Za-z0-9_-]*(?:\([^)]+\))?!:', subject)
                or re.search(r'(?m)^BREAKING[ -]CHANGE:\s*\S', message)):
            return 'major'
        feature |= bool(re.match(r'^feat(?:\([^)]+\))?:', subject, re.IGNORECASE))
    return 'minor' if feature else 'patch'


def allocate_version(base, bump):
    major, minor, patch = base
    if bump == 'major':
        major, minor, patch = major + 1, 0, 0
    elif bump == 'minor':
        minor, patch = minor + 1, 0
    elif bump == 'patch':
        patch += 1
    else:
        raise ValueError('VERSION_BUMP must be patch, minor, or major when set')
    return f'{major}.{minor}.{patch}'


def highest_tagged_code(tags):
    """Published beta/stable tags record the versionCode of the delivered APK."""
    highest = 0
    for tag in tags:
        if not re.fullmatch(r'v?(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?(?:-beta\.[0-9A-Za-z.-]+)?', tag):
            continue
        for module in ('android/app', 'app'):
            try:
                text = subprocess.check_output(['git', 'show', f'{tag}:{module}/build.gradle'],
                                               text=True, encoding='utf-8', stderr=subprocess.DEVNULL)
            except subprocess.CalledProcessError:
                # Early tags predate this Android source/layout.
                continue
            match = re.search(r'\bversionCode\s+(\d+)', text)
            if match:
                code = int(match.group(1))
                if not 1 <= code <= MAX_VERSION_CODE:
                    raise ValueError(f'Invalid Android versionCode recorded in {tag}')
                highest = max(highest, code)
                break
    return highest


def android_code(run, attempt, previous=0):
    # A retry is a newer beta too. Leave 99 attempts per run and stay above all
    # old runner-generated APKs (the latest legacy versionCode was 33).
    if run < 1 or not 1 <= attempt <= 99:
        raise ValueError('GitHub run number must be positive and run attempt must be 1..99')
    # Publishing workflows are serialized and fetch tags before allocation.
    # A queued older run must still update a newer beta that published first.
    code = max(1000 + 100 * run + attempt, previous + 1)
    if code > MAX_VERSION_CODE:
        raise ValueError('Allocated Android versionCode exceeds the supported limit')
    return code


def android_module():
    for module in (pathlib.Path('android/app'), pathlib.Path('app')):
        if (module / 'build.gradle').is_file():
            return module
    raise ValueError('Android application build.gradle was not found')


def replace_once(text, pattern, replacement, label):
    result, count = re.subn(pattern, replacement, text)
    if count != 1:
        raise ValueError(f'Expected exactly one {label} declaration, found {count}')
    return result


def head_is_already_released(tag):
    result = subprocess.run(['git', 'merge-base', '--is-ancestor', 'HEAD', tag],
                            capture_output=True, text=True, encoding='utf-8')
    if result.returncode not in (0, 1):
        raise ValueError(f'Could not compare the main source with released tag {tag}')
    return result.returncode == 0


def main():
    tags = subprocess.check_output(['git', 'tag'], text=True, encoding='utf-8').splitlines()
    base, tag = latest_stable(tags)
    event_name = os.environ['GITHUB_EVENT_NAME']
    if event_name == 'push' and tag and head_is_already_released(tag):
        # GitHub's concurrency queue has no ordering guarantee. Do not package
        # an older main source as a new release after a newer push publishes.
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            output.write('skip=true\n')
        print(f'Skipping release: this main source was already delivered by {tag}.')
        return
    bump = os.environ.get('VERSION_BUMP', '').strip().lower()
    if not bump:
        # This also excludes the released source when its tag points to a
        # metadata-only child commit that was never pushed back to main.
        revision = f'{tag}..HEAD' if tag else 'HEAD'
        messages = subprocess.check_output(['git', 'log', '--format=%B%x00', revision],
                                          text=True, encoding='utf-8').split('\0')
        bump = infer_bump(messages)
    version = allocate_version(base, bump)
    run, attempt = int(os.environ['GITHUB_RUN_NUMBER']), int(os.environ['GITHUB_RUN_ATTEMPT'])
    code = android_code(run, attempt, highest_tagged_code(tags))
    if event_name == 'pull_request':
        event = json.loads(pathlib.Path(os.environ['GITHUB_EVENT_PATH']).read_text(encoding='utf-8'))
        version += f'-beta.{int(event["number"])}.{run}.{attempt}'
    elif event_name == 'workflow_dispatch':
        version += f'-beta.manual.{run}.{attempt}'
    elif event_name != 'push':
        raise ValueError(f'Unsupported version allocation event: {event_name}')

    module = android_module()
    gradle = module / 'build.gradle'
    gradle_text = replace_once(gradle.read_text(encoding='utf-8'),
                              r'versionCode \d+; versionName "[^"]+"',
                              f'versionCode {code}; versionName "{version}"', 'Android version')
    badge = module / 'src/main/java/com/krishna/kalam/AppVersion.kt'
    badge_text = replace_once(badge.read_text(encoding='utf-8'), r'const val NAME = "[^"]+"',
                             f'const val NAME = "{version}"', 'AppVersion.NAME')
    today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=5, minutes=30))).strftime('%d-%b-%Y')
    badge_text = replace_once(badge_text, r'const val RELEASE_DATE = "[^"]+"',
                             f'const val RELEASE_DATE = "{today}"', 'AppVersion.RELEASE_DATE')
    gradle.write_text(gradle_text, encoding='utf-8')
    badge.write_text(badge_text, encoding='utf-8')
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        output.write(f'version={version}\ncode={code}\ndate={today}\nbump={bump}\nskip=false\n')
    print(f'Kalam {version}, Android code {code} ({bump} update)')


if __name__ == '__main__':
    main()
