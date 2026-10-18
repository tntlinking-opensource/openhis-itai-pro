/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.service;

import com.zxplat.plugin.elep.domain.prescription.queries.model.ElepPlatformMapQueryModel;
import com.zxplat.plugin.elep.domain.prescription.repository.ElepEncounterPlatformMapReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 挂号医保登记映射只读（仅本模块 JDBC 仓储；不写 CHS 表）。
 *
 * <p>聚合边界：仅 {@link ElepEncounterPlatformMapReadRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ElepEncounterPlatformMapQueryService {

    private final ElepEncounterPlatformMapReadRepository platformMapReadRepository;

    /**
     * 只读普通门诊 map(11)。没有则表示自费或未做医保挂号，电子处方须拦截。
     *
     * @param encounterId 就诊主键
     * @param tenantId    租户
     * @return 映射行
     */
    public Optional<ElepPlatformMapQueryModel> findGeneralOutpatientMap(Long encounterId, Long tenantId) {
        return platformMapReadRepository.findGeneralOutpatientMap(encounterId, tenantId);
    }
}
