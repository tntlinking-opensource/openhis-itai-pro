/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.position.dto;

import lombok.Data;

import java.util.List;

/**
 * 按所选平台角色汇总人员维护块 StaffScope 请求。
 */
@Data
public class ResolveStaffScopesRequest {

    /** 平台角色主键列表。 */
    private List<Long> roleIds;
}
