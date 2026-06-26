/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.dailysettlement.mapper;

import com.openhis.health.application.outpatient.financial.dailysettlement.request.PreviewOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.SubmitOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.LoadOutpatientDailySettlementPageInitResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.PreviewOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.SubmitOutpatientDailySettlementResponse;
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
 * 门诊日结交账 Request/Command/Response 映射（AS-OP-FIN-004-SDD §2.4）。
 *
 * <p>禁止承载业务流程判断；费用性质展示过滤与渠道汇总在实现类内按字段规则装配。</p>
 */
public interface OutpatientDailySettlementMapper {

    /**
     * 装配门诊日结页初始化响应。
     *
     * @param defaultDate           默认结账日
     * @param financialDelegateEnabled 是否允许财务代交账
     * @param expensePropertyOptions 费用性质下拉
     * @param cashierOptions        经办人下拉
     * @param currentCashierId      当前经办人 Practitioner 主键
     * @return 初始化响应
     */
    LoadOutpatientDailySettlementPageInitResponse toLoadOutpatientDailySettlementPageInitResponse(
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
     * @param cashierId 已解析经办人 Practitioner 主键
     * @return 领域预览查询
     */
    PreviewCashierSettlementQuery toPreviewCashierSettlementQuery(
        PreviewOutpatientDailySettlementRequest request,
        String typeEnum,
        Long cashierId);

    /**
     * 预览结果与 enrichment 数据装配查询响应。
     *
     * @param previewModel            候选 payment 预览
     * @param paymentModels           同日窗 payment 读模型（含 ybSettlements）
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     * @param expenseProperty         费用性质展示过滤，可空
     * @param insuranceByContractBusNo 合同 bus_no → 是否医保
     * @param finTypeOptions          fin_type_code 字典项（上半区展示顺序）
     * @return 日结查询响应
     */
    PreviewOutpatientDailySettlementResponse toPreviewOutpatientDailySettlementResponse(
        PreviewCashierSettlementQueryModel previewModel,
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        String expenseProperty,
        Map<String, Boolean> insuranceByContractBusNo,
        List<OptionDto> finTypeOptions);

    /**
     * 提交请求转领域命令。
     *
     * @param request    HTTP 请求
     * @param typeEnum   交账类型 code
     * @param cashierId  已解析经办人
     * @param operatorId 当前操作员用户主键
     * @return 提交命令
     */
    SubmitCashierSettlementCommand toSubmitCashierSettlementCommand(
        SubmitOutpatientDailySettlementRequest request,
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
    SubmitOutpatientDailySettlementResponse toSubmitOutpatientDailySettlementResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount);
}
