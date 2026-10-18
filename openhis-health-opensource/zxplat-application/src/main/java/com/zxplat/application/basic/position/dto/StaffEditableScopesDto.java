/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.position.dto;

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
