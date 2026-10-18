/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceResourceKeyDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-15：签发医嘱批次请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-签发医嘱批次请求")
public class SignDoctorStationAdviceBatchRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者门控，须与就诊一致。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 待签发资源键列表（表名 + 请求 id）。 */
    @NotEmpty(message = "签发目标不能为空")
    @Valid
    /** 签发目标列表。 */
    @Schema(description = "签发目标列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DoctorStationAdviceResourceKeyDto> signTargets;

    /**
     * 医生已确认皮试药可跨批号开立（BR-ORD-034 / EX-ORD-309）。
     *
     * <p>皮试跨批确认已在 IF-DOR-14 暂存完成；签发相位通常忽略本字段。
     * 未勾选皮试或已能单批覆盖时忽略本字段。</p>
     */
    @Schema(description = "已确认皮试药跨批号开立（应对 EX-ORD-309）")
    private Boolean confirmCrossLotSkinTest;

    /**
     * 医生已确认药耗 FIFO 跨批次取用及分段金额（BR-ORD-035 / EX-ORD-310）。
     *
     * <p>首次签发若 FIFO 拆为多批则抛 EX-ORD-310（文案含各批数量与金额、合计）；
     * 前端确认后带 {@code true} 重试，本批不再提示。单批覆盖时忽略本字段。</p>
     */
    @Schema(description = "已确认跨批次取用及分段金额（应对 EX-ORD-310）")
    private Boolean confirmCrossLotPricing;
}
