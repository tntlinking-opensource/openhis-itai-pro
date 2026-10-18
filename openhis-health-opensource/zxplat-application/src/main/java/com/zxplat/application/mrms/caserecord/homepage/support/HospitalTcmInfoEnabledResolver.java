/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.support;

import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.constants.HospitalMedinsTypeConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按医院节点 {@code medins_type} 判定病案首页是否启用「中医信息」。
 *
 * <p>当前租户任一启用医院为中医院（{@link HospitalMedinsTypeConstants#isTcmHospital}）则开启；
 * 无医院或均为西医则关闭。不再依赖前端院点 override。</p>
 */
@Component
@RequiredArgsConstructor
public class HospitalTcmInfoEnabledResolver {

    private final OrganizationQueryService organizationQueryService;

    /**
     * 当前租户是否应按中医院首页展示中医信息段。
     *
     * @return true 表示启用中医信息 UI/编辑口径
     */
    public boolean resolveTcmInfoEnabled() {
        // Pre：只读启用态医院；科室节点不参与
        List<Organization> hospitals = organizationQueryService.listActiveHospitals();
        if (hospitals == null || hospitals.isEmpty()) {
            return false;
        }
        for (Organization hospital : hospitals) {
            if (hospital == null) {
                continue;
            }
            // 任一医院标为中医即开（多院区租户按「有中医院则开」）
            if (HospitalMedinsTypeConstants.isTcmHospital(hospital.getMedinsType())) {
                return true;
            }
        }
        return false;
    }
}
