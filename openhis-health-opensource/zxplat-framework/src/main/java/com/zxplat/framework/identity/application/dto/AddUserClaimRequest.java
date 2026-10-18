/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加用户声明请求
 */
@Data
public class AddUserClaimRequest {

    @NotBlank(message = "声明类型不能为空")
    @Size(max = 256, message = "声明类型长度不能超过256")
    private String claimType;

    @Size(max = 512, message = "声明值长度不能超过512")
    private String claimValue;
}
