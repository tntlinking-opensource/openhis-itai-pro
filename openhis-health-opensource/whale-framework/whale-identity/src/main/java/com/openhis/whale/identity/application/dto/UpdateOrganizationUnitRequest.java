/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新组织单元请求
 */
@Data
public class UpdateOrganizationUnitRequest {

    private Long id;  // 组织单元ID

    @Size(max = 256, message = "显示名称长度不能超过256")
    private String displayName;

    private Long parentId;

    private Integer sortOrder;

    private Boolean isEnabled;

    @Size(max = 512, message = "备注长度不能超过512")
    private String remark;
}
