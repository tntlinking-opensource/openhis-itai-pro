/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.warehouse.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建库房/药房请求。
 */
@Data
public class CreateWarehouseRequest {
    /** 库房名称。 */
    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 物理形式：存 {@link com.zxplat.domain.share.enums.LocationForm#getCode()}（WAREHOUSE / PHARMACY / DEPARTMENT_PHARMACY）。
     */
    @NotBlank(message = "物理形式不能为空")
    private String formEnum;

    /** 上级科室主键（科室药房必填；药库/药房由服务端回落默认机构）。 */
    private Long organizationId;
}
