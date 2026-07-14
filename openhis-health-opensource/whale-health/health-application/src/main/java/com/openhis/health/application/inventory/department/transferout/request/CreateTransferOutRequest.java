/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.transferout.request;

import com.openhis.health.application.inventory.department.transferout.dto.TransferOutLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 新建科室调出单请求（IF-DEPT-TR-04）。
 *
 * <p>头级类型字段与明细行共同装配为领域 {@link com.openhis.health.domain.workflow.supplyrequest.command.CreateTransferCommand}；枚举类字段一律传持久化 {@code code}（B.10.8.1）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class CreateTransferOutRequest {

    /** 源科室药房。 */
    @Schema(description = "源科室药房", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 目的位置。 */
    @Schema(description = "目的位置", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 源位置类型 SupplyRequestType#getCode。 */
    @Schema(description = "源位置类型 SupplyRequestType#getCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceTypeEnum;

    /** 目的位置类型。 */
    @Schema(description = "目的位置类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String purposeTypeEnum;

    /** 供应类别 SupplyCategory#getCode。 */
    @Schema(description = "供应类别 SupplyCategory#getCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /**
     * 上游业务单号；本期调出单不支持手写关联科室请领（请领下游见药库出库 AS-INV-WHS-003）。
     * 非空时建单/改单将拒绝（DEPT_TR_002）。
     */
    @Schema(description = "上游单号（本期勿填请领单号）")
    /** 上游单号（本期勿填请领单号）。 */
    private String originalBusNo;

    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 预分配业务单号，空则领域生成。 */
    @Schema(description = "预分配业务单号，空则领域生成")
    private String busNo;

    /** 明细行。 */
    @Schema(description = "明细行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TransferOutLineDto> lines;
}
