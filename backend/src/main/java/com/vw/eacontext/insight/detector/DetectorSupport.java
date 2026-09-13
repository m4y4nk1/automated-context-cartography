package com.vw.eacontext.insight.detector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vw.eacontext.model.Application;
import com.vw.eacontext.model.CanonicalModel;

/** Small shared helpers reused across detector implementations. */
final class DetectorSupport {

    private DetectorSupport() {
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static Map<String, Application> applicationsById(CanonicalModel model) {
        Map<String, Application> byId = new HashMap<>();
        for (Application app : model.applications()) {
            if (!isBlank(app.id())) {
                byId.putIfAbsent(app.id(), app);
            }
        }
        return byId;
    }

    /** Every application id in {@code model.applications()}, ignoring blanks. */
    static Set<String> applicationIds(CanonicalModel model) {
        return applicationsById(model).keySet();
    }

    /** Non-blank ids, in argument order, for a finding's {@code relatedEntityIds}. */
    static List<String> ids(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) {
                result.add(value);
            }
        }
        return result;
    }
}
