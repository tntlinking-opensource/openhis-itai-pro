/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建组织单元请求
 */
@Data
public class CreateOrganizationUnitRequest {

    @NotBlank(message = "组织单元代码不能为空")
    @Size(max = 128, message = "组织单元代码长度不能超过128")
    private String code;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 256, message = "显示名称长度不能超过256")
    private String displayName;

    private Long parentId;

    private Integer sortOrder = 0;

    private Boolean isEnabled = true;

    @Size(max = 512, message = "备注长度不能超过512")
    private String remark;
}
