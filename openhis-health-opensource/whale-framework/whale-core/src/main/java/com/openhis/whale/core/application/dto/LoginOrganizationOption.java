/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录选医院/科室候选项（HIS {@code adm_organization.id}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginOrganizationOption {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String code;

    private String displayName;
}
