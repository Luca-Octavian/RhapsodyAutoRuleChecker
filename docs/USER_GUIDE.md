# User Guide

Rhapsody Rule Verifier checks an IBM Rhapsody model against rules stored in a YAML configuration file. It shows failed checks in the application and can export them to Excel.

For the YAML format and rule settings, see the [Configuration Reference](CONFIG_REFERENCE.md).

## Installation

Download the latest release zip from the [Releases](../../releases) page. Extract it to any folder. The zip contains:

- `RhapsodyRuleVerifier.exe`
- `RhapsodyRuleVerifier.l4j.ini`
- `jre/` (bundled Java runtime)

Keep all three in the same folder. Run `RhapsodyRuleVerifier.exe` to start the application. No separate Java installation is needed.

## Before you start

You need:

- Windows
- IBM Rhapsody 10.0.2 with its Java API available
- A Rhapsody project file, usually `.rpyx`
- A YAML configuration file, or a loaded model from which to create one with the wizard

Rhapsody is required when loading a model directly from Rhapsody or when navigating to an element from the results. A previously created cache can be loaded while Rhapsody is closed.

## Main window

The main window contains:

- **Model (.rpyx)**: the path to the Rhapsody project file.
- **Config (.yaml)**: the path to the rule configuration.
- **Load Model**: loads the model from Rhapsody or from an existing cache.
- **Update Model**: refreshes the loaded model after changes.
- **Run**: evaluates the selected configuration.
- **Export Excel**: saves the displayed failures as an Excel report.
- **New Config (Wizard)**: creates a new configuration from the loaded model.
- **Edit Config (Wizard)**: opens the most recently run configuration for editing.
- **Cache**: opens the cache folder in Windows Explorer.
- **Package tree**: selects a package to limit the evaluation scope.
- **Results**: lists failed checks and their details.

The model and configuration path fields remember recently used files.

## Basic workflow

### 1. Choose a model

Select a `.rpyx` or `.rpy` model file with **Browse** next to **Model (.rpyx)**.

### 2. Load the model

Click **Load Model**.

If no cache is available, the application loads the model from Rhapsody and creates or updates the local cache.

If a valid cache is available, choose one of these options:

- **Load from Cache**: loads saved model data without contacting Rhapsody. This is useful when Rhapsody is closed or when a fast start is more important than using the latest model state.
- **Reload from Rhapsody**: connects to Rhapsody and reads the current model again.

A cache load is marked as unverified because the application does not check whether Rhapsody has changed since the cache was created.

When loading completes, the package tree is populated and the status bar reports the source of the loaded data.

### 3. Choose or create a configuration

Select an existing `.yaml` or `.yml` file with **Browse** next to **Config (.yaml)**.

To create a configuration instead, click **New Config (Wizard)** after loading a model. The wizard uses the loaded model to suggest available element kinds and stereotypes.

See [Create a configuration with the wizard](#create-a-configuration-with-the-wizard).

### 4. Run the checks

Click **Run**.

The application loads the configuration, selects the elements that match each rule, evaluates enabled rules, and displays failures in the Results pane.

To check only one area of the model, select a package in the package tree before clicking **Run**. The selected package becomes the evaluation scope.

### 5. Review failures

The Results pane shows failed checks only.

- Use the filter field to search by rule ID, element name, location, or failure reason.
- Select a result to see the element path and full failure message in the Details pane.
- Double-click an individual rule failure to navigate to that element in Rhapsody.
- If rules use a group, failures for the same element are shown together under the group.

Navigation requires a running Rhapsody instance. Group summary rows cannot be used for navigation. Double-click an individual rule below the group instead.

### 6. Export the report

Click **Export Excel** after running a configuration.

Choose a location for the `.xlsx` file. The report contains failed checks and the information needed to review them.

## Update a loaded model

Use **Update Model** after changing the model.

If a cache exists, the application performs a smart update:

1. It connects to Rhapsody.
2. It checks whether the model or its saved units have changed.
3. It reuses unchanged cached data when possible.
4. It refreshes changed data when needed.

If no cache exists, the application performs a full Rhapsody load.

If the smart update cannot complete, the application falls back to a full reload from Rhapsody.

After any update, run the configuration again. Earlier results are cleared because they belong to the previous model state.

## Create a configuration with the wizard

Click **New Config (Wizard)** after loading a model.

The wizard has three steps.

### Step 1: Element sets

An element set is a reusable definition of which model elements a rule should check.

Click **Add Set**, give the set a unique ID, then choose filters such as:

- Element kinds
- Rhapsody types
- Stereotypes
- Included package patterns
- Excluded package patterns

Use a clear ID such as `Blocks`, `SafetyRequirements`, or `InterfacePorts`.

Filters in the same category match any selected value. Filters in different categories are combined. For example, selecting both `BLOCK` and `INTERFACE_BLOCK` kinds matches either kind. Adding a stereotype filter then limits the set to matching elements that also have one of those stereotypes.

The wizard shows an estimated element count while you edit a set. Use it to confirm that the set is neither empty nor unexpectedly broad.

### Step 2: Rules

Click **Add Rule** and enter a unique rule ID.

For each rule, choose:

- The rule type
- An element set, if the rule should use one
- Rule-specific settings
- An optional custom failure message
- An optional group

A group combines related rules in the results view. A grouped element passes only when every rule in that group passes for that element.

For `RequiredValue`, choose the target field directly in the rule:

- Description
- Name
- Tagged value
- Port type
- Port direction
- Port multiplicity

A tagged value target requires its profile name and tag name.

The available rule types and settings are described in the [Configuration Reference](CONFIG_REFERENCE.md).

### Step 3: Review and save

Review the element sets and rules. Click **Save & Close** to choose a YAML file location.

When editing an existing configuration, the wizard saves back to the existing file by default. Use **Save As...** on the review step to save a separate copy.

After saving, the application places the saved configuration in the configuration path field. You can run it immediately.

## Edit an existing configuration

To edit a configuration in the wizard:

1. Load a model.
2. Select the configuration file.
3. Click **Run** once so the application loads the configuration.
4. Click **Edit Config (Wizard)**.

The application may show a warning if the current model does not contain values referenced by the configuration. You can still open the wizard and correct the configuration.

## Configuring the Rhapsody path

The application needs to know where the IBM Rhapsody Java API native libraries are installed. This is controlled by `RhapsodyRuleVerifier.l4j.ini`, a plain-text file that must sit in the same folder as `RhapsodyRuleVerifier.exe`.

The default contents shipped with the release are:

```ini
# Rhapsody Rule Verifier - runtime JVM options
# If IBM Rhapsody is installed somewhere other than the path below,
# change it here and restart the application.
-Djava.library.path="C:\LegacyApp\Rhapsody_1002_64bit\Share\JavaAPI"
```

If Rhapsody is installed in a different location, open `RhapsodyRuleVerifier.l4j.ini` in any text editor, change the path to match your Rhapsody installation's `Share\JavaAPI` folder, save the file, and restart the application.

## Common issues

### I cannot click Run

Load a model first. Then select a configuration file.

### I cannot create a configuration

Load a model first. The wizard is enabled only when model data is available.

### I cannot edit a configuration

Run the configuration first. The edit action works with the configuration loaded during evaluation.

### The cache does not match my model

The application rejects caches that belong to another model, are corrupted, or use an unsupported cache format. Reload from Rhapsody to create a new cache.

### My results might be outdated

A cache-only load describes the model when the cache was created. Use **Reload from Rhapsody** or **Update Model** when you need current results.

### A rule reports no failures

The rule may be valid and all matching elements may pass. It is also possible that its element set selects no elements. Open the configuration wizard and check the estimated count for the element set.

### Double-click does not open the element in Rhapsody

Start Rhapsody and make sure the selected model is available there. Double-click an individual rule failure, not a group summary.

### The model scan is incomplete

The application warns when a scan is incomplete. Some cached data may have been kept, so relation and requirement checks can reflect an earlier snapshot. Reload from Rhapsody before relying on those results.

---

[Project overview](../README.md) · [Configuration reference](CONFIG_REFERENCE.md) · [Architecture](ARCHITECTURE.md)