# Architecture

This document covers the internal design, runtime pipeline, and extension points of Rhapsody Rule Verifier. It is intended for developers who need to maintain, debug, or extend the project.

For end-user documentation, see the [User Guide](USER_GUIDE.md) and [Configuration Reference](CONFIG_REFERENCE.md).

## Technology stack

- Java 8 (source and target level)
- Swing (FlatLaf dark theme via `AppTheme`)
- IBM Rhapsody Java API (COM interop on Windows)
- SnakeYAML for configuration parsing
- Apache POI for Excel export
- Jackson for JSON cache serialization
- Maven with the Shade plugin (fat JAR) and Launch4j (Windows `.exe`)

## Package layout

```text
org.rhapsodychecker.rhapsodyruleverifier
  adapter/rhapsody/         Rhapsody COM adapter and model loading
    load/                   Element, tag, reference, and relation readers
  cache/                    JSON model cache, metadata, freshness, incremental update
    update/                 Fingerprinting and diffing for incremental cache refresh
  config/                   YAML config model, loader, element-set definitions
    generate/               Wizard state, config builder, YAML writer, schema registry
      schema/               Rule parameter schemas for the wizard
  core/
    config/                 Enums: RuleType, AliasKind, ComparisonOperator, etc.
    index/                  ElementIndex and ElementIndexBuilder
    model/                  ElementRecord, ElementKind (domain model, no Rhapsody dependency)
    profiler/               PipelineProfiler, PhaseTimer, diagnostics
    progress/               LoadingStep, ProgressReporter interface
    resolve/                AliasResolver interface and ResolvedValue
    rule/                   Rule interface, RuleEngine, RuleFactory, RuleResult, RuleGrouping
      impl/                 Built-in rule implementations
    selector/               ElementSelector (scope resolution from config)
    service/                ModelLoadService, EvaluationService, ExportService, NavigationService
    util/                   ReflectiveMethodCache
  detection/                Model detection facade, port probing, suggestions
  export/                   ExcelReportExporter
  prefs/                    RecentFilesStore (Java Preferences API)
  ui/                       Swing UI: MainFrame, PackageTreePanel, ResultsTablePanel
    controller/             MainFrameController (all UI logic)
    style/                  AppTheme, AccentColors, GradientAccentButton, etc.
    wizard/                 WizardDialog, ElementSetDialog, RuleDialog, ReviewStepPanel
      help/                 HoverInfoProvider, StepIndicator, CollapsibleSection
      rule/                 RuleParameterEditor, RuleFormLayout, RuleValueCodec
```

## Runtime pipeline

The application follows a linear pipeline when loading a model, running rules, and producing results.

```text
1. Connect to Rhapsody (or read cached JSON)
2. Build RhapsodyModelSnapshot (elements, relations, references)
3. Build ElementIndex (GUID-keyed lookup, kind/stereotype/owner indexes)
4. Build package tree (for the UI tree view)
5. Run fast detection (aggregate element statistics for wizard suggestions)
6. Write or update the JSON cache
7. Load YAML configuration (ConfigLoader -> RuleCheckerConfig)
8. For each enabled rule:
   a. ElementSelector resolves the matching elements
   b. RuleFactory creates the Rule from RuleSpec
   c. Rule.evaluate() runs for each selected element
9. RuleGrouping conjoins grouped results per element
10. Results are displayed in the tree view and can be exported to Excel
```

`ModelLoadService` handles steps 1 through 6 and supports three paths:

- **Full Rhapsody load** connects via COM, scans all elements, writes a new cache.
- **Offline cache load** reads a previously saved JSON file without contacting Rhapsody. The result is marked as freshness-unverified.
- **Incremental update** reads the existing cache, connects to Rhapsody, compares save-unit markers, and only re-reads changed elements. It falls back to a full load when Rhapsody is unavailable or the cache is incompatible.

`EvaluationService` handles steps 7 through 9.

## Rhapsody adapter layer

All Rhapsody API interaction is isolated under `adapter/rhapsody/`. The core packages never import `com.telelogic.rhapsody.core` directly.

Key classes:

- `RhapsodyConnectionManager` manages the COM connection singleton.
- `RhapsodyModelLoader` walks the model and delegates to readers.
- `RhapsodyElementReader`, `RhapsodyTagReader`, `RhapsodyReferenceReader` read individual element properties through the COM API.
- `ModelRecordPostProcessor` normalizes loaded data into `ElementRecord` objects.
- `RhapsodyModelSnapshot` holds the complete in-memory model state: records, relations by owner, references by element, and a GUID-to-handle map.
- `RhapsodyAliasResolver` resolves target fields (description, name, tagged values, port properties) from the snapshot.
- `RhapsodyEvaluationContext` wires the alias resolver and selector into the `EvaluationContext` interface used by rules.

This separation means that a different adapter (for example, reading from an exported XMI file) could implement `EvaluationContext` and `AliasResolver` without changing any rule or config code.

## Domain model

`ElementRecord` is an immutable value object holding the normalized data for one model element: GUID, name, meta-class, `ElementKind`, owner path, owner GUID, stereotypes, description, tag values, type name, port direction, port multiplicity, initial value, and any relations and references attached during loading.

`ElementKind` is an enum that classifies Rhapsody elements into application-level categories such as `BLOCK`, `INTERFACE_BLOCK`, `PART`, `PORT_STANDARD`, `PORT_FLOW`, `PORT_PROXY`, `FLOW_PROPERTY`, `REQUIREMENT`, `PACKAGE`, `INTERFACE`, and `CONNECTOR`. The `ElementKind` mapping is handled during loading so that rules and element sets can refer to stable kind names instead of Rhapsody's internal meta-class strings.

## Element index

`ElementIndex` provides fast lookup over all loaded `ElementRecord` objects. It is built once after loading and used throughout evaluation.

`ElementIndexBuilder` constructs the index from a list of records. The index provides:

- GUID-based record lookup
- Kind-based grouping
- Stereotype-based grouping
- Owner-based lookups

These indexes allow `ElementSelector` to resolve element-set filters efficiently without scanning every record on every rule.

## Configuration and parsing

`ConfigLoader` parses a YAML file into an immutable `RuleCheckerConfig` using SnakeYAML.

The configuration model consists of:

- `RuleCheckerConfig`: top-level container with schema version, element sets, and rules.
- `ElementSetDefinition`: named filter with kinds, types, stereotypes, and package patterns.
- `RuleSpec`: immutable rule specification with ID, type, scope, target, params, group, and metadata.
- `TargetSpec`: identifies the field a `REQUIRED_VALUE` rule reads (kind, profile, tag name).

Validation happens at build time inside `RuleCheckerConfig.Builder.build()`. Cross-references between rules and element sets are checked. Parse errors are collected and reported together rather than failing on the first problem.

## Rule engine

### Rule interface

Every rule implements the `Rule` interface:

```java
public interface Rule {
    String id();
    String title();
    void configure(Map<String, Object> params);
    boolean appliesTo(ElementRecord element, EvaluationContext context);
    RuleResult evaluate(ElementRecord element, EvaluationContext context);
}
```

`configure()` receives a map containing `ruleId`, `ruleTitle`, `ruleMessage`, `target` (for `REQUIRED_VALUE`), and a nested `params` map with rule-specific settings. Each built-in rule parses its own parameters from this map.

### Rule factory

`RuleFactory` maps each `RuleType` enum value to a `Supplier<Rule>`. The built-in types are registered in a static initializer. To add a new rule type:

1. Add a value to `RuleType` and update `RuleType.fromString()`.
2. Create a class implementing `Rule` under `core/rule/impl/`.
3. Register it in the `RuleFactory` static block.
4. Add a schema to `RuleParamSchemaRegistry` so the wizard can display its parameters.
5. Add a case to `RuleParameterEditor.build()` for the wizard UI.

External code can also call `RuleFactory.register(type, supplier)` at startup without modifying the factory class.

### Rule evaluation flow

`RuleEngine` iterates over enabled rules. For each rule, `ElementSelector` resolves the matching elements based on the rule's `appliesTo` scope. The rule's `evaluate()` method is called for each matching element, producing a `RuleResult` with a status of `PASS`, `FAIL`, or `SKIPPED`.

After individual evaluation, `RuleGrouping` conjoins results for rules that share a `group` label. An element passes its group only when every rule in that group passes for it.

### Element selection

`ElementSelector` resolves a rule's element scope against the `ElementIndex`. It supports two modes:

- Reference to a named element set (`appliesTo.set`).
- Inline filters (`appliesTo.types`, `appliesTo.stereotypes`, `appliesTo.includePackages`, `appliesTo.excludePackages`).

When a package scope is active (the user selected a package in the tree before clicking Run), the selector further filters by the selected package path.

## Cache system

### JSON model cache

`ModelCacheManager` serializes `RhapsodyModelSnapshot` to JSON using Jackson. The cache file is stored alongside the model file by default. Each cache includes `CacheMetadata` with:

- Cache format version
- Project name and GUID
- Canonical model path
- Timestamp
- Element count
- Snapshot completeness flag
- Save-unit markers (for freshness detection)

Cache validation occurs before offering the cache to the user. Mismatched model paths, outdated format versions, and corrupted files are rejected with specific error messages.

### Incremental update

`IncrementalCacheUpdater` compares the existing cached elements against a live Rhapsody scan. It uses `ElementFingerprint` to detect changes without fully reading every element. `DiffResult` tracks changed, new, removed, and unchanged GUIDs.

Only changed and new elements are fully read from Rhapsody. Unchanged elements are reused from the cache. This reduces the time needed for large models where only a small part has changed.

### Cache freshness

`RhapsodyFreshnessProbe` implements a tiered freshness check:

1. **Tier 1 (O(1))**: Check whether the project has unsaved modifications.
2. **Tier 2 (O(units))**: Compare Rhapsody's save-unit markers against the cached markers.
3. **Tier 3 (full scan)**: Run the incremental element scan when markers differ or are unavailable.

If both tiers 1 and 2 pass, the cache can be reused without scanning any elements.

`CacheStatus` is an enum that tracks the provenance of a loaded snapshot: `LIVE_FULL_LOAD`, `LIVE_INCREMENTAL_REFRESHED`, `LIVE_INCREMENTAL_PARTIAL`, `LIVE_UNITS_UNCHANGED`, `OFFLINE_CACHE_UNVERIFIED`, or `CACHE_CORRUPT`. The UI displays the appropriate status message for each.

## Profiler

`PipelineProfiler` measures wall-clock time for each phase of the pipeline. It collects `PhaseTimer` instances, each of which records:

- Phase name
- Duration in nanoseconds
- Optional item count (for throughput calculation)

After a pipeline completes, `PipelineProfiler.summary()` produces a formatted table showing each phase's duration, percentage of total time, and throughput (items per second). This output is written to the log.

Additional profiler utilities:

- `BenchmarkStatistics` collects repeated measurements for statistical analysis.
- `ComLoadDiagnostics` profiles COM API call patterns during model loading.
- `SnapshotEquivalence` compares two snapshots for structural equality (used in testing).

The profiler is always active. It does not need to be enabled or configured. Its output appears in the application log file.

## Logger

`AppLogger` wraps `java.util.logging` with no external dependencies. It is initialized once at application startup in `MainFrame.main()` and closed on shutdown via a runtime shutdown hook.

Log output goes to two destinations:

- **File**: all levels, stored in `%TEMP%/.rhapsody-logs/` with a timestamped filename.
- **Console**: INFO level and above.

The format is `[date time] [LEVEL] message`.

Structured log helpers produce labeled summaries for model loading, evaluation, cache reads, cache writes, and incremental updates. These summaries include element counts, durations, and provenance information.

To find logs, open the `%TEMP%/.rhapsody-logs/` directory. Each application run creates a separate file named `rule-checker-YYYY-MM-DD_HH-mm-ss.log`.

## UI architecture

### MainFrame and MainFrameController

`MainFrame` is a pure layout class. It creates Swing components, wires button listeners, and implements the `MainFrameController.View` interface. It contains no business logic.

`MainFrameController` owns all application state and coordinates between the UI and the service layer. It manages SwingWorkers for background operations, handles button enablement, recent-file tracking, and error reporting.

This separation means the controller can be tested or driven without a visible window.

### Results display

`ResultsTablePanel` displays evaluation failures in a JTree layout:

- Ungrouped failures appear as top-level nodes with rule ID, element name, and abbreviated reason.
- Grouped failures are nested: a parent node shows the group name and element, and its children show each failed rule.
- A filter field narrows displayed results by rule ID, element name, location, or reason text.
- A details pane shows the full element path and failure message for the selected node.
- Double-clicking an individual rule node navigates to the element in Rhapsody.

### Wizard

`WizardDialog` uses a `CardLayout` with three steps: Element sets, Rules, and Review.

- `ElementSetDialog` and `ElementSetStepPanel` handle element-set creation and editing.
- `RuleDialog` handles rule creation with common fields (ID, type, element set, group, message) and delegates type-specific controls to `RuleParameterEditor`.
- `ReviewStepPanel` shows the final configuration summary and allows disabling individual rules before saving.
- `RuleParamSchemaRegistry` defines the expected parameters for each rule type so the wizard can validate input.

### Theme and styling

`AppTheme` applies FlatLaf with customized colors. `AccentColors` defines semantic color constants (orange for model actions, green for export, purple for wizard/config actions). `GradientAccentButton` renders buttons with gradient borders and hover effects. Components like `GradientProgressBar`, `SectionHeader`, `EmptyStatePanel`, and `IndentGuideTree` provide consistent visual treatment.

The theme is applied once at startup. All UI components pick up the theme automatically through FlatLaf's look-and-feel integration.

## Packaging

Maven produces two outputs during `mvn package`:

1. A shaded JAR containing all dependencies (via `maven-shade-plugin`). The main class is `org.rhapsodychecker.rhapsodyruleverifier.ui.MainFrame`.
2. A Windows executable wrapping that JAR (via `launch4j-maven-plugin`). The executable looks for a `jre` directory beside it and falls back to the system JRE.

Both outputs require the Rhapsody Java API native libraries at runtime. The current build configuration expects them at `C:\LegacyApp\Rhapsody_1002_64bit\Share\JavaAPI`. This path is set in the Maven Surefire plugin (for tests) and in the Launch4j JVM options (for the executable).

## Adding a new rule type

To add a new rule type to the project:

1. **Add the enum value.** Add a new entry to `RuleType` and add its string mappings to `RuleType.fromString()`.

2. **Create the rule implementation.** Add a class under `core/rule/impl/` that implements `Rule`. Parse your custom parameters in `configure()`. Return `PASS`, `FAIL`, or `SKIPPED` from `evaluate()`.

3. **Register in the factory.** Add a line in the `RuleFactory` static initializer:
   ```java
   register(RuleType.YOUR_NEW_TYPE, YourNewRule::new);
   ```

4. **Add a parameter schema.** Add a method in `RuleParamSchemaRegistry` that returns a `RuleParamSchema` for your type. This tells the wizard what fields to display.

5. **Add wizard controls.** Add a case to `RuleParameterEditor.build()` to create the UI controls for the new type's parameters. Implement `buildParams()` to serialize the wizard state, `prefill()` to load existing values, and `validationMessage()` to check required fields.

6. **Update the YAML parser if needed.** If the new type requires special parsing beyond the generic `params` map, update `ConfigLoader`.

7. **Add documentation.** Add a section to `docs/CONFIG_REFERENCE.md` describing the YAML params and a wizard walkthrough.

## Adding an element kind

To support a new category of Rhapsody elements:

1. Add a value to `ElementKind`.
2. Update the element-kind classification logic in `RhapsodyElementReader` or `ModelRecordPostProcessor` so that the new kind is assigned during loading.
3. Update the wizard's element-kind list in `RuleParameterEditor` (the `ELEMENT_KINDS` static field is populated from the enum automatically).
4. No changes to the rule engine are needed unless the new kind requires special evaluation behavior.

## Key design decisions

- **Immutable domain model.** `ElementRecord`, `RuleSpec`, `RuleCheckerConfig`, and `TargetSpec` are immutable. This eliminates a class of bugs related to shared mutable state between the UI, cache, and evaluation.

- **Rhapsody isolation.** All COM calls are confined to the `adapter/rhapsody/` package. The core model, rules, and configuration have no Rhapsody dependency. This makes it possible to test rules without a running Rhapsody instance.

- **Config-driven rules.** Each built-in rule type is a single generic class driven entirely by its `params` map. There is no per-rule-instance subclass unless the evaluation logic genuinely differs. This keeps the rule count small and makes the configuration reference the authoritative source of rule behavior.

- **Wizard-generated YAML.** The wizard writes the same YAML format that the parser reads. There is no separate wizard-only format. Opening a wizard-generated file in a text editor and editing it manually is explicitly supported.

- **Cache as acceleration, not authority.** The cache exists to make the application start faster. It is never treated as authoritative. The UI always tells the user whether the loaded data came from cache or from a live Rhapsody connection, and whether its freshness was verified.

---

[Project overview](../README.md) · [User guide](USER_GUIDE.md) · [Configuration reference](CONFIG_REFERENCE.md)