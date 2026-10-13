import contextlib
import json
import os
import pathlib
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import ci_version


SCRIPT = pathlib.Path(__file__).with_name('ci_version.py')


class VersionTest(unittest.TestCase):
    def check_version(self, event, tags, expected, bump='', messages='fix: resolve installer conflict\0',
                      run=10, attempt=2, module='app', real_git=False):
        root = pathlib.Path.cwd() if real_git else None
        with contextlib.ExitStack() as stack:
            if root is None:
                root = pathlib.Path(stack.enter_context(tempfile.TemporaryDirectory()))
            gradle = root / module / 'build.gradle'
            badge = root / module / 'src/main/java/com/krishna/kalam/AppVersion.kt'
            badge.parent.mkdir(parents=True)
            gradle.write_text('versionCode 34; versionName "1.24.0-beta.1"\n// Bengali বাংলা', encoding='utf-8')
            badge.write_text('const val NAME = "1.24.0-beta.1"\nconst val RELEASE_DATE = "09-Oct-2026"\n// বাংলা', encoding='utf-8')
            (root / 'event.json').write_text(json.dumps({'number': 7}), encoding='utf-8')
            env = dict(VERSION_BUMP=bump, GITHUB_EVENT_NAME=event, GITHUB_EVENT_PATH=str(root / 'event.json'),
                       GITHUB_RUN_NUMBER=str(run), GITHUB_RUN_ATTEMPT=str(attempt), GITHUB_OUTPUT=str(root / 'output'))
            stack.enter_context(contextlib.chdir(root))
            stack.enter_context(patch.dict(os.environ, env))
            if not real_git:
                def git_output(command, **kwargs):
                    self.assertEqual('utf-8', kwargs['encoding'])
                    return tags if command == ['git', 'tag'] else messages
                mocked_git = stack.enter_context(patch('subprocess.check_output', side_effect=git_output))
            runpy.run_path(str(SCRIPT), run_name='__main__')
            code = 1000 + 100 * run + attempt
            self.assertIn(f'versionName "{expected}"', gradle.read_text(encoding='utf-8'))
            self.assertIn(f'versionCode {code};', gradle.read_text(encoding='utf-8'))
            self.assertIn(f'NAME = "{expected}"', badge.read_text(encoding='utf-8'))
            self.assertIn('বাংলা', badge.read_text(encoding='utf-8'))
            self.assertTrue((root / 'output').read_text(encoding='utf-8').startswith(f'version={expected}\ncode={code}\ndate='))
            return mocked_git.call_args_list if not real_git else []

    def test_first_beta_uses_three_part_base(self):
        self.check_version('pull_request', '', '1.21.1-beta.7.10.2')

    def test_legacy_two_part_tag(self):
        calls = self.check_version('push', 'v1.21\n', '1.21.1')
        self.assertEqual(['git', 'log', '--format=%B%x00', 'v1.21..HEAD'], calls[1].args[0])

    def test_latest_stable_excludes_betas(self):
        self.check_version('push', 'v1.21.1\nv1.21.2\nv1.22.0-beta.7\n', '1.21.3')

    def test_manual_beta_channel_and_relocated_android_module(self):
        self.check_version('workflow_dispatch', 'v1.23.0\n', '1.24.0-beta.manual.10.2',
                           messages='feat(updates): add daily checks\0', module='android/app')

    def test_override_patch_minor_and_major(self):
        for bump, expected in [('patch', '1.23.5'), ('minor', '1.24.0'), ('major', '2.0.0')]:
            with self.subTest(bump=bump):
                self.check_version('push', 'v1.23.4\n', expected, bump=bump,
                                   messages='feat!: change application storage\0')
        with self.assertRaisesRegex(ValueError, 'VERSION_BUMP'):
            self.check_version('push', 'v1.23.0\n', '', bump='invalid')

    def test_commit_classification_uses_largest_unreleased_change(self):
        cases = [
            (['fix: installer conflict', 'docs: update help'], 'patch'),
            (['fix: bug', 'feat(clocks): backup clocks'], 'minor'),
            (['feat!: replace storage format', 'fix: bug'], 'major'),
            (['fix(storage)!: change identifiers'], 'major'),
            (['feat: add updates\n\nBREAKING CHANGE: replace the package identity'], 'major'),
            (['chore: clean API\n\nBREAKING-CHANGE: changed persisted fields'], 'major'),
            (['docs: explain conventional commits\n\nfeat: quoted example'], 'patch'),
        ]
        for messages, bump in cases:
            with self.subTest(messages=messages):
                self.assertEqual(bump, ci_version.infer_bump(messages))

    def test_retry_and_next_run_codes_are_strictly_increasing(self):
        self.assertGreater(ci_version.android_code(1, 1), 33)
        self.assertGreater(ci_version.android_code(10, 2), ci_version.android_code(10, 1))
        self.assertGreater(ci_version.android_code(11, 1), ci_version.android_code(10, 99))
        self.check_version('pull_request', 'v1.23.0\n', '1.23.1-beta.7.10.3', attempt=3)
        for run, attempt in [(0, 1), (1, 0), (1, 100), (21_000_000, 1)]:
            with self.subTest(run=run, attempt=attempt), self.assertRaises(ValueError):
                ci_version.android_code(run, attempt)

    def test_metadata_tag_child_excludes_its_released_source_commits(self):
        # Publish historically tags a metadata-only child of main. A later main
        # commit is a sibling: tag..HEAD must not count the released feat again.
        with tempfile.TemporaryDirectory() as directory, contextlib.chdir(directory):
            def git(*args):
                return subprocess.run(['git', *args], check=True, capture_output=True, text=True, encoding='utf-8').stdout.strip()
            git('init')
            git('config', 'user.name', 'Version Test')
            git('config', 'user.email', 'version-test@example.invalid')
            pathlib.Path('source.txt').write_text('released feature', encoding='utf-8')
            git('add', 'source.txt')
            git('commit', '-m', 'feat: already released feature')
            source_commit = git('rev-parse', 'HEAD')
            pathlib.Path('metadata.txt').write_text('stable 1.23.0', encoding='utf-8')
            git('add', 'metadata.txt')
            git('commit', '-m', 'Record delivered Kalam 1.23.0 metadata')
            git('tag', 'v1.23.0')
            git('checkout', '--detach', source_commit)
            pathlib.Path('source.txt').write_text('fixed feature', encoding='utf-8')
            git('commit', '-am', 'fix: correct existing behavior')
            self.check_version('push', '', '1.23.1', real_git=True)


if __name__ == '__main__':
    unittest.main()
