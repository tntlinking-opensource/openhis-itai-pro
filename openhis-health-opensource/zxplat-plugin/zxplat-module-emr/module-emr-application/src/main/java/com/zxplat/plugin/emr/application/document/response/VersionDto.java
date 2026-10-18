/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class VersionDto {

    /** 主键 id */
    private Long id;
    /** 文书主键 id */
    private Long documentId;
    /** 版本序号（从 1 递增） */
    private Integer versionNumber;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 创建人 id */
    private Long createdBy;
}
