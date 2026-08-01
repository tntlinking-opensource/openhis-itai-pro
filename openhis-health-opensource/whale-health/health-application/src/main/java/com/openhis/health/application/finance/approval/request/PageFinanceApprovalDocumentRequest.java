/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.FinanceApprovalConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 分页查询财务审批单据请求（IF-APR-02）。
 *
 * <p>缺省 {@code typeEnum}/{@code statusEnum} 时<strong>禁止</strong>服务端写死进货或待审（BR-APR-006）。</p>
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页查询财务审批单据请求", requiredProperties = {"pageNo", "pageSize"})
public class PageFinanceApprovalDocumentRequest extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 单据类型：空/{@link FinanceApprovalConstants#TYPE_ALL}/供应码/
     * {@link FinanceApprovalConstants#TYPE_PRICE_ADJUSTMENT}。
     */
    @Schema(description = "单据类型：空/ALL/供应码/PRICE_ADJUSTMENT")
    private String typeEnum;

    /**
     * 审批状态：空=不限；待审时供应侧传 {@code APPROVAL}，调价侧由 Loader 映射 {@code pending}。
     */
    @Schema(description = "审批状态 code；空=不限")
    private String statusEnum;

    /** 单号模糊（仅 bus_no LIKE）。 */
    @Schema(description = "单号模糊")
    private String busNo;

    /** 库房位置主键（源或目的命中）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房位置主键")
    private Long locationId;

    /** 供应商主键（调价可忽略）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商主键")
    private Long supplierId;

    /** 品类表名：药品/耗材两档。 */
    @Schema(description = "品类表名 med_medication_definition / adm_device_definition")
    private String itemTable;

    /** 制单人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "制单人主键")
    private Long applicantId;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 制单时间窗起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单时间窗起")
    private LocalDateTime occurrenceTimeFrom;

    /** 制单时间窗止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单时间窗止")
    private LocalDateTime occurrenceTimeTo;

    /** 审批时间窗起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间窗起")
    private LocalDateTime approvalTimeFrom;

    /** 审批时间窗止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间窗止")
    private LocalDateTime approvalTimeTo;
}
