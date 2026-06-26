/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.repository;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 追溯码差异比对用的本地流水行（追溯码 + 上报主表字段）。
 */
@Data
@Accessors(chain = true)
public class InvUploadTraceCompareRow {

    /** 追溯码明细主键。 */
    private Long traceId;

    /** 追溯码。 */
    private String drugTracCodg;

    /** 上报流水主键。 */
    private Long uploadId;

    /** 契约 bus_no。 */
    private String contractBusNo;

    /** 接口编号 infno。 */
    private String infno;

    /** 定点批次流水号。 */
    private String fixmedinsBchno;

    /** 医保目录编码。 */
    private String medListCodg;

    /** 上报状态。 */
    private String uploadStatus;

    /** 上报时间。 */
    private LocalDateTime createdAt;
}
