/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.support;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量追溯码解析入参（**AS-IP-NUR-015 · IF-IP-TRC-03**；与门诊追溯弹窗同族）。
 *
 * <p>module.common 中性契约（B.6.6 §2.2.1 #2）：供住院护士站与门诊药房等场景共用；
 * AppService/Assembler 负责装配，{@link PharmacyTraceCodeResolver} 只读匹配。</p>
 */
@Getter
@Setter
public class ResolveTraceCodesQuery {

    /** 规范化后的追溯码列表。 */
    private List<String> traceCodes;

    /** 当前患者待分配汇总组快照。 */
    private List<GroupSnapshot> groups;

    /**
     * 码池绑定：追溯码在汇总单维度归属的药品（由 SupplyDelivery + SupplyRequest 装配）。
     */
    private List<TraceCodePoolBinding> poolBindings;

    /**
     * 汇总组快照（药品 + 批号匹配键）。
     */
    @Getter
    @Setter
    public static class GroupSnapshot {

        /** 稳定组键 {@code summaryNo:medicationId:lotNumber}。 */
        private String groupKey;

        /** 汇总单号。 */
        private String summaryNo;

        /** 药品定义主键。 */
        private Long medicationId;

        /** 批号。 */
        private String lotNumber;
    }

    /**
     * 汇总码池内追溯码与药品的绑定关系。
     */
    @Getter
    @Setter
    public static class TraceCodePoolBinding {

        /** 追溯码字符串。 */
        private String traceCode;

        /** 所属汇总单号。 */
        private String summaryNo;

        /** 码池侧药品定义主键（SupplyRequest.itemId）。 */
        private Long medicationId;

        /** 主表批号（IF-TRC-21 enrich，可选；用于同药多批唯一定位）。 */
        private String lotNumber;
    }
}
