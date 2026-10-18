/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries;

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
