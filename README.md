# MaceKill Addon

Meteor Client addon for Minecraft 1.21.11.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.2 or compatible
- Java 21
- Meteor Client 1.21.11-SNAPSHOT

## Build locally

With Gradle installed:

```bash
gradle build
```

The compiled addon is created in `build/libs/`.

## GitHub Actions

The repository includes a workflow under `.github/workflows/build.yml` that builds the addon on GitHub and uploads the JAR as a workflow artifact.
