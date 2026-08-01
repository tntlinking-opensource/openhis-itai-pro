/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.shared;

/**
 * Campus 领域常量。
 */
public final class CampusDomainConstants {

    public static final String SUBJECT_STUDENT = "STUDENT";
    public static final String SUBJECT_STAFF = "STAFF";

    public static final String SCHOOL_STATUS_IN_SCHOOL = "IN_SCHOOL";
    /** 已毕业（出具在校健康档案场景）。 */
    public static final String SCHOOL_STATUS_GRADUATED = "GRADUATED";
    public static final String JOB_STATUS_ACTIVE = "ACTIVE";

    public static final String GF_STATUS_ELIGIBLE = "ELIGIBLE";
    public static final String GF_STATUS_NOT_ELIGIBLE = "NOT_ELIGIBLE";
    public static final String GF_STATUS_UNKNOWN = "UNKNOWN";

    public static final String PHYSICAL_EXAM_PASS = "PASS";

    public static final String GF_SWITCH_KEY_MASTER = "MASTER";

    public static final String TRANSFER_SOURCE_UPDATE_DERIVED = "UPDATE_DERIVED";

    private CampusDomainConstants() {
    }
}
