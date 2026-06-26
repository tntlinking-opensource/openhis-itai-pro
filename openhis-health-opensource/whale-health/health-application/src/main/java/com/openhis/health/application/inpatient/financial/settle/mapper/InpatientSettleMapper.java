/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.financial.settle.mapper;

import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleQueueItemDto;
import com.openhis.health.module.querysupport.financial.dto.SettledPaymentBriefDto;
import com.openhis.health.application.inpatient.financial.settle.request.ConfirmInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PageInpatientSettleQueueRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PreSettleInpatientChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.response.ReverseInpatientSettleResponse;
import com.openhis.health.application.inpatient.financial.settle.response.ConfirmInpatientSettleResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettlePageInitResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettleSummaryResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadSettledInpatientPaymentViewResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PageInpatientSettleQueueResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PreSettleInpatientChargesResponse;
import com.openhis.health.application.inpatient.financial.settle.response.QueryInpatientSettleChargesResponse;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.encounter.command.CompleteInpatientFinancialSettlementCommand;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CancelInpatientSettleAsRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteInpatientSettleCancelRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 住院结算应用层映射：读模型/领域命令与契约 DTO 之间的字段搬运。
 *
 * <p>禁止承载业务流程判断与金额校验。</p>
 */
public interface InpatientSettleMapper {

    /**
     * 初始化下拉选项 → 页面初始化响应。
     *
     * @param queueTabs           队列页签选项；调用方保证非 null
     * @param presentationModes   费用呈现模式选项；调用方保证非 null
     * @param paymentChannels     支付渠道选项；调用方保证非 null
     * @return 初始化响应
     */
    LoadInpatientSettlePageInitResponse toLoadInpatientSettlePageInitResponse(
        List<OptionDto> queueTabs,
        List<OptionDto> presentationModes,
        List<OptionDto> paymentChannels);

    /**
     * 队列分页请求 → 住院就诊上下文分页查询。
     *
     * @param request 分页请求；调用方保证非 null
     * @return 分页入参；request 为 null 时返回 null
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(PageInpatientSettleQueueRequest request);

    /**
     * 上下文行 + 账户 + 展示 overlay → 结算队列卡片 DTO。
     *
     * @param contextRow              住院就诊上下文；null 时返回 null
     * @param account                 个人现金户；未开户时为 null
     * @param displayOverlay          展示回填 overlay；可 null
     * @param pendingSettlementTotal  待结算合计；可 null
     * @param lastSettleTime          最近结算时间；已结算页签使用，可 null
     * @return 队列行 DTO
     */
    InpatientSettleQueueItemDto toInpatientSettleQueueItemDto(
        InpatientEncounterContextQueryModel contextRow,
        Account account,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
        BigDecimal pendingSettlementTotal,
        LocalDateTime lastSettleTime);

    /**
     * 已装配队列行 + Spring 分页壳 → 分页响应。
     *
     * @param rows 当前页 DTO 列表
     * @param page 上下文分页结果
     * @return 经 PagedResultBuilder 装配的分页响应
     */
    PageInpatientSettleQueueResponse toPageInpatientSettleQueueResponse(
        List<InpatientSettleQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page);

    /**
     * 摘要装配 → 结算摘要响应。
     *
     * @param contextRow           住院就诊上下文
     * @param account              个人现金户
     * @param pendingTotal         全部待结算合计
     * @param selectedTotal        选中合计
     * @param prepayNet            预交金净值
     * @param balanceHint          差额提示
     * @param displayBalance       展示余额
     * @return 摘要响应
     */
    LoadInpatientSettleSummaryResponse toLoadInpatientSettleSummaryResponse(
        InpatientEncounterContextQueryModel contextRow,
        Account account,
        BigDecimal pendingTotal,
        BigDecimal selectedTotal,
        BigDecimal prepayNet,
        BigDecimal balanceHint,
        BigDecimal displayBalance);

    /**
     * 费用查询请求 + 缺省入院日 → 收费明细查询命令。
     *
     * @param request        费用查询请求
     * @param admissionDate  缺省业务日起（入院日）
     * @return 查询命令
     */
    ChargeItemQueryCommand toChargeItemQueryCommand(
        QueryInpatientSettleChargesRequest request,
        LocalDate admissionDate);

    /**
     * 收费明细 + 产品展示 DTO → 费用行 DTO 列表。
     *
     * @param chargeItems  收费明细列表
     * @param productByKey 产品展示 DTO 索引（复合键）
     * @param defaultSelected 是否默认勾选
     * @return 费用行 DTO 列表
     */
    List<InpatientSettleChargeLineDto> toInpatientSettleChargeLineDtoList(
        List<ChargeItem> chargeItems,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        boolean defaultSelected);

    /**
     * 费用行列表 → 大类汇总 DTO 列表。
     *
     * @param lines 费用行 DTO 列表
     * @return 大类汇总列表
     */
    List<InpatientSettleCategorySummaryDto> toInpatientSettleCategorySummaryDtoList(
        List<InpatientSettleChargeLineDto> lines);

    /**
     * 费用查询结果 → 响应。
     *
     * @param encounterId       就诊 ID
     * @param mode              呈现模式 code
     * @param lines             明细行；SUMMARY 模式可为 null
     * @param categories        大类汇总；DETAIL 模式可为 null
     * @param total             待结算合计
     * @param defaultSelectAll  是否默认全选
     * @return 费用查询响应
     */
    QueryInpatientSettleChargesResponse toQueryInpatientSettleChargesResponse(
        Long encounterId,
        String mode,
        List<InpatientSettleChargeLineDto> lines,
        List<InpatientSettleCategorySummaryDto> categories,
        BigDecimal total,
        boolean defaultSelectAll);

    /**
     * 收费明细 ID 列表 → 批量命令。
     *
     * @param chargeItemIds 收费明细 ID 列表
     * @param operatorId    操作员 ID
     * @return 批量命令
     */
    BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId);

    /**
     * 预结算请求 + 金额 + 草稿明细 → 创建支付草稿命令。
     *
     * @param request         预结算请求
     * @param receivableTotal 应收合计
     * @param draftDetails    草稿占位明细
     * @param operatorId      操作员 ID
     * @param chargeItemIdsJoined 收费项 ID 逗号串
     * @param contractNo      合同号；可 null
     * @return 创建草稿命令（调用方须 setPaymentDetails）
     */
    CreatePaymentDraftFromPreSettleCommand toCreatePaymentDraftFromPreSettleCommand(
        PreSettleInpatientChargesRequest request,
        BigDecimal receivableTotal,
        List<InpatientSettlePaymentDetailDto> draftDetails,
        Long operatorId,
        String chargeItemIdsJoined,
        String contractNo);

    /**
     * 预结算结果 → 响应。
     *
     * @param paymentDraftId  支付草稿 ID
     * @param receivableTotal 应收合计
     * @return 预结算响应
     */
    PreSettleInpatientChargesResponse toPreSettleInpatientChargesResponse(
        Long paymentDraftId,
        BigDecimal receivableTotal);

    /**
     * 正式结算请求 → 住院支付结算命令。
     *
     * @param request    正式结算请求
     * @param operatorId 操作员 ID（审计用，命令体不含明细）
     * @return 住院支付结算命令
     */
    SettleInpatientPaymentCommand toSettleInpatientPaymentCommand(
        ConfirmInpatientSettleRequest request,
        Long operatorId);

    /**
     * 支付流水 + 支付 ID → 发票草稿命令。
     *
     * @param payment   支付流水读模型
     * @param paymentId 支付主键
     * @return 发票草稿命令
     */
    CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(
        PaymentReconciliationQueryModel payment,
        Long paymentId);

    /**
     * 出院全结 → 就诊财务终态命令。
     *
     * @param encounterId    就诊 ID
     * @param settlementTime 结算时间
     * @param operatorId     操作员 ID
     * @return 出院财务全结命令
     */
    CompleteInpatientFinancialSettlementCommand toCompleteInpatientFinancialSettlementCommand(
        Long encounterId,
        LocalDateTime settlementTime,
        Long operatorId);

    /**
     * 正式结算结果 → 响应。
     *
     * @param paymentId              支付 ID
     * @param invoiceDraftId         发票草稿 ID
     * @param dischargeFullSettle    是否出院全结
     * @param advanceRefundPaymentId 出院多退预交金支付单 ID；无多退时为 null
     * @param ybSetlId               医保结算 ID；无医保时为 null
     * @return 正式结算响应
     */
    ConfirmInpatientSettleResponse toConfirmInpatientSettleResponse(
        Long paymentId,
        Long invoiceDraftId,
        boolean dischargeFullSettle,
        Long advanceRefundPaymentId,
        String ybSetlId);

    /**
     * 出院全结多退 → 退还预交金领域命令（现金退现占位明细）。
     *
     * @param encounterId  就诊 ID
     * @param patientId    患者 ID
     * @param accountId    登记个人现金户 ID
     * @param refundAmount 退费金额（正数）
     * @param operatorId   操作员 ID
     * @return 退还命令
     */
    RefundAdvanceDepositCommand toDischargeDepositRefundCommand(
        Long encounterId,
        Long patientId,
        Long accountId,
        BigDecimal refundAmount,
        Long operatorId);

    /**
     * 结算少补 → 收取预交金领域命令（渠道明细来自结算实付）。
     *
     * @param encounterId       就诊 ID
     * @param patientId           患者 ID
     * @param accountId           登记个人现金户 ID
     * @param supplementAmount    少补金额（正数）
     * @param paymentDetails      用户渠道实付明细
     * @param operatorId          操作员 ID
     * @return 收取命令
     */
    CollectAdvanceDepositCommand toSettleSupplementCollectCommand(
        Long encounterId,
        Long patientId,
        Long accountId,
        BigDecimal supplementAmount,
        List<InpatientSettlePaymentDetailDto> paymentDetails,
        Long operatorId);

    /**
     * 住院结算/反结历史 + 渠道展示 → 摘要 DTO 列表（含 NET 与可反结门禁）。
     *
     * @param payments                          支付头列表
     * @param payMethodTexts                    paymentId → 渠道展示
     * @param encounter                         就诊快照（可反结门禁）
     * @param paymentReconciliationQueryService 支付读侧（逆向关联判定）
     * @return 已结算单摘要列表
     */
    List<SettledPaymentBriefDto> toSettledPaymentBriefDtoList(
        List<PaymentReconciliationQueryModel> payments,
        Map<Long, String> payMethodTexts,
        EncounterDetailQueryModel encounter,
        PaymentReconciliationQueryService paymentReconciliationQueryService);

    /**
     * 已结算单查阅 → 响应。
     *
     * @param encounterId     就诊 ID
     * @param payments        结算单摘要列表
     * @param activePaymentId 当前展示支付 ID
     * @param settleTime      结算时间
     * @param operatorName    操作员名
     * @param receivableTotal 应收合计
     * @param payMethodText   支付方式展示
     * @param lines           费用明细快照
     * @param categories      大类汇总快照
     * @return 已结算单查阅响应
     */
    LoadSettledInpatientPaymentViewResponse toLoadSettledInpatientPaymentViewResponse(
        Long encounterId,
        List<SettledPaymentBriefDto> payments,
        Long activePaymentId,
        LocalDateTime settleTime,
        String operatorName,
        BigDecimal receivableTotal,
        String payMethodText,
        List<InpatientSettleChargeLineDto> lines,
        List<InpatientSettleCategorySummaryDto> categories);

    /**
     * 原正向结算支付单 + 渠道明细 → 住院反结逆向单领域命令（DM-FIN-002 §6.1.7）。
     *
     * @param originalPayment 成功结算支付单读模型
     * @param paymentDetails  原单渠道明细；调用方保证非 null
     * @param operatorId      操作员 ID
     * @return 逆向单创建命令
     */
    CancelInpatientSettleAsRefundCommand toCancelInpatientSettleAsRefundCommand(
        PaymentReconciliationQueryModel originalPayment,
        List<PaymentRecDetailQueryModel> paymentDetails,
        Long operatorId);

    /**
     * 反结结果 → 响应。
     *
     * @param refundPaymentId 逆向反结支付单 ID
     * @return 住院反结响应
     */
    ReverseInpatientSettleResponse toReverseInpatientSettleResponse(Long refundPaymentId);

    /**
     * 出院嘱退回草稿 → 领域命令（BR-IP-RVS-007）。
     *
     * @param serviceRequestId 出院类诊疗 ID
     * @param operatorId       操作员 ID
     * @param performerCheckId 校对人 ID
     * @param checkTime        校对时间
     * @return 退回草稿命令
     */
    RejectServiceRequestToDraftCommand toRejectDischargeServiceRequestToDraftCommand(
        Long serviceRequestId,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime);

    /**
     * 取消结算收口 → 领域命令。
     *
     * @param originalPaymentId 原结算支付单 ID
     * @param reversePaymentId  逆向退款支付单 ID
     * @param ybSetlId          撤销后的医保结算 ID；无医保时为 null
     * @return 收口命令
     */
    CompleteInpatientSettleCancelRefundCommand toCompleteInpatientSettleCancelRefundCommand(
        Long originalPaymentId,
        Long reversePaymentId,
        String ybSetlId);

    /**
     * 按支付单查询关联票据。
     *
     * @param paymentId 结算支付单 ID
     * @return 票据查询命令
     */
    InvoiceQueryCommand toInvoiceQueryByPaymentId(Long paymentId);

    /**
     * 构建票据作废/冲红命令。
     *
     * @param invoiceId  票据 ID
     * @param reason     作废或冲红原因
     * @param operatorId 操作员 ID
     * @return 作废/冲红命令
     */
    CancelInvoiceCommand toCancelInvoiceCommand(Long invoiceId, String reason, Long operatorId);
}
