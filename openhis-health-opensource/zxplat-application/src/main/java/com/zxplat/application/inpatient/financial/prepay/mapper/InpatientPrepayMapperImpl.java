/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.prepay.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.firstNonBlank;

import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inpatient.financial.prepay.dto.PrepayPaymentDetailDto;
import com.zxplat.application.shared.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.zxplat.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.zxplat.application.inpatient.financial.prepay.dto.PrepayPaymentRecordDto;
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
import com.zxplat.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.share.constants.InpatientPrepayConstants;
import com.zxplat.domain.share.constants.InpatientPrepayStayScope;
import com.zxplat.domain.share.enums.AdministrativeGender;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.share.enums.PaymentType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link InpatientPrepayMapper} 的 Spring 实现：字段映射；展示余额经 {@link InpatientAccountBalanceDisplayAssembler} 装配。
 */
@Component
public class InpatientPrepayMapperImpl implements InpatientPrepayMapper {

    /**
     * 装配预交金页初始化响应。
     *
     * @param paymentChannelOptions 用户可选支付渠道下拉
     * @return 初始化响应
     */
    @Override
    public LoadInpatientPrepayPageInitResponse toLoadInpatientPrepayPageInitResponse(
        List<OptionDto> paymentChannelOptions) {
        LoadInpatientPrepayPageInitResponse response = new LoadInpatientPrepayPageInitResponse();
        response.setPaymentChannelOptions(paymentChannelOptions);
        return response;
    }

    /**
     * 队列分页请求 → 住院信息读模型分页查询。
     *
     * @param request 分页请求；调用方保证非 null
     * @return 读模型分页入参
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(PagePrepayPatientQueueRequest request) {
        if (request == null) {
            return null;
        }
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setAdmissionDate(request.getAdmissionDate());
        query.setPrepayEligibleOnly(Boolean.TRUE);
        // 预交金卡片不展示诊断，与 pagePrepayQueue 一致跳过 diagnosisJson enrich
        query.setIncludeDiagnosisSnapshot(Boolean.FALSE);
        // 在院范围 → statusEnumIn（与资格态求交由读侧 applyPrepayEligibleStatus 完成）
        query.setStatusEnumIn(this.resolveStayScopeStatusEnumIn(request.getStayScope()));
        return query;
    }

    /**
     * 解析 stayScope 为就诊状态码集合；空/ALL/未知返回 null（读侧用全部资格态，含退院 CANCELLED）。
     *
     * @param stayScope 在院范围码（{@link InpatientPrepayStayScope}）
     * @return 状态码集合；null 表示不额外收窄
     */
    private Collection<String> resolveStayScopeStatusEnumIn(String stayScope) {
        if (StringUtils.isEmpty(stayScope)) {
            return null;
        }
        String scope = stayScope.trim();
        if (InpatientPrepayStayScope.ALL.equals(scope)) {
            return null;
        }
        if (InpatientPrepayStayScope.IN_HOSPITAL.equals(scope)) {
            List<String> inHospital = new ArrayList<>();
            inHospital.add(EncounterStatus.REGISTERED.getCode());
            inHospital.add(EncounterStatus.IN_PROGRESS.getCode());
            inHospital.add(EncounterStatus.ON_HOLD.getCode());
            inHospital.add(EncounterStatus.PENDING.getCode());
            return inHospital;
        }
        if (InpatientPrepayStayScope.NOT_IN_HOSPITAL.equals(scope)) {
            // 待清床 + 已出院 + 取消登记（退院）；读侧再 EXISTS 预交金流水，无流水不入队
            List<String> notInHospital = new ArrayList<>();
            notInHospital.add(EncounterStatus.COMPLETED.getCode());
            notInHospital.add(EncounterStatus.DISCHARGED.getCode());
            notInHospital.add(EncounterStatus.CANCELLED.getCode());
            return notInHospital;
        }
        // 未知码：按全部，避免误空列表
        return null;
    }

    /**
     * 读模型行 + 账户 → 队列卡片 DTO。
     *
     * @param readModel 住院信息读模型；null 时返回 null
     * @param account   个人现金户；未开户时为 null（展示余额按 0 计）
     * @return 队列行 DTO
     */
    @Override
    public PrepayQueueItemDto toPrepayQueueItemDto(
        InpatientEncounterContextQueryModel contextRow,
        Account account,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay) {
        if (contextRow == null) {
            return null;
        }
        String overlayBusNo = null;
        String overlayPatientName = null;
        String overlayOrgName = null;
        String overlayWardName = null;
        String overlayBedName = null;
        String overlayStatusText = null;
        String overlayContract = null;
        String overlayGenderEnum = null;
        LocalDateTime overlayBirthDate = null;
        String overlayAgeText = null;
        if (displayOverlay != null) {
            overlayBusNo = displayOverlay.getEncounterBusNo();
            overlayPatientName = displayOverlay.getPatientName();
            overlayOrgName = displayOverlay.getOrganizationName();
            overlayWardName = displayOverlay.getWardName();
            overlayBedName = displayOverlay.getBedName();
            overlayStatusText = displayOverlay.getStatusEnumText();
            overlayContract = displayOverlay.getContractDisplay();
            overlayGenderEnum = displayOverlay.getGenderEnum();
            overlayBirthDate = displayOverlay.getBirthDate();
            overlayAgeText = displayOverlay.getAgeText();
        }
        PrepayQueueItemDto dto = new PrepayQueueItemDto();
        dto.setEncounterId(contextRow.getEncounterId());
        dto.setPatientId(contextRow.getPatientId());
        dto.setEncounterBusNo(firstNonBlank(contextRow.getEncounterBusNo(), overlayBusNo));
        dto.setPatientName(firstNonBlank(contextRow.getPatientName(), overlayPatientName));
        String genderCode = firstNonBlank(contextRow.getGenderEnum(), overlayGenderEnum);
        dto.setGenderEnum(genderCode);
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderCode);
        if (gender != null) {
            dto.setGenderEnumText(gender.getDisplay());
        }
        dto.setAgeText(firstNonBlank(contextRow.getAgeText(), overlayAgeText));
        dto.setOrganizationName(firstNonBlank(contextRow.getOrganizationName(), overlayOrgName));
        dto.setWardName(firstNonBlank(contextRow.getWardName(), overlayWardName));
        dto.setBedName(firstNonBlank(contextRow.getBedName(), overlayBedName));
        dto.setStatusEnum(contextRow.getStatusEnum());
        dto.setStatusEnumText(firstNonBlank(contextRow.getStatusEnumText(), overlayStatusText));
        dto.setStartTime(contextRow.getStartTime());
        dto.setContractDisplay(overlayContract);
        return dto;
    }

    /**
     * 已装配队列行 + Spring 分页壳 → 分页响应。
     *
     * @param rows 当前页 DTO 列表
     * @param page 读模型分页结果
     * @return 经 PagedResultBuilder 装配的分页响应
     */
    @Override
    public PagePrepayPatientQueueResponse toPagePrepayPatientQueueResponse(
        List<PrepayQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page) {
        return PagedResultBuilder.build(new PagePrepayPatientQueueResponse(), rows, page);
    }

    /**
     * 住院存款支付列表 + 操作员名索引 → 流水 DTO 列表（已按 bill_date/id 降序）。
     *
     * @param payments                  已过滤 HOSPITAL_DEPOSIT 的支付头
     * @param operatorNameById          entererId → 展示名
     * @param payMethodTextByPaymentId  paymentId → 支付方式展示
     * @param payChannelCodeByPaymentId paymentId → 支付渠道 code
     * @return 流水 DTO 列表
     */
    @Override
    public List<PrepayPaymentRecordDto> toPrepayPaymentRecordDtoList(
        List<PaymentReconciliationQueryModel> payments,
        Map<Long, String> operatorNameById,
        Map<Long, String> payMethodTextByPaymentId,
        Map<Long, String> payChannelCodeByPaymentId) {
        List<PrepayPaymentRecordDto> rows = new ArrayList<>();
        if (payments == null) {
            return rows;
        }
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null) {
                continue;
            }
            PrepayPaymentRecordDto dto = new PrepayPaymentRecordDto();
            dto.setPaymentId(payment.getId());
            LocalDateTime tradeTime = payment.getBillDate();
            if (tradeTime == null) {
                tradeTime = LocalDateTime.now();
            }
            dto.setTradeTime(tradeTime);
            String paymentEnumCode = payment.getPaymentEnum();
            dto.setPaymentEnum(paymentEnumCode);
            dto.setBizTypeText(this.resolveBizTypeText(paymentEnumCode));
            BigDecimal amount = payment.getDisplayAmount();
            if (amount == null) {
                amount = payment.getTenderedAmount();
            }
            dto.setAmount(amount);
            if (payment.getId() != null) {
                if (payMethodTextByPaymentId != null) {
                    dto.setPayMethodText(payMethodTextByPaymentId.get(payment.getId()));
                }
                if (payChannelCodeByPaymentId != null) {
                    dto.setPayChannelCode(payChannelCodeByPaymentId.get(payment.getId()));
                }
            }
            if (payment.getEntererId() != null && operatorNameById != null) {
                dto.setOperatorName(operatorNameById.get(payment.getEntererId()));
            }
            dto.setTicketNo(payment.getPaymentNo());
            PaymentStatus status = payment.getStatusEnum();
            if (status != null) {
                dto.setStatusEnum(status.getCode());
                dto.setStatusEnumText(status.getDisplay());
            }
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 续收请求 → 收取预交金领域命令。
     *
     * @param request    收取请求
     * @param accountId  登记个人现金户 ID
     * @param operatorId 经办人 ID
     * @return 收取命令
     */
    @Override
    public CollectAdvanceDepositCommand toCollectAdvanceDepositCommand(
        CollectPrepayAdvanceDepositRequest request,
        Long accountId,
        Long operatorId) {
        if (request == null) {
            return null;
        }
        CollectAdvanceDepositCommand command = new CollectAdvanceDepositCommand();
        command.setEncounterId(request.getEncounterId());
        command.setPatientId(request.getPatientId());
        command.setEntererId(operatorId);
        command.setTenderedAmount(request.getAmount());
        command.setDisplayAmount(request.getAmount());
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        command.setPaymentEnum(PaymentType.PAY.getCode());
        command.setPaymentDetails(this.toPaymentDetailItemCommands(request.getPaymentDetails(), accountId));
        return command;
    }

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
    @Override
    public CollectAdvanceDepositCommand toCollectAdvanceDepositCommandForChannel(
        CollectPrepayAdvanceDepositRequest request,
        Long accountId,
        Long operatorId,
        BigDecimal amount,
        PrepayPaymentDetailDto detail) {
        // 无渠道明细时无法装配
        if (request == null || detail == null) {
            return null;
        }
        CollectAdvanceDepositCommand command = new CollectAdvanceDepositCommand();
        command.setEncounterId(request.getEncounterId());
        command.setPatientId(request.getPatientId());
        command.setEntererId(operatorId);
        // 头金额与本渠道一致，保证一渠道一流水
        command.setTenderedAmount(amount);
        command.setDisplayAmount(amount);
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        command.setPaymentEnum(PaymentType.PAY.getCode());
        command.setPaymentDetails(this.toPaymentDetailItemCommands(List.of(detail), accountId));
        return command;
    }

    /**
     * 退费请求 → 退还预交金领域命令。
     *
     * @param request    退费请求
     * @param accountId  登记个人现金户 ID
     * @param operatorId 经办人 ID
     * @return 退还命令
     */
    @Override
    public RefundAdvanceDepositCommand toRefundAdvanceDepositCommand(
        RefundPrepayAdvanceDepositRequest request,
        Long accountId,
        Long operatorId) {
        if (request == null) {
            return null;
        }
        RefundAdvanceDepositCommand command = new RefundAdvanceDepositCommand();
        command.setEncounterId(request.getEncounterId());
        command.setPatientId(request.getPatientId());
        command.setEntererId(operatorId);
        command.setTenderedAmount(request.getRefundAmount());
        command.setDisplayAmount(request.getRefundAmount());
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        command.setPaymentEnum(PaymentType.UN_PAY.getCode());
        command.setPaymentDetails(this.toPaymentDetailItemCommands(request.getPaymentDetails(), accountId));
        return command;
    }

    /**
     * 支付草稿 ID → 住院支付结算命令。
     *
     * @param paymentId 支付主键
     * @return 结算命令
     */
    @Override
    public SettleInpatientPaymentCommand toSettleInpatientPaymentCommand(Long paymentId) {
        SettleInpatientPaymentCommand command = new SettleInpatientPaymentCommand();
        command.setPaymentId(paymentId);
        command.setOutcomeEnum(PaymentStatus.SUCCESS.getCode());
        command.setBillDate(LocalDateTime.now());
        return command;
    }

    /**
     * 收取结果 → 响应。
     *
     * @param paymentId      支付流水 ID
     * @param accountBalance 结算后展示余额（动态计算）
     * @return 收取响应
     */
    @Override
    public CollectPrepayAdvanceDepositResponse toCollectPrepayAdvanceDepositResponse(
        Long paymentId,
        BigDecimal accountBalance) {
        CollectPrepayAdvanceDepositResponse response = new CollectPrepayAdvanceDepositResponse();
        response.setPaymentId(paymentId);
        response.setAccountBalance(accountBalance);
        return response;
    }

    /**
     * 退费结果 → 响应。
     *
     * @param paymentId      支付流水 ID
     * @param accountBalance 结算后展示余额（动态计算）
     * @return 退费响应
     */
    @Override
    public RefundPrepayAdvanceDepositResponse toRefundPrepayAdvanceDepositResponse(
        Long paymentId,
        BigDecimal accountBalance) {
        RefundPrepayAdvanceDepositResponse response = new RefundPrepayAdvanceDepositResponse();
        response.setPaymentId(paymentId);
        response.setAccountBalance(accountBalance);
        return response;
    }

    /**
     * 按收付方向派生业务类型展示文案。
     *
     * @param paymentEnumCode 支付头 payment_enum（PAY/UN_PAY）
     * @return 收预交金/退预交金文案
     */
    private String resolveBizTypeText(String paymentEnumCode) {
        if (PaymentType.UN_PAY.getCode().equals(paymentEnumCode)) {
            return InpatientPrepayConstants.BIZ_TYPE_TEXT_REFUND;
        }
        return InpatientPrepayConstants.BIZ_TYPE_TEXT_COLLECT;
    }

    /**
     * 续收结算后 → 账户余额调整命令。
     *
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @param amount      收取金额（正数）
     * @param operatorId  操作员 ID
     * @return 余额调整命令
     */
    @Override
    public AdjustAdvanceDepositBalanceCommand toCollectBalanceAdjustCommand(
        Long patientId,
        Long encounterId,
        BigDecimal amount,
        Long operatorId) {
        AdjustAdvanceDepositBalanceCommand command = new AdjustAdvanceDepositBalanceCommand();
        command.setPatientId(patientId);
        command.setEncounterId(encounterId);
        command.setBalanceDelta(amount);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 退费结算后 → 账户余额调整命令。
     *
     * @param patientId    患者 ID
     * @param encounterId  就诊 ID
     * @param refundAmount 退费金额（正数，命令内取负增量）
     * @param operatorId   操作员 ID
     * @return 余额调整命令
     */
    @Override
    public AdjustAdvanceDepositBalanceCommand toRefundBalanceAdjustCommand(
        Long patientId,
        Long encounterId,
        BigDecimal refundAmount,
        Long operatorId) {
        AdjustAdvanceDepositBalanceCommand command = new AdjustAdvanceDepositBalanceCommand();
        command.setPatientId(patientId);
        command.setEncounterId(encounterId);
        command.setBalanceDelta(refundAmount.negate());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 明细 DTO → 领域支付明细命令。
     *
     * @param rows      前端明细
     * @param accountId 登记账户 ID
     * @return 明细命令列表
     */
    private List<PaymentDetailItemCommand> toPaymentDetailItemCommands(
        List<PrepayPaymentDetailDto> rows,
        Long accountId) {
        List<PaymentDetailItemCommand> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (PrepayPaymentDetailDto row : rows) {
            if (row == null) {
                continue;
            }
            PaymentDetailItemCommand item = new PaymentDetailItemCommand();
            item.setPayEnum(row.getPayChannelCode());
            item.setAmount(row.getAmount());
            Long detailAccountId = row.getAccountId();
            if (detailAccountId == null) {
                detailAccountId = accountId;
            }
            item.setAccountId(detailAccountId);
            items.add(item);
        }
        return items;
    }
}
