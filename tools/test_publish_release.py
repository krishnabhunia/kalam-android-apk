import hashlib
import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import publish_release


class PublicationRetryTest(unittest.TestCase):
    version = '1.24.0'
    code = '2201'
    date = '13-Oct-2026'
    target = 'a' * 40

    def exercise(self, release, expected_error=None):
        with tempfile.TemporaryDirectory() as folder:
            archive = pathlib.Path(folder) / 'Kalam_1.24.0.zip'
            archive.write_bytes(b'original verified ZIP')
            calls = []
            def command(*args):
                calls.append(args)
                if args[:3] == ('git', 'show', 'v1.24.0:android/app/build.gradle'):
                    return 'versionCode 2201; versionName "1.24.0"'
                if args[:3] == ('git', 'show', 'v1.24.0:android/app/src/main/java/com/krishna/kalam/AppVersion.kt'):
                    return 'NAME = "1.24.0"\nRELEASE_DATE = "13-Oct-2026"'
                if '--format=%P' in args:
                    return self.target
                return ''
            api = subprocess.CompletedProcess([], 1, '', 'HTTP 404') if release is None else subprocess.CompletedProcess([], 0, json.dumps(release), '')
            with patch('publish_release.run', side_effect=command), patch('publish_release.subprocess.run', side_effect=[
                    subprocess.CompletedProcess([], 0, 'matching existing remote tag', ''), api]):
                if expected_error:
                    with self.assertRaisesRegex(ValueError, expected_error):
                        publish_release.publish(self.version, self.code, self.date, self.target, 'owner/repo', archive)
                else:
                    publish_release.publish(self.version, self.code, self.date, self.target, 'owner/repo', archive)
            self.assertFalse(any(args[:2] == ('git', 'tag') for args in calls))
            return calls

    def test_retry_after_tag_was_pushed_creates_missing_release(self):
        calls = self.exercise(None)
        self.assertTrue(any(args[:3] == ('gh', 'release', 'create') for args in calls))

    def test_retry_after_asset_upload_is_a_safe_noop(self):
        release = dict(draft=False, prerelease=False, html_url='https://github.com/owner/repo/releases/tag/v1.24.0',
            assets=[dict(name='Kalam_1.24.0.zip', digest='sha256:' + hashlib.sha256(b'original verified ZIP').hexdigest())])
        calls = self.exercise(release)
        self.assertFalse(any(args[:2] == ('gh', 'release') for args in calls))

    def test_retry_does_not_overwrite_different_published_apk(self):
        release = dict(draft=False, prerelease=False, assets=[dict(name='Kalam_1.24.0.zip', digest='sha256:' + 'b'*64)])
        self.exercise(release, 'refusing to overwrite')

    def test_retry_after_release_creation_uploads_missing_asset(self):
        release = dict(draft=False, prerelease=False, assets=[])
        calls = self.exercise(release)
        self.assertTrue(any(args[:3] == ('gh', 'release', 'upload') for args in calls))


if __name__ == '__main__':
    unittest.main()
