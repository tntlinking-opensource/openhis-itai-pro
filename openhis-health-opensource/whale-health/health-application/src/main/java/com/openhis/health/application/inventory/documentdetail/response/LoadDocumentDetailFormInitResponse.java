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

    @Schema(description = "默认单据类型")
    /** 默认单据类型。 */
    private String defaultTypeEnum;

    @Schema(description = "源库位下拉")
    /** 源库位下拉。 */
    private List<OptionDto> sourceLocationOptions;

    @Schema(description = "目的库位下拉")
    /** 目的库位下拉。 */
    private List<OptionDto> purposeLocationOptions;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "默认审批时间起")
    /** 默认审批时间起。 */
    private LocalDateTime defaultApprovalTimeStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "默认审批时间止")
    /** 默认审批时间止。 */
    private LocalDateTime defaultApprovalTimeEnd;
}
