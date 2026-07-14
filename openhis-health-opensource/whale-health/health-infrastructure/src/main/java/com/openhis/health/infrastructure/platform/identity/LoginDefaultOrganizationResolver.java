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
 * 默认登录组织：{@code adm_practitioner.org_id}；未填时回落会话医院。
 */
@Component
@RequiredArgsConstructor
public class LoginDefaultOrganizationResolver implements ILoginDefaultOrganizationResolver {

    private final PractitionerQueryService practitionerQueryService;
    private final ILoginOrganizationScopeQuery loginOrganizationScopeQuery;

    /**
     * 默认登录组织：优先 {@code adm_practitioner.org_id}，未填时回落会话所选医院。
     *
     * @param userId                      平台用户主键
     * @param sessionHospitalOrganizationId 当前会话医院 id，可为 null
     * @return 默认登录组织 id
     */
    @Override
    public Optional<Long> resolveDefaultLoginOrganizationId(Long userId, Long sessionHospitalOrganizationId) {
        if (userId == null) {
            return Optional.empty();
        }
        Practitioner practitioner = practitionerQueryService.findByUserId(userId);
        // 医务人员主档已配置 org_id 时直接采用
        if (practitioner != null && practitioner.getOrgId() != null) {
            return Optional.of(practitioner.getOrgId());
        }
        // 未配置 org 时回落会话医院，避免登录后无默认科室
        if (sessionHospitalOrganizationId != null) {
            return Optional.of(sessionHospitalOrganizationId);
        }
        return Optional.empty();
    }

    /**
     * 当用户无默认 org 时，列出全部启用医院 id 供前端兜底选择。
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
}
