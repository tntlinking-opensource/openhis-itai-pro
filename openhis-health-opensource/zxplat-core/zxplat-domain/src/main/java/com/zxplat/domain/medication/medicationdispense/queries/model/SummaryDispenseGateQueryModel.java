/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 汇总单号维度发放表门控读模型：是否已发药仅以 {@code med_medication_dispense} 终态为准。
 */
@Getter
@Setter
public class SummaryDispenseGateQueryModel {

    /** 汇总单号。 */
    private String summaryNo;

    /** 同号发放行是否全部为 {@link com.zxplat.domain.share.enums.DispenseStatus#COMPLETED}。 */
    private boolean allCompleted;

    /** 同号发放行是否全部为 {@link com.zxplat.domain.share.enums.DispenseStatus#SUMMARIZED}（待发核发中间态）。 */
    private boolean allSummarized;

    /** 同号发放行最大发药时刻（仅已发药时有值）。 */
    private LocalDateTime maxDispenseTime;
}
