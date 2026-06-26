/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.dailysettlement.mapper;

import com.openhis.health.application.inpatient.financial.dailysettlement.request.PreviewInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.SubmitInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.LoadInpatientDailySettlementPageInitResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.PreviewInpatientDailySettlementResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.SubmitInpatientDailySettlementResponse;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 住院日结交账 Request/Command/Response 映射（AS-IP-FIN-005-SDD §2.4）。
 */
public interface InpatientDailySettlementMapper {

    /**
     * 装配住院日结页初始化响应。
     *
     * @param defaultDate              默认结账日
     * @param financialDelegateEnabled 是否允许财务代交账
     * @param expensePropertyOptions   费用性质下拉
     * @param cashierOptions           经办人下拉
     * @param currentCashierId         当前经办人
     * @return 初始化响应
     */
    LoadInpatientDailySettlementPageInitResponse toLoadInpatientDailySettlementPageInitResponse(
        LocalDate defaultDate,
        boolean financialDelegateEnabled,
        List<OptionDto> expensePropertyOptions,
        List<OptionDto> cashierOptions,
        Long currentCashierId);

    /**
     * 预览请求转领域查询条件。
     *
     * @param request   HTTP 请求
     * @param typeEnum  交账类型 code
     * @param cashierId 已解析经办人
     * @return 领域预览查询
     */
    PreviewCashierSettlementQuery toPreviewCashierSettlementQuery(
        PreviewInpatientDailySettlementRequest request,
        String typeEnum,
        Long cashierId);

    /**
     * 预览结果与 enrichment 装配查询响应。
     *
     * @param previewModel          候选 payment 预览
     * @param paymentModels         payment 读模型
     * @param recDetailsByPaymentId 子明细
     * @param encounterByEncounterId  就诊上下文
     * @param expenseProperty       费用性质展示过滤
     * @param insuranceByContractBusNo 合同 bus_no → 是否医保
     * @param finTypeOptions           fin_type_code 字典顺序
     * @return 查询响应
     */
    PreviewInpatientDailySettlementResponse toPreviewInpatientDailySettlementResponse(
        PreviewCashierSettlementQueryModel previewModel,
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId,
        String expenseProperty,
        Map<String, Boolean> insuranceByContractBusNo,
        List<OptionDto> finTypeOptions);

    /**
     * 提交请求转领域命令。
     *
     * @param request    HTTP 请求
     * @param typeEnum   交账类型
     * @param cashierId  经办人
     * @param operatorId 操作员
     * @return 提交命令
     */
    SubmitCashierSettlementCommand toSubmitCashierSettlementCommand(
        SubmitInpatientDailySettlementRequest request,
        String typeEnum,
        Long cashierId,
        Long operatorId);

    /**
     * 提交结果装配响应。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param paymentCount 挂载条数
     * @return 提交响应
     */
    SubmitInpatientDailySettlementResponse toSubmitInpatientDailySettlementResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount);
}
