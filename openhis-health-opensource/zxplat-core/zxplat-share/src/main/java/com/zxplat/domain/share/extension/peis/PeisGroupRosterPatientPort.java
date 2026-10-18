/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.peis;

import java.util.List;

/**
 * 患者端团检名册选定 SPI（miniapp BFF → PEIS）。
 *
 * <p>实现落在 health-application；调用方须校验就诊人与名册归属。</p>
 */
public interface PeisGroupRosterPatientPort {

    /**
     * 按患者查询在册团检名册上下文。
     *
     * @param patientId 患者主键
     * @return 命中的名册列表（可能为空）
     */
    List<PeisGroupRosterDeskView> lookupByPatientId(Long patientId);

    /**
     * 选定套餐并建单（BR-GRP-SEL-02）。
     *
     * @param patientId         就诊人（须与名册 patient_id 一致）
     * @param rosterId          名册主键
     * @param packageActivityId 选定套餐 AD
     * @param orgId             机构覆盖（可空）
     * @param operatorId        操作人（adm_practitioner.id）
     * @return 建单结果
     */
    PeisGroupRosterSelectResult selectPackage(
            Long patientId, Long rosterId, Long packageActivityId, Long orgId, Long operatorId);

    /**
     * 到检前换套餐（BR-GRP-SEL-03）。
     *
     * @param patientId         就诊人（须与名册 patient_id 一致）
     * @param rosterId          名册主键
     * @param packageActivityId 新选定套餐 AD
     * @param operatorId        操作人（adm_practitioner.id）
     * @return 换套餐结果
     */
    PeisGroupRosterSelectResult changePackage(
            Long patientId, Long rosterId, Long packageActivityId, Long operatorId);
}
