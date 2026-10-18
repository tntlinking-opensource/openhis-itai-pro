/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.support;

import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;

/**
 * 门诊护士站执行列表排除中药饮片（AS-OP-NUR-001 IF-NUR-02）。
 *
 * <p>{@code tcm_flag=true} 的中药饮片由医生站中医 Tab / 药房发药域承载，不进护士站中区「项目列表」。</p>
 */
public final class NurseStationOutpatientTcmExecuteExcludeHelper {

    private NurseStationOutpatientTcmExecuteExcludeHelper() {
    }

    /**
     * 执行列表是否排除该药品行（中药饮片）。
     *
     * @param medicationRequest 药品申请列表行
     * @return true 表示不进入门诊护士站执行 Tab 项目列表
     */
    public static boolean shouldExcludeMedicationFromOutpatientNurseStationExecuteList(
        MedicationRequestListQueryModel medicationRequest) {
        if (medicationRequest == null) {
            return false;
        }
        return Boolean.TRUE.equals(medicationRequest.getTcmFlag());
    }
}
