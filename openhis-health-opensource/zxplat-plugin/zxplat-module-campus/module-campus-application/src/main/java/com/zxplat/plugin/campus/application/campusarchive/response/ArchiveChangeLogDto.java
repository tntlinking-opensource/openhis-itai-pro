/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusarchive.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ArchiveChangeLogDto {

    private Long id;
    private String subjectTypeEnum;
    private Long subjectId;
    private String changeTypeEnum;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime changeTime;
    private String changeSourceEnum;
    private String campusCode;
    private String batchId;
    private String remark;
}
