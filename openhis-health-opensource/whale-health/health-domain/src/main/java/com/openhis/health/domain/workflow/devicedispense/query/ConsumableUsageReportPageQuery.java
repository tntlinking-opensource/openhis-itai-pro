/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材使用报表患者侧分页/汇总筛选（AS-RPT-005/006；仅 DeviceDispense）。
 *
 * <p>门诊/住院域由应用层预解析 {@link #resolvedEncounterScope}；科室维汇总须预写
 * {@link #organizationIdByEncounterId}。</p>
 */
@Getter
@Setter
public class ConsumableUsageReportPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 发放业务时间起（含）；对齐 {@code dispense_time}。 */
    private LocalDateTime businessTimeFrom;

    /** 发放业务时间止（含）。 */
    private LocalDateTime businessTimeTo;

    /** 单库位筛选；与 {@link #resolvedLocationIds} 并存时优先本字段。 */
    private Long locationId;

    /**
     * 库位预解析集合；非 null 时 {@code location_id IN}；空列表拒查。
     */
    private List<Long> resolvedLocationIds;

    /** 品规预解析；非 null 时 {@code device_def_id IN}；空列表拒查。 */
    private List<Long> resolvedDeviceDefIds;

    /**
     * 就诊收窄（门诊 AMB / 住院 IMP 由应用层写入）；{@code null} 表示不按就诊 IN；
     * 空列表拒查。
     */
    private List<Long> resolvedEncounterScope;

    /**
     * 汇总维度：{@link ConsumableUsageReportConstants#SUMMARY_DIMENSION_PATIENT} 等。
     */
    private String summaryDimension;

    /**
     * 就诊 → 科室组织；维度 DEPARTMENT 时由应用层预解析。
     */
    private Map<Long, Long> organizationIdByEncounterId;

    /**
     * 分页大小：缺省 {@link ConsumableUsageReportConstants#DEFAULT_PAGE_SIZE}，
     * 上限 {@link ConsumableUsageReportConstants#MAX_PAGE_SIZE}。
     *
     * @return 受上下界约束的页大小
     */
    @Override
    public int getBoundedPageSize() {
        Integer pageSizeValue = getPageSize();
        if (pageSizeValue == null || pageSizeValue < 1) {
            return ConsumableUsageReportConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSizeValue > ConsumableUsageReportConstants.MAX_PAGE_SIZE) {
            return ConsumableUsageReportConstants.MAX_PAGE_SIZE;
        }
        return pageSizeValue;
    }
}
