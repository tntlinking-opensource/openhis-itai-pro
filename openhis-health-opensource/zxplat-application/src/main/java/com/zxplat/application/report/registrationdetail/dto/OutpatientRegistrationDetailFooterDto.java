/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.registrationdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊挂号明细报表明细页脚挂/退统计（IF-RPT-ORG-03；对齐门诊挂号当日统计）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表明细页脚统计")
public class OutpatientRegistrationDetailFooterDto {

    /** 挂号笔数（排除待挂号 PLANNED 与未收费取消 ENTERED_IN_ERROR）。 */
    @Schema(description = "挂号笔数")
    private Long registrationCount;

    /** 已退笔数（仅退号 CANCELLED）。 */
    @Schema(description = "已退笔数")
    private Long cancelledCount;

    /** 有效笔数（挂号 − 已退，下限 0）。 */
    @Schema(description = "有效笔数")
    private Long activeCount;
}
