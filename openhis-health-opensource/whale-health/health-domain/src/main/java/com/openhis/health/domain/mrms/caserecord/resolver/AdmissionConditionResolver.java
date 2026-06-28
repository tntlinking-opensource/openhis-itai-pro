/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.resolver;

import java.util.Map;

/**
 * 可选 EMR 入院病情解析（由 {@code MrmsEmrAdmissionConditionResolverAdapter} 委托 {@code EmrAdmissionConditionExtension}）。
 */
public interface AdmissionConditionResolver {

    /**
     * 按就诊解析入院病情（ICD 编码 → adm_cond_code）。
     *
     * @param encounterId 就诊主键
     * @return ICD 编码到 adm_cond_code 的映射
     */
    Map<String, String> resolveByEncounterId(Long encounterId);
}
