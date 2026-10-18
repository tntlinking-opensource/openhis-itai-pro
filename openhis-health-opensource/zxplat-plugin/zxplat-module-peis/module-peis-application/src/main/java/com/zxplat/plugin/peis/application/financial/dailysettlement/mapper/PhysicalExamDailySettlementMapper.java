/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.dailysettlement.mapper;

import com.zxplat.plugin.peis.application.financial.dailysettlement.dto.PhysicalExamDailySettlementLineDto;
import com.zxplat.plugin.peis.application.financial.dailysettlement.response.ListPhysicalExamDailySettlementResponse;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.encounter.entity.Encounter;

import java.util.List;
import java.util.Map;

/**
 * 体检日结清单应用层映射器。
 */
public interface PhysicalExamDailySettlementMapper {

    /**
     * 装配空结果（日期窗合法但无已结体检收费）。
     *
     * @param dateFrom 收费业务日起
     * @param dateTo   收费业务日止
     * @return 人次与合计为零的响应
     */
    ListPhysicalExamDailySettlementResponse toEmptyResponse(String dateFrom, String dateTo);

    /**
     * 装配一日清单明细与汇总。
     *
     * @param dateFrom          收费业务日起
     * @param dateTo            收费业务日止
     * @param chargeItems       已结体检收费行
     * @param encounterById     就诊主键 → 实体
     * @param departmentNames   科室主键 → 名称
     * @param ordererNames      开单人主键 → 姓名
     * @param patientNames      患者主键 → 姓名
     * @param itemLabels        收费项主键 → 项目名
     * @return 排序后的明细与人次、金额合计
     */
    ListPhysicalExamDailySettlementResponse toListResponse(
        String dateFrom,
        String dateTo,
        List<ChargeItem> chargeItems,
        Map<Long, Encounter> encounterById,
        Map<Long, String> departmentNames,
        Map<Long, String> ordererNames,
        Map<Long, String> patientNames,
        Map<Long, String> itemLabels);

    /**
     * 将已结收费行映射为清单明细（未排序）。
     *
     * @param chargeItems     已结体检收费行
     * @param encounterById   就诊主键 → 实体
     * @param departmentNames 科室主键 → 名称
     * @param ordererNames    开单人主键 → 姓名
     * @param patientNames    患者主键 → 姓名
     * @param itemLabels      收费项主键 → 项目名
     * @return 明细行
     */
    List<PhysicalExamDailySettlementLineDto> toLines(
        List<ChargeItem> chargeItems,
        Map<Long, Encounter> encounterById,
        Map<Long, String> departmentNames,
        Map<Long, String> ordererNames,
        Map<Long, String> patientNames,
        Map<Long, String> itemLabels);
}
