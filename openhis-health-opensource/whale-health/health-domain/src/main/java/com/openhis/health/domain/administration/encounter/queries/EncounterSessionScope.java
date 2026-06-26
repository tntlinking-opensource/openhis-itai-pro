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

package com.openhis.health.domain.administration.encounter.queries;

import java.util.List;

/**
 * 就诊分页查询会话收窄上下文（机构单元、管辖病区），由 module-support Loader 从会话解析后传入 domain 查询服务。
 */
public record EncounterSessionScope(
    Long sessionOrganizationUnitId,
    boolean organizationScopeEnforcementActive,
    List<Long> managedWardLocationIds) {

    /** 无机构/病区收窄（仅用于缺省回落）。 */
    public static EncounterSessionScope empty() {
        return new EncounterSessionScope(null, false, List.of());
    }
}
