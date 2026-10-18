/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.support;

import com.zxplat.domain.mrms.caserecord.resolver.AdmissionConditionResolver;
import com.zxplat.domain.infrastructure.integration.emr.EmrAdmissionConditionExtension;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * domain {@link AdmissionConditionResolver} 适配：委托 {@link EmrAdmissionConditionExtension}。
 *
 * <p>非跨 Module Bridge 模块；仅将 domain 可选 SPI 接到 health-integration-contracts Extension。</p>
 */
@Component
@RequiredArgsConstructor
public class MrmsEmrAdmissionConditionResolverAdapter implements AdmissionConditionResolver {

    private final EmrAdmissionConditionExtension admissionConditionExtension;

    /**
     * 委托 EMR Extension 按就诊解析入院病情（ICD → adm_cond_code）。
     *
     * @param encounterId 就诊主键
     * @return 诊断编码 → 入院病情码；无数据时空 Map
     */
    @Override
    public Map<String, String> resolveByEncounterId(Long encounterId) {
        return admissionConditionExtension.resolveByEncounterId(encounterId);
    }
}
