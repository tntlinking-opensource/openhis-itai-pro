/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 有效点评映射 DTO（IF-PRV-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "有效点评")
public class PrescriptionReviewActiveDto {

    /** 点评主键。 */
    @Schema(description = "点评主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 点评业务号（PRV 日序）。 */
    @Schema(description = "业务号")
    private String busNo;

    /** 点评状态 code。 */
    @Schema(description = "点评状态 code")
    private String statusEnum;

    /** 点评状态展示文案。 */
    @Schema(description = "点评状态文案")
    private String statusText;

    /** 是否合理。 */
    @Schema(description = "是否合理")
    private Boolean reasonableFlag;

    /** 是否合理展示文案。 */
    @Schema(description = "是否合理文案")
    private String reasonableText;

    /** 原因。 */
    @Schema(description = "原因")
    private String reason;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 点评人主键。 */
    @Schema(description = "点评人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewerId;

    /** 点评人姓名。 */
    @Schema(description = "点评人姓名")
    private String reviewerName;

    /** 点评时间（yyyy-MM-dd HH:mm:ss）。 */
    @Schema(description = "点评时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private String reviewedAt;
}
