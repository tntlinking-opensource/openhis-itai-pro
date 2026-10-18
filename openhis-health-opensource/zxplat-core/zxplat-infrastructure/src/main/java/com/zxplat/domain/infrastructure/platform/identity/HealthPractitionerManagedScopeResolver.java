/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.platform.identity;

import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.practitionerrole.queries.model.PractitionerManagedScopeQueryModel;
import com.zxplat.framework.core.application.IPractitionerManagedScopeResolver;
import com.zxplat.framework.core.application.dto.PractitionerManagedScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * zxplat-framework {@link IPractitionerManagedScopeResolver} 的 health 实现：从 {@code adm_pr_assign} 派生管辖范围
 * （药库/药房 {@code adm_location.id}、科室 {@code adm_organization.id}、病区 {@code adm_location.id}）。
 *
 * <p>所有账号（含 admin）均按岗位分配派生，无账号级特例。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthPractitionerManagedScopeResolver implements IPractitionerManagedScopeResolver {

    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerManagedScopeLoader practitionerManagedScopeLoader;

    /**
     * 解析医务人员管辖范围：未传 practitionerId 时按 userId 反查主档（B.6.4：跨聚合读经 Loader）。
     *
     * @param userId          平台用户主键，可为 null
     * @param practitionerId  医务人员主键，可为 null
     * @param tenantId        租户主键（预留，当前未参与过滤）
     * @return 管辖库位/科室/病区 id 集合；无法解析 practitioner 时 empty
     */
    @Override
    public Optional<PractitionerManagedScope> resolve(Long userId, Long practitionerId, Long tenantId) {
        Long resolvedPractitionerId = practitionerId;
        // 仅传 userId 时从 adm_practitioner 反查绑定主键
        if (resolvedPractitionerId == null && userId != null) {
            var practitioner = practitionerQueryService.findByUserId(userId);
            if (practitioner != null) {
                resolvedPractitionerId = practitioner.getId();
            }
        }
        // 无有效 practitioner 则无法派生岗位管辖
        if (resolvedPractitionerId == null) {
            return Optional.empty();
        }
        // Pre：经 platform identity Loader 组装管辖读模型（B.6.4）
        PractitionerManagedScopeQueryModel model =
            this.practitionerManagedScopeLoader.resolveManagedScope(resolvedPractitionerId);
        PractitionerManagedScope scope = new PractitionerManagedScope(
            model.getManagedLocationIds(),
            model.getManagedOrganizationUnitIds(),
            model.getManagedWardLocationIds());
        return Optional.of(scope);
    }
}
