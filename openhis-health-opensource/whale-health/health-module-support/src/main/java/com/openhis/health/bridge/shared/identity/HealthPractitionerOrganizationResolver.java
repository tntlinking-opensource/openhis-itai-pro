/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.bridge.shared.identity;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.whale.core.application.IPractitionerOrganizationResolver;
import com.openhis.whale.core.application.dto.PractitionerOrganizationIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * whale-core {@link IPractitionerOrganizationResolver} 的 health 实现：按平台用户解析医务人员与机构主键。
 *
 * <p>落点 {@code bridge.shared.identity}：供 identity 等模块经接口注入，业务 AppService 不应直接依赖本类。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthPractitionerOrganizationResolver implements IPractitionerOrganizationResolver {

    private final PractitionerQueryService practitionerQueryService;

    @Override
    public Optional<PractitionerOrganizationIds> resolve(Long userId, Long tenantId) {
        if (userId == null) {
            return Optional.empty();
        }
        Practitioner p = practitionerQueryService.findByUserId(userId);
        if (p == null || p.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(new PractitionerOrganizationIds(p.getId(), p.getOrgId()));
    }
}
