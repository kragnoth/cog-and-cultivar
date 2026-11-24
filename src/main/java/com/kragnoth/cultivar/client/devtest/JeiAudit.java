package com.kragnoth.cultivar.client.devtest;

import com.kragnoth.cultivar.compat.jei.client.CultivarJeiPlugin;

/** JEI part of the audit; only loaded when JEI is installed. */
final class JeiAudit {
    private JeiAudit() {}

    static void check(ResourceAudit a) {
        var counts = CultivarJeiPlugin.recipeCounts();
        if (counts.isEmpty()) {
            a.warnings.add("JEI hasn't finished loading: run the audit again in a moment");
            return;
        }
        counts.forEach((category, n) -> {
            if (n == 0) {
                a.errors.add("JEI category " + category + " has no pages");
            }
        });
        a.info.add("JEI: " + counts);
    }
}
