import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("jitpack", Path(__file__).parents[1] / "jitpack.py")
jitpack = importlib.util.module_from_spec(spec)
spec.loader.exec_module(jitpack)


class JitpackTest(unittest.TestCase):
    version = "0.19.0-test.1"
    commit = "1" * 40

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repository = self.root / "repository"
        self.archive = self.root / "mosaic-maven.tar.gz"
        self.destination = self.root / "maven-local"
        self.group_dir = self.repository / jitpack.GROUP.replace(".", "/")
        for module in jitpack.MODULES:
            for target in jitpack.TARGETS:
                artifact = f"mosaic-{module}{target}"
                directory = self.group_dir / artifact / self.version
                directory.mkdir(parents=True)
                name = f"{artifact}-{self.version}"
                (directory / f"{name}.pom").write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>{jitpack.GROUP}</groupId><artifactId>{artifact}</artifactId>
                    <version>{self.version}</version></project>''')
                filename = f"{name}.jar" if target in ("", "-jvm") else f"{name}.klib"
                (directory / filename).write_bytes(b"artifact")
                if target == "-jvm":
                    with zipfile.ZipFile(directory / filename, "w") as jar:
                        jar.writestr("META-INF/LICENSE.txt", "Apache-2.0")
                        jar.writestr("META-INF/NOTICE.txt", "Mosaic fork")
                with zipfile.ZipFile(directory / f"{name}-licensing.zip", "w") as licenses:
                    for entry in ("LICENSE.txt", "NOTICE.txt", "licenses/Unicode-3.0.txt",
                                  "licenses/OpenJDK-GPL-2.0-with-Classpath-Exception.txt"):
                        licenses.writestr(entry, "License fixture")
                (directory / f"{name}.module").write_text(json.dumps({
                    "component": {"group": jitpack.GROUP, "version": self.version},
                    "variants": [{"name": "apiElements", "files": [{"name": filename, "url": filename}]}],
                }))
        with zipfile.ZipFile(self.tty_jar(), "a") as jar:
            for index in range(8):
                jar.writestr(f"jni/{index}/libmosaic.so", b"library")
            jar.writestr("META-INF/versions/22/Libmosaic.class", b"class")

    def tty_jar(self):
        return self.group_dir / "mosaic-tty-jvm" / self.version / f"mosaic-tty-jvm-{self.version}.jar"

    def pack(self):
        jitpack.pack(self.repository, self.archive, self.version, self.commit)

    def test_round_trip_preserves_all_publications(self):
        self.pack()
        jitpack.install(self.archive, self.version, self.commit, self.destination)
        originals = {p.relative_to(self.repository): p.read_bytes()
                     for p in self.repository.rglob("*") if p.is_file()}
        installed = {p.relative_to(self.destination): p.read_bytes()
                     for p in self.destination.rglob("*") if p.is_file()}
        self.assertEqual(originals, installed)

    def test_rejects_archive_for_another_commit(self):
        self.pack()
        with self.assertRaisesRegex(ValueError, "checked-out commit"):
            jitpack.install(self.archive, self.version, "2" * 40, self.destination)
        self.assertFalse(self.destination.exists())

    def test_prepare_exposes_every_publication_for_jitpack_discovery(self):
        self.pack()
        project = self.root / "jitpack-build"
        jitpack.prepare_jitpack(self.archive, self.version, self.commit, project, self.destination)
        pom = ET.parse(project / "pom.xml").getroot()
        self.assertEqual(pom.findtext("m:groupId", namespaces=jitpack.NAMESPACE), jitpack.GROUP)
        self.assertEqual(pom.findtext("m:version", namespaces=jitpack.NAMESPACE), self.version)
        self.assertEqual(pom.findtext("m:packaging", namespaces=jitpack.NAMESPACE), "pom")
        originals = {p.relative_to(self.repository): p.read_bytes()
                     for p in self.repository.rglob("*") if p.is_file()}
        for repository in (project / "build" / "jitpack" / "installed" / self.version, self.destination):
            files = {p.relative_to(repository): p.read_bytes()
                     for p in repository.rglob("*") if p.is_file()}
            self.assertEqual(originals, files)
            group = repository / pom.findtext("m:groupId", namespaces=jitpack.NAMESPACE).replace(".", "/")
            self.assertEqual(len(list(group.glob(f"*/{self.version}/*.pom"))), 36)

    def test_prepare_rejects_unverified_archive_before_exposing_artifacts(self):
        self.pack()
        project = self.root / "jitpack-build"
        with self.assertRaisesRegex(ValueError, "checked-out commit"):
            jitpack.prepare_jitpack(self.archive, self.version, "2" * 40, project, self.destination)
        self.assertFalse((project / "pom.xml").exists())
        self.assertFalse(self.destination.exists())

    def test_prepare_splits_native_interop_and_refreshes_metadata_checksum(self):
        directory = self.group_dir / "mosaic-tty-linuxx64" / self.version
        name = f"mosaic-tty-linuxx64-{self.version}"
        metadata_path = directory / f"{name}.module"
        metadata = json.loads(metadata_path.read_text())
        files = metadata["variants"][0]["files"]
        files[0]["name"] = f"mosaic-tty-linuxX64Main-{self.version}.klib"
        interop = f"{name}-cinterop-mosaic.klib"
        (directory / interop).write_bytes(b"cinterop fixture")
        files.append({"name": f"mosaic-tty-linuxX64Cinterop-mosaicMain-{self.version}.klib", "url": interop})
        metadata_path.write_text(json.dumps(metadata))
        Path(f"{metadata_path}.sha256").write_text(hashlib.sha256(metadata_path.read_bytes()).hexdigest())
        self.pack()
        project = self.root / "jitpack-build"
        jitpack.prepare_jitpack(self.archive, self.version, self.commit, project, self.destination)
        installed = self.destination / directory.relative_to(self.repository)
        path = installed / metadata_path.name
        files = json.loads(path.read_text())["variants"][0]["files"]
        self.assertEqual([f["name"] for f in files], [f"{name}.klib"])
        self.assertEqual(Path(f"{path}.sha256").read_text(), hashlib.sha256(path.read_bytes()).hexdigest())
        self.assertEqual((installed / f"{name}.klib").read_bytes(), b"artifact")
        interop_id = "mosaic-tty-linuxx64-cinterop-mosaic"
        dependency = {"group": jitpack.GROUP, "module": interop_id, "version": {"requires": self.version}}
        self.assertIn(dependency, json.loads(path.read_text())["variants"][0]["dependencies"])
        interop_dir = installed.parent.parent / interop_id / self.version
        self.assertEqual((interop_dir / f"{interop_id}-{self.version}.klib").read_bytes(), b"cinterop fixture")
        interop_metadata = json.loads((interop_dir / f"{interop_id}-{self.version}.module").read_text())
        self.assertEqual(interop_metadata["component"]["module"], interop_id)
        self.assertEqual(len(interop_metadata["variants"][0]["files"]), 1)
        self.assertIn("published-with-gradle-metadata", (interop_dir / f"{interop_id}-{self.version}.pom").read_text())
        self.assertTrue((interop_dir / f"{interop_id}-{self.version}-licensing.zip").is_file())

    def test_excludes_older_versions_in_staging_repository(self):
        stale = self.group_dir / "mosaic-runtime" / "v0.18.0"
        stale.mkdir()
        (stale / "old.pom").write_text("old version")
        self.pack()
        jitpack.install(self.archive, self.version, self.commit, self.destination)
        self.assertFalse((self.destination / stale.relative_to(self.repository)).exists())

    def test_rejects_corrupted_archive(self):
        self.pack()
        with self.archive.open("ab") as archive:
            archive.write(b"corruption")
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            jitpack.install(self.archive, self.version, self.commit, self.destination)

    def test_missing_native_publication_fails_before_packaging(self):
        next(self.group_dir.glob("mosaic-tty-macosarm64/*/*.pom")).unlink()
        with self.assertRaises(FileNotFoundError):
            self.pack()
        self.assertFalse(self.archive.exists())

    def test_rejects_upstream_and_wrong_version_dependencies(self):
        for group, version in (("com.jakewharton.mosaic", self.version), (jitpack.GROUP, "0.18.0")):
            with self.subTest(group=group, version=version), self.assertRaises(ValueError):
                jitpack.validate_dependency(group, "mosaic-runtime", version, self.version)

    def test_requires_all_jni_libraries(self):
        with zipfile.ZipFile(self.tty_jar(), "w") as jar:
            jar.writestr("META-INF/LICENSE.txt", "Apache-2.0")
            jar.writestr("META-INF/NOTICE.txt", "Mosaic fork")
            jar.writestr("jni/linux/libmosaic.so", b"library")
        with self.assertRaisesRegex(ValueError, "eight JNI"):
            self.pack()

    def test_rejects_missing_license_bundle(self):
        next(self.group_dir.glob("mosaic-runtime-macosarm64/*/*-licensing.zip")).unlink()
        with self.assertRaises(FileNotFoundError):
            self.pack()

    def test_rejects_unsafe_archive_paths_and_links(self):
        for name, kind in (("../outside", tarfile.REGTYPE), ("link", tarfile.SYMTYPE)):
            with self.subTest(name=name):
                with tarfile.open(self.archive, "w:gz") as tar:
                    member = tarfile.TarInfo(name)
                    member.type = kind
                    member.linkname = "../outside" if kind == tarfile.SYMTYPE else ""
                    member.size = 0
                    tar.addfile(member, io.BytesIO())
                Path(f"{self.archive}.sha256").write_text(hashlib.sha256(self.archive.read_bytes()).hexdigest())
                with self.assertRaisesRegex(ValueError, "Unsafe archive"):
                    jitpack.install(self.archive, self.version, self.commit, self.destination)

    def test_accepts_numeric_and_v_prefixed_tags(self):
        for version in ("0.19.0", "v0.19.0", "0.19.0-fork.1"):
            with self.subTest(version=version):
                jitpack.validate_version(version)

    def test_rejects_snapshots_and_unsafe_versions(self):
        for version in ("main-SNAPSHOT", "v1.0-SNAPSHOT", "../v1", "v1/x", "$(id)"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                jitpack.validate_version(version)


if __name__ == "__main__":
    unittest.main()
