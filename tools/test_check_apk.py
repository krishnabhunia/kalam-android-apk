import pathlib
import re
import unittest

import check_apk

GOOD = ("package: name='com.krishna.kalam' versionCode='1203' versionName='1.24.1' platformBuildVersionName='14'\n"
        "sdkVersion:'26'\ntargetSdkVersion:'34'\napplication-label:'Kalam'\n")
WORKFLOW = pathlib.Path(__file__).resolve().parent.parent / '.github' / 'workflows' / 'software.yml'


def jobs(text):
    """Split the workflow into {job: body} using its two-space job indentation."""
    body = text.split('\njobs:\n', 1)[1]
    out, name = {}, None
    for line in body.splitlines():
        match = re.fullmatch(r'  ([A-Za-z0-9_-]+):\s*', line)
        if match:
            name = match.group(1)
            out[name] = ''
        elif name:
            out[name] += line + '\n'
    return out


class CheckApkTest(unittest.TestCase):
    def test_release_apk_passes(self):
        self.assertEqual([], check_apk.problems(GOOD, 1203, '1.24.1'))

    def test_debuggable_apk_is_refused(self):
        self.assertIn('APK is debuggable', check_apk.problems(GOOD + 'application-debuggable\n', 1203, '1.24.1'))

    def test_wrong_version_or_package_is_refused(self):
        self.assertTrue(check_apk.problems(GOOD, 1204, '1.24.1'))
        self.assertTrue(check_apk.problems(GOOD.replace('com.krishna.kalam', 'com.evil.kalam'), 1203, '1.24.1'))
        self.assertTrue(check_apk.problems('garbage', 1203, '1.24.1'))


class WorkflowKeyIsolationTest(unittest.TestCase):
    """The permanent key must only be readable by the gated, non-PR signing job."""

    def setUp(self):
        self.text = WORKFLOW.read_text()
        self.jobs = jobs(self.text)

    def test_only_the_signing_job_reads_secrets(self):
        readers = sorted(name for name, body in self.jobs.items() if 'secrets.' in body)
        self.assertEqual(['signed-release'], readers)
        self.assertNotIn('secrets.', self.text.split('\njobs:\n', 1)[0])

    def test_signing_job_is_gated_never_runs_for_prs_and_builds_nothing(self):
        sign = self.jobs['signed-release']
        self.assertIn('environment: android-signing', sign)
        condition = next(line for line in sign.splitlines() if line.strip().startswith('if:'))
        self.assertNotIn('pull_request', condition)
        self.assertNotIn('gradlew', sign)

    def test_both_delivered_apks_are_checked_and_releases_never_cancelled(self):
        self.assertEqual(2, self.text.count('python tools/check_apk.py'))
        self.assertNotIn('cancel-in-progress: true', self.text)


if __name__ == '__main__':
    unittest.main()
