# rvd-explorer

Desktop explorer for **rotational Voronoi–style diagrams**, built with **Java 25**, **JavaFX**, and the **DrawingFX** / **mars-bits** libraries. The main entry point is `rvd.RVDExplorer`: a single window with interactive gadgets, diagram modes, and serialization via the “Data String” field.

## Requirements

- **JDK 25** that includes **JavaFX** (the `javafx.controls` module is used at compile time, for tests, and at runtime).

  Examples of suitable distributions:

  - [BellSoft Liberica JDK **Full** / `jdk+fx`](https://bell-sw.com/pages/downloads/) (also what CI uses)
  - Other **JDK+FX** builds that ship the `javafx.*` modules on the module path

  A plain JDK without JavaFX (e.g. standard Temurin) is **not** enough unless you add JavaFX yourself.

- **Gradle** is not required globally; the repo includes the **Gradle Wrapper** (`./gradlew`).

## Dependencies

| Kind | Artifact |
|------|-----------|
| Local JARs (committed next to `build.gradle`) | `drawing-fx-2-2022-03-18.jar`, `mars-bits-2026-03-18.jar` |
| Tests | JUnit 5 (via `junit-bom` on Maven Central) |

JavaFX itself comes from the **JDK+FX** install, not from Maven in this project.

## How to build

```bash
./gradlew build
```

Runs compilation, tests, and assembles the standard JAR and distribution artifacts under `build/`.

Clean build:

```bash
./gradlew clean build
```

## How to run (from source)

```bash
./gradlew run
```

The `run` task sets the JVM module flag and uses the project directory as the working directory (same as a typical IDE run from the repo root).

Manual equivalent (after `./gradlew classes` or from a built classpath), conceptually:

```bash
java --add-modules javafx.controls … rvd.RVDExplorer
```

Use your JDK+FX 25 `java` and the classpath/module setup Gradle uses if you run outside Gradle.

## Release JAR (fat JAR)

Published **GitHub Releases** attach **`rvd-explorer-all.jar`**: one JAR containing the application plus the two local library JARs (via the Shadow plugin). It does **not** bundle JavaFX; you still need **JDK+FX 25**.

```bash
java --add-modules javafx.controls -jar rvd-explorer-all.jar
```

Run from a directory where any paths your app expects (e.g. assets, working directory) still make sense—mirrors how `./gradlew run` uses the project root.

To build the fat JAR locally:

```bash
./gradlew shadowJar
```

Output: `build/libs/rvd-explorer-all.jar`.

## Continuous integration and releases

- **CI** (`.github/workflows/ci.yml`): on every **pull request** and on **pushes to `main`**, runs `./gradlew build` on Ubuntu with **Liberica JDK 25 + JavaFX** (`jdk+fx`).
- **Release** (`.github/workflows/release.yml`): when you **publish** a GitHub Release, runs tests and `shadowJar`, then uploads **`rvd-explorer-all.jar`** to that release.

## Contributing

1. **Fork / branch** off `main`, make focused changes, and open a **pull request**.
2. **Keep CI green**: `./gradlew build` should pass locally with JDK+FX 25 before you push.
3. **Match the existing style**: packages under `rvd`, naming and formatting consistent with nearby code; avoid drive-by refactors unless discussed.
4. **Tests**: add or update JUnit tests when behavior is meant to stay fixed; place them under `src/test/java` mirroring the `rvd.*` package layout.

If you change dependencies (JAR names, JavaFX usage, or Java version), update this README and the workflow JDK setup so newcomers and CI stay aligned.

## Repository layout (short)

- `src/` — main Java sources and `META-INF` (test sources are under `src/test/java`, not under `src/test/` inside `src/`).
- `images/` — image resources used by the app.
- `build.gradle`, `settings.gradle`, `gradlew*` — Gradle build and wrapper.
- `.github/workflows/` — CI and release automation.
