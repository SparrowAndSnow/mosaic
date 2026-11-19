# Releasing this fork through JitPack

This fork uses the multi-module group `com.github.SparrowAndSnow.mosaic`.
Kotlin packages and the original Apache 2.0 copyright notices remain unchanged.

## Build and distribution

The release workflow builds all six library modules from source on macOS with the JDK specified
in `.github/workflows/.java-version`. It publishes the KMP root metadata, JVM, `linuxArm64`,
`linuxX64`, `macosArm64`, and `mingwX64` publications into `build/jitpack/repository`.
The TTY JVM jar also contains all eight cross-compiled JNI libraries and the JDK 22+ FFM classes.
Wall-clock frame-pacing assertions run first on Linux; those absolute deadlines were exceeded
on the shared macOS release runner. The Mac job runs all other JVM tests and the full
Native API/publication checks, and cannot publish unless the Linux pacing job succeeds.
Every publication includes a companion `-licensing.zip` with the license and notice texts;
JVM and source jars also carry them under `META-INF`.

A Mac is required because `mosaic-tty` uses C interop for Apple targets. Running a normal Gradle
publication on JitPack's Linux host would leave the Apple publications missing. Instead, the
workflow validates and packages the complete repository as `mosaic-maven.tar.gz` and attaches it,
with a SHA-256 checksum, to the GitHub release. JitPack's custom install command downloads the
assets, checks their checksum, commit, coordinates, and completeness, then copies the repository
into `~/.m2/repository`. No Sonatype credentials or GPG keys are needed.
The install step also retains the verified artifacts under the project build directory and
generates a root `pom.xml` so JitPack's custom-command collector can discover the Maven group.

References: [Kotlin host requirements](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html#host-requirements),
[JitPack custom commands](https://docs.jitpack.io/building/#custom-commands),
[JitPack multi-module coordinates](https://docs.jitpack.io/building/#multi-module-projects).

## Release steps

1. Update `CHANGELOG.md` with the fork's release notes. Use a new tag such as `0.19.0`;
   tags must start with a digit (optionally prefixed by `v`) and contain only letters, digits,
   `.`, `_`, or `-`.
   `SNAPSHOT` versions are not supported. Use the exact tag in dependency versions.
   Add `docs/releases/<version-without-v>.md`; the workflow uses that file as the release body.
2. Run `./gradlew spotlessCheck apiCheck jvmTest` on macOS. Commit the release changes.
3. Create and push the tag explicitly:

   ```bash
   git tag -a 0.19.0 -m "Release 0.19.0"
   git push origin main
   git push origin 0.19.0
   ```

4. Wait for the `release` GitHub Actions workflow to finish. It overrides `VERSION_NAME` with the
   exact tag, checks the project, stages every publication, and creates a GitHub release containing
   `mosaic-maven.tar.gz` and `mosaic-maven.tar.gz.sha256`. `VERSION_NAME` in `gradle.properties`
   remains the local development default. Do not manually create the release before this workflow.
   Before creating the release, an independent consumer compiles against the installed archive
   for every target and links a macOS executable, without project dependency substitution.
   The consumer validation harness comes from the workflow controller commit; published library
   binaries and source archives always come from the selected release tag.
   If the tag push did not start a run, use Actions → release → Run workflow on `main`, setting
   `version` to the existing release tag. The workflow checks out that tag and binds its artifact
   manifest to the checked-out commit, even though the workflow definition comes from `main`.
   The manual form also allows the standard `macos-26` runner when the `macos-15` pool is queued.
5. Look up `SparrowAndSnow/mosaic` and the tag on [JitPack](https://jitpack.io/#SparrowAndSnow/mosaic),
   then request its build. Confirm the build log succeeds and all target modules are listed.
6. Verify a separate JVM application and a Native sample resolve and run with
   `com.github.SparrowAndSnow.mosaic:mosaic-runtime:<release-tag>`.

This pipeline supports release tags only. `main-SNAPSHOT` and commit-only JitPack builds have no
matching multi-platform release asset and intentionally fail. If JitPack was requested too early,
wait for the GitHub release assets, then retry the failed build through JitPack. Never move a
published tag or replace its assets; fix the issue in a new release.

## Local publication checks

On macOS, reproduce staging and validation with a test version:

```bash
./gradlew publishAllPublicationsToJitpackRepository -PVERSION_NAME=v0.19.0-local
python3 scripts/jitpack.py check build/jitpack/repository v0.19.0-local
python3 scripts/jitpack.py pack build/jitpack/repository build/jitpack/mosaic-maven.tar.gz v0.19.0-local "$(git rev-parse HEAD)"
```

On Linux, JVM publications can be checked independently without claiming Apple coverage:

```bash
./gradlew publishJvmPublicationToJitpackRepository -PVERSION_NAME=v0.19.0-local
python3 scripts/jitpack.py check build/jitpack/repository v0.19.0-local --jvm-only
```

The packaging/installation tests use only Python's standard library:

```bash
python3 -m unittest discover -s scripts/tests
```
