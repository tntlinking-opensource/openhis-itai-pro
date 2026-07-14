/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.code.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建概念请求（编码系统管理菜单）
 *
 */
@Data
public class CreateConceptRequest {

    /** 所属编码体系 ID。 */
    @NotNull(message = "编码系统ID不能为空")
    private Long codeSystemId;

    /** 概念编码（业务键）。 */
    @NotBlank(message = "编码不能为空")
    @Size(max = 100, message = "编码长度不能超过100")
    private String code;

    /** 显示名称。 */
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 500, message = "显示名称长度不能超过500")
    private String display;

    /** 定义说明。 */
    @Size(max = 1000, message = "定义长度不能超过1000")
    private String definition;

//    private Long parentId;

    /** 排序号。 */
    private Long sortOrder;

    /** 是否启用。 */
    private Boolean isActive;

//    @Size(max = 100, message = "CSS样式类长度不能超过100")
//    private String cssClass;

//    @Size(max = 100, message = "列表样式类长度不能超过100")
//    private String listClass;

//    @Size(max = 1, message = "是否默认标识长度不能超过1")
//    private String isDefault;
}
