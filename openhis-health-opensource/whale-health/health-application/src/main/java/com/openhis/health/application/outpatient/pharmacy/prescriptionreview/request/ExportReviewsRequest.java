/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 导出点评记录请求（IF-PRV-03）。
 *
 * <p>筛选与列表同源；{@code pageNo}/{@code pageSize} 指定导出当前页窗口（一页 N 条即导出 N 条）。
 * 关键字继承父类 {@code keyword}：处方号或点评业务号模糊（OR）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "导出点评记录请求", requiredProperties = {"pageNo", "pageSize", "exportColumns"})
public class ExportReviewsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 点评状态：PENDING/REVIEWED；空=不限。 */
    @Schema(description = "点评状态 code")
    private String statusEnum;

    /** 点评日起（yyyy-MM-dd）；空=不限。 */
    @Schema(description = "点评日起（yyyy-MM-dd）")
    private String reviewedDateFrom;

    /** 点评日止（yyyy-MM-dd）；空=不限。 */
    @Schema(description = "点评日止（yyyy-MM-dd）")
    private String reviewedDateTo;

    /** 是否合理；空=不限。 */
    @Schema(description = "是否合理")
    private Boolean reasonableFlag;

    /** 点评人主键；空=不限。 */
    @Schema(description = "点评人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewerId;

    /** 开方日起（yyyy-MM-dd）；与止成对；空=不限。 */
    @Schema(description = "开方日起（yyyy-MM-dd）")
    private String openedDateFrom;

    /** 开方日止（yyyy-MM-dd）；与起成对；空=不限。 */
    @Schema(description = "开方日止（yyyy-MM-dd）")
    private String openedDateTo;

    /**
     * 导出列清单（field 须落在 ExportRowDto {@code @Excel} 白名单；title 用工作表表头）。
     */
    @Schema(description = "导出列清单", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ExcelExportColumnDto> exportColumns;
}
