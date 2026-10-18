/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.terminology.command.codesystem;

import lombok.Data;

/**
 * 创建编码系统命令
 *
 * <p>用于封装创建编码系统的领域操作参数</p>
 *
 */
@Data
public class CreateCodeSystemCommand {

    /**
     * 编码系统名称（必填，对应原来的dictType）
     */
    private String name;

    /**
     * 编码系统标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 版本号（可选，默认为"1.0"）
     */
    private String version;

    /**
     * 状态（可选，默认为ACTIVE）
     */
    private String status;

    /**
     * 租户ID
     */
    private Long tenantId;
}
