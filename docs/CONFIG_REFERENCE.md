# Configuration Reference

Rhapsody Rule Verifier uses YAML files to define element sets and rules.

The recommended way to create a configuration is **New Config (Wizard)**. You can also edit the YAML file directly when needed.

For how to load and run a configuration, see the [User Guide](USER_GUIDE.md).

## Complete example

```yaml
schemaVersion: 1

elementSets:
  InterfaceBlocks:
    kinds: [INTERFACE_BLOCK]

  FlowProperties:
    kinds: [FLOW_PROPERTY]

rules:
  - id: interface-block-prefix
    type: NAMING_PATTERN
    title: InterfaceBlock names must start with 'if'
    appliesTo:
      set: InterfaceBlocks
    params:
      startsWith: if
      caseSensitive: true

  - id: flow-property-fields
    type: FLOW_PROPERTY_CONSTRAINT
    title: FlowProperties must have a type and direction
    appliesTo:
      set: FlowProperties
    params:
      type:
        required: true
      direction:
        required: true
        allowed: [In, Out, Bidirectional]
```

## Top-level structure

A configuration has three top-level fields.

| Field | Required | Description |
| --- | --- | --- |
| `schemaVersion` | Yes | Configuration format version. Use `1`. |
| `elementSets` | No | Named reusable filters that select model elements. |
| `rules` | No | List of rules to evaluate. |

```yaml
schemaVersion: 1
elementSets: {}
rules: []
```

## Element sets

An element set is a named filter. Rules can refer to it through `appliesTo.set`.

```yaml
elementSets:
  Blocks:
    title: Blocks in the system model
    kinds: [BLOCK, INTERFACE_BLOCK]
    stereotypes: [SafetyRelevant]
    includePackages: ["System.*"]
    excludePackages: ["System::Library.*"]
```

### Element-set fields

| Field | Description |
| --- | --- |
| `title` | Optional descriptive label. |
| `kinds` | List of application element kinds, such as `BLOCK`, `INTERFACE_BLOCK`, `FLOW_PROPERTY`, or port kinds. |
| `types` | List of Rhapsody meta-class names. |
| `stereotypes` | List of applied stereotype names. |
| `includePackages` | List of package path patterns to include. |
| `excludePackages` | List of package path patterns to exclude. |

Values inside one filter category are alternatives. For example, `kinds: [BLOCK, INTERFACE_BLOCK]` selects either kind.

Different filter categories narrow the result together. A set with both `kinds` and `stereotypes` selects elements that satisfy both filters.

Use the Element Sets step of the wizard when possible. It provides an estimated element count, which is useful for checking that a set selects the intended elements.

## Rules

Each item under `rules` defines one check.

```yaml
rules:
  - id: block-description-required
    type: REQUIRED_VALUE
    enabled: true
    title: Blocks must have descriptions
    message: Add a description to this Block.
    appliesTo:
      set: Blocks
    target:
      kind: description
    params:
      nonEmpty: true
```

### Common rule fields

| Field | Required | Description |
| --- | --- | --- |
| `id` | Yes | Unique rule identifier. Use a stable, descriptive ID. |
| `type` | Yes | Rule type. See [Rule types](#rule-types). |
| `enabled` | No | Whether the rule runs. Default is `true`. |
| `title` | No | Human-readable rule title. |
| `message` | No | Custom failure message. |
| `group` | No | Groups related rules in the results view. |
| `appliesTo` | Yes | Defines which elements the rule checks. |
| `target` | Required by `REQUIRED_VALUE` | Field to read from the element. |
| `params` | Usually | Settings specific to the rule type. |

### Rule scope with `appliesTo`

The simplest scope refers to an element set.

```yaml
appliesTo:
  set: Blocks
```

A rule can also define an inline scope.

```yaml
appliesTo:
  types: [Class]
  stereotypes: [SafetyRelevant]
  includePackages: ["System.*"]
  excludePackages: ["System::Library.*"]
```

An `appliesTo` block must contain either:

- `set`, or
- at least one of `types` or `stereotypes`.

Package filters can be used with either approach.

### Groups

Rules with the same `group` label are displayed together for each affected element.

```yaml
group: interface-quality
```

The application evaluates every rule normally. In the grouped result, an element passes the group only if all rules in that group pass.

Use groups for checks that belong together, such as a required description and a required safety tag.

## Required value targets

`REQUIRED_VALUE` reads one field from an element. The target is part of the rule.

### Simple targets

```yaml
target:
  kind: description
```

Supported simple target kinds are:

- `description`
- `name`
- `portType`
- `portDirection`
- `portMultiplicity`

The parser also accepts equivalent underscore forms, such as `port_type`.

### Tagged value target

A tagged value target needs a profile and tag name.

```yaml
target:
  kind: taggedValue
  profile: SafetyProfile
  tag: ASIL
```

The wizard creates targets directly inside the `REQUIRED_VALUE` rule. It does not use separate alias definitions.

## Rule types

The application supports these rule types:

- `REQUIRED_VALUE`
- `REQUIRED_STEREOTYPE`
- `REQUIRED_STEREOTYPE_ONE_OF`
- `NAMING_PATTERN`
- `RELATION_EXISTS`
- `OWNER_STEREOTYPE_CONSTRAINT`
- `FLOW_PROPERTY_CONSTRAINT`
- `UNIQUE_NAME`
- `CHILD_COUNT`
- `MAX_DEPTH`

### `REQUIRED_VALUE`

Checks a target field such as a description, name, tagged value, or port property.

#### Require a value

```yaml
type: REQUIRED_VALUE
appliesTo:
  set: Blocks
target:
  kind: description
params:
  nonEmpty: true
```

#### Match one of several values

```yaml
type: REQUIRED_VALUE
appliesTo:
  set: SafetyElements
target:
  kind: taggedValue
  profile: SafetyProfile
  tag: ASIL
params:
  operator: in
  values: [QM, ASIL_A, ASIL_B, ASIL_C, ASIL_D]
```

#### Enforce a length range

```yaml
type: REQUIRED_VALUE
appliesTo:
  set: Blocks
target:
  kind: description
params:
  minLength: 10
  maxLength: 500
```

#### Match a regular expression

```yaml
type: REQUIRED_VALUE
appliesTo:
  set: Requirements
target:
  kind: name
params:
  operator: matches
  pattern: "^REQ-[0-9]+$"
```

#### Compare a numeric value

```yaml
type: REQUIRED_VALUE
appliesTo:
  set: Ports
target:
  kind: portMultiplicity
params:
  operator: gte
  value: 1
```

In the wizard, select one of these friendly comparison choices:

| Wizard choice | What it checks |
| --- | --- |
| **equals (=)** | The value equals the entered value. |
| **not equals (≠)** | The value does not equal the entered value. |
| **greater than (>)** | The numeric value is greater than the entered value. |
| **at least (≥)** | The numeric value is at least the entered value. |
| **less than (<)** | The numeric value is less than the entered value. |
| **at most (≤)** | The numeric value is at most the entered value. |
| **between** | The numeric value is within the entered minimum and maximum values. |

For a range, choose **between**. The wizard then shows **Range Min** and **Range Max** instead of a single value.

The wizard provides these check modes:

- Must not be empty
- Must match specific value(s)
- Length constraint
- Regex pattern
- Numeric comparison

If you edit YAML manually, the stored operator names are `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `between`, `in`, `not_in`, and `matches`. For `between`, use `min` and `max` instead of `value`.

### `REQUIRED_STEREOTYPE`

Checks that an element has a required stereotype.

```yaml
type: REQUIRED_STEREOTYPE
appliesTo:
  set: Blocks
params:
  requiredStereotypes: SafetyRelevant
```

The wizard selects one stereotype for this rule. When manually editing YAML, `requiredStereotypes` can also be written as a list.

```yaml
params:
  requiredStereotypes: [SafetyRelevant, Reviewed]
```

### `REQUIRED_STEREOTYPE_ONE_OF`

Checks that an element has at least one stereotype from the supplied list.

```yaml
type: REQUIRED_STEREOTYPE_ONE_OF
appliesTo:
  set: Blocks
params:
  anyOf: [LogicalElement, TechnicalElement, Subsystem]
```

### `NAMING_PATTERN`

Checks whether an element name starts with, ends with, or contains a value.

```yaml
type: NAMING_PATTERN
appliesTo:
  set: InterfaceBlocks
params:
  startsWith: if
  caseSensitive: true
```

Use exactly one of these fields:

| Field | Meaning |
| --- | --- |
| `startsWith` | Name must start with the value. |
| `endsWith` | Name must end with the value. |
| `contains` | Name must contain the value. |

`caseSensitive` is optional and defaults to `true` in the wizard.

### `RELATION_EXISTS`

Checks the number of relations matching the selected filters.

```yaml
type: RELATION_EXISTS
appliesTo:
  set: Blocks
params:
  relationKind: dependency
  direction: outgoing
  operator: gte
  value: 1
```

Available fields:

| Field | Description |
| --- | --- |
| `relationKind` | Relation type to match. The wizard offers `any`, `dependency`, `association`, `generalization`, `usage`, `realization`, `abstraction`, and `link`. |
| `direction` | `any`, `outgoing`, or `incoming`. |
| `relationStereotypes` | Optional stereotype filter for the relation. |
| `operator` | Stored count comparison used by manually edited YAML. |
| `value` | Relation count used with the selected comparison. |

In the wizard, choose the count in the **Require** control:

| Wizard choice | What it checks |
| --- | --- |
| **At least** | At least the entered number of matching relations. |
| **Exactly** | Exactly the entered number of matching relations. |
| **At most** | No more than the entered number of matching relations. |
| **More than** | More than the entered number of matching relations. |
| **Fewer than** | Fewer than the entered number of matching relations. |

For example, select **association**, **incoming**, and **Exactly** `1` to require exactly one incoming association.

If you edit YAML manually, these choices are stored as `gte`, `eq`, `lte`, `gt`, and `lt` respectively.

### `OWNER_STEREOTYPE_CONSTRAINT`

Checks the kind of an element when its owner has a specified stereotype.

```yaml
type: OWNER_STEREOTYPE_CONSTRAINT
appliesTo:
  set: AllModelElements
params:
  ownerStereotype: InterfaceDefinition
  allowedKinds: [PORT_FLOW, PORT_PROXY]
```

Elements whose owner does not have `ownerStereotype` are not checked by this rule.

Use element-kind names from the wizard for `allowedKinds`.

### `FLOW_PROPERTY_CONSTRAINT`

Checks FlowProperty fields. At least one constraint must be configured.

```yaml
type: FLOW_PROPERTY_CONSTRAINT
appliesTo:
  set: FlowProperties
params:
  type:
    required: true
    allowed: [Speed, Temperature]
  initialValue:
    required: true
  direction:
    required: true
    allowed: [In, Out, Bidirectional]
```

Available blocks:

| Block | Fields | Description |
| --- | --- | --- |
| `type` | `required`, `allowed`, `caseSensitive` | Requires a type and optionally limits allowed type names. |
| `initialValue` | `required`, `mustBeEmpty`, `allowed`, `caseSensitive` | Requires an initial value, requires it to be empty, or limits allowed initial values. Use either `required` or `mustBeEmpty`, not both. |
| `direction` | `required`, `allowed`, `caseSensitive` | Requires a direction and optionally limits it to `In`, `Out`, and `Bidirectional`. |

### `UNIQUE_NAME`

Checks that no two sibling elements (elements with the same owner) share the same name.

This rule does not require an `appliesTo` scope. It runs against all elements in the model automatically.

```yaml
type: UNIQUE_NAME
```

No parameters are required. The rule compares every element's name against its siblings under the same owner and flags duplicates.

Custom failure messages can use `{elementName}` and `{count}` placeholders.

```yaml
rules:
  - id: no-duplicate-names
    type: UNIQUE_NAME
    title: Elements must have unique names among siblings
    message: "'{elementName}' appears {count} times under the same owner."
```

### `CHILD_COUNT`

Checks that an element has at least a given number of children of a specific element kind.

```yaml
type: CHILD_COUNT
appliesTo:
  set: InterfaceBlocks
params:
  child_kind: PORT_FLOW
  min_count: 1
```

| Field | Required | Default | Description |
| --- | --- | --- | --- |
| `child_kind` | Yes | | The `ElementKind` to count among the element's children (for example `PORT_FLOW`, `PORT_PROXY`, `BLOCK`). |
| `min_count` | No | `1` | Minimum number of matching children required. |

Custom failure messages can use `{elementName}`, `{count}`, `{minCount}`, and `{childKind}` placeholders.

```yaml
rules:
  - id: interface-block-has-flow-port
    type: CHILD_COUNT
    title: InterfaceBlocks must have at least one FlowPort
    appliesTo:
      set: InterfaceBlocks
    params:
      child_kind: PORT_FLOW
      min_count: 1
```

### `MAX_DEPTH`

Checks that an element's package nesting depth does not exceed a threshold. Depth is measured by counting `::` separators in the element's owner path.

This rule does not require an `appliesTo` scope. It runs against all elements in the model automatically.

```yaml
type: MAX_DEPTH
params:
  max_depth: 5
```

| Field | Required | Default | Description |
| --- | --- | --- | --- |
| `max_depth` | No | `5` | Maximum allowed number of `::` separators in the element's owner path. |

Custom failure messages can use `{elementName}`, `{depth}`, and `{maxDepth}` placeholders.

```yaml
rules:
  - id: nesting-limit
    type: MAX_DEPTH
    title: Elements must not be nested more than 5 levels deep
    params:
      max_depth: 5
```

## Validation rules

The application validates configuration structure before evaluation.

Common validation errors are:

- `schemaVersion` is missing or less than `1`.
- A rule has no `id` or `type`.
- A rule has no valid `appliesTo` scope.
- A rule refers to an element set that does not exist.
- A `REQUIRED_VALUE` rule has no `target`.
- A tagged value target has an invalid or incomplete definition.
- A rule type name is unknown.

Use the wizard to avoid most format errors. When editing YAML manually, keep IDs and element set names consistent.

## Build configurations with the wizard

These examples describe how to create common rules without editing YAML.

### Require descriptions for all Blocks

1. Load the model and click **New Config (Wizard)**.
2. In **Element sets**, add a set named `Blocks`.
3. Select the `BLOCK` kind, then save the set.
4. Go to **Rules** and click **Add Rule**.
5. Set the rule ID to `block-description-required`.
6. Choose `REQUIRED_VALUE`.
7. Set **Applies To Set** to `Blocks`.
8. Set **Target** to **Description**.
9. Set **Check Mode** to **Must not be empty**.
10. Save the rule, then review and save the configuration.

### Require one of several stereotypes

1. Create an element set for the elements to check, such as `Blocks`.
2. Add a rule with ID `block-classification-required`.
3. Choose `REQUIRED_STEREOTYPE_ONE_OF`.
4. Select the `Blocks` element set.
5. Select the allowed stereotypes, such as `LogicalElement`, `TechnicalElement`, and `Subsystem`.
6. Save the rule and configuration.

### Require an InterfaceBlock naming prefix

1. Create an element set named `InterfaceBlocks`.
2. Select the `INTERFACE_BLOCK` kind.
3. Add a rule with ID `interface-block-prefix`.
4. Choose `NAMING_PATTERN`.
5. Select the `InterfaceBlocks` set.
6. Choose **Starts with**.
7. Enter `if`.
8. Leave **Case-sensitive** selected if uppercase and lowercase must be treated differently.
9. Save the rule and configuration.

### Require an outgoing dependency

1. Create an element set for the elements to check.
2. Add a rule with ID `outgoing-dependency-required`.
3. Choose `RELATION_EXISTS`.
4. Select the element set.
5. Choose `dependency` as the relation kind.
6. Choose `outgoing` as the direction.
7. Set the count to **At least** `1`.
8. Save the rule and configuration.

### Detect duplicate sibling names

1. Load the model and click **New Config (Wizard)**.
2. Go to **Rules** and click **Add Rule**.
3. Set the rule ID to `no-duplicate-names`.
4. Choose `UNIQUE_NAME`.
5. No element set or parameters are needed.
6. Save the rule, then review and save the configuration.

### Require at least one FlowPort on InterfaceBlocks

1. Create an element set named `InterfaceBlocks`.
2. Select the `INTERFACE_BLOCK` kind.
3. Add a rule with ID `interface-block-has-flow-port`.
4. Choose `CHILD_COUNT`.
5. Select the `InterfaceBlocks` set.
6. Set **Child Kind** to `PORT_FLOW`.
7. Set **Minimum Count** to `1`.
8. Save the rule and configuration.

### Limit package nesting depth

1. Load the model and click **New Config (Wizard)**.
2. Go to **Rules** and click **Add Rule**.
3. Set the rule ID to `nesting-limit`.
4. Choose `MAX_DEPTH`.
5. No element set is needed.
6. Set **Maximum Depth** to `5` (or your preferred limit).
7. Save the rule and configuration.

### Require FlowProperty type and direction

1. Create an element set named `FlowProperties`.
2. Select the `FLOW_PROPERTY` kind.
3. Add a rule with ID `flow-property-fields`.
4. Choose `FLOW_PROPERTY_CONSTRAINT`.
5. Select the `FlowProperties` set.
6. Under Type Constraints, select **Type is required**.
7. Under Direction Constraints, select **Direction is required**.
8. Select allowed directions if the model uses only specific directions.
9. Save the rule and configuration.

---

[Project overview](../README.md) · [User guide](USER_GUIDE.md) · [Architecture](ARCHITECTURE.md)