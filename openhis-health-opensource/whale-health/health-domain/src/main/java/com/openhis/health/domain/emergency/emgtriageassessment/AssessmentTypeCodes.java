/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.emergency.emgtriageassessment;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code emg_triage_assessment.assessment_type} 允许编码（DM-EMG-001 §4.5）。
 */
public final class AssessmentTypeCodes {

    private AssessmentTypeCodes() {
    }

    public static final String PAIN = "PAIN";
    public static final String GCS = "GCS";
    public static final String MEWS = "MEWS";
    public static final String REMS = "REMS";
    public static final String ESI = "ESI";
    public static final String ERTA = "ERTA";
    public static final String TRAUMA = "TRAUMA";
    public static final String TRAUMA_RTS = "TRAUMA_RTS";

    private static final Set<String> ALL = new HashSet<>();

    static {
        ALL.add(PAIN);
        ALL.add(GCS);
        ALL.add(MEWS);
        ALL.add(REMS);
        ALL.add(ESI);
        ALL.add(ERTA);
        ALL.add(TRAUMA);
        ALL.add(TRAUMA_RTS);
    }

    public static Set<String> allowedCodes() {
        return Collections.unmodifiableSet(ALL);
    }

    public static boolean isAllowed(String code) {
        return code != null && ALL.contains(code);
    }
}
