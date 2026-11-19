"""Package and install the full, commit-bound Maven repository used by JitPack."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import tarfile
import tempfile
import xml.etree.ElementTree as ET
import zipfile

GROUP = "com.github.SparrowAndSnow.mosaic"
MODULES = ("animation", "runtime", "terminal", "testing", "tty", "tty-terminal")
TARGETS = ("", "-jvm", "-linuxarm64", "-linuxx64", "-macosarm64", "-mingwx64")
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


def validate_version(version):
    if not re.fullmatch(r"v?[0-9][0-9A-Za-z._-]*", version) or "SNAPSHOT" in version:
        raise ValueError("Use a release tag such as 0.19.0; snapshots are not supported")


def validate_repository(repository, version, targets=TARGETS):
    group_dir = repository / GROUP.replace(".", "/")
    for module in MODULES:
        for target in targets:
            artifact = f"mosaic-{module}{target}"
            directory = group_dir / artifact / version
            base = directory / f"{artifact}-{version}"
            pom = ET.parse(f"{base}.pom").getroot()
            for field, expected in (("groupId", GROUP), ("artifactId", artifact), ("version", version)):
                if pom.findtext(f"m:{field}", namespaces=NAMESPACE) != expected:
                    raise ValueError(f"Incorrect {field} in {base}.pom")
            metadata = json.loads(Path(f"{base}.module").read_text())
            if metadata["component"]["group"] != GROUP or metadata["component"]["version"] != version:
                raise ValueError(f"Incorrect component coordinates in {base}.module")
            for variant in metadata["variants"]:
                for file in variant.get("files", []):
                    path = directory / file["url"]
                    if not path.is_file() or path.stat().st_size == 0:
                        raise ValueError(f"Missing publication file: {path}")
                reference = variant.get("available-at")
                if reference:
                    if reference["group"] != GROUP or reference["version"] != version:
                        raise ValueError(f"Incorrect target coordinates in {base}.module")
                    if targets == TARGETS and not (directory / reference["url"]).is_file():
                        raise ValueError(f"Missing target metadata: {reference['url']}")
                for dependency in variant.get("dependencies", []):
                    validate_dependency(dependency["group"], dependency["module"],
                                        dependency.get("version", {}).get("requires"), version)
            for dependency in pom.findall(".//m:dependency", NAMESPACE):
                validate_dependency(dependency.findtext("m:groupId", namespaces=NAMESPACE),
                                    dependency.findtext("m:artifactId", namespaces=NAMESPACE),
                                    dependency.findtext("m:version", namespaces=NAMESPACE), version)
            with zipfile.ZipFile(f"{base}-licensing.zip") as licenses:
                required = {"LICENSE.txt", "NOTICE.txt", "licenses/Unicode-3.0.txt",
                            "licenses/OpenJDK-GPL-2.0-with-Classpath-Exception.txt"}
                if not required.issubset(licenses.namelist()):
                    raise ValueError(f"Missing license texts in {base}-licensing.zip")
            if target == "-jvm":
                with zipfile.ZipFile(f"{base}.jar") as jar:
                    if not {"META-INF/LICENSE.txt", "META-INF/NOTICE.txt"}.issubset(jar.namelist()):
                        raise ValueError(f"Missing license/notice in {base}.jar")
    # JVM users need all eight JNI binaries and the JDK 22+ FFM implementation.
    tty_jar = group_dir / "mosaic-tty-jvm" / version / f"mosaic-tty-jvm-{version}.jar"
    with zipfile.ZipFile(tty_jar) as jar:
        names = jar.namelist()
        libraries = [n for n in names if n.endswith((".so", ".dylib", ".dll"))]
        if len(libraries) != 8 or not any(n.startswith("META-INF/versions/22/") for n in names):
            raise ValueError("TTY JVM jar must contain all eight JNI libraries and JDK 22 FFM classes")


def validate_dependency(group, artifact, dependency_version, version):
    if group == "com.jakewharton.mosaic" or (artifact.startswith("mosaic-") and group != GROUP):
        raise ValueError(f"Dependency still points outside this fork: {group}:{artifact}")
    if group == GROUP and dependency_version != version:
        raise ValueError(f"Incorrect inter-module version: {artifact}:{dependency_version}")


def pack(repository, archive, version, commit):
    validate_version(version)
    validate_repository(repository, version)
    archive.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        manifest = Path(temporary) / "manifest.json"
        manifest.write_text(json.dumps({"group": GROUP, "version": version, "commit": commit}) + "\n")
        with tarfile.open(archive, "w:gz", dereference=True) as tar:
            tar.add(manifest, arcname="manifest.json")
            group_dir = repository / GROUP.replace(".", "/")
            for artifact in sorted(group_dir.iterdir()):
                publication = artifact / version
                if publication.is_dir():
                    tar.add(publication, arcname="repository/" + str(publication.relative_to(repository)))
    checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
    Path(f"{archive}.sha256").write_text(f"{checksum}  {archive.name}\n")


def install(archive, version, commit, destination):
    validate_version(version)
    expected = Path(f"{archive}.sha256").read_text().split()[0]
    if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
        raise ValueError("Release archive SHA-256 mismatch")
    with tempfile.TemporaryDirectory() as temporary:
        staging = Path(temporary).resolve()
        with tarfile.open(archive, "r:gz") as tar:
            # Only ordinary files/directories inside the staging tree are accepted.
            for member in tar.getmembers():
                path = staging / member.name
                if staging not in path.resolve().parents or not (member.isfile() or member.isdir()):
                    raise ValueError(f"Unsafe archive entry: {member.name}")
            for member in tar.getmembers():
                path = staging / member.name
                if member.isdir():
                    path.mkdir(parents=True, exist_ok=True)
                else:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    with tar.extractfile(member) as source, path.open("wb") as output:
                        shutil.copyfileobj(source, output)
        manifest = json.loads((staging / "manifest.json").read_text())
        if manifest != {"group": GROUP, "version": version, "commit": commit}:
            raise ValueError("Release archive does not match the checked-out commit/version/group")
        repository = staging / "repository"
        validate_repository(repository, version)
        shutil.copytree(repository, destination, dirs_exist_ok=True)
    print(f"Installed {GROUP}:*:{version} for {commit}")


def prepare_jitpack(archive, version, commit, project, destination):
    # JitPack's custom-command collector harvests the group from a root pom.xml.
    # Keep project artifacts as well as Maven-local copies, as required by its build contract.
    repository = project / "build" / "jitpack" / "installed" / version
    install(archive, version, commit, repository)
    group_path = Path(GROUP.replace(".", "/"))
    for module in MODULES:
        for target in TARGETS:
            publication = group_path / f"mosaic-{module}{target}" / version
            shutil.copytree(repository / publication, destination / publication, dirs_exist_ok=True)
    pom = ET.Element("project", {"xmlns": NAMESPACE["m"]})
    for name, value in (("modelVersion", "4.0.0"), ("groupId", GROUP),
                        ("artifactId", "mosaic"), ("version", version), ("packaging", "pom")):
        ET.SubElement(pom, name).text = value
    ET.ElementTree(pom).write(project / "pom.xml", encoding="utf-8", xml_declaration=True)
    print(f"Prepared JitPack discovery POM and {len(MODULES) * len(TARGETS)} publications")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("version").add_argument("version")
    check = subparsers.add_parser("check")
    check.add_argument("repository", type=Path)
    check.add_argument("version")
    check.add_argument("--jvm-only", action="store_true", help="Local Linux validation only")
    for command in ("pack", "install", "prepare"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("path", type=Path)
        if command == "pack":
            subparser.add_argument("archive", type=Path)
        subparser.add_argument("version")
        subparser.add_argument("commit")
        if command == "prepare":
            subparser.add_argument("project", type=Path)
        if command in ("install", "prepare"):
            subparser.add_argument("destination", type=Path)
    args = parser.parse_args()
    if args.command == "version":
        validate_version(args.version)
    elif args.command == "check":
        validate_repository(args.repository, args.version, ("-jvm",) if args.jvm_only else TARGETS)
    elif args.command == "pack":
        pack(args.path, args.archive, args.version, args.commit)
    elif args.command == "prepare":
        prepare_jitpack(args.path, args.version, args.commit, args.project, args.destination)
    else:
        install(args.path, args.version, args.commit, args.destination)


if __name__ == "__main__":
    main()
