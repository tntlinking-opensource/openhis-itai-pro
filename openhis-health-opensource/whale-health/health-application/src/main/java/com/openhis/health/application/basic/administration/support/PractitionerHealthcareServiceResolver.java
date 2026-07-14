/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.administration.support;

import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 医师可接诊号源类型收窄与写侧 Gate（用户管理配置，不按科室区分）。
 */
@Component
@RequiredArgsConstructor
public class PractitionerHealthcareServiceResolver {

    private final PractitionerRoleQueryService practitionerRoleQueryService;

    /**
     * 加载医师在全院岗位下配置的可接诊号源类型主键（去重）。
     *
     * @param practitionerId 医师主键
     * @return 号源主键集合；未配置时为空集
     */
    public Set<Long> loadHealthcareServiceIdsByPractitioner(Long practitionerId) {
        // Gate：无医师主键时不查岗位，返回空集表示「未配置可接诊号源」
        if (practitionerId == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(
            practitionerRoleQueryService.listHealthcareServiceIdsByPractitioner(practitionerId));
    }

    /**
     * 按医师可接诊号源收窄挂号/排班下拉候选。
     *
     * @param allServices    候选号源（通常为 ACTIVE 全量）
     * @param practitionerId 医师主键；为空时不收窄
     * @return 收窄结果；医师已选但未配置号源时返回空列表
     */
    public List<HealthcareService> narrowByPractitioner(List<HealthcareService> allServices, Long practitionerId) {
        // 未选医师或候选为空：不做收窄，保持挂号页全量号源
        if (practitionerId == null || allServices == null) {
            return allServices;
        }
        Set<Long> allowedIds = this.loadHealthcareServiceIdsByPractitioner(practitionerId);
        // 医师已选但未维护可接诊号源：下拉须为空，禁止误选全院号源
        if (allowedIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<HealthcareService> narrowed = new ArrayList<>();
        for (HealthcareService service : allServices) {
            if (service != null && service.getId() != null && allowedIds.contains(service.getId())) {
                narrowed.add(service);
            }
        }
        return narrowed;
    }

    /**
     * 校验医师可接诊指定号源；指定医师但未配置号源或号源不在范围内时拒绝。
     *
     * @param practitionerId       医师主键
     * @param healthcareServiceId 号源类型主键
     * @throws BusinessException 医师未配置可接诊号源或号源不在可接诊范围内
     */
    public void assertPractitionerAcceptsHealthcareService(Long practitionerId, Long healthcareServiceId) {
        // 未指定医师时不做号源 Gate（由上层决定是否必选医师）
        if (practitionerId == null) {
            return;
        }
        if (healthcareServiceId == null) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT, "号源类型不能为空");
        }
        Set<Long> allowedIds = this.loadHealthcareServiceIdsByPractitioner(practitionerId);
        if (allowedIds.isEmpty()) {
            // 写侧拒绝：用户管理未维护可接诊号源
            throw new BusinessException(
                ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT,
                "该医师未配置可接诊号源，请在用户管理中维护");
        }
        if (!allowedIds.contains(healthcareServiceId)) {
            // 写侧拒绝：所选号源不在该医师岗位配置范围内
            throw new BusinessException(
                ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT,
                "所选号源类型不在该医师可接诊范围内");
        }
    }
}
