/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.queries;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 本地上报流水列表查询条件（多条件 AND）。
 */
@Data
@Accessors(chain = true)
public class ListLocalInvUploadRecordsQuery {

    /** 医保契约业务编号；空则不限契约。 */
    private String contractBusNo;

    /** 来源业务类型（如 DISPENSE）；空则不限。 */
    private String sourceType;

    /** 来源业务主键；空则不限。 */
    private Long sourceId;

    /** 平台交易号（3501–3506）；空则不限。 */
    private String infno;

    /** 定点批次流水号（业务 busNo）；空则不限。 */
    private String fixmedinsBchno;

    /** 上报状态（如 FAILED、PENDING）；空则不限。选 SKIPPED 时匹配 SKIPPED* 前缀。 */
    private String uploadStatus;

    /** 创建时间起（含）；空则不限。 */
    private LocalDateTime createdFrom;

    /** 创建时间止（含）；空则不限。 */
    private LocalDateTime createdTo;
}
