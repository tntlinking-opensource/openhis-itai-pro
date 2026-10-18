/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色下拉选项（含岗位范围声明，供岗位分配 UI 使用）。
 */
@Data
public class RoleOptionDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String displayName;

    /** 是否内置角色（内置角色参与岗位业务语义）。 */
    private Boolean builtIn;

    /** 服务端角色 code（供岗位 UI 逻辑使用，不对用户展示）。 */
    private String code;

    /** 岗位范围声明（AssignmentScope）。 */
    private List<String> assignmentScopes = new ArrayList<>();
}
