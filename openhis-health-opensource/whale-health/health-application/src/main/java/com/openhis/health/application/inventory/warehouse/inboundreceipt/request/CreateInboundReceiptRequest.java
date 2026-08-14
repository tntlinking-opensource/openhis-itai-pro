/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 新建药库入库单请求（IF-WHS-IN-04）。
 *
 * <p>枚举语义字段须传各业务枚举的 {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 字符串（B.10.8.1）。
 * {@link #lines} 须非空且至少一行；源/目的位置须通过应用层药库白名单门禁。
 * {@link #originalBusNo} <strong>可选</strong>：空白为手工直建；非空须引用已同意的药库订货单。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建药库入库单 IF-WHS-IN-04")
public class CreateInboundReceiptRequest {

    /** 来源订货业务单号；可空（手工直建），非空须为已同意药库订货单。 */
    @Schema(description = "来源订货业务单号；可空（手工直建）")
    private String originalBusNo;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 源药库主键 sourceLocationId。 */
    @Schema(description = "源药库主键 sourceLocationId")
    private Long sourceLocationId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 目的药库主键 purposeLocationId。 */
    @Schema(description = "目的药库主键 purposeLocationId")
    private Long purposeLocationId;

    /** 源位置类型 sourceTypeEnum。 */
    @Schema(description = "源位置类型 sourceTypeEnum")
    private String sourceTypeEnum;

    /** 目的位置类型 purposeTypeEnum。 */
    @Schema(description = "目的位置类型 purposeTypeEnum")
    private String purposeTypeEnum;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 供应商主键 supplierId。 */
    @Schema(description = "供应商主键 supplierId")
    private Long supplierId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /**
     * 采购发票号（同单共享，落库 {@code wkf_supply_request.invoice_no}；进销存 {@code purc_invo_no}）。
     */
    @Schema(description = "采购发票号 invoiceNo")
    private String invoiceNo;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<InboundReceiptLineDto> lines;
}
