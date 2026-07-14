/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库入库单详情响应（IF-WHS-IN-03）。
 *
 * <p>头字段来自同号写模型首行；凡 {@code *Enum} 且为 {@link String} 的字段均为枚举
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()} 持久化值（B.10.8.1）。
 * {@link #rowVersion} 使用 {@link JsonInclude.Include#ALWAYS}，保证 JSON 中显式出现 {@code 0}。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库入库单详情 IF-WHS-IN-03")
public class GetInboundReceiptDetailResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 来源订货单号 originalBusNo。 */
    @Schema(description = "来源订货单号 originalBusNo")
    private String originalBusNo;

    /** 单据类型 typeEnum。 */
    @Schema(description = "单据类型 typeEnum")
    private String typeEnum;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private String statusEnum;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 源药库 sourceLocationId。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 目的药库 purposeLocationId。 */
    @Schema(description = "目的药库 purposeLocationId")
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

    /** 乐观锁 rowVersion。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "乐观锁 rowVersion")
    private Integer rowVersion;

    /** 明细行 lines。 */
    @Schema(description = "明细行 lines")
    private List<InboundReceiptDetailLineDto> lines;
}
