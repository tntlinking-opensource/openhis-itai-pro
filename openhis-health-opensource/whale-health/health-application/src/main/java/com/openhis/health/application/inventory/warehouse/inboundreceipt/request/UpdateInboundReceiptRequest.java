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
 * 编辑药库入库单请求（IF-WHS-IN-05）。
 *
 * <p>枚举语义字段与 {@link CreateInboundReceiptRequest} 相同（B.10.8.1）。
 * {@link #lines} 为替换后全量明细；{@link #rowVersion} 须与详情接口返回的乐观锁版本一致。
 * {@link #originalBusNo} 可空；若传入须与单据快照一致且不可变更来源订货单。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "编辑药库入库单 IF-WHS-IN-05")
public class UpdateInboundReceiptRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 来源订货业务单号 originalBusNo。 */
    @Schema(description = "来源订货业务单号 originalBusNo")
    private String originalBusNo;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 源药库主键。 */
    @Schema(description = "源药库主键")
    private Long sourceLocationId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 目的药库主键。 */
    @Schema(description = "目的药库主键")
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
    /** 供应商主键。 */
    @Schema(description = "供应商主键")
    private Long supplierId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /**
     * 采购发票号（同单共享，落库 {@code invoice_no}）。
     * <p>选填；空白时 Mapper 落库占位 {@code 0}（库 DEFAULT / 医保上报同口径）。</p>
     */
    @Schema(description = "采购发票号 invoiceNo")
    private String invoiceNo;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<InboundReceiptLineDto> lines;

    /** 乐观锁 rowVersion。 */
    @Schema(description = "乐观锁 rowVersion")
    private Long rowVersion;
}
