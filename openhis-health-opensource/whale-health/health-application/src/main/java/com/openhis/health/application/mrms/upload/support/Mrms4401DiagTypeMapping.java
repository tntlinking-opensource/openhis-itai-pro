/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.support;

import com.openhis.health.domain.share.enums.HomepageDiagType;

import java.util.HashMap;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 首页诊断类型 → 4401 ipt_patn_disediag_type_code 映射。
 */
public final class Mrms4401DiagTypeMapping {

    private static final Map<String, String> TYPE_CODE = new HashMap<>();
    private static final Map<String, String> MAIN_FLAG = new HashMap<>();

    static {
        TYPE_CODE.put(HomepageDiagType.DISCHARGE_MAIN.getCode(), "1");
        TYPE_CODE.put(HomepageDiagType.DISCHARGE_OTHER.getCode(), "1");
        TYPE_CODE.put(HomepageDiagType.ADMISSION_MAIN.getCode(), "2");
        TYPE_CODE.put(HomepageDiagType.ADMISSION_OTHER.getCode(), "2");
        TYPE_CODE.put(HomepageDiagType.PATHOLOGY.getCode(), "3");
        TYPE_CODE.put(HomepageDiagType.INJURY_POISON.getCode(), "4");

        MAIN_FLAG.put(HomepageDiagType.DISCHARGE_MAIN.getCode(), "1");
        MAIN_FLAG.put(HomepageDiagType.ADMISSION_MAIN.getCode(), "1");
        MAIN_FLAG.put(HomepageDiagType.DISCHARGE_OTHER.getCode(), "0");
        MAIN_FLAG.put(HomepageDiagType.ADMISSION_OTHER.getCode(), "0");
        MAIN_FLAG.put(HomepageDiagType.PATHOLOGY.getCode(), "0");
        MAIN_FLAG.put(HomepageDiagType.INJURY_POISON.getCode(), "0");
    }

    private Mrms4401DiagTypeMapping() {
    }

    /**
     * 首页诊断类型 → 4401 {@code ipt_patn_disediag_type_code}。
     *
     * @param diagTypeCode {@link HomepageDiagType} code
     * @return 4401 类型码，未知类型默认 {@code "1"}
     */
    public static String mapTypeCode(String diagTypeCode) {
        return TYPE_CODE.getOrDefault(diagTypeCode, "1");
    }

    /**
     * 首页诊断主诊断标志 → 4401 {@code maindiag_flag}；行上已有值时优先行值。
     *
     * @param diagTypeCode 诊断类型 code
     * @param rowMainFlag 行上主诊断标志，可为 null
     * @return 4401 主诊断标志
     */
    public static String mapMainFlag(String diagTypeCode, String rowMainFlag) {
        // 行上已显式标记主诊断时优先行值
        if (trimToNull(rowMainFlag) != null) {
            return rowMainFlag;
        }
        return MAIN_FLAG.getOrDefault(diagTypeCode, "0");
    }
}
