package org.rhapsodychecker.rhapsodyruleverifier;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;

/**
 * Diagnostic probe: finds InterfaceBlocks in the model and dumps their
 * FlowProperty attributes using the corrected API approach.
 *
 * FlowProperty = IRPAttribute where getUserDefinedMetaClass() == "FlowProperty"
 * Confirmed extraction paths: getName(), getType(), getDefaultValue()
 * Direction discovery: getAllTags() — if direction is a profile tag, it appears here.
 *
 * Run as a plain Java application from Eclipse (with Rhapsody native libs on PATH).
 * Does NOT touch the rule engine, cache, or ElementRecord.
 */
public class FlowPropertyProbe {

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\Rhapsody Model 2\\Rhapsody Model 2\\L2_PECU_SYS-Architecture.rpyx";
        if (args.length >= 1) rpyxPath = args[0];

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            System.out.println("=== FlowProperty Probe (corrected) ===\n");
            conn.connect(rpyxPath);
            IRPProject project = conn.getProject();
            System.out.println("Project: " + project.getName() + "  GUID: " + project.getGUID());

            // ── Collect all elements ─────────────────────────────────────────
            IRPCollection allElements = project.getNestedElementsRecursive();
            int total = allElements != null ? allElements.getCount() : 0;
            System.out.println("Total nested elements: " + total);

            // ── Pass 1: Find ALL InterfaceBlocks and list their attribute counts ──
            java.util.List<IRPModelElement> interfaceBlocks = new java.util.ArrayList<>();

            for (int i = 1; i <= total; i++) {
                Object obj = allElements.getItem(i);
                if (!(obj instanceof IRPModelElement)) continue;
                IRPModelElement elt = (IRPModelElement) obj;

                try {
                    String meta = elt.getMetaClass();
                    if (!"Class".equals(meta) && !"Classifier".equals(meta)) continue;
                } catch (Throwable t) {
                    continue;
                }

                // Check stereotypes for "InterfaceBlock" (case-insensitive contains)
                boolean isIB = false;
                try {
                    IRPCollection stereos = elt.getStereotypes();
                    if (stereos == null) continue;
                    for (int s = 1; s <= stereos.getCount(); s++) {
                        Object sObj = stereos.getItem(s);
                        if (sObj instanceof IRPModelElement) {
                            String sName = ((IRPModelElement) sObj).getName();
                            if (sName.toLowerCase().contains("interfaceblock")) {
                                isIB = true;
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    // ignore and keep looking
                }

                if (isIB) {
                    interfaceBlocks.add(elt);
                }
            }

            // ── Print InterfaceBlock inventory ────────────────────────────────
            System.out.println("\n=== InterfaceBlock inventory: " + interfaceBlocks.size() + " found ===");
            IRPModelElement interfaceBlock = null;
            for (IRPModelElement ib : interfaceBlocks) {
                int ac = 0;
                int fpCount = 0;
                try {
                    if (ib instanceof IRPClassifier) {
                        IRPCollection a = ((IRPClassifier) ib).getAttributes();
                        ac = a != null ? a.getCount() : 0;
                        // Count FlowProperties specifically
                        for (int j = 1; j <= ac; j++) {
                            try {
                                IRPModelElement attrElt = (IRPModelElement) a.getItem(j);
                                if ("FlowProperty".equals(attrElt.getUserDefinedMetaClass())) {
                                    fpCount++;
                                }
                            } catch (Throwable t) { /* skip */ }
                        }
                    }
                } catch (Throwable t) { /* ignore */ }
                System.out.println("  IB: " + ib.getName() + " | GUID: " + ib.getGUID()
                    + " | attributes: " + ac + " | flowProperties: " + fpCount);
                if (fpCount > 0 && interfaceBlock == null) {
                    interfaceBlock = ib; // pick first one with FlowProperties
                }
            }

            if (interfaceBlock == null && !interfaceBlocks.isEmpty()) {
                // fallback to first IB with any attributes, then any IB at all
                for (IRPModelElement ib : interfaceBlocks) {
                    try {
                        if (ib instanceof IRPClassifier) {
                            IRPCollection a = ((IRPClassifier) ib).getAttributes();
                            if (a != null && a.getCount() > 0) {
                                interfaceBlock = ib;
                                break;
                            }
                        }
                    } catch (Throwable t) { /* skip */ }
                }
                if (interfaceBlock == null) {
                    interfaceBlock = interfaceBlocks.get(0);
                }
                System.out.println("\n[PROBE] No InterfaceBlock has FlowProperties. Using '" + interfaceBlock.getName() + "' for dump.");
            }
            if (interfaceBlock == null) {
                System.out.println("\n[PROBE] No InterfaceBlock found in the model. Aborting.");
                return;
            }

            // ── Detailed dump of selected InterfaceBlock ─────────────────────
            System.out.println("\n========================================");
            System.out.println("InterfaceBlock: " + interfaceBlock.getName());
            System.out.println("GUID:           " + interfaceBlock.getGUID());
            System.out.println("MetaClass:      " + interfaceBlock.getMetaClass());
            System.out.println("========================================\n");

            // ── Get attributes ───────────────────────────────────────────────
            IRPCollection attrs = null;
            try {
                if (interfaceBlock instanceof IRPClassifier) {
                    attrs = ((IRPClassifier) interfaceBlock).getAttributes();
                } else {
                    attrs = (IRPCollection) interfaceBlock.getClass()
                            .getMethod("getAttributes").invoke(interfaceBlock);
                }
            } catch (Throwable t) {
                System.out.println("[PROBE] getAttributes() failed: " + t.getMessage());
                return;
            }

            int attrCount = attrs != null ? attrs.getCount() : 0;
            System.out.println("Attribute count: " + attrCount + "\n");

            for (int i = 1; i <= attrCount; i++) {
                Object attrObj;
                try {
                    attrObj = attrs.getItem(i);
                } catch (Throwable t) {
                    System.out.println("--- Attribute #" + i + ": FAILED to retrieve: " + t.getMessage());
                    continue;
                }

                if (!(attrObj instanceof IRPAttribute)) {
                    System.out.println("--- Attribute #" + i + ": not IRPAttribute (type="
                        + (attrObj != null ? attrObj.getClass().getName() : "null") + "), skipping");
                    continue;
                }

                IRPAttribute attribute = (IRPAttribute) attrObj;

                // ── Identity ─────────────────────────────────────────────────
                String name = "<unknown>";
                try { name = attribute.getName(); } catch (Throwable t) { name = "EXCEPTION: " + t.getMessage(); }

                String userMeta = "<unknown>";
                try { userMeta = attribute.getUserDefinedMetaClass(); } catch (Throwable t) { userMeta = "EXCEPTION: " + t.getMessage(); }

                boolean isFlowProperty = "FlowProperty".equals(userMeta);

                System.out.println("--- Attribute: " + name + (isFlowProperty ? " [FlowProperty]" : " [" + userMeta + "]"));

                // ── MetaClass / userMetaClass / class ────────────────────────
                try { System.out.println("  metaclass:       " + attribute.getMetaClass()); }
                catch (Throwable t) { System.out.println("  metaclass:       EXCEPTION: " + t.getMessage()); }
                System.out.println("  userMetaClass:   " + userMeta);
                System.out.println("  class:           " + attribute.getClass().getName());

                // ── Stereotypes ──────────────────────────────────────────────
                try {
                    IRPCollection stereos = attribute.getStereotypes();
                    if (stereos == null || stereos.getCount() == 0) {
                        System.out.println("  stereotypes:     (none)");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int s = 1; s <= stereos.getCount(); s++) {
                            Object sObj = stereos.getItem(s);
                            if (s > 1) sb.append(", ");
                            if (sObj instanceof IRPModelElement) {
                                sb.append(((IRPModelElement) sObj).getName());
                            } else {
                                sb.append(sObj);
                            }
                        }
                        System.out.println("  stereotypes:     " + sb);
                    }
                } catch (Throwable t) {
                    System.out.println("  stereotypes:     EXCEPTION: " + t.getMessage());
                }

                // ── Type ─────────────────────────────────────────────────────
                try {
                    IRPClassifier type = attribute.getType();
                    if (type != null) {
                        System.out.println("  type:            " + type.getName());
                        System.out.println("  typeGuid:        " + type.getGUID());
                    } else {
                        System.out.println("  type:            NULL");
                        System.out.println("  typeGuid:        NULL");
                    }
                } catch (Throwable t) {
                    System.out.println("  type:            EXCEPTION: " + t.getMessage());
                }

                // ── Default value (initial value) ────────────────────────────
                try {
                    String defVal = attribute.getDefaultValue();
                    System.out.println("  defaultValue:    \"" + defVal + "\""
                        + (defVal == null || defVal.isEmpty() ? " (not set)" : ""));
                } catch (Throwable t) {
                    System.out.println("  defaultValue:    EXCEPTION: " + t.getMessage());
                }

                // ── getAllTags() — the correct discovery mechanism ────────────
                System.out.println();
                System.out.println("  [getAllTags() — profile tag discovery]");
                try {
                    IRPCollection allTags = attribute.getAllTags();
                    if (allTags == null || allTags.getCount() == 0) {
                        System.out.println("  tags: NONE");
                    } else {
                        System.out.println("  tag count: " + allTags.getCount());
                        for (int ti = 1; ti <= allTags.getCount(); ti++) {
                            Object tagObj = allTags.getItem(ti);
                            if (tagObj instanceof IRPTag) {
                                IRPTag tag = (IRPTag) tagObj;
                                String tagName = "<unknown>";
                                String tagValue = "<unknown>";
                                String tagType = "<unknown>";
                                try { tagName = tag.getName(); } catch (Throwable ex) { tagName = "EXCEPTION:" + ex.getMessage(); }
                                try { tagValue = tag.getValue(); } catch (Throwable ex) { tagValue = "EXCEPTION:" + ex.getMessage(); }
                                try { tagType = tag.getMetaClass(); } catch (Throwable ex) { tagType = "EXCEPTION:" + ex.getMessage(); }
                                System.out.println("    tag[" + ti + "]: name=\"" + tagName
                                    + "\" value=\"" + tagValue + "\" metaClass=\"" + tagType + "\"");
                            } else {
                                System.out.println("    tag[" + ti + "]: (not IRPTag: "
                                    + (tagObj != null ? tagObj.getClass().getName() : "null") + ")");
                            }
                        }
                    }
                } catch (Throwable t) {
                    System.out.println("  getAllTags(): EXCEPTION: " + t.getMessage());
                    // Fallback: try getTag(String) for known direction names
                    System.out.println("  [fallback: trying getTag(name) for known direction tag names]");
                    String[] directionNames = {"Direction", "direction", "FlowDirection", "flowDirection", "flow_direction"};
                    for (String dn : directionNames) {
                        try {
                            IRPTag tag = attribute.getTag(dn);
                            if (tag != null) {
                                System.out.println("    getTag(\"" + dn + "\"): value=\"" + tag.getValue() + "\"");
                            } else {
                                System.out.println("    getTag(\"" + dn + "\"): NULL");
                            }
                        } catch (Throwable ex) {
                            System.out.println("    getTag(\"" + dn + "\"): EXCEPTION: " + ex.getMessage());
                        }
                    }
                }

                System.out.println();
            }

            System.out.println("=== Probe complete ===");

        } catch (Throwable t) {
            System.err.println("[PROBE FATAL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }
}