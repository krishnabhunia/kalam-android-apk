"""Fail before publishing unless this run contains exactly the expected ZIP bytes."""
import hashlib
import json
import os
import pathlib
import sys
import time
import urllib.request


def verify(data, artifact_id, filename, digest):
    artifacts = data.get('artifacts', [])
    # total_count covers later pages too; never accept a partial list as one artifact.
    if data.get('total_count') != 1 or len(artifacts) != 1:
        raise ValueError(f"Expected exactly one artifact, found {data.get('total_count')}")
    artifact = artifacts[0]
    if artifact.get('id') != int(artifact_id) or artifact.get('name') != filename:
        raise ValueError('The retained artifact is not the ZIP uploaded by this job')
    if artifact.get('expired') or artifact.get('digest') != 'sha256:' + digest:
        raise ValueError('The retained artifact checksum does not match the verified ZIP')
    return artifact


def main():
    path = pathlib.Path(os.environ['ZIP_PATH'])
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    url = f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}/artifacts?per_page=100"
    headers = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Kalam-artifact-verification'}
    # Allow the artifact listing to catch up immediately after upload/delete.
    for attempt in range(4):
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
            data = json.load(response)
        try:
            artifact = verify(data, os.environ['ARTIFACT_ID'], path.name, digest)
            print(f"Verified one artifact: {artifact['name']} (ID {artifact['id']}, sha256:{digest})")
            return
        except ValueError:
            if attempt == 3:
                raise
            time.sleep(2)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        sys.exit('::error::' + str(error))
