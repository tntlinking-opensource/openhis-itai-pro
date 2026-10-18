/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * IF-REG-12：挂号台有效预约快照响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号台有效预约快照")
public class LoadAppointmentForRegisterResponse {

    /** 预约主键（业务字段）。 */
    @Schema(description = "预约主键")
    private Long appointmentId;

    /** 预约单号。 */
    @Schema(description = "预约单号")
    private String appointmentBusNo;

    /** 号源主键。 */
    @Schema(description = "号源主键")
    private Long slotId;

    /** 科室主键。 */
    @Schema(description = "科室主键")
    private Long organizationId;

    /** 医师主键。 */
    @Schema(description = "医师主键")
    private Long practitionerId;

    /** 号源类型主键。 */
    @Schema(description = "号源类型主键")
    private Long healthcareServiceId;

    /** 时段开始。 */
    @Schema(description = "时段开始")
    private LocalDateTime slotStart;

    /** 时段结束。 */
    @Schema(description = "时段结束")
    private LocalDateTime slotEnd;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 医师姓名。 */
    @Schema(description = "医师姓名")
    private String practitionerName;

    /** 号源类型名称。 */
    @Schema(description = "号源类型名称")
    private String serviceTypeName;

    /** 预约状态。 */
    @Schema(description = "预约状态")
    private String statusEnum;
}
