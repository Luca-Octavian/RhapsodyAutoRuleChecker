# Rhapsody Rule Verifier

Rhapsody Rule Verifier is a desktop application for checking IBM Rhapsody models against rules defined in a YAML configuration file.

Use it to check model conventions such as:

- Required descriptions, names, tagged values, or port properties
- Required stereotypes
- Naming patterns
- Required relations
- Constraints on elements under a stereotyped owner
- Flow property type and direction requirements

The application loads model data from IBM Rhapsody, evaluates enabled rules, shows failures in a results table, and can export the failures to an Excel file.

## Features

- Load a model directly from IBM Rhapsody
- Reuse a local JSON cache when working offline
- Perform a smart update when an existing cache is available
- Limit an evaluation to a selected package
- Create and edit configurations with the built-in wizard
- Review failed checks and navigate to the related element in Rhapsody
- Export failures to `.xlsx`

## Documentation

- [User guide](docs/USER_GUIDE.md)
- [Configuration reference](docs/CONFIG_REFERENCE.md)
- [Architecture](docs/ARCHITECTURE.md)

## Download

Go to the [Releases](../../releases) page and download the latest `.zip` file. The zip contains everything you need to run the application:

| File | Purpose |
|---|---|
| `RhapsodyRuleVerifier.exe` | The application |
| `RhapsodyRuleVerifier.l4j.ini` | JVM options (editable Rhapsody path) |
| `jre/` | Bundled Java runtime |
| `docs/` | User guide and configuration reference (HTML) |

Extract the zip to any folder and run `RhapsodyRuleVerifier.exe`. No separate Java installation is required.

### Configuring the Rhapsody path

The application needs the Rhapsody Java API native libraries to connect to Rhapsody. By default it looks for them at:

```text
C:\LegacyApp\Rhapsody_1002_64bit\Share\JavaAPI
```

If your Rhapsody installation is in a different location, open `RhapsodyRuleVerifier.l4j.ini` in a text editor, update the path to point to your Rhapsody `Share\JavaAPI` folder, and restart the application.

## Requirements

- Windows
- IBM Rhapsody 10.0.2 with the Java API available

If building from source, you also need Java 8 or later and Maven.

## Quick start

1. Start the application from `RhapsodyRuleVerifier.exe`.
2. Select an IBM Rhapsody project file.
3. Load the model.
4. Select an existing YAML configuration, or create one with **New Config (Wizard)**.
5. Run the configuration.
6. Review failed checks in the results table.
7. Export the failures to Excel if needed.

See the [user guide](docs/USER_GUIDE.md) for the full workflow.

## Build from source

If you want to build the application yourself instead of using the release zip, run the following from the project root:

```bat
mvn package
```

This creates a shaded JAR and a Windows executable under `target/`. The build also copies `RhapsodyRuleVerifier.l4j.ini` into `target/` so that the exe and ini sit side by side.

To create a distributable zip, copy the following from `target/` into a zip file:

- `RhapsodyRuleVerifier.exe`
- `RhapsodyRuleVerifier.l4j.ini`
- A `jre/` folder containing a Java 8 runtime
- The `docs/` folder (contains the generated HTML documentation)

The `jre/` folder is not produced by Maven. You need to copy a JRE 8 installation into `target/jre/` (or into the zip directly) before distributing.

The Maven build expects the Rhapsody Java API libraries at:

```text
C:\LegacyApp\Rhapsody_1002_64bit\Share\JavaAPI
```

Update the path in `pom.xml` if Rhapsody is installed elsewhere on your build machine.

## Example configuration

```yaml
schemaVersion: 1

elementSets:
  InterfaceBlocks:
    kinds: [INTERFACE_BLOCK]

rules:
  - id: interface-block-prefix
    type: NAMING_PATTERN
    title: InterfaceBlock names must start with 'if'
    appliesTo:
      set: InterfaceBlocks
    params:
      startsWith: if
      caseSensitive: true
```

See the [configuration reference](docs/CONFIG_REFERENCE.md) for all supported rule types and wizard-based examples.

## Project layout

```text
src/main/java/       Application source code
src/main/resources/  Example YAML configurations and resources
src/main/launch4j/   Launch4j companion files (ini template)
docs/                User, configuration, and architecture documentation