/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.terminology.command.codesystem;

import lombok.Data;

/**
 * 更新编码系统命令
 *
 * <p>用于封装更新编码系统的领域操作参数</p>
 *
 */
@Data
public class UpdateCodeSystemCommand {

    /**
     * 编码系统ID（必填）
     */
    private Long id;

    /**
     * 编码系统名称（可选）
     */
    private String name;

    /**
     * 编码系统标题（可选）
     */
    private String title;

    /**
     * 描述（可选）
     */
    private String description;

    /**
     * 版本号（可选）
     */
    private String version;

    /**
     * 状态（可选）
     */
    private String status;
}
