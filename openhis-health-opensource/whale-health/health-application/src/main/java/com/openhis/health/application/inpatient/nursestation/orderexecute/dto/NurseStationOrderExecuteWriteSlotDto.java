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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行写路径槽位（IF-IP-EXE-02/04）。
 *
 * <p>前端从列表 pending 槽位勾选后回传；{@code planTime} 须与列表一致（BR-IP-EXE-023）。
 * {@code bindItems} 为列表 IF-IP-EXE-01 下发的绑定项子集，执行时按勾选派生写路径（BR-IP-EXE-020）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行写路径槽位", requiredProperties = {"orderId", "orderType", "planTime", "accountId"})
public class NurseStationOrderExecuteWriteSlotDto {

    /** 医嘱/申请主键。 */
    @NotNull(message = "医嘱标识不能为空")
    @Schema(description = "医嘱/申请主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @NotBlank(message = "医嘱类型不能为空")
    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @Schema(description = "医嘱类型：MED_REQUEST / SERVICE_REQUEST", required = true)
    private String orderType;

    /** 计划执行时刻（须与列表 pending 槽位一致，BR-IP-EXE-023）。 */
    @NotNull(message = "计划执行时刻不能为空")
    /** 计划执行时刻。 */
    @Schema(description = "计划执行时刻", required = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime planTime;

    /** 护士实际执行时刻（→ recorded_time；默认同 planTime；仅允许同日改时分；展示用，逻辑锚点仍为 planTime）。 */
    @Schema(description = "护士实际执行时刻（默认同计划时点，仅允许同日调整时分）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime actualTime;

    /**
     * 就诊默认挂账账户主键（医保入院=医保户，否则个人现金户；由护士站列表回传，服务端仍按医保优先覆盖）。
     */
    @NotNull(message = "就诊账户标识不能为空")
    @Schema(description = "就诊默认挂账账户主键（医保优先）", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊默认挂账账户主键。 */
    private Long accountId;

    /** 列表回传的绑定项。 */
    @Schema(description = "列表回传的绑定项")
    private List<NurseStationOrderExecuteBindItemDto> bindItems;

    /**
     * 中药方内全部药品申请主键（可选；列表 lineRequestIds 回传时可免按处方号扩行查库）。
     */
    @Schema(description = "中药方内全部药品申请主键（可选，列表 lineRequestIds 回传）")
    private List<Long> lineRequestIds;

    /** 中药处方号（可选）。 */
    @Schema(description = "中药处方号（可选）")
    private String prescriptionNo;

    /**
     * 手术/麻醉执行弹窗标记（AS-IP-NUR-016 IF-IP-SAE-02）。
     *
     * <p>为 {@code true} 时：create/complete 写入弹窗横表字段，并以 SR 横表人员 + 弹窗配合护士挂入多角色 performer；
     * 不再登记 PRIMARY 主执行人。</p>
     */
    @Schema(description = "手术/麻醉执行弹窗路径标记")
    private Boolean surgeryAnesthExecuteFlag;

    /**
     * 手术时间（→ Procedure.procedureTime；禁止写入 occurrence_time）。
     */
    @Schema(description = "手术时间（弹窗可改）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime procedureTime;

    /** 样本（仅手术弹窗）。 */
    @Schema(description = "样本")
    private String specimen;

    /** 备注（仅手术弹窗；与样本分列）。 */
    @Schema(description = "备注")
    private String note;

    /**
     * 配合护士（→ performer NURSE；可覆盖申请 nurse_id）。
     */
    @Schema(description = "配合护士主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nurseId;

    /** 麻醉开始时间（仅麻醉弹窗）。 */
    @Schema(description = "麻醉开始时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime anesthesiaStartTime;

    /** 麻醉结束时间（仅麻醉弹窗；须 ≥ 开始）。 */
    @Schema(description = "麻醉结束时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime anesthesiaEndTime;
}
