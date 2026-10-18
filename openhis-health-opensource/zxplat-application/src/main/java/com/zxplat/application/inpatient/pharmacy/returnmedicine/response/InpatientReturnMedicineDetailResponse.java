/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.response;

import com.zxplat.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnMedicineDetailRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 查询住院退药明细响应（IF-IPR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询住院退药明细响应")
public class InpatientReturnMedicineDetailResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 就诊下待退/已退发放行明细（PENDING_REFUND 行可勾选确认）。 */
    @Schema(description = "退药明细行（逐发放行）")
    private List<InpatientReturnMedicineDetailRowDto> items;
}
