/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.application.basic.position.dto;

import lombok.Data;

import java.util.List;

/**
 * 当前操作者可维护的岗位人员信息块（来自平台角色 StaffScope 声明并集）。
 */
@Data
public class StaffEditableScopesDto {

    /** 可编辑的 StaffScope 声明值列表。 */
    private List<String> editableScopes;
}
