/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 单据详情明细分页入参（IF-INV-DDC-{域}-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情明细分页 IF-INV-DDC-02", requiredProperties = {"pageNo", "pageSize", "domainScope"})
public class PageDocumentDetailsRequest extends AbstractHealthPageRequest {

    /**
     * 业务域：{@code WAREHOUSE} / {@code PHARMACY} / {@code DEPARTMENT}。
     */
    @Schema(description = "业务域 domainScope", requiredMode = Schema.RequiredMode.REQUIRED)
    private String domainScope;

    /**
     * 单据类型 {@code SupplyType#getCode()}；空或未传 = 查本域白名单全部类型。
     */
    @Schema(description = "单据类型 typeEnum；空则查本域全部")
    private String typeEnum;

    /** 源库位 sourceLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源库位 sourceLocationId")
    private Long sourceLocationId;

    /** 目的库位 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的库位 purposeLocationId")
    private Long purposeLocationId;

    /**
     * 供应商 supplierId；空则该维不过滤（BR-INV-DDC-004）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 审批时间起 approvalTimeStart。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间起 approvalTimeStart")
    private LocalDateTime approvalTimeStart;

    /** 审批时间止 approvalTimeEnd。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间止 approvalTimeEnd")
    private LocalDateTime approvalTimeEnd;

    /**
     * 项目分类 itemTable：{@code med_medication_definition}（药品）/
     * {@code adm_device_definition}（耗材）；空则不限。
     */
    @Schema(description = "项目分类 itemTable：med_medication_definition/adm_device_definition")
    private String itemTable;

    /** 品名/编码关键字 productKeyword。 */
    @Schema(description = "品名/编码关键字 productKeyword")
    private String productKeyword;

    /** 批号关键字 lotNumberKeyword。 */
    @Schema(description = "批号关键字 lotNumberKeyword")
    private String lotNumberKeyword;

    /** 单据号 busNo（模糊 LIKE）。 */
    @Schema(description = "单据号 busNo（模糊）")
    private String busNo;
}
