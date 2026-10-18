/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.department.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑科室请求参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateDepartmentRequest extends CreateDepartmentRequest {

    /** 科室主键。 */
    @NotNull(message = "ID不能为空")
    private Long id;

    /**
     * 上级组织是否发生变更（用于区分“未传 parentId”与“显式改为根节点”）。
     */
    private Boolean parentIdChanged;
}
