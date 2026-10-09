import contextlib
import json
import os
import pathlib
import runpy
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).with_name('ci_version.py')

class VersionTest(unittest.TestCase):
    def check_version(self, event, tags, expected):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            gradle = root/'app/build.gradle'
            badge = root/'app/src/main/java/com/krishna/kalam/AppVersion.kt'
            badge.parent.mkdir(parents=True)
            gradle.write_text('versionCode 23; versionName "1.21.1-beta.1"')
            badge.write_text('const val NAME = "1.21.1-beta.1"\nconst val RELEASE_DATE = "09-Oct-2026"')
            (root/'event.json').write_text(json.dumps({'number': 7}))
            env = dict(GITHUB_EVENT_NAME=event, GITHUB_EVENT_PATH=str(root/'event.json'),
                       GITHUB_RUN_NUMBER='10', GITHUB_RUN_ATTEMPT='2', GITHUB_OUTPUT=str(root/'output'))
            with contextlib.chdir(root), patch.dict(os.environ, env), patch('subprocess.check_output', return_value=tags):
                runpy.run_path(str(SCRIPT))
            self.assertIn(f'versionName "{expected}"', gradle.read_text())
            self.assertIn('versionCode 33;', gradle.read_text())
            self.assertIn(f'NAME = "{expected}"', badge.read_text())
            self.assertEqual(f'version={expected}\ncode=33\n', (root/'output').read_text())

    def test_first_beta(self):
        self.check_version('pull_request', '', '1.21.1-beta.7.10.2')

    def test_legacy_two_part_tag(self):
        self.check_version('push', 'v1.21\n', '1.21.1')

    def test_latest_stable_excludes_betas(self):
        self.check_version('push', 'v1.21.1\nv1.21.2\nv1.22.0-beta.7\n', '1.21.3')

if __name__ == '__main__':
    unittest.main()
