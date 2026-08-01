/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院患者入出明细报表人次页脚（IF-RPT-IOE-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者入出明细报表人次页脚")
public class InpatientInOutDetailFooterDto {

    /** 窗内入院人次（按 Encounter）。 */
    @Schema(description = "入院人次")
    private Long admissionPersonTimes;

    /** 窗内出院人次（按 Encounter）。 */
    @Schema(description = "出院人次")
    private Long dischargePersonTimes;
}
