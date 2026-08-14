/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dailysettlement.mapper;

import com.openhis.health.application.inpatient.pharmacy.dailysettlement.request.PreviewInpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.inpatient.pharmacy.dailysettlement.request.SubmitInpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.inpatient.pharmacy.dailysettlement.response.LoadInpatientPharmacyDailySettlementPageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.dailysettlement.response.SubmitInpatientPharmacyDailySettlementResponse;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.PreviewPharmacyCashierSettlementQuery;
import com.openhis.whale.core.application.dto.OptionDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院药房日结 Request/Command/Response 映射（AS-IP-PHA-006）。
 */
public interface InpatientPharmacyDailySettlementMapper {

    /**
     * 装配页初始化响应。
     *
     * @param defaultDateFrom  默认窗起
     * @param defaultDateTo    默认窗止
     * @param pharmacyOptions      药房下拉
     * @param practitionerOptions  药师岗发药人下拉
     * @return 初始化响应
     */
    LoadInpatientPharmacyDailySettlementPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        List<OptionDto> pharmacyOptions,
        List<OptionDto> practitionerOptions);

    /**
     * 预览请求转领域查询。
     *
     * @param request  预览请求
     * @param typeEnum 交账类型 code
     * @return 领域预览查询
     */
    PreviewPharmacyCashierSettlementQuery toPreviewQuery(
        PreviewInpatientPharmacyDailySettlementRequest request,
        String typeEnum);

    /**
     * 提交请求转领域命令。
     *
     * @param request              提交请求
     * @param typeEnum             交账类型 code
     * @param submitterId          提交人 Practitioner 主键
     * @param operatorId           操作人用户主键
     * @param selectedDispenseIds  勾选发药行
     * @param practitionerIds      窗内发药人快照
     * @param paymentCount         挂载条数
     * @param totalIncome          头表 total_income（日结利润：售价−进价）
     * @return 提交命令
     */
    SubmitCashierSettlementCommand toSubmitCommand(
        SubmitInpatientPharmacyDailySettlementRequest request,
        String typeEnum,
        Long submitterId,
        Long operatorId,
        List<Long> selectedDispenseIds,
        String practitionerIds,
        Integer paymentCount,
        java.math.BigDecimal totalIncome);

    /**
     * 装配提交响应。
     *
     * @param settlementId  交账头主键
     * @param busNo         单号
     * @param paymentCount  挂载条数
     * @return 提交响应
     */
    SubmitInpatientPharmacyDailySettlementResponse toSubmitResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount);
}
