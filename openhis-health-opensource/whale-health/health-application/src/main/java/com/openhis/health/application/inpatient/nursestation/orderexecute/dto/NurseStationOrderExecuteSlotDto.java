/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医嘱执行槽位（IF-IP-EXE-01，BR-IP-EXE-007～009）。
 *
 * <p>pending 槽位 status=PREPARATION 且无 procedureId；已执行/不执行/已取消槽位
 * 携带 procedureId 与 recordedTime。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行槽位")
public class NurseStationOrderExecuteSlotDto {

    /** 计划执行时刻（occurrence_time；deadline/Tab 过滤键）。 */
    @Schema(description = "计划执行时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime planTime;

    /** 执行记录主键；pending 槽位为空。 */
    @Schema(description = "执行记录主键；pending 槽位为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long procedureId;

    /** 槽位终态 code（EventStatus）。 */
    @Schema(description = "槽位终态 code")
    private String statusEnum;

    /** 槽位终态展示。 */
    @Schema(description = "槽位终态展示")
    private String statusEnumText;

    /** 护士登记/实际执行时刻（recorded_time）。 */
    @Schema(description = "护士登记/实际执行时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime recordedTime;
}
