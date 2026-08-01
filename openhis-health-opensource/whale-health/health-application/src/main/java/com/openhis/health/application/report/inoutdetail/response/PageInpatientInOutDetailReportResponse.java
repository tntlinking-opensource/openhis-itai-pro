/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.inoutdetail.response;

import com.openhis.health.application.report.inoutdetail.dto.InpatientInOutDetailFooterDto;
import com.openhis.health.application.report.inoutdetail.dto.InpatientInOutDetailRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 住院患者入出明细报表明细分页响应（IF-RPT-IOE-02）。
 *
 * <p>分页元数据走 {@link PagedResult}（{@code totalCount} 1-based {@code pageNo}）；另附人次页脚与转科列开关。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院患者入出明细报表明细分页响应")
public class PageInpatientInOutDetailReportResponse extends PagedResult<InpatientInOutDetailRowDto> {

    /**
     * 当前页是否存在已确认转科行（前端据此动态展示「转往」列）。
     */
    @Schema(description = "是否展示转科列")
    private boolean showTransferColumn;

    /** 入院/出院人次页脚（同筛选全窗，忽略分页与 movementStatus）。 */
    @Schema(description = "人次页脚")
    private InpatientInOutDetailFooterDto footer;
}
