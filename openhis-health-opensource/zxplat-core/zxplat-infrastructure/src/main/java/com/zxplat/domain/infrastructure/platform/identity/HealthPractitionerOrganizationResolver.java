/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.platform.identity;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.framework.core.application.IPractitionerOrganizationResolver;
import com.zxplat.framework.core.application.dto.PractitionerOrganizationIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * zxplat-framework {@link IPractitionerOrganizationResolver} 的 health 实现：按平台用户解析医务人员与机构主键。
 *
 * <p>落点 {@code infrastructure.platform.identity}：供 identity 等模块经接口注入，业务 AppService 不应直接依赖本类。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthPractitionerOrganizationResolver implements IPractitionerOrganizationResolver {

    private final PractitionerQueryService practitionerQueryService;

    /**
     * 按平台用户解析绑定的医务人员主键与所属机构 id，供 identity/会话模块注入。
     *
     * @param userId   平台用户主键
     * @param tenantId 租户主键（预留，当前未参与过滤）
     * @return 医务人员与机构 id 对；未绑定或 userId 为空时 empty
     */
    @Override
    public Optional<PractitionerOrganizationIds> resolve(Long userId, Long tenantId) {
        if (userId == null) {
            return Optional.empty();
        }
        // 经 adm_practitioner.user_id 反查主档
        Practitioner p = practitionerQueryService.findByUserId(userId);
        if (p == null || p.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(new PractitionerOrganizationIds(p.getId(), p.getOrgId()));
    }
}
