/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.dailysettlement.mapper;

import com.zxplat.application.outpatient.financial.dailysettlement.dto.OutpatientDailySettlementPatientExportRowDto;
import com.zxplat.application.outpatient.financial.dailysettlement.dto.OutpatientDailySettlementPatientSummaryDto;
import com.zxplat.application.outpatient.financial.dailysettlement.request.PreviewOutpatientDailySettlementRequest;
import com.zxplat.application.outpatient.financial.dailysettlement.request.SubmitOutpatientDailySettlementRequest;
import com.zxplat.application.outpatient.financial.dailysettlement.response.ExportOutpatientDailySettlementPatientResponse;
import com.zxplat.application.outpatient.financial.dailysettlement.response.LoadOutpatientDailySettlementPageInitResponse;
import com.zxplat.application.outpatient.financial.dailysettlement.response.PreviewOutpatientDailySettlementResponse;
import com.zxplat.application.outpatient.financial.dailysettlement.response.SubmitOutpatientDailySettlementResponse;
import com.zxplat.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.zxplat.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.zxplat.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.zxplat.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.framework.core.application.dto.OptionDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊日结交账 Request/Command/Response 映射（AS-OP-FIN-004-SDD §2.4）。
 *
 * <p>禁止承载业务流程判断；费用性质展示过滤与渠道汇总在实现类内按字段规则装配。</p>
 */
public interface OutpatientDailySettlementMapper {

    /**
     * 装配门诊日结页初始化响应。
     *
     * @param defaultDate            默认结账日
     * @param expensePropertyOptions 费用性质下拉
     * @param cashierOptions         经办人下拉
     * @param currentCashierId       当前经办人 Practitioner 主键
     * @return 初始化响应
     */
    LoadOutpatientDailySettlementPageInitResponse toLoadOutpatientDailySettlementPageInitResponse(
        LocalDate defaultDate,
        List<OptionDto> expensePropertyOptions,
        List<OptionDto> cashierOptions,
        Long currentCashierId);

    /**
     * 预览请求转领域查询条件。
     *
     * @param request      HTTP 请求（含 orgId）
     * @param typeEnum     交账类型 code
     * @param locationIds  科室下 location 白名单（应用层解析）
     * @return 领域预览查询
     */
    PreviewCashierSettlementQuery toPreviewCashierSettlementQuery(
        PreviewOutpatientDailySettlementRequest request,
        String typeEnum,
        List<Long> locationIds);

    /**
     * 预览结果与 enrichment 数据装配查询响应。
     *
     * @param previewModel            未交账候选 payment 预览（paymentCount / items）
     * @param summaryPreviewItems     日结展示口径 payment 行（含已交账，供 rows/summary）
     * @param paymentModels           展示口径 payment 读模型（含 ybSettlements）
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     * @param chargeItemsByPaymentId   paymentId → 收费项（items enrichment）
     * @param encounterByEncounterId   门诊就诊上下文（患者分项身份字段）
     * @param hospitalizedPatientIds   有在院住院线的患者主键（患者分项「是否住院」）
     * @param expenseProperty          费用性质展示过滤，可空
     * @param insuranceByContractBusNo 合同 bus_no → 是否医保
     * @param finTypeOptions           fin_type_code 字典项（上半区展示顺序）
     * @return 日结查询响应
     */
    PreviewOutpatientDailySettlementResponse toPreviewOutpatientDailySettlementResponse(
        PreviewCashierSettlementQueryModel previewModel,
        List<UnsettledPaymentPreviewItemQueryModel> summaryPreviewItems,
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId,
        Map<Long, OutpatientEncounterContextQueryModel> encounterByEncounterId,
        Set<Long> hospitalizedPatientIds,
        String expenseProperty,
        Map<String, Boolean> insuranceByContractBusNo,
        List<OptionDto> finTypeOptions);

    /**
     * 提交请求转领域命令。
     *
     * @param request            HTTP 请求
     * @param typeEnum           交账类型 code
     * @param submitterId        提交人 Practitioner 主键
     * @param operatorId         当前操作员用户主键（审计）
     * @param selectedPaymentIds 勾选挂载 payment 主键
     * @param locationIds        科室下 location 白名单
     * @param practitionerIds    窗内 enterer 快照（逗号分隔）
     * @param paymentCount       挂载笔数
     * @param totalIncome        勾选集合日结「总收入」（与预览 handoverSummary 同管线）
     * @return 提交命令
     */
    SubmitCashierSettlementCommand toSubmitCashierSettlementCommand(
        SubmitOutpatientDailySettlementRequest request,
        String typeEnum,
        Long submitterId,
        Long operatorId,
        List<Long> selectedPaymentIds,
        List<Long> locationIds,
        String practitionerIds,
        Integer paymentCount,
        BigDecimal totalIncome);

    /**
     * 按勾选 payment 现算日结「总收入」（与 IF-OP-DCL-02 {@code handoverSummary.totalIncome} 同管线）。
     *
     * <p>复用预览 {@code buildDisplayBundle}：fin_type 快照代数和优先，否则回落 payment 净额代数和
     *（**BR-OP-DCL-011**）。提交头表 {@code total_income} 必须调用本方法，禁止另写口径。</p>
     *
     * @param lockedItems        提交前锁定的未交账预览行
     * @param selectedPaymentIds 本次勾选挂载 payment 主键
     * @return 结算刻度总收入；空选为 0
     */
    BigDecimal resolveSelectedSettlementTotalIncome(
        List<UnsettledPaymentPreviewItemQueryModel> lockedItems,
        List<Long> selectedPaymentIds);

    /**
     * 按勾选 payment 现算头表笔数（与总收入同口径：不含工伤合同）。
     *
     * <p>提交头表 {@code payment_count} 必须调用本方法；挂载仍含工伤 payment，笔数快照不含。</p>
     *
     * @param lockedItems        提交前锁定的未交账预览行
     * @param selectedPaymentIds 本次勾选挂载 payment 主键
     * @return 不含工伤的勾选笔数；空选为 0
     */
    Integer resolveSelectedSettlementPaymentCount(
        List<UnsettledPaymentPreviewItemQueryModel> lockedItems,
        List<Long> selectedPaymentIds);

    /**
     * 提交结果装配响应。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param paymentCount 计入总收入笔数（不含工伤）
     * @return 提交响应
     */
    SubmitOutpatientDailySettlementResponse toSubmitOutpatientDailySettlementResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount);

    /**
     * 患者分项块 → 导出行（金额解析为 BigDecimal，供 AnnotatedExcelExporter 写数字格）。
     *
     * @param patientSummaries 预览患者分项；可空
     * @return 导出行；无分项时为空列表
     */
    List<OutpatientDailySettlementPatientExportRowDto> toPatientExportRows(
        List<OutpatientDailySettlementPatientSummaryDto> patientSummaries);

    /**
     * 装配患者分项导出响应（真 xlsx 字节）。
     *
     * @param body     xlsx 内容
     * @param rowCount 导出行数
     * @return 导出响应
     */
    ExportOutpatientDailySettlementPatientResponse toExportPatientResponse(byte[] body, int rowCount);
}
