/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编码体系（术语系统）元数据 DTO。
 *
 */
@Data
public class CodeSystemDto {

    /** 主键。 */
    private Long id;
    /** 体系唯一名称（机器可读）。 */
    private String name;
    /** 标题/展示名。 */
    private String title;
    /** 描述。 */
    private String description;
    /** 版本号。 */
    private String version;
    /** 状态码（整型化后的持久化值）。 */
    private Integer status;
    /** 状态中文标签。 */
    private String status_dictText;
    /** 租户 ID。 */
    private Long tenantId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
