/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 住院发药在院患者队列行（IF-IPP-02）。
 *
 * <p>展示字段来自 {@code InpatientInfo} 读模型，与领域队列 encounter 顺序一致。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药在院患者队列行")
public class PendingDispenseInpatientEncounterQueueRowDto {

    /** 住院就诊主键；队列按存在待发/在途发放事实的在院 encounter 收窄。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 患者主键。 */
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别持久化 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 年龄展示文本。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 病区库位主键。 */
    @Schema(description = "病区 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床位名称。 */
    @Schema(description = "床位名称")
    private String bedName;

    /** 当前科室名称。 */
    @Schema(description = "当前科室名称")
    private String organizationName;

    /** 主治医生姓名。 */
    @Schema(description = "主治医生姓名")
    private String attendingDoctorName;

    /** 住院就诊状态 code。 */
    @Schema(description = "住院状态 code")
    private String statusEnum;

    /** 住院就诊状态展示。 */
    @Schema(description = "住院状态展示")
    private String statusEnumText;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    /**
     * 费用性质展示名（就诊优先合同名；与门诊发药/医生站卡片 {@code contractDisplay} 同口径）。
     * <p>无合同时为空，前端回落「自费」。</p>
     */
    @Schema(description = "费用性质展示名")
    private String contractDisplay;
}
