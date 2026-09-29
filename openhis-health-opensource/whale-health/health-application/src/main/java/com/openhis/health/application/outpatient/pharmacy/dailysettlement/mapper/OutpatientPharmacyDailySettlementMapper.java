/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dailysettlement.mapper;

import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.PreviewOutpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.SubmitOutpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.LoadOutpatientPharmacyDailySettlementPageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.SubmitOutpatientPharmacyDailySettlementResponse;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.medication.medicationdispense.queries.PreviewPharmacyCashierSettlementQuery;
import com.openhis.whale.core.application.dto.OptionDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊药房日结 Request/Command/Response 映射（AS-OP-PHA-006）。
 */
public interface OutpatientPharmacyDailySettlementMapper {

    /**
     * 装配页初始化响应。
     *
     * @param defaultDateFrom      默认窗起
     * @param defaultDateTo        默认窗止
     * @param pharmacyOptions      药房下拉
     * @param practitionerOptions  药师岗发药人下拉
     * @param keyDrugTypeOptions      重点药类型下拉（品规销售排名筛选）
     * @param medicinePropertyOptions 药品性质下拉（麻/精，品规销售排名筛选）
     * @return 初始化响应
     */
    LoadOutpatientPharmacyDailySettlementPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        List<OptionDto> pharmacyOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> keyDrugTypeOptions,
        List<OptionDto> medicinePropertyOptions);

    /**
     * 预览请求转领域查询。
     *
     * @param request  预览请求
     * @param typeEnum 交账类型 code
     * @return 领域预览查询
     */
    PreviewPharmacyCashierSettlementQuery toPreviewQuery(
        PreviewOutpatientPharmacyDailySettlementRequest request,
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
     * @param totalIncome          头表 total_income（售价合计，与大类售价之和同口径）
     * @return 提交命令
     */
    SubmitCashierSettlementCommand toSubmitCommand(
        SubmitOutpatientPharmacyDailySettlementRequest request,
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
    SubmitOutpatientPharmacyDailySettlementResponse toSubmitResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount);
}
