/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交账审批列表分页请求（IF-CS-APR-02）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "交账审批列表分页请求", requiredProperties = {"pageNo", "pageSize"})
public class PageCashierSettlementApprovalsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 费用/药房大类：PAYMENT / PHARMACY；空表示不限。 */
    @Schema(description = "费用/药房大类：PAYMENT / PHARMACY")
    private String categoryEnum;

    /** 交账类型 code；空表示不限（与 categoryEnum 二选一或组合，typeEnum 优先）。 */
    @Schema(description = "交账类型 code")
    private String typeEnum;

    /** 交账类型 code 集合；空表示不限。 */
    @Schema(description = "交账类型 code 集合")
    private List<String> typeEnums;

    /** 审批状态 code；空表示不限。 */
    @Schema(description = "审批状态 code")
    private String statusEnum;

    /** 审批状态 code 集合；空表示不限。 */
    @Schema(description = "审批状态 code 集合")
    private List<String> statusEnums;

    /** 交账提交时间起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时间起")
    private LocalDateTime submittedFrom;

    /** 交账提交时间止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时间止")
    private LocalDateTime submittedTo;

    /** 交账科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账科室主键")
    private Long orgId;

    /** 交账药房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账药房主键")
    private Long locationId;

    /** 交账单号模糊关键字。 */
    @Schema(description = "交账单号模糊关键字")
    private String busNoKeyword;
}
