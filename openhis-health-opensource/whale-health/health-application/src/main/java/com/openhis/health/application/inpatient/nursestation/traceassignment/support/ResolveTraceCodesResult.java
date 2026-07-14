/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.support;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量追溯码解析结果（**AS-IP-NUR-015 · IF-IP-TRC-03**；只读预览，不落库）。
 *
 * <p>module.common 中性出参；由 AppService Mapper 映射为 application Response DTO。</p>
 */
@Getter
@Setter
public class ResolveTraceCodesResult {

    /** 按汇总组聚合的命中码列表。 */
    private List<GroupMatch> matches;

    /** 无法匹配或校验失败的码明细。 */
    private List<FailItem> failItems;

    /**
     * 单汇总组匹配预览。
     */
    @Getter
    @Setter
    public static class GroupMatch {

        /** 汇总组键。 */
        private String groupKey;

        /** 命中追溯码。 */
        private List<String> matchedTraceCodes;

        /** 药品定义主键。 */
        private Long medicationId;

        /** 批号。 */
        private String lotNumber;
    }

    /**
     * 单码解析失败明细。
     */
    @Getter
    @Setter
    public static class FailItem {

        /** 原追溯码。 */
        private String traceCode;

        /** 失败原因码（INVALID / NOT_IN_POOL / NOT_MATCHED / OVER_LIMIT）。 */
        private String reasonCode;
    }
}
