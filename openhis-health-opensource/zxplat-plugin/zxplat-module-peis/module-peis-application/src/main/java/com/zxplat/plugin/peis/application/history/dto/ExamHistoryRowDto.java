/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.history.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExamHistoryRowDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    private String patientName;
    private String idCardNo;
    private String phone;
    private String busNo;

    @JsonSerialize(using = ToStringSerializer.class)
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

    @JsonSerialize(using = ToStringSerializer.class)
    private Long diagnosticReportId;

    /** 就诊状态中文（在检/已完成等）。 */
    private String statusText;
    /** 是否存在已结算收费明细。 */
    private Boolean paid;
    /** 是否允许未收费取消。 */
    private Boolean cancelable;
}
