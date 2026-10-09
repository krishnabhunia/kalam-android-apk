"""Allocate a bugfix semantic version from stable GitHub tags, then sync source badges."""
import datetime
import os
import pathlib
import re
import subprocess

tags = subprocess.check_output(['git', 'tag'], text=True).splitlines()
stable = []
for tag in tags:
    match = re.fullmatch(r'v?(\d+)\.(\d+)(?:\.(\d+))?', tag)
    if match:
        stable.append(tuple(int(part or 0) for part in match.groups()))
major, minor, patch = max(stable, default=(1,21,0))
version = f'{major}.{minor+1}.0' if os.environ.get('VERSION_BUMP') == 'minor' else f'{major}.{minor}.{patch+1}'
if os.environ['GITHUB_EVENT_NAME'] == 'pull_request':
    import json
    event = json.loads(pathlib.Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    version += f'-beta.{event["number"]}.{os.environ["GITHUB_RUN_NUMBER"]}.{os.environ["GITHUB_RUN_ATTEMPT"]}'
# This repository previously released code 22. The monotonic workflow run number
# avoids beta/stable code collisions without encoding SemVer into Android's int.
code = 23 + int(os.environ['GITHUB_RUN_NUMBER'])
gradle = pathlib.Path('app/build.gradle')
gradle.write_text(re.sub(r'versionCode \d+; versionName "[^"]+"',
                        f'versionCode {code}; versionName "{version}"', gradle.read_text()))
badge = pathlib.Path('app/src/main/java/com/krishna/kalam/AppVersion.kt')
text = re.sub(r'const val NAME = "[^"]+"', f'const val NAME = "{version}"', badge.read_text())
today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=5,minutes=30))).strftime('%d-%b-%Y')
text = re.sub(r'const val RELEASE_DATE = "[^"]+"', f'const val RELEASE_DATE = "{today}"', text)
badge.write_text(text)
with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
    output.write(f'version={version}\ncode={code}\n')
print(f'Kalam {version}, Android code {code}')
