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


def upload_steps(text):
    """The `with:` lines of every actions/upload-artifact step."""
    steps = []
    for chunk in text.split('uses: actions/upload-artifact@')[1:]:
        lines = []
        for line in chunk.splitlines()[1:]:
            if line.strip().startswith('- ') or re.fullmatch(r'  [A-Za-z0-9_-]+:\s*', line):
                break
            lines.append(line.strip())
        steps.append(dict(line.split(': ', 1) for line in lines if ': ' in line))
    return steps


class SingleArtifactTest(unittest.TestCase):
    """A release run must keep exactly one Kalam_<version>.zip artifact."""

    def setUp(self):
        self.text = WORKFLOW.read_text()

    def test_name_matches_the_file_so_overwrite_can_find_it(self):
        # archive: false names the artifact after the file, but overwrite deletes by `name`.
        for step in upload_steps(self.text):
            if step.get('archive') == 'false':
                self.assertEqual(step['path'].rsplit('/', 1)[-1], step.get('name'), step)

    def test_signed_zip_replaces_the_validation_zip_and_is_proven_single(self):
        self.assertIn('true', [step.get('overwrite') for step in upload_steps(self.text)])
        sign = jobs(self.text)['signed-release']
        self.assertIn('Prove the run keeps exactly one artifact', sign)
        self.assertLess(sign.index('Prove the run keeps exactly one artifact'),
                        sign.index('Record signed build and optionally publish release'))


if __name__ == '__main__':
    unittest.main()
