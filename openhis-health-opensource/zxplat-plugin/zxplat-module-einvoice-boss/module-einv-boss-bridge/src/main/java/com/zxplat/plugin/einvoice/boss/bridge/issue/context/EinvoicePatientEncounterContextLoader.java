/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.issue.context;

import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.entity.Patient;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

/**
 * 患者与就诊快照加载器，并提供博思报文所需的性别/年龄展示转换。
 */
@Component
@RequiredArgsConstructor
public class EinvoicePatientEncounterContextLoader {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;

    /**
     * 按主键加载患者。
     *
     * @param patientId 患者主键
     * @return 患者实体
     */
    public Patient requirePatient(Long patientId) {
        return patientQueryService.findOptionalPatientEntityById(patientId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "患者不存在"));
    }

    /**
     * 按主键加载就诊。
     *
     * @param encounterId 就诊主键
     * @return 就诊实体
     */
    public Encounter requireEncounter(Long encounterId) {
        return encounterQueryService.findOptionalEncounterEntityById(encounterId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "就诊不存在"));
    }

    /**
     * 将性别枚举码转为博思展示值。
     *
     * @param genderEnum 性别码
     * @return 男/女
     */
    public String toGenderDisplay(String genderEnum) {
        if (genderEnum == null) {
            return "男";
        }
        String normalized = genderEnum.trim().toUpperCase();
        if ("FEMALE".equals(normalized) || "F".equals(normalized) || "2".equals(normalized)) {
            return "女";
        }
        return "男";
    }

    /**
     * 根据出生日期计算年龄字符串。
     *
     * @param birthDate 出生日期
     * @return 年龄
     */
    public String resolveAge(LocalDate birthDate) {
        if (birthDate == null) {
            return "0";
        }
        int years = Period.between(birthDate, LocalDate.now()).getYears();
        if (years < 0) {
            return "0";
        }
        return String.valueOf(years);
    }
}
