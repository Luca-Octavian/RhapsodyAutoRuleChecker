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

## Requirements

- Windows
- IBM Rhapsody 10.0.2 with the Java API available
- Java 8 or later
- Maven, if building from source

## Build

From the project root, run:

```bat
mvn package
```

The Maven build creates a shaded application JAR and a Windows executable under `target/`.

The application needs access to the IBM Rhapsody Java API and its native libraries. The current Maven configuration expects the Rhapsody Java API libraries at:

```text
C:\LegacyApp\Rhapsody_1002_64bit\Share\JavaAPI
```

Update the path in `pom.xml` if Rhapsody is installed elsewhere.

## Quick start

1. Start the application from the generated files in `target/`.
2. Select an IBM Rhapsody project file.
3. Load the model.
4. Select an existing YAML configuration, or create one with **New Config (Wizard)**.
5. Run the configuration.
6. Review failed checks in the results table.
7. Export the failures to Excel if needed.

See the [user guide](docs/USER_GUIDE.md) for the full workflow.

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
docs/                User, configuration, and architecture documentation
```
