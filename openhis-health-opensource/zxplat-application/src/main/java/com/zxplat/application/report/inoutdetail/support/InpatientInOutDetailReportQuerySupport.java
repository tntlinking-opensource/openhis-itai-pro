/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.inoutdetail.support;

import com.zxplat.domain.administration.encounter.queries.InpatientInOutDetailReportPageQuery;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.share.constants.InpatientInOutDetailReportConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 住院患者入出明细报表筛选校验与领域 Query scope 预解析（AS-RPT-010）。
 *
 * <p>入院科室 → 病区 id 经 {@link LocationQueryService#listWardsByOrganization} 预写
 * {@code resolvedAdmissionWardLocationIds}（B.6.4）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientInOutDetailReportQuerySupport {

    /** 病区挂靠科室读侧。 */
    private final LocationQueryService locationQueryService;

    /**
     * 校验日期闭区间与入出状态白名单。
     *
     * @param dateFrom 窗起
     * @param dateTo 窗止
     * @param movementStatus 入出状态
     * @throws BusinessException EX-RPT-IOE-102
     */
    public void assertDateAndStatus(LocalDateTime dateFrom, LocalDateTime dateTo, String movementStatus) {
        // Gate：日期必填
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_102,
                "请选择有效查询日期");
        }
        // Gate：止不得早于起
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_102,
                "结束时间不能早于开始时间");
        }
        String status = StringUtils.trimToNull(movementStatus);
        // Gate：状态须为全部/入院/出院
        if (status == null
            || (!InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ALL.equals(status)
                && !InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ADMISSION.equals(status)
                && !InpatientInOutDetailReportConstants.MOVEMENT_STATUS_DISCHARGE.equals(status))) {
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_102,
                "请选择有效的入出状态");
        }
    }

    /**
     * 校验分页窗口（1-based 页码、页大小上下界）。
     *
     * @param pageNo 页码
     * @param pageSize 页大小
     * @throws BusinessException EX-RPT-IOE-102
     */
    public void assertPageWindow(Integer pageNo, Integer pageSize) {
        // Gate：页码须 ≥1
        if (pageNo == null || pageNo < 1) {
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_102,
                "页码不合法");
        }
        // Gate：页大小须在允许区间
        if (pageSize == null
            || pageSize < 1
            || pageSize > InpatientInOutDetailReportConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(
                InpatientInOutDetailReportConstants.EX_RPT_IOE_102,
                "页大小不合法");
        }
    }

    /**
     * 填充领域 Query 的时间窗、状态、科室/病区及入院病区 scope。
     *
     * @param query 领域 Query（已含分页字段时可再覆盖）
     * @param dateFrom 窗起
     * @param dateTo 窗止
     * @param movementStatus 入出状态
     * @param organizationId 入院科室；可空
     * @param wardLocationId 入院病区；可空
     */
    public void prepareQueryScopes(
        InpatientInOutDetailReportPageQuery query,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        String movementStatus,
        Long organizationId,
        Long wardLocationId) {
        query.setDateFrom(dateFrom);
        query.setDateTo(dateTo);
        query.setMovementStatus(StringUtils.trimToNull(movementStatus));
        query.setOrganizationId(organizationId);
        query.setWardLocationId(wardLocationId);
        // Pre：已选入院科室 → 预写该科下启用病区 id（空列表表示该科无病区，领域拒查）
        if (organizationId != null) {
            List<Location> wards = this.locationQueryService.listWardsByOrganization(organizationId);
            List<Long> wardIds = new ArrayList<>();
            for (Location ward : wards) {
                // 跳过空行，仅收集有效病区主键
                if (ward != null && ward.getId() != null) {
                    wardIds.add(ward.getId());
                }
            }
            query.setResolvedAdmissionWardLocationIds(wardIds);
        } else {
            // 未选科室：不按病区 scope 收窄
            query.setResolvedAdmissionWardLocationIds(null);
        }
    }
}
