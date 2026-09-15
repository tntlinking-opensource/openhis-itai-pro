/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.access;

import com.openhis.elep.domain.prescription.command.ElepEncounterContextQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterContextResult;
import com.openhis.elep.domain.prescription.result.ElepPharmacistReviewerResult;

/**
 * 电子处方就诊上下文装载（HIS 读 + 挂号 map(11) 只读；由 bridge 实现）。
 */
public interface ElepEncounterContextAccess {

    /**
     * 装载当前就诊只读上下文并判定能否开方。
     *
     * @param query 就诊、患者、契约
     * @return 上下文；{@code allowed=false} 时仍尽量带出已有展示字段
     */
    ElepEncounterContextResult load(ElepEncounterContextQuery query);

    /**
     * 按当前登录用户装载审方药师（{@code adm_practitioner.yb_no}）。
     *
     * @return 药师投影；缺医保码时 {@code allowed=false}
     */
    ElepPharmacistReviewerResult loadCurrentPharmacist();
}
