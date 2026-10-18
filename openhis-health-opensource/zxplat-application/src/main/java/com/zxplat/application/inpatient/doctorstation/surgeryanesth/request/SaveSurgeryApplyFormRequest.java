/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineRequest;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 请求：SaveSurgeryApplyForm。
 *
 * <p>勾选 {@code nestAnesthesia} 时须带齐麻醉申请内容（{@code anesthesiaLines} + planned 中麻醉方法/医师），
 * 保存时一并生成麻醉申请单，而非仅空壳。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "SaveSurgeryApplyFormRequest")
public class SaveSurgeryApplyFormRequest {

    /** 就诊标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 编辑时旧申请单号。 */
    @Schema(description = "编辑时旧申请单号", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String previousPrescriptionNo;

    /** 申请行。 */
    @Schema(description = "申请行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SurgeryApplyFormLineRequest> lines;

    /** 计划字段；勾选嵌套麻醉时麻醉医师必填。 */
    @Schema(description = "计划字段", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private PlannedSurgeryFieldsDto planned;

    /** 是否嵌套生成麻醉申请。 */
    @Schema(description = "是否嵌套生成麻醉申请", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean nestAnesthesia;

    /**
     * 嵌套麻醉明细行（与独立麻醉页同构：ANESTHESIA / MEDICATION；禁止 SURCHARGE）。
     *
     * <p>{@code nestAnesthesia=true} 时必填至少一行；false 时忽略。</p>
     */
    @Schema(description = "嵌套麻醉明细行", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<AnesthesiaApplyFormLineRequest> anesthesiaLines;
}
