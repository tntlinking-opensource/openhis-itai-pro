/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.diagnosis;

import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.setting.domain.SettingDefinitionEnum;
import com.zxplat.framework.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 读取系统设置「医生站西医诊断检索目录来源」，供门诊/住院诊断定义分页检索下推 {@code source_enum IN}。
 *
 * <p>值约定：逗号分隔的目录来源码（如 {@code hospital_dx,general_dx} 或 {@code clinical_v2}）；
 * 仅接受 {@link DoctorStationDiagnosisConstants#OUTPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCE_ALLOWLIST}
 * 内的码；空/非法回落默认两目录。中医检索不读本配置。
 * <strong>仅门诊</strong>使用；住院西医固定 {@link DoctorStationDiagnosisConstants#INPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCES}。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationWesternDiagnosisSourceSettingResolver {

    private final SettingManager settingManager;

    /**
     * 当前上下文下西医诊断检索可用的目录来源列表（已去重、保序）。
     *
     * @return 非空列表；至少回落 {@link DoctorStationDiagnosisConstants#DEFAULT_WESTERN_DIAGNOSIS_SEARCH_SOURCES}
     */
    public List<String> resolveWesternSearchSources() {
        String raw = settingManager.getEffectiveValue(
            SettingDefinitionEnum.DOCTOR_STATION_WESTERN_DIAGNOSIS_SOURCES.getName());
        if (StringUtils.isEmpty(raw)) {
            return new ArrayList<>(DoctorStationDiagnosisConstants.DEFAULT_WESTERN_DIAGNOSIS_SEARCH_SOURCES);
        }
        // 白名单含国临，便于院点将门诊检索切到 clinical_v2
        Set<String> allowed = new LinkedHashSet<>(
            DoctorStationDiagnosisConstants.OUTPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCE_ALLOWLIST);
        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (part == null) {
                continue;
            }
            String code = part.trim();
            if (code.isEmpty()) {
                continue;
            }
            if (allowed.contains(code)) {
                parsed.add(code);
            }
        }
        if (parsed.isEmpty()) {
            return new ArrayList<>(DoctorStationDiagnosisConstants.DEFAULT_WESTERN_DIAGNOSIS_SEARCH_SOURCES);
        }
        return new ArrayList<>(parsed);
    }
}
