/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.prepay.mapper;

import com.zxplat.application.inpatient.financial.prepay.dto.PrepayPaymentDetailDto;
import com.zxplat.application.inpatient.financial.prepay.dto.PrepayPaymentRecordDto;
import com.zxplat.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.zxplat.application.inpatient.financial.prepay.dto.PrepayQueueItemDto;
import com.zxplat.application.inpatient.financial.prepay.request.CollectPrepayAdvanceDepositRequest;
import com.zxplat.application.inpatient.financial.prepay.request.PagePrepayPatientQueueRequest;
import com.zxplat.application.inpatient.financial.prepay.request.RefundPrepayAdvanceDepositRequest;
import com.zxplat.application.inpatient.financial.prepay.response.CollectPrepayAdvanceDepositResponse;
import com.zxplat.application.inpatient.financial.prepay.response.LoadInpatientPrepayPageInitResponse;
import com.zxplat.application.inpatient.financial.prepay.response.PagePrepayPatientQueueResponse;
import com.zxplat.application.inpatient.financial.prepay.response.RefundPrepayAdvanceDepositResponse;
import com.zxplat.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 住院预交金应用层映射：读模型/支付查询模型与契约 DTO、领域命令之间的字段搬运。
 *
 * <p>禁止承载业务流程判断与金额校验。</p>
 */
public interface InpatientPrepayMapper {

    /**
     * 装配预交金页初始化响应。
     *
     * @param paymentChannelOptions 用户可选支付渠道下拉
     * @return 初始化响应
     */
    LoadInpatientPrepayPageInitResponse toLoadInpatientPrepayPageInitResponse(List<OptionDto> paymentChannelOptions);

    /**
     * 队列分页请求 → 住院就诊上下文分页查询。
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(PagePrepayPatientQueueRequest request);

    /**
     * 住院上下文行 + 账户 → 队列卡片 DTO。
     */
    PrepayQueueItemDto toPrepayQueueItemDto(
        InpatientEncounterContextQueryModel contextRow,
        Account account,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay);

    /**
     * 已装配队列行 + Spring 分页壳 → 分页响应。
     *
     * @param rows 当前页 DTO 列表
     * @param page 读模型分页结果
     * @return 经 PagedResultBuilder 装配的分页响应
     */
    PagePrepayPatientQueueResponse toPagePrepayPatientQueueResponse(
        List<PrepayQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page);

    /**
     * 住院存款支付列表 + 操作员名索引 → 流水 DTO 列表（已按 bill_date/id 降序）。
     *
     * @param payments                 已过滤 HOSPITAL_DEPOSIT 的支付头
     * @param operatorNameById         entererId → 展示名
     * @param payMethodTextByPaymentId paymentId → 支付方式展示（明细首条渠道）
     * @param payChannelCodeByPaymentId paymentId → 支付渠道 code（明细首条 pay_enum）
     * @return 流水 DTO 列表
     */
    List<PrepayPaymentRecordDto> toPrepayPaymentRecordDtoList(
        List<PaymentReconciliationQueryModel> payments,
        Map<Long, String> operatorNameById,
        Map<Long, String> payMethodTextByPaymentId,
        Map<Long, String> payChannelCodeByPaymentId);

    /**
     * 续收请求 → 收取预交金领域命令。
     *
     * @param request    收取请求
     * @param accountId  登记个人现金户 ID
     * @param operatorId 经办人 ID
     * @return 收取命令
     */
    CollectAdvanceDepositCommand toCollectAdvanceDepositCommand(
        CollectPrepayAdvanceDepositRequest request,
        Long accountId,
        Long operatorId);

    /**
     * 单渠道续收命令（一渠道一笔流水）。
     *
     * @param request    收取请求（取就诊/患者）
     * @param accountId  登记个人现金户 ID
     * @param operatorId 经办人 ID
     * @param amount     本渠道金额
     * @param detail     单条支付明细
     * @return 收取命令；明细空时 null
     */
    CollectAdvanceDepositCommand toCollectAdvanceDepositCommandForChannel(
        CollectPrepayAdvanceDepositRequest request,
        Long accountId,
        Long operatorId,
        BigDecimal amount,
        PrepayPaymentDetailDto detail);

    /**
     * 退费请求 → 退还预交金领域命令。
     *
     * @param request    退费请求
     * @param accountId  登记个人现金户 ID
     * @param operatorId 经办人 ID
     * @return 退还命令
     */
    RefundAdvanceDepositCommand toRefundAdvanceDepositCommand(
        RefundPrepayAdvanceDepositRequest request,
        Long accountId,
        Long operatorId);

    /**
     * 支付草稿 ID → 住院支付结算命令。
     *
     * @param paymentId 支付主键
     * @return 结算命令
     */
    SettleInpatientPaymentCommand toSettleInpatientPaymentCommand(Long paymentId);

    /**
     * 收取结果 → 响应。
     *
     * @param paymentId      支付流水 ID
     * @param accountBalance 结算后展示余额（动态计算）
     * @return 收取响应
     */
    CollectPrepayAdvanceDepositResponse toCollectPrepayAdvanceDepositResponse(
        Long paymentId,
        BigDecimal accountBalance);

    /**
     * 退费结果 → 响应。
     *
     * @param paymentId      支付流水 ID
     * @param accountBalance 结算后展示余额（动态计算）
     * @return 退费响应
     */
    RefundPrepayAdvanceDepositResponse toRefundPrepayAdvanceDepositResponse(
        Long paymentId,
        BigDecimal accountBalance);

    /**
     * 续收结算后 → 账户余额调整命令。
     *
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @param amount      收取金额（正数）
     * @param operatorId  操作员 ID
     * @return 余额调整命令
     */
    AdjustAdvanceDepositBalanceCommand toCollectBalanceAdjustCommand(
        Long patientId,
        Long encounterId,
        BigDecimal amount,
        Long operatorId);

    /**
     * 退费结算后 → 账户余额调整命令。
     *
     * @param patientId    患者 ID
     * @param encounterId  就诊 ID
     * @param refundAmount 退费金额（正数，命令内取负增量）
     * @param operatorId   操作员 ID
     * @return 余额调整命令
     */
    AdjustAdvanceDepositBalanceCommand toRefundBalanceAdjustCommand(
        Long patientId,
        Long encounterId,
        BigDecimal refundAmount,
        Long operatorId);
}
