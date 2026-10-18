/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 护士划价列表按就诊分组（IF-IP-PRC-03/05）。
 *
 * <p>同一 DTO 承载两种列表：{@link #orders} 为补费父医嘱；{@link #pricedOrders} 为已划价行，互斥使用。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "护士划价按就诊分组")
public class NurseStationPricingPatientGroupDto {

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 补费父医嘱行。 */
    @Schema(description = "补费父医嘱行")
    private List<NurseStationSupplementParentOrderRowDto> orders = new ArrayList<>();

    /** 已划价行。 */
    @Schema(description = "已划价行")
    private List<NurseStationPricedOrderRowDto> pricedOrders = new ArrayList<>();
}
