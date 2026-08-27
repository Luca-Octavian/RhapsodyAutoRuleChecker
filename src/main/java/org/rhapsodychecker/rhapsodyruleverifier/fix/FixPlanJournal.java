package org.rhapsodychecker.rhapsodyruleverifier.fix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Reads and writes {@link FixPlan} instances as JSON files.
 * Journals are stored in {@code %TEMP%/.rhapsody-logs/fix-journals/}.
 */
public final class FixPlanJournal {

    private static final String JOURNAL_DIR = "fix-journals";
    private final File baseDir;
    private final ObjectMapper mapper;

    public FixPlanJournal() {
        this(defaultBaseDir());
    }

    public FixPlanJournal(File baseDir) {
        this.baseDir = baseDir;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static File defaultBaseDir() {
        String temp = System.getProperty("java.io.tmpdir");
        return new File(new File(temp, ".rhapsody-logs"), JOURNAL_DIR);
    }

    /**
     * Write a fix plan to a new journal file. Returns the file written.
     */
    public File write(FixPlan plan) throws IOException {
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IOException("Cannot create journal directory: " + baseDir);
        }
        String filename = "fix-journal-" + formatTimestamp(plan.timestamp()) + ".json";
        File file = new File(baseDir, filename);

        ObjectNode root = mapper.createObjectNode();
        root.put("modelGuid", plan.modelGuid());
        root.put("configPath", plan.configPath());
        root.put("timestamp", plan.timestamp());

        ArrayNode entriesNode = root.putArray("entries");
        for (FixEntry entry : plan.entries()) {
            entriesNode.add(serializeEntry(entry));
        }

        mapper.writeValue(file, root);
        return file;
    }

    /**
     * Read a fix plan from a journal file.
     */
    public FixPlan read(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        String modelGuid = root.get("modelGuid").asText();
        String configPath = root.has("configPath") && !root.get("configPath").isNull()
                ? root.get("configPath").asText() : null;
        long timestamp = root.get("timestamp").asLong();

        List<FixEntry> entries = new ArrayList<FixEntry>();
        JsonNode entriesNode = root.get("entries");
        if (entriesNode != null && entriesNode.isArray()) {
            for (JsonNode entryNode : entriesNode) {
                entries.add(deserializeEntry(entryNode));
            }
        }
        return new FixPlan(modelGuid, configPath, timestamp, entries);
    }

    /**
     * List all journal files in the base directory, newest first.
     */
    public List<File> listJournals() {
        File[] files = baseDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("fix-journal-") && name.endsWith(".json");
            }
        });
        if (files == null) return Collections.emptyList();
        List<File> result = new ArrayList<File>(Arrays.asList(files));
        Collections.sort(result, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return result;
    }

    /**
     * Delete journal files older than the given age in milliseconds.
     */
    public int cleanOlderThan(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        int deleted = 0;
        for (File f : listJournals()) {
            if (f.lastModified() < cutoff && f.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    // ---- Serialization helpers ----

    private ObjectNode serializeEntry(FixEntry entry) {
        ObjectNode node = mapper.createObjectNode();
        FixAction a = entry.action();
        node.put("elementGuid", a.elementGuid());
        node.put("elementName", a.elementName());
        node.put("actionType", a.actionType().name());
        node.put("field", a.field());
        node.put("oldValue", a.oldValue());
        node.put("newValue", a.newValue());
        node.put("ruleId", a.ruleId());
        node.put("description", a.description());
        node.put("status", entry.status().name());
        node.put("errorMessage", entry.errorMessage());
        node.put("liveOldValue", entry.liveOldValue());
        return node;
    }

    private FixEntry deserializeEntry(JsonNode node) {
        FixAction action = FixAction.builder()
                .elementGuid(textOrNull(node, "elementGuid"))
                .elementName(textOrNull(node, "elementName"))
                .actionType(FixActionType.valueOf(node.get("actionType").asText()))
                .field(textOrNull(node, "field"))
                .oldValue(textOrNull(node, "oldValue"))
                .newValue(textOrNull(node, "newValue"))
                .ruleId(textOrNull(node, "ruleId"))
                .description(textOrNull(node, "description"))
                .build();

        FixStatus status = FixStatus.valueOf(node.get("status").asText());
        FixEntry entry = new FixEntry(action, status);

        String error = textOrNull(node, "errorMessage");
        if (error != null && status == FixStatus.FAILED) {
            entry.markFailed(error);
        } else if (error != null && status == FixStatus.CONFLICT) {
            entry.markConflict(error);
        }

        // liveOldValue is informational, stored for rollback reference
        // It's accessed via the entry but set during apply — for deserialized
        // entries we don't restore it into the entry since the field is private.
        // The journal file preserves it for manual inspection.

        return entry;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return null;
        return child.asText();
    }

    private static String formatTimestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(millis));
    }
}