/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.position.dto;

import lombok.Data;

import java.util.List;

/**
 * 所选角色对应的人员维护块 StaffScope 并集。
 */
@Data
public class StaffRoleScopesDto {

    /** StaffScope 声明值列表（去重保序）。 */
    private List<String> staffScopes;
}
