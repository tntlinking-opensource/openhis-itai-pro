/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建编码系统请求（编码系统管理菜单）
 *
 */
@Data
public class CreateCodeSystemRequest {

    /** 体系机器名（唯一）。 */
    @NotBlank(message = "编码系统名称不能为空")
    @Size(max = 100, message = "编码系统名称长度不能超过100")
    private String name;

    /** 展示标题。 */
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    /** 说明。 */
    private String description;

    /** 版本号字符串。 */
    @Size(max = 50, message = "版本号长度不能超过50")
    private String version;
}
