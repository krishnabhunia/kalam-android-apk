import pathlib
import re
import unittest
import check_artifacts


class ArtifactVerificationTest(unittest.TestCase):
    def artifact(self, artifact_id=2, digest='a'*64):
        return dict(id=artifact_id, name='Kalam_1.24.1.zip', expired=False, digest='sha256:' + digest)

    def test_single_matching_signed_zip_passes(self):
        expected = self.artifact()
        self.assertEqual(expected, check_artifacts.verify(dict(total_count=1, artifacts=[expected]), 2, expected['name'], 'a'*64))

    def test_unsigned_and_signed_same_name_are_refused(self):
        with self.assertRaisesRegex(ValueError, 'exactly one'):
            check_artifacts.verify(dict(total_count=2, artifacts=[self.artifact(1), self.artifact()]), 2, 'Kalam_1.24.1.zip', 'a'*64)

    def test_partial_page_wrong_artifact_or_digest_are_refused(self):
        for data in [dict(total_count=101, artifacts=[self.artifact()]),
                     dict(total_count=1, artifacts=[self.artifact(1)]),
                     dict(total_count=1, artifacts=[self.artifact(digest='b'*64)])]:
            with self.assertRaises(ValueError):
                check_artifacts.verify(data, 2, 'Kalam_1.24.1.zip', 'a'*64)

    def test_overwrite_name_equals_actual_archive_filename(self):
        text = (pathlib.Path(__file__).resolve().parent.parent/'.github/workflows/software.yml').read_text(encoding='utf-8')
        uploads = [step for step in re.split(r'(?m)^      - ', text) if 'uses: actions/upload-artifact@v7' in step]
        self.assertEqual(2, len(uploads))
        for step in uploads:
            name = re.search(r'(?m)^          name: (.+)$', step).group(1)
            path = re.search(r'(?m)^          path: (.+)$', step).group(1)
            self.assertEqual(path.rsplit('/', 1)[-1], name)
            self.assertIn('overwrite: true', step)
            self.assertIn('archive: false', step)
        self.assertEqual(2, text.count('python tools/check_artifacts.py'))
        self.assertLess(text.index('Verify only the signed ZIP remains'), text.index('Record signed build'))


if __name__ == '__main__':
    unittest.main()
