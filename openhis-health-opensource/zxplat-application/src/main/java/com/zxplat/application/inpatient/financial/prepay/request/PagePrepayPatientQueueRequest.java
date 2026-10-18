/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.prepay.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 预交金在院患者队列分页请求（IF-IP-PREP-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "预交金在院患者队列分页请求")
public class PagePrepayPatientQueueRequest extends AbstractHealthPageRequest {

    /** 入院日期筛选。 */
    @Schema(description = "入院日期筛选")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate admissionDate;

    /**
     * 在院范围：{@code IN_HOSPITAL} / {@code NOT_IN_HOSPITAL} / {@code ALL}；
     * 空或未知按 {@code ALL}（在院四态直接入队；不在院三态须有已收口预交金流水）。
     * {@code NOT_IN_HOSPITAL}：已结算待清床 / 已出院 / 取消登记（退院），且存在预交金流水才入队。
     */
    @Schema(description = "在院范围：IN_HOSPITAL / NOT_IN_HOSPITAL / ALL")
    private String stayScope;
}
