/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import java.time.LocalDateTime;
import java.util.List;

import com.openhis.health.domain.share.constants.InpatientInOutDetailReportConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院患者入出明细报表分页/人次筛选（AS-RPT-010；仅 Encounter IMP）。
 *
 * <p>入院科室筛选：转科确认后主档 {@code organization_id} 会切到目标科；应用层须经 Location QS
 * 预解析该入院科室下病区 id 写入 {@link #resolvedAdmissionWardLocationIds}（B.6.4），
 * 本聚合按「最早 WARD」命中；未分床就诊另以 {@link #organizationId} 匹配主档。</p>
 */
@Getter
@Setter
public class InpatientInOutDetailReportPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 业务时间窗起（含）。 */
    private LocalDateTime dateFrom;

    /** 业务时间窗止（含）。 */
    private LocalDateTime dateTo;

    /**
     * 入出状态：{@link InpatientInOutDetailReportConstants#MOVEMENT_STATUS_ALL} 等。
     * 人次计数忽略本字段，始终按窗内入院/出院两侧统计。
     */
    private String movementStatus;

    /**
     * 入院科室；与 {@link #resolvedAdmissionWardLocationIds} 联用（未分床命中主档）。
     */
    private Long organizationId;

    /** 入院病区位置主键（最早 WARD 的 location_id）。 */
    private Long wardLocationId;

    /**
     * 入院科室下病区位置 id 预解析；非 null 时按最早 WARD ∈ 集合收窄；空列表拒查。
     */
    private List<Long> resolvedAdmissionWardLocationIds;

    /**
     * 就诊主键额外收窄；非 null 时 IN；空列表拒查。
     */
    private List<Long> resolvedEncounterIds;

    /**
     * 分页大小：缺省 {@link InpatientInOutDetailReportConstants#DEFAULT_PAGE_SIZE}，
     * 上限 {@link InpatientInOutDetailReportConstants#MAX_PAGE_SIZE}。
     *
     * @return 受上下界约束的页大小
     */
    @Override
    public int getBoundedPageSize() {
        Integer pageSizeValue = getPageSize();
        if (pageSizeValue == null || pageSizeValue < 1) {
            return InpatientInOutDetailReportConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSizeValue > InpatientInOutDetailReportConstants.MAX_PAGE_SIZE) {
            return InpatientInOutDetailReportConstants.MAX_PAGE_SIZE;
        }
        return pageSizeValue;
    }
}
