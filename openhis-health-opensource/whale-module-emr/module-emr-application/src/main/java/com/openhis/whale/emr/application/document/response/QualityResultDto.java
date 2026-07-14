/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class QualityResultDto {

    /** passed（业务字段） */
    private boolean passed;
    /** 质控问题列表（预留） */
    private List<QualityIssueDto> issues;

    @Getter
    @Setter
    public static class QualityIssueDto {
        private String level;        // INFO / WARN / ERROR
        private String dataItemName; // 关联的数据项名（可选）
        private String location;     // 位置描述（区域/数据组/数据元）
        private String message;      // 问题描述
    }
}
