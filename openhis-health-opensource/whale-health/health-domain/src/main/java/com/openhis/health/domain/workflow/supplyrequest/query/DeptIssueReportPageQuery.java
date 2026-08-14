/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 科室领用/退领报表分页与汇总共用筛选（AS-RPT-003～006 科室侧；仅 SupplyRequest）。
 *
 * <p>跨聚合：领用科室组织、库位域收窄由应用层预解析为 {@code purposeLocationId} 集合写入本 Query。</p>
 */
@Getter
@Setter
public class DeptIssueReportPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 行项目表名：药品 {@link com.openhis.health.domain.share.constants.TableNameConstants#MED_MEDICATION_DEFINITION}
     * 或耗材 {@link com.openhis.health.domain.share.constants.TableNameConstants#ADM_DEVICE_DEFINITION}；必填。
     */
    private String itemTable;

    /** 业务时间起（含）；对齐同意/发生时间窗，空则不按下界。 */
    private LocalDateTime businessTimeFrom;

    /** 业务时间止（含）；空则不按上界。 */
    private LocalDateTime businessTimeTo;

    /** 单库位筛选；与 {@link #resolvedPurposeLocationIds} 互斥优先本字段。 */
    private Long purposeLocationId;

    /**
     * 门诊/住院相关科室药房预解析主键；非 null 时 {@code purpose_location_id IN}；空列表拒查。
     */
    private List<Long> resolvedPurposeLocationIds;

    /** 品规关键字预解析 item_id；非 null 时 IN；空列表拒查。 */
    private List<Long> resolvedItemIds;

    /**
     * 汇总维度（仅汇总 IF 使用）：{@link DeptIssueReportConstants#SUMMARY_DIMENSION_DEPARTMENT} 等。
     */
    private String summaryDimension;

    /**
     * 科室药房 → 领用科室组织主键；维度 DEPARTMENT 时由应用层预解析。
     */
    private Map<Long, Long> organizationIdByPurposeLocationId;

    /**
     * 领用科室组织主键；非 null 时按 {@code consuming_organization_id} 筛选
     *（空落库历史行由应用层先收窄 {@link #resolvedPurposeLocationIds}）。
     */
    private Long consumingOrganizationId;

    /**
     * 分页大小：缺省 {@link DeptIssueReportConstants#DEFAULT_PAGE_SIZE}，
     * 上限 {@link DeptIssueReportConstants#MAX_PAGE_SIZE}。
     *
     * @return 受上下界约束的页大小
     */
    @Override
    public int getBoundedPageSize() {
        Integer pageSizeValue = getPageSize();
        if (pageSizeValue == null || pageSizeValue < 1) {
            return DeptIssueReportConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSizeValue > DeptIssueReportConstants.MAX_PAGE_SIZE) {
            return DeptIssueReportConstants.MAX_PAGE_SIZE;
        }
        return pageSizeValue;
    }
}
