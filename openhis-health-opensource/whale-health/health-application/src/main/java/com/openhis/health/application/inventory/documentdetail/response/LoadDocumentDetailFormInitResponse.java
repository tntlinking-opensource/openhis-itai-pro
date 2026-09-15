/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单据详情 init 出参（IF-INV-DDC-{域}-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情初始化 IF-INV-DDC-01")
public class LoadDocumentDetailFormInitResponse {

    @Schema(description = "本域单据类型下拉")
    /** 本域单据类型下拉。 */
    private List<OptionDto> typeEnumOptions;

    /**
     * 建议默认单据类型（仅供前端初始选中回填；后端查询未传 {@code typeEnum} 时查本域全部，不强制此值）。
     */
    @Schema(description = "建议默认单据类型（前端回填用）")
    private String defaultTypeEnum;

    @Schema(description = "源库位下拉")
    /** 源库位下拉。 */
    private List<OptionDto> sourceLocationOptions;

    @Schema(description = "目的库位下拉")
    /** 目的库位下拉。 */
    private List<OptionDto> purposeLocationOptions;

    @Schema(description = "供应商下拉")
    /** 供应商下拉（租户内全部有效供应商；筛选用）。 */
    private List<OptionDto> supplierOptions;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "默认审批时间起")
    /** 默认审批时间起。 */
    private LocalDateTime defaultApprovalTimeStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "默认审批时间止")
    /** 默认审批时间止。 */
    private LocalDateTime defaultApprovalTimeEnd;

    /**
     * 汇总维度下拉；本期仅「按品规」（{@code ITEM}），供汇总 Tab 回填。
     */
    @Schema(description = "汇总维度下拉（本期仅按品规）")
    private List<OptionDto> summaryDimensionOptions;
}
