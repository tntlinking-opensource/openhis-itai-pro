/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteBindItemDto;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交手术/麻醉执行弹窗请求（IF-IP-SAE-02）。
 *
 * <p>挂接 IF-IP-EXE-02：{@code planTime}→occurrence；{@code actualTime}→recorded；计费/发药委托 EXE。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交手术/麻醉执行请求", requiredProperties = {
    "encounterId", "serviceRequestId", "planTime", "accountId"
})
public class SubmitSurgeryAnesthExecuteRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 服务申请主键。 */
    @NotNull(message = "服务申请标识不能为空")
    @Schema(description = "服务申请主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /**
     * 计划槽位时刻 → Procedure.occurrence_time（禁止用手术/麻醉业务时间覆盖）。
     */
    @NotNull(message = "计划执行时刻不能为空")
    @Schema(description = "计划执行槽位时刻", required = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime planTime;

    /**
     * 实际执行时刻 → Procedure.recorded_time（同 EXE；可空则服务端默认=planTime）。
     */
    @Schema(description = "实际执行时刻（默认同计划时点）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime actualTime;

    /**
     * 就诊挂账账户（透传 EXE-02；服务端仍按医保优先覆盖）。
     */
    @NotNull(message = "就诊账户标识不能为空")
    @Schema(description = "就诊挂账账户主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 手术时间（→ Procedure.procedureTime；可空）。 */
    @Schema(description = "手术时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime procedureTime;

    /** 样本（手术弹窗）。 */
    @Schema(description = "样本")
    private String specimen;

    /** 备注（手术弹窗；与样本分列）。 */
    @Schema(description = "备注")
    private String note;

    /** 配合护士（可覆盖申请 nurse_id）。 */
    @Schema(description = "配合护士主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nurseId;

    /** 麻醉开始时间（麻醉弹窗）。 */
    @Schema(description = "麻醉开始时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime anesthesiaStartTime;

    /** 麻醉结束时间（麻醉弹窗；须 ≥ 开始）。 */
    @Schema(description = "麻醉结束时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime anesthesiaEndTime;

    /** 列表回传绑定项（透传 EXE-02）。 */
    @Schema(description = "绑定项子集")
    private List<NurseStationOrderExecuteBindItemDto> bindItems;
}
