/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import java.time.LocalDateTime;
import java.util.List;

import com.openhis.health.domain.share.constants.OutpatientRegistrationReportConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊挂号明细报表分页/汇总筛选（AS-RPT-007；仅 Encounter AMB）。
 *
 * <p>跨聚合收窄（患者关键字、接诊医生）由应用层预解析后写入 {@link #resolvedEncounterIds}：
 * 医生筛复用 {@code filterEncounterIdsByActiveAdmitterDoctor}
 * （活动接诊医生 ADMITTER；B.6.4）。可选 {@link #statusEnum} 单值收窄就诊状态。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationReportPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 挂号发生时刻起（含）；语义 coalesce(startTime, createdAt)。 */
    private LocalDateTime businessTimeFrom;

    /** 挂号发生时刻止（含）。 */
    private LocalDateTime businessTimeTo;

    /** 挂号科室（{@code organization_id}）。 */
    private Long organizationId;

    /** 挂号科室多选预解析；非 null 时 IN；空列表拒查。 */
    private List<Long> resolvedOrganizationIds;

    /**
     * 就诊主键收窄（患者关键字 / 接诊医生筛等）；非 null 时 IN；空列表拒查。
     */
    private List<Long> resolvedEncounterIds;

    /**
     * 汇总视角：{@link OutpatientRegistrationReportConstants#SUMMARY_DIMENSION_ORGANIZATION} 等。
     */
    private String summaryDimension;

    /**
     * 就诊状态单值筛选（{@code EncounterStatus.getCode()}）；空表示不按状态收窄。
     *
     * <p>明细/汇总/导出同筛；页脚挂/退统计与明细分页共用本字段。</p>
     */
    private String statusEnum;

    /**
     * 分页大小：缺省 {@link OutpatientRegistrationReportConstants#DEFAULT_PAGE_SIZE}，
     * 上限 {@link OutpatientRegistrationReportConstants#MAX_PAGE_SIZE}。
     *
     * @return 受上下界约束的页大小
     */
    @Override
    public int getBoundedPageSize() {
        Integer pageSizeValue = getPageSize();
        if (pageSizeValue == null || pageSizeValue < 1) {
            return OutpatientRegistrationReportConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSizeValue > OutpatientRegistrationReportConstants.MAX_PAGE_SIZE) {
            return OutpatientRegistrationReportConstants.MAX_PAGE_SIZE;
        }
        return pageSizeValue;
    }
}
