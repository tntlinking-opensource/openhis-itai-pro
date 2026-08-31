/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 住院平台在院态诊断/修复出参。 */
@Data
@Accessors(chain = true)
public class InpatientPlatformVisitAlignExtensionOut {

    /** 本地 map 的 mdtrt_id；无则空。 */
    private String localMdtrtId;
    /** 5201 同人在院 mdtrt 列表。 */
    private List<String> platformInHospitalMdtrtIds = new ArrayList<>();
    /**
     * 不一致码：{@code OK} / {@code NO_LOCAL_MAP} / {@code LOCAL_DEAD} /
     * {@code MULTI_INHOSP} / {@code PLATFORM_OUT_LOCAL_IN} / {@code PLATFORM_ORPHAN_INHOSP}。
     */
    private String mismatchCode;
    /** 建议动作说明。 */
    private String advice;
    /** repair 是否已执行修复动作。 */
    private boolean repaired;
    /** 修复摘要。 */
    private String repairSummary;
}
