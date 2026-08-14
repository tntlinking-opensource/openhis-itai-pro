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

    /** 计划执行时刻（occurrence_time；deadline/Tab 过滤与逻辑锚点；发药/账单按此归并）。长期终态取 occurrence；临嘱已执行与 recorded 同值。 */
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

    /**
     * 护士登记/实际执行时刻（recorded_time）。
     *
     * <p>已执行/不执行/取消执行等终态 Tab 的<strong>展示</strong>优先本字段；
     * 待执行 Tab 展示 {@link #planTime}。逻辑汇总禁止用本字段替代 planTime。</p>
     */
    @Schema(description = "护士登记/实际执行时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime recordedTime;

    /**
     * 待执行槽位建议的实际执行时分（写路径 {@code actualTime} 默认值）。
     *
     * <p>开嘱日<strong>首次</strong> pending 槽为开立时刻延后两分钟（BR-IP-EXE-032）；
     * 已停嘱时停嘱日<strong>末</strong> pending 槽为停嘱时刻（对称易用，同槽开嘱优先）；
     * 其余 pending 为空，前端/写路径回落 {@link #planTime}（BR-IP-EXE-030）。</p>
     */
    @Schema(description = "待执行建议实际执行时分；首次槽=开立+2分钟，停嘱日末槽=停嘱")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime defaultActualTime;

    /**
     * 操作人展示名（已执行/不执行/取消执行槽位；待执行为空）。
     *
     * <p>优先主执行人（{@code cln_procedure_performer} PRIMARY）；无则回落 Procedure {@code recorder_id}
     *（不执行/取消等仅写记录人场景）。前端与汇总领药「时刻+数量」同构，拼接为「HH:mm 姓名」。</p>
     */
    @Schema(description = "操作人展示名；待执行槽位为空")
    private String operatorName;
}
