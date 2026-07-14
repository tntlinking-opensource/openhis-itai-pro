/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.body.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建身体部位请求（部位管理菜单）。
 *
 *
 */
@Data
public class CreateBodyRequest {

    /** 身体部位名称。 */
    @NotBlank(message = "部位名称不能为空")
    @Size(max = 255, message = "部位名称长度不能超过255")
    private String name;

    /** 上级部位ID（根节点可为空）。 */
    private Long parentId;
}
