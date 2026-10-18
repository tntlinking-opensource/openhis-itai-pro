/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.patient.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门诊护士站患者队列行 DTO（IF-NUR-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊护士站患者队列行")
public class NurseStationPatientQueueItemDto {

    /**
     * 就诊主键 {@code adm_encounter.id}，与处置单/执行写入同一锚点。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /**
     * 患者姓名（来自就诊上下文读模型，供队列卡片标题）。
     */
    @Schema(description = "患者姓名")
    private String patientName;

    /**
     * 性别持久化码（AdministrativeGender code）。
     */
    @Schema(description = "性别持久化码")
    private String genderEnum;

    /**
     * 性别展示文案（由 genderEnum 解析）。
     */
    @Schema(description = "性别展示文案")
    private String genderEnumText;

    /**
     * 出生日期（展示用），派生 {@link #ageText}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /**
     * 由出生日期派生的年龄展示串（如「56岁」）。
     */
    @Schema(description = "派生年龄展示")
    private String ageText;

    /**
     * 业务流水/挂号相关展示字段。
     */
    @Schema(description = "业务流水/挂号相关展示字段")
    private String slipNo;

    /**
     * 就诊开始时间（与门诊信息列表 {@code startTime} 同源）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "就诊时间")
    private LocalDateTime encounterTime;

    /**
     * 费用性质展示名：有医保账户优先其合同名，否则登记个人现金户合同名（与门诊医生站同口径）。
     */
    @Schema(description = "费用性质/医保类型展示")
    private String contractDisplay;

    /**
     * 是否仍有待护士发放的耗材（IF-NUR-01 / BR-NUR-017）。
     *
     * <p>true=已收费非退单耗材请求且发放单缺失或仍为草稿/待配药/待发药；
     * 供队列卡片红框与「待发耗材」标签，口径与处置单侧栏「待发放」一致。</p>
     */
    @Schema(description = "是否有未执行耗材")
    private Boolean hasPendingDevice;
}
