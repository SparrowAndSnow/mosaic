"""Package and install the full, commit-bound Maven repository used by JitPack."""

import argparse
import copy
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
ET.register_namespace("", NAMESPACE["m"])
ET.register_namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance")


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


def discovery_pom(version):
    validate_version(version)
    pom = ET.Element("project", {"xmlns": NAMESPACE["m"]})
    for name, value in (("modelVersion", "4.0.0"), ("groupId", GROUP),
                        ("artifactId", "mosaic"), ("version", version), ("packaging", "pom")):
        ET.SubElement(pom, name).text = value
    return ET.tostring(pom, encoding="utf-8", xml_declaration=True)


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
    publications = set()
    for module in MODULES:
        for target in TARGETS:
            artifact_id = f"mosaic-{module}{target}"
            publication = group_path / artifact_id / version
            metadata_path = repository / publication / f"{artifact_id}-{version}.module"
            metadata = json.loads(metadata_path.read_text())
            interops = split_interop_publications(repository, publication, artifact_id, version, metadata)
            publications.update(interops)
            changed = bool(interops)
            for variant in metadata["variants"]:
                for artifact in variant.get("files", []):
                    if artifact.get("name") != artifact["url"]:
                        artifact["name"] = artifact["url"]
                        changed = True
            if changed:
                data = (json.dumps(metadata, indent=2) + "\n").encode()
                metadata_path.write_bytes(data)
                for algorithm in ("md5", "sha1", "sha256", "sha512"):
                    checksum = Path(f"{metadata_path}.{algorithm}")
                    if checksum.exists():
                        checksum.write_text(hashlib.new(algorithm, data).hexdigest())
            publications.add(publication)
    for publication in sorted(publications):
        shutil.copytree(repository / publication, destination / publication, dirs_exist_ok=True)
    pom = discovery_pom(version)
    (project / "pom.xml").write_bytes(pom)
    print(f"Prepared JitPack discovery POM and {len(publications)} publications")


def split_interop_publications(repository, publication, artifact_id, version, metadata):
    # JitPack rewrites every .klib entry in a variant to the same main-file URL,
    # including classified cinterop files. Give each cinterop its own Maven module.
    directory = repository / publication
    pom_path = directory / f"{artifact_id}-{version}.pom"
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    pom = ET.parse(pom_path, parser=parser)
    original_pom = copy.deepcopy(pom.getroot())
    interops = {}
    for variant in metadata["variants"]:
        dependencies = copy.deepcopy(variant.get("dependencies", []))
        remaining = []
        for artifact in variant.get("files", []):
            match = re.fullmatch(re.escape(f"{artifact_id}-{version}-cinterop-") + r"([\w.-]+)\.klib", artifact["url"])
            if not match:
                remaining.append(artifact)
                continue
            name = match.group(1)
            interop_id = f"{artifact_id}-cinterop-{name}"
            interop_path = publication.parent.parent / interop_id / version
            interop_dir = repository / interop_path
            interop_dir.mkdir(parents=True, exist_ok=True)
            filename = f"{interop_id}-{version}.klib"
            data = (directory / artifact["url"]).read_bytes()
            (interop_dir / filename).write_bytes(data)
            file = {"name": filename, "url": filename, "size": len(data)}
            file.update({algorithm: hashlib.new(algorithm, data).hexdigest()
                         for algorithm in ("md5", "sha1", "sha256", "sha512")})
            if interop_path not in interops:
                interops[interop_path] = {
                    "formatVersion": metadata.get("formatVersion", "1.1"),
                    "component": {"group": GROUP, "module": interop_id, "version": version},
                    "variants": [],
                }
                root = copy.deepcopy(original_pom)
                root.find("m:artifactId", NAMESPACE).text = interop_id
                # Preserve the marker that tells Gradle to consume .module metadata.
                if not any(child.tag is ET.Comment and "published-with-gradle-metadata" in (child.text or "") for child in root):
                    root.insert(0, ET.Comment(" do_not_remove: published-with-gradle-metadata "))
                ET.ElementTree(root).write(interop_dir / f"{interop_id}-{version}.pom", encoding="utf-8", xml_declaration=True)
                shutil.copyfile(directory / f"{artifact_id}-{version}-licensing.zip",
                                interop_dir / f"{interop_id}-{version}-licensing.zip")
            interops[interop_path]["variants"].append({
                "name": variant["name"], "attributes": variant.get("attributes", {}),
                "dependencies": dependencies, "files": [file],
            })
            dependency = {"group": GROUP, "module": interop_id, "version": {"requires": version}}
            if dependency not in variant.setdefault("dependencies", []):
                variant["dependencies"].append(dependency)
            pom_dependencies = pom.getroot().find("m:dependencies", NAMESPACE)
            if pom_dependencies is None:
                pom_dependencies = ET.SubElement(pom.getroot(), f"{{{NAMESPACE['m']}}}dependencies")
            if not any(d.findtext("m:artifactId", namespaces=NAMESPACE) == interop_id for d in pom_dependencies):
                entry = ET.SubElement(pom_dependencies, f"{{{NAMESPACE['m']}}}dependency")
                for key, value in (("groupId", GROUP), ("artifactId", interop_id), ("version", version), ("scope", "compile")):
                    ET.SubElement(entry, f"{{{NAMESPACE['m']}}}{key}").text = value
        if "files" in variant:
            variant["files"] = remaining
    if interops:
        pom.write(pom_path, encoding="utf-8", xml_declaration=True)
        for path in (Path(f"{pom_path}.{a}") for a in ("md5", "sha1", "sha256", "sha512")):
            if path.exists():
                path.write_text(hashlib.new(path.suffix[1:], pom_path.read_bytes()).hexdigest())
    for path, interop_metadata in interops.items():
        interop_dir = repository / path
        artifact = path.parent.name
        (interop_dir / f"{artifact}-{version}.module").write_text(json.dumps(interop_metadata, indent=2) + "\n")
    return interops


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
