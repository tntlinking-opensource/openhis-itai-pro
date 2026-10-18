/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新编码系统请求（编码系统管理菜单）
 *
 */
@Data
public class UpdateCodeSystemRequest {

    /** 编码体系主键。 */
    @NotNull(message = "编码系统ID不能为空")
    private Long id;

    /** 展示标题。 */
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    /** 说明。 */
    private String description;

    /** 版本号。 */
    @Size(max = 50, message = "版本号长度不能超过50")
    private String version;
}
