/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.identity;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.whale.core.application.ILoginDefaultOrganizationResolver;
import com.openhis.whale.core.application.ILoginOrganizationScopeQuery;
import com.openhis.whale.core.application.dto.LoginOrganizationOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 默认登录科室：仅 {@code adm_practitioner.org_id}（须为科室，不得为医院）。
 *
 * <p>产品口径：默认登录科室可选可不选；不填则会话停留医院级；填写则登录到该科室。
 * 登录页「选科室」按出诊科室同步结果出现，账号无特例。</p>
 */
@Component
@RequiredArgsConstructor
public class LoginDefaultOrganizationResolver implements ILoginDefaultOrganizationResolver {

    private final PractitionerQueryService practitionerQueryService;
    private final ILoginOrganizationScopeQuery loginOrganizationScopeQuery;

    /**
     * 默认登录科室：仅采用医务人员主档 {@code org_id}，且该节点不得为医院。
     *
     * <p>{@code sessionHospitalOrganizationId} 仅用于校验科室归属医院，不再作为回落默认科室。</p>
     *
     * @param userId                        平台用户主键
     * @param sessionHospitalOrganizationId 当前会话医院 id，可为 null
     * @return 默认登录科室 id；未配置、配置为医院、或不属于会话医院时 empty
     */
    @Override
    public Optional<Long> resolveDefaultLoginOrganizationId(Long userId, Long sessionHospitalOrganizationId) {
        if (userId == null) {
            return Optional.empty();
        }
        Practitioner practitioner = practitionerQueryService.findByUserId(userId);
        if (practitioner == null || practitioner.getOrgId() == null) {
            return Optional.empty();
        }
        Long orgId = practitioner.getOrgId();
        // 医院节点属于选院步骤，不能作为登录科室
        if (isHospitalOrganization(orgId)) {
            return Optional.empty();
        }
        // 已选医院时，默认科室须落在该医院下
        if (sessionHospitalOrganizationId != null
            && !loginOrganizationScopeQuery.belongsToHospital(orgId, sessionHospitalOrganizationId)) {
            return Optional.empty();
        }
        return Optional.of(orgId);
    }

    /**
     * 当用户无默认 org 时，列出全部启用医院 id 供登录选院步骤使用。
     *
     * @param userId 平台用户主键
     * @return 医院 id 列表；无绑定 practitioner 时为空列表
     */
    @Override
    public List<Long> resolveFallbackHospitalOrganizationIds(Long userId) {
        if (userId == null || loginOrganizationScopeQuery == null) {
            return List.of();
        }
        if (practitionerQueryService.findByUserId(userId) == null) {
            return List.of();
        }
        List<Long> hospitalIds = new ArrayList<>();
        for (LoginOrganizationOption option : loginOrganizationScopeQuery.listActiveHospitalOrganizations()) {
            if (option != null && option.getId() != null) {
                hospitalIds.add(option.getId());
            }
        }
        return hospitalIds;
    }

    /**
     * 判定业务组织是否为医院节点（{@code belongsToHospital(id, id)}）。
     *
     * @param organizationId 业务组织主键
     * @return 医院节点为 true
     */
    private boolean isHospitalOrganization(Long organizationId) {
        if (organizationId == null || loginOrganizationScopeQuery == null) {
            return false;
        }
        return loginOrganizationScopeQuery.belongsToHospital(organizationId, organizationId);
    }
}
