"""Offline regression tests. No real source requests are made."""
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import build_name_stats as stats


ROW = {"이름": "도윤", "건수": 10, "순위": 1}


class FakeResponse(io.BytesIO):
    pass


class FakeOpener:
    def __init__(self, payload):
        self.payload = payload
        self.calls = 0

    def open(self, request, timeout):
        self.calls += 1
        return FakeResponse(json.dumps(self.payload, ensure_ascii=False).encode())


class CollectorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.query = stats.Query(2026, "11", "1")
        self.path = self.root / f"{self.query.key}.json"

    def write_cache(self, *, rows=None, **overrides):
        rows = [ROW] if rows is None else rows
        payload = {
            "cacheSchemaVersion": 1, "querySchemaVersion": 1,
            "source": stats.QUERY, "query": self.query.identity(),
            "fetchedAt": "2026-01-01T00:00:00Z", "rows": rows,
            "rowsSha256": stats.sha256(stats.rows_bytes(rows)),
        }
        payload.update(overrides)
        self.path.write_text(json.dumps(payload, ensure_ascii=False))

    def test_offline_cache_does_not_start_session(self):
        self.write_cache()
        collector = stats.Collector(self.root, offline=True, opener_factory=lambda: self.fail("network opened"))
        self.assertEqual([ROW], collector.fetch(self.query).rows)

    def test_invalid_cache_is_not_trusted_or_silently_replaced(self):
        for override in [
            {"cacheSchemaVersion": 2}, {"cacheSchemaVersion": True},
            {"querySchemaVersion": True}, {"rowsSha256": "wrong"},
            {"source": "https://example.invalid"}, {"query": {"year": 2025}},
            {"fetchedAt": "2026-01-01"}, {"rows": [dict(ROW, 건수=-3)]},
        ]:
            with self.subTest(override=override):
                self.write_cache(**override)
                original = self.path.read_bytes()
                with self.assertRaises(stats.ValidationError):
                    stats.Collector(self.root, offline=True).fetch(self.query)
                self.assertEqual(original, self.path.read_bytes())

    def test_legacy_cache_requires_opt_in_and_never_invents_a_date(self):
        self.path.write_text(json.dumps([ROW]))
        with self.assertRaises(stats.ValidationError):
            stats.Collector(self.root, offline=True).fetch(self.query)
        snapshot = stats.Collector(self.root, offline=True, allow_legacy_cache=True).fetch(self.query)
        self.assertIsNone(snapshot.fetched_at)

    def test_empty_cache_requires_exact_reviewed_query(self):
        self.write_cache(rows=[])
        with self.assertRaises(stats.ValidationError):
            stats.Collector(self.root, offline=True).fetch(self.query)
        reviewed = stats.Collector(self.root, offline=True, empty_allowlist={self.query.key: "source page reviewed"})
        self.assertEqual([], reviewed.fetch(self.query).rows)

    def test_missing_data_and_empty_response_preserve_previous_cache(self):
        for payload in [{}, {"data": []}, {"data": None}, {"data": [dict(ROW, 건수=True)]}]:
            with self.subTest(payload=payload):
                self.write_cache()
                original = self.path.read_bytes()
                collector = stats.Collector(self.root, refresh_all=True, opener_factory=lambda: FakeOpener(payload))
                with self.assertRaises(stats.ValidationError):
                    collector.fetch(self.query)
                self.assertEqual(original, self.path.read_bytes())

    def test_refresh_current_year_replaces_only_current_cache(self):
        self.write_cache()
        older = stats.Query(2025, "11", "1")
        older_path = self.root / f"{older.key}.json"
        cache = json.loads(self.path.read_text())
        cache["query"] = older.identity()
        older_path.write_text(json.dumps(cache))
        before = older_path.read_bytes()
        opener = FakeOpener({"data": [dict(ROW, 건수=20)]})
        collector = stats.Collector(self.root, refresh_current_year=True, current_year=2026,
                                    opener_factory=lambda: opener)
        with patch.object(stats.time, "sleep"):
            self.assertEqual(10, collector.fetch(older).rows[0]["건수"])
            self.assertEqual(20, collector.fetch(self.query).rows[0]["건수"])
        self.assertEqual(1, opener.calls)
        self.assertEqual(before, older_path.read_bytes())
        self.assertEqual(20, stats.Collector(self.root, offline=True).fetch(self.query).rows[0]["건수"])

    def test_offline_refresh_fails_without_network_or_replacement(self):
        self.write_cache()
        before = self.path.read_bytes()
        with self.assertRaises(stats.ValidationError):
            stats.Collector(self.root, offline=True, refresh_all=True,
                            opener_factory=lambda: self.fail("network opened")).fetch(self.query)
        self.assertEqual(before, self.path.read_bytes())

    def test_atomic_cache_replace_failure_preserves_original_and_cleans_temporary(self):
        self.write_cache()
        before = self.path.read_bytes()
        with patch.object(stats.os, "replace", side_effect=OSError("replace failed")):
            with self.assertRaises(OSError):
                stats.atomic_write(self.path, b"new cache")
        self.assertEqual(before, self.path.read_bytes())
        self.assertFalse(list(self.root.glob(".*.tmp")))

    def test_duplicate_names_invalid_numbers_and_oversized_rows_rejected_but_rank_ties_allowed(self):
        for rows in [[ROW, ROW], [dict(ROW, 건수=-1)], [dict(ROW, 건수=1.2)],
                     [dict(ROW, 건수=True)], [dict(ROW, 건수=0)], [dict(ROW, 순위=0)],
                     [dict(ROW, 이름=" ")], [dict(ROW, 이름="도\n윤")], [ROW] * 21]:
            with self.subTest(rows=rows), self.assertRaises(stats.ValidationError):
                stats.validate_rows(rows)
        tied = [ROW, dict(ROW, 이름="하준")]
        self.assertEqual(tied, stats.validate_rows(tied))

    def test_review_allowlist_requires_reason(self):
        path = self.root / "review.json"
        path.write_text(json.dumps({"schemaVersion": 1, "queries": {self.query.key: ""}}))
        with self.assertRaises(stats.ValidationError):
            stats.read_empty_allowlist(path)
        path.write_text(json.dumps({"schemaVersion": 1, "queries": {self.query.key: "verified empty in source UI"}}))
        self.assertIn(self.query.key, stats.read_empty_allowlist(path))


class BundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.tsv = self.root / "names.tsv"
        self.meta = self.root / "metadata.properties"
        self.tsv.write_bytes(b"old dataset")
        self.meta.write_bytes(b"old metadata")

    def assert_unchanged(self):
        self.assertEqual(b"old dataset", self.tsv.read_bytes())
        self.assertEqual(b"old metadata", self.meta.read_bytes())

    def test_partial_network_failure_never_publishes(self):
        class PartialCollector:
            def fetch(self, query):
                if query.gender == "2":
                    raise OSError("request failed")
                return stats.Snapshot(query, [ROW], "2026-01-01T00:00:00Z")
        with self.assertRaises(OSError):
            stats.build(PartialCollector(), years=[2026], regions=["11"], out_tsv=self.tsv, out_metadata=self.meta)
        self.assert_unchanged()

    def test_metadata_replace_failure_rolls_back_data(self):
        real_replace = stats.os.replace
        def fail_metadata_once(source, target):
            if Path(target) == self.meta:
                raise OSError("metadata replacement failed")
            real_replace(source, target)
        with patch.object(stats.os, "replace", side_effect=fail_metadata_once), self.assertRaises(OSError):
            stats.publish_bundle(self.tsv, self.meta, b"new dataset", b"new metadata")
        self.assert_unchanged()
        self.assertFalse(list(self.root.glob("*.tmp")))
        self.assertFalse(list(self.root.glob(".*.tmp")))

    def test_staging_failure_leaves_both_originals(self):
        real_stage = stats.stage_file
        def fail_second(path, data):
            if Path(path) == self.meta:
                raise OSError("disk full")
            return real_stage(path, data)
        with patch.object(stats, "stage_file", side_effect=fail_second), self.assertRaises(OSError):
            stats.publish_bundle(self.tsv, self.meta, b"new dataset", b"new metadata")
        self.assert_unchanged()
        self.assertFalse(list(self.root.glob(".*.tmp")))

    def test_valid_full_build_hash_dates_and_query_scope(self):
        class FullCollector:
            def fetch(self, query):
                name = "도윤" if query.gender == "1" else "서연"
                return stats.Snapshot(query, [dict(ROW, 이름=name)], "2026-01-02T00:00:00Z")
        with contextlib.redirect_stdout(io.StringIO()):
            count = stats.build(FullCollector(), years=[2025, 2026], regions=["11"], out_tsv=self.tsv, out_metadata=self.meta)
        self.assertEqual(2, count)
        text = self.meta.read_text()
        self.assertIn(f"datasetSha256={stats.sha256(self.tsv.read_bytes())}\n", text)
        self.assertIn("expectedRequestCount=4\n", text)
        self.assertIn("collectedFrom=2026-01-02T00:00:00Z\n", text)
        self.assertIn("sourcePeriodEnd=unknown\n", text)
        self.assertIn("queryYearStart=2025\n", text)
        self.assertIn("queryYearEnd=2026\n", text)

    def test_one_legacy_cache_means_unknown_collection_span(self):
        snapshots = [stats.Snapshot(stats.Query(2026, "11", "1"), [ROW], None),
                     stats.Snapshot(stats.Query(2026, "11", "2"), [ROW], "2026-01-02T00:00:00Z")]
        metadata = stats.render_metadata(b"data", 1, snapshots, [2026], ["11"]).decode()
        self.assertIn("coverageStatus=legacy_unverified\n", metadata)
        self.assertIn("collectedFrom=unknown\n", metadata)
        self.assertIn("collectedThrough=unknown\n", metadata)

    def test_overflow_and_all_filtered_names_do_not_publish(self):
        for row in [dict(ROW, 건수=stats.MAX_INT), dict(ROW, 이름="long-name")]:
            class FullCollector:
                def fetch(self, query):
                    return stats.Snapshot(query, [row], None)
            with self.subTest(row=row), self.assertRaises(stats.ValidationError):
                stats.build(FullCollector(), years=[2026], regions=["11"], out_tsv=self.tsv, out_metadata=self.meta)
            self.assert_unchanged()


if __name__ == "__main__":
    unittest.main()
