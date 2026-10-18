/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.mapper;

import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamChargeItemRowDto;
import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamChargeQueueRowDto;
import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamPreSettleLinePreviewDto;
import com.zxplat.plugin.peis.application.financial.charge.request.AdjustPhysicalExamChargePricesRequest;
import com.zxplat.plugin.peis.application.financial.charge.request.ConfirmPhysicalExamChargeSettlementRequest;
import com.zxplat.plugin.peis.application.financial.charge.response.ConfirmPhysicalExamChargeSettlementResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.LoadPhysicalExamChargePageInitResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.PagePhysicalExamChargeQueueResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.PreSettlePhysicalExamChargesResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.PreviewPhysicalExamRefundResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.RefundPhysicalExamChargesResponse;
import com.zxplat.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.zxplat.domain.administration.chargeitem.command.ManualOverridePlannedChargeItemPricesCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.zxplat.domain.administration.patient.queries.model.PatientInpatientProjectionSnapshot;
import com.zxplat.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 体检收费应用层映射器。
 */
public interface PhysicalExamChargeMapper {

    /**
     * 装配页初始化响应。
     *
     * @param chargeItemStatusOptions 收费项状态下拉
     * @param paymentChannelOptions   支付渠道下拉
     * @return 初始化响应
     */
    LoadPhysicalExamChargePageInitResponse toLoadPageInitResponse(
        List<OptionDto> chargeItemStatusOptions,
        List<OptionDto> paymentChannelOptions);

    /**
     * 装配队列分页响应。
     *
     * @param page    分页壳
     * @param records 队列行
     * @return 分页响应
     */
    PagePhysicalExamChargeQueueResponse toPageQueueResponse(Page<?> page, List<PhysicalExamChargeQueueRowDto> records);

    /**
     * 装配批量收费项命令。
     *
     * @param chargeItemIds 收费项主键列表
     * @param operatorId    操作人
     * @return 批量命令
     */
    BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId);

    /**
     * 装配创建支付草稿命令。
     *
     * @param encounterId   就诊主键
     * @param patientId     患者主键
     * @param chargeItemIds 收费项 id 串
     * @param amount        金额
     * @param operatorId    操作人
     * @param contractNo    合同号
     * @return 创建草稿命令
     */
    CreatePaymentDraftFromPreSettleCommand toCreatePaymentDraftFromPreSettleCommand(
        Long encounterId,
        Long patientId,
        String chargeItemIds,
        BigDecimal amount,
        Long operatorId,
        String contractNo);

    /**
     * 装配门诊正式结算命令（自费路径复用）。
     *
     * @param request 正式结算请求
     * @return 结算命令
     */
    SettleOutpatientPaymentCommand toSettleOutpatientPaymentCommand(ConfirmPhysicalExamChargeSettlementRequest request);

    /**
     * 装配票据草稿命令。
     *
     * @param payment           支付读模型
     * @param paymentId         支付主键
     * @param operatorId        操作人
     * @param invoiceFaceAmount 票面金额（收费项合计结算刻度；含医保已付）
     * @return 票据草稿命令
     */
    CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(
        PaymentReconciliationQueryModel payment,
        Long paymentId,
        Long operatorId,
        BigDecimal invoiceFaceAmount);

    /**
     * 装配预结算响应。
     *
     * @param paymentDraftId 支付草稿主键
     * @param displayAmount  展示金额
     * @param previews       预览行
     * @return 预结算响应
     */
    PreSettlePhysicalExamChargesResponse toPreSettleResponse(
        Long paymentDraftId,
        BigDecimal displayAmount,
        List<PhysicalExamPreSettleLinePreviewDto> previews);

    /**
     * 装配正式结算响应。
     *
     * @param paymentId 支付主键
     * @param invoiceId 发票主键
     * @return 正式结算响应
     */
    ConfirmPhysicalExamChargeSettlementResponse toConfirmSettlementResponse(Long paymentId, Long invoiceId);

    /**
     * 装配队列行列表。
     *
     * @param encounterIds           就诊主键（分页顺序）
     * @param encounterById          就诊实体映射
     * @param patientSnapshotsById   患者人口学快照（姓名/性别/年龄）
     * @param extByEncounter         体检扩展映射
     * @param chargeItemsByEncounter 收费明细映射
     * @return 队列行
     */
    List<PhysicalExamChargeQueueRowDto> toQueueRows(
        List<Long> encounterIds,
        Map<Long, Encounter> encounterById,
        Map<Long, PatientInpatientProjectionSnapshot> patientSnapshotsById,
        Map<Long, PeisEncounterExtModel> extByEncounter,
        Map<Long, List<ChargeItem>> chargeItemsByEncounter);

    /**
     * 装配明细行列表。
     *
     * @param rows       收费明细
     * @param itemLabels 收费项展示名
     * @return 明细行
     */
    List<PhysicalExamChargeItemRowDto> toChargeItemRows(List<ChargeItem> rows, Map<Long, String> itemLabels);

    /**
     * 装配预结算预览行。
     *
     * @param chargeItems  收费明细
     * @param itemLabels   展示名映射
     * @return 预览行
     */
    List<PhysicalExamPreSettleLinePreviewDto> toPreSettlePreviews(
        List<ChargeItem> chargeItems,
        Map<Long, String> itemLabels);

    /**
     * 装配手工改价领域命令。
     *
     * @param request    改价请求
     * @param operatorId 操作人
     * @return 改价命令
     */
    ManualOverridePlannedChargeItemPricesCommand toManualOverridePlannedChargeItemPricesCommand(
        AdjustPhysicalExamChargePricesRequest request,
        Long operatorId);

    /**
     * 装配退费预览响应。
     *
     * @param originalPaymentId       原支付主键
     * @param refundableAmount        应退金额
     * @param originalPayChannelCodes 原支付渠道码
     * @param refundingChargeItemIds  拟退收费项
     * @param previews                拟退行预览
     * @return 预览响应
     */
    PreviewPhysicalExamRefundResponse toPreviewRefundResponse(
        Long originalPaymentId,
        BigDecimal refundableAmount,
        List<String> originalPayChannelCodes,
        List<Long> refundingChargeItemIds,
        List<PhysicalExamPreSettleLinePreviewDto> previews);

    /**
     * 装配退费执行响应。
     *
     * @param originalPaymentId 原支付主键
     * @param reversePaymentId  逆向支付主键
     * @return 执行响应
     */
    RefundPhysicalExamChargesResponse toRefundChargesResponse(Long originalPaymentId, Long reversePaymentId);
}
