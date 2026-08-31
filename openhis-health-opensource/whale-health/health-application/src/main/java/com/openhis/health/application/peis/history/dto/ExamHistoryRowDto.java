/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.history.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExamHistoryRowDto {
    private Long encounterId;
    private String busNo;
    private Long packageActivityId;
    private String packageName;
    private Boolean groupFlag;
    private String statusEnum;
    private LocalDateTime createdAt;
    /** freshman / annual / staff / other / personal / group */
    private String examKind;
    private Integer calendarYear;
    /** 总检报告是否 final。 */
    private Boolean finalReport;
    private Long diagnosticReportId;
    /** 就诊状态中文（在检/已完成等）。 */
    private String statusText;
    /** 是否存在已结算收费明细。 */
    private Boolean paid;
    /** 是否允许未收费取消。 */
    private Boolean cancelable;
}
