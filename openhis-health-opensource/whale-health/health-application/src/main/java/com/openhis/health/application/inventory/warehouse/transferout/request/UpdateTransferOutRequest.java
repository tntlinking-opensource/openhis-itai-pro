/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.transferout.request;

import com.openhis.health.application.inventory.warehouse.transferout.dto.TransferOutLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 修订药库调出单请求（IF-WHS-TR-05）。
 *
 * <p>语义上为「同号覆盖草稿」：{@link #busNo} 标识既有调出单；装配层塌缩为 {@link CreateTransferOutRequest} 后复用同一领域创建命令路径。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class UpdateTransferOutRequest {
    /** 业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 源药库。 */

    @Schema(description = "源药库", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 目的位置。 */
    @Schema(description = "目的位置", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 源位置类型。 */
    @Schema(description = "源位置类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceTypeEnum;

    /** 目的位置类型。 */
    @Schema(description = "目的位置类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String purposeTypeEnum;

    /** 供应类别。 */
    @Schema(description = "供应类别", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /**
     * 上游业务单号；本期调出单不支持手写关联药房请领（请领下游见药库出库 AS-INV-WHS-003）。
     * 非空时改单将拒绝（WHS_TR_002）。
     */
    @Schema(description = "上游单号（本期勿填请领单号）")
    /** 上游单号（本期勿填请领单号）。 */
    private String originalBusNo;

    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 明细行。 */
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TransferOutLineDto> lines;
}
