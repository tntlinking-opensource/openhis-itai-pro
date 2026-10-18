/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.settlelist.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 结算清单只读预览请求。
 *
 * <p>{@code caseRecordId} 与 {@code encounterId} 至少其一；均有时以 caseRecordId 为准。</p>
 */
@Getter
@Setter
public class LoadSettleListPreviewRequest {

    /** 病案主档主键。 */
    @Schema(description = "病案主档主键")
    private Long caseRecordId;

    /** 住院就诊主键（无主档时用于定位/幂等建档）。 */
    @Schema(description = "住院就诊主键")
    private Long encounterId;

    /** 费用性质契约 busNo；可空，后端从医保账户解析。 */
    @Schema(description = "费用性质契约编号")
    private String contractBusNo;
}
