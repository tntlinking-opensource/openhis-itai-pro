/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.repository;

import com.zxplat.plugin.elep.domain.prescription.queries.model.ElepPlatformMapQueryModel;

import java.util.Optional;

/**
 * 只读挂号医保登记映射（CHS 表 {@code yb_encounter_platform_map}）。
 *
 * <p>电子处方禁止向该表 INSERT/UPDATE；仅取普通门诊 {@code med_type=11} 行。</p>
 */
public interface ElepEncounterPlatformMapReadRepository {

    /**
     * 按就诊取普通门诊登记行（同一就诊多行时取最近更新）。
     *
     * @param encounterId 就诊主键
     * @param tenantId    租户
     * @return 映射；自费/未医保挂号为空
     */
    Optional<ElepPlatformMapQueryModel> findGeneralOutpatientMap(Long encounterId, Long tenantId);
}
