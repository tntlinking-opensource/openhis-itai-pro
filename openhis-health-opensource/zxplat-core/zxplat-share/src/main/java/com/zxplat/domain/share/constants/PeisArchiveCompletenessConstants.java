/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 学生体检档案完整性缺失项编码。
 */
public final class PeisArchiveCompletenessConstants {

    public static final String LEVEL_INFO = "info";
    public static final String LEVEL_WARN = "warn";
    public static final String LEVEL_BLOCK = "block";

    public static final String MISSING_PAST_HISTORY = "ARCHIVE_PAST_HISTORY";
    public static final String MISSING_ALLERGY = "ARCHIVE_ALLERGY";
    public static final String MISSING_FRESHMAN = "FRESHMAN_EXAM";
    public static final String MISSING_ANNUAL_YEAR = "ANNUAL_YYYY";
    public static final String MISSING_FINAL_REPORT = "FINAL_REPORT";
    public static final String MISSING_ANY_EXAM = "ANY_EXAM";

    private PeisArchiveCompletenessConstants() {
    }
}
