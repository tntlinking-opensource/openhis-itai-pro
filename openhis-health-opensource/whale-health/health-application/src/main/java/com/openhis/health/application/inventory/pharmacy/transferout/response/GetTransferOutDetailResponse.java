/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.transferout.response;

import com.openhis.health.application.inventory.pharmacy.transferout.dto.TransferOutDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药房调出单详情响应（IF-PHA-TR-03）。
 *
 * <p>头字段取自同号多行写模型的首行约定列；类型与状态字段为持久化 {@code code}。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房调出单详情 IF-PHA-TR-03")
public class GetTransferOutDetailResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 类型 PHARMACY_TRANSFER_OUT。 */
    @Schema(description = "类型 PHARMACY_TRANSFER_OUT")
    private String typeEnum;

    /** 状态。 */
    @Schema(description = "状态")
    private String statusEnum;

    /** 源位置主键。 */
    @Schema(description = "源位置主键")
    private Long sourceLocationId;

    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    /** 源位置类型。 */
    @Schema(description = "源位置类型")
    private String sourceTypeEnum;

    /** 目的位置类型。 */
    @Schema(description = "目的位置类型")
    private String purposeTypeEnum;

    /** 供应类别。 */
    @Schema(description = "供应类别")
    private String categoryEnum;

    /** 关联上游单号。 */
    @Schema(description = "关联上游单号")
    private String originalBusNo;

    /** 明细行。 */
    @Schema(description = "明细行")
    private List<TransferOutDetailLineDto> lines;
}
