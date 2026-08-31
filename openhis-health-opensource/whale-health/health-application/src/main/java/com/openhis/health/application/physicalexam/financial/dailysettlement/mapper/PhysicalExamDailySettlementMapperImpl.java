/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.dailysettlement.mapper;

import com.openhis.health.application.physicalexam.financial.dailysettlement.dto.PhysicalExamDailySettlementLineDto;
import com.openhis.health.application.physicalexam.financial.dailysettlement.response.ListPhysicalExamDailySettlementResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.share.constants.PhysicalExamChargeConstants;
import com.openhis.health.domain.share.constants.PhysicalExamDailySettlementConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 体检日结清单应用层映射器实现。
 */
@Component
public class PhysicalExamDailySettlementMapperImpl implements PhysicalExamDailySettlementMapper {

    /**
     * 装配空结果（日期窗合法但无已结体检收费）。
     *
     * @param dateFrom 收费业务日起
     * @param dateTo   收费业务日止
     * @return 人次与合计为零的响应
     */
    @Override
    public ListPhysicalExamDailySettlementResponse toEmptyResponse(String dateFrom, String dateTo) {
        return this.buildResponse(dateFrom, dateTo, 0, BigDecimal.ZERO, new ArrayList<>());
    }

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
    @Override
    public ListPhysicalExamDailySettlementResponse toListResponse(
        String dateFrom,
        String dateTo,
        List<ChargeItem> chargeItems,
        Map<Long, Encounter> encounterById,
        Map<Long, String> departmentNames,
        Map<Long, String> ordererNames,
        Map<Long, String> patientNames,
        Map<Long, String> itemLabels) {
        List<PhysicalExamDailySettlementLineDto> lines = this.toLines(
            chargeItems, encounterById, departmentNames, ordererNames, patientNames, itemLabels);
        this.sortLines(lines);
        Set<Long> encounterIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PhysicalExamDailySettlementLineDto line : lines) {
            if (line.getEncounterId() != null) {
                encounterIds.add(line.getEncounterId());
            }
            if (line.getAmount() != null) {
                total = total.add(line.getAmount());
            }
        }
        return this.buildResponse(
            dateFrom, dateTo, encounterIds.size(), MonetaryAmountSupport.scaleSettle(total), lines);
    }

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
    @Override
    public List<PhysicalExamDailySettlementLineDto> toLines(
        List<ChargeItem> chargeItems,
        Map<Long, Encounter> encounterById,
        Map<Long, String> departmentNames,
        Map<Long, String> ordererNames,
        Map<Long, String> patientNames,
        Map<Long, String> itemLabels) {
        List<PhysicalExamDailySettlementLineDto> lines = new ArrayList<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return lines;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            Encounter encounter = null;
            if (encounterById != null && chargeItem.getEncounterId() != null) {
                encounter = encounterById.get(chargeItem.getEncounterId());
            }
            PhysicalExamDailySettlementLineDto line = new PhysicalExamDailySettlementLineDto();
            line.setChargeItemId(chargeItem.getId());
            line.setEncounterId(chargeItem.getEncounterId());
            line.setPatientId(chargeItem.getPatientId());
            line.setDepartmentName(this.resolveDepartmentName(chargeItem, encounter, departmentNames));
            line.setOrdererName(this.resolveOrdererName(chargeItem, encounter, ordererNames));
            line.setPatientName(this.resolvePatientName(chargeItem.getPatientId(), patientNames));
            line.setItemName(this.resolveItemName(chargeItem.getId(), itemLabels));
            line.setAmount(chargeItem.getTotalPrice());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 组装响应头：查询时间、人次文案与合计。
     *
     * @param dateFrom   收费业务日起
     * @param dateTo     收费业务日止
     * @param visitCount 去重就诊数
     * @param totalAmount 结算刻度合计
     * @param lines      明细
     * @return 响应
     */
    private ListPhysicalExamDailySettlementResponse buildResponse(
        String dateFrom,
        String dateTo,
        int visitCount,
        BigDecimal totalAmount,
        List<PhysicalExamDailySettlementLineDto> lines) {
        ListPhysicalExamDailySettlementResponse response = new ListPhysicalExamDailySettlementResponse();
        String fromText = dateFrom;
        if (StringUtils.isEmpty(fromText)) {
            fromText = "";
        }
        String toText = dateTo;
        if (StringUtils.isEmpty(toText)) {
            toText = "";
        }
        response.setQueryTimeText(
            fromText + PhysicalExamDailySettlementConstants.DATE_RANGE_JOIN + toText);
        response.setVisitCount(visitCount);
        response.setVisitCountText(visitCount + PhysicalExamDailySettlementConstants.VISIT_COUNT_SUFFIX);
        response.setTotalAmount(totalAmount);
        response.setLines(lines);
        return response;
    }

    /**
     * 解析科室名：优先账单开立科室，其次就诊机构。
     *
     * @param chargeItem      收费行
     * @param encounter       就诊
     * @param departmentNames 科室主键 → 名称
     * @return 科室展示名
     */
    private String resolveDepartmentName(
        ChargeItem chargeItem,
        Encounter encounter,
        Map<Long, String> departmentNames) {
        Long orgId = chargeItem.getRequestingOrgId();
        if (orgId == null && encounter != null) {
            orgId = encounter.getOrganizationId();
        }
        String name = this.lookupName(orgId, departmentNames);
        if (StringUtils.isNotEmpty(name)) {
            return name;
        }
        return PhysicalExamDailySettlementConstants.EMPTY_DEPARTMENT_NAME;
    }

    /**
     * 解析开单人：优先就诊登记人，其次账单收费人。
     *
     * @param chargeItem   收费行
     * @param encounter    就诊
     * @param ordererNames 人员主键 → 姓名
     * @return 开单人展示名
     */
    private String resolveOrdererName(
        ChargeItem chargeItem,
        Encounter encounter,
        Map<Long, String> ordererNames) {
        Long ordererId = null;
        if (encounter != null) {
            ordererId = encounter.getRegistrarId();
        }
        if (ordererId == null) {
            ordererId = chargeItem.getEntererId();
        }
        String name = this.lookupName(ordererId, ordererNames);
        if (StringUtils.isNotEmpty(name)) {
            return name;
        }
        return PhysicalExamDailySettlementConstants.EMPTY_ORDERER_NAME;
    }

    /**
     * 解析病人姓名。
     *
     * @param patientId    患者主键
     * @param patientNames 患者主键 → 姓名
     * @return 病人展示名
     */
    private String resolvePatientName(Long patientId, Map<Long, String> patientNames) {
        String name = this.lookupName(patientId, patientNames);
        if (StringUtils.isNotEmpty(name)) {
            return name;
        }
        return PhysicalExamDailySettlementConstants.EMPTY_PATIENT_NAME;
    }

    /**
     * 解析项目名称。
     *
     * @param chargeItemId 收费项主键
     * @param itemLabels   收费项主键 → 项目名
     * @return 项目展示名
     */
    private String resolveItemName(Long chargeItemId, Map<Long, String> itemLabels) {
        String name = this.lookupName(chargeItemId, itemLabels);
        if (StringUtils.isNotEmpty(name)) {
            return name;
        }
        return PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
    }

    /**
     * 从映射表取展示名。
     *
     * @param id    主键
     * @param names 主键 → 名称
     * @return 名称；未命中为空
     */
    private String lookupName(Long id, Map<Long, String> names) {
        if (id == null || names == null) {
            return null;
        }
        return names.get(id);
    }

    /**
     * 按科室、开单人、病人、项目排序，便于前端合并单元格。
     *
     * @param lines 明细
     */
    private void sortLines(List<PhysicalExamDailySettlementLineDto> lines) {
        lines.sort((left, right) -> {
            int departmentCompare = this.compareText(left.getDepartmentName(), right.getDepartmentName());
            if (departmentCompare != 0) {
                return departmentCompare;
            }
            int ordererCompare = this.compareText(left.getOrdererName(), right.getOrdererName());
            if (ordererCompare != 0) {
                return ordererCompare;
            }
            int patientCompare = this.compareText(left.getPatientName(), right.getPatientName());
            if (patientCompare != 0) {
                return patientCompare;
            }
            return this.compareText(left.getItemName(), right.getItemName());
        });
    }

    /**
     * 空安全文本比较。
     *
     * @param left  左值
     * @param right 右值
     * @return 比较结果
     */
    private int compareText(String left, String right) {
        String leftText = left;
        if (leftText == null) {
            leftText = "";
        }
        String rightText = right;
        if (rightText == null) {
            rightText = "";
        }
        return leftText.compareTo(rightText);
    }
}
