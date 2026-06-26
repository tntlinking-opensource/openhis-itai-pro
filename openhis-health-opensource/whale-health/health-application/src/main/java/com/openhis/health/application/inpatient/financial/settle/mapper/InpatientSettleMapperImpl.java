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

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;

import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
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
import com.openhis.health.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteInpatientSettleCancelRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.module.querysupport.financial.InpatientSettleReadAssembler;
import com.openhis.health.module.querysupport.financial.InpatientSettleChargesQuery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link InpatientSettleMapper} 的 Spring 实现：字段映射；禁止承载业务流程判断。
 */
@Component
@RequiredArgsConstructor
public class InpatientSettleMapperImpl implements InpatientSettleMapper {

    private final InpatientSettleReadAssembler inpatientSettleReadAssembler;

    /**
     * 初始化下拉选项 → 页面初始化响应。
     *
     * @param queueTabs           队列页签选项；调用方保证非 null
     * @param presentationModes   费用呈现模式选项；调用方保证非 null
     * @param paymentChannels     支付渠道选项；调用方保证非 null
     * @return 初始化响应
     */
    @Override
    public LoadInpatientSettlePageInitResponse toLoadInpatientSettlePageInitResponse(
        List<OptionDto> queueTabs,
        List<OptionDto> presentationModes,
        List<OptionDto> paymentChannels) {
        LoadInpatientSettlePageInitResponse response = new LoadInpatientSettlePageInitResponse();
        response.setSettleQueueTabOptions(queueTabs);
        response.setPresentationModeOptions(presentationModes);
        response.setPaymentChannelOptions(paymentChannels);
        response.setDefaultSettleQueueTab(InpatientSettleQueueTab.PENDING_SETTLE);
        response.setDefaultPresentationMode(InpatientSettleConstants.PRESENTATION_MODE_DETAIL);
        return response;
    }

    /**
     * 队列分页请求 → 住院就诊上下文分页查询。
     *
     * @param request 分页请求；调用方保证非 null
     * @return 分页入参；request 为 null 时返回 null
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(PageInpatientSettleQueueRequest request) {
        if (request == null) {
            return null;
        }
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setAdmissionDate(request.getAdmissionDate());
        query.setSettleQueue(Boolean.TRUE);
        String tab = request.getSettleQueueTab();
        if (StringUtils.isBlank(tab)) {
            tab = InpatientSettleQueueTab.PENDING_SETTLE;
        }
        query.setSettleQueueTab(tab.trim());
        return query;
    }

    /**
     * 上下文行 + 账户 + 展示 overlay → 结算队列卡片 DTO。
     *
     * @param contextRow              住院就诊上下文；null 时返回 null
     * @param account                 个人现金户；未开户时为 null
     * @param displayOverlay          展示回填 overlay；可 null
     * @param pendingSettlementTotal  待结算合计；可 null
     * @param lastSettleTime          最近结算时间；已结算页签使用，可 null
     * @param lastSettleTime          最近结算时间；已结算页签使用，可 null
     * @return 队列行 DTO
     */
    @Override
    public InpatientSettleQueueItemDto toInpatientSettleQueueItemDto(
        InpatientEncounterContextQueryModel contextRow,
        Account account,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
        BigDecimal pendingSettlementTotal,
        LocalDateTime lastSettleTime) {
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
            overlayAgeText = displayOverlay.getAgeText();
        }
        InpatientSettleQueueItemDto dto = new InpatientSettleQueueItemDto();
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
        String statusText = firstNonBlank(contextRow.getStatusEnumText(), overlayStatusText);
        if (StringUtils.isBlank(statusText)) {
            statusText = InpatientEncounterStatusDisplayUtil.resolveDisplayTextForReadModel(
                contextRow.getStatusEnum(),
                contextRow.getTransferOrgId(),
                contextRow.getDischargeTypeEnum());
        }
        dto.setStatusEnumText(statusText);
        dto.setStartTime(contextRow.getStartTime());
        dto.setPendingSettlementTotal(pendingSettlementTotal);
        dto.setLastSettleTime(lastSettleTime);
        dto.setContractDisplay(firstNonBlank(overlayContract, null));
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
    public PageInpatientSettleQueueResponse toPageInpatientSettleQueueResponse(
        List<InpatientSettleQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page) {
        return PagedResultBuilder.build(new PageInpatientSettleQueueResponse(), rows, page);
    }

    /**
     * 摘要装配 → 结算摘要响应。
     *
     * @param readModel            住院信息读模型
     * @param account              个人现金户
     * @param pendingTotal         全部待结算合计
     * @param selectedTotal        选中合计
     * @param prepayNet            预交金净值
     * @param balanceHint          差额提示
     * @param displayBalance       展示余额
     * @return 摘要响应
     */
    @Override
    public LoadInpatientSettleSummaryResponse toLoadInpatientSettleSummaryResponse(
        InpatientEncounterContextQueryModel contextRow,
        Account account,
        BigDecimal pendingTotal,
        BigDecimal selectedTotal,
        BigDecimal prepayNet,
        BigDecimal balanceHint,
        BigDecimal displayBalance) {
        LoadInpatientSettleSummaryResponse response = new LoadInpatientSettleSummaryResponse();
        if (contextRow != null) {
            response.setEncounterId(contextRow.getEncounterId());
            response.setPatientId(contextRow.getPatientId());
            response.setEncounterBusNo(contextRow.getEncounterBusNo());
            response.setPatientName(contextRow.getPatientName());
            response.setOrganizationName(contextRow.getOrganizationName());
            response.setWardName(contextRow.getWardName());
            response.setBedName(contextRow.getBedName());
            response.setStatusEnum(contextRow.getStatusEnum());
            String statusText = contextRow.getStatusEnumText();
            if (StringUtils.isBlank(statusText)) {
                statusText = InpatientEncounterStatusDisplayUtil.resolveDisplayTextForReadModel(
                    contextRow.getStatusEnum(),
                    contextRow.getTransferOrgId(),
                    contextRow.getDischargeTypeEnum());
            }
            response.setStatusEnumText(statusText);
        }
        response.setPendingSettlementTotal(pendingTotal);
        response.setPrepayNetBalance(prepayNet);
        response.setSelectedTotal(selectedTotal);
        response.setBalanceHint(balanceHint);
        response.setDisplayAccountBalance(displayBalance);
        return response;
    }

    /**
     * 费用查询请求 + 缺省入院日 → 收费明细查询命令。
     *
     * @param request        费用查询请求
     * @param admissionDate  缺省业务日起（入院日）
     * @return 查询命令
     */
    @Override
    public ChargeItemQueryCommand toChargeItemQueryCommand(
        QueryInpatientSettleChargesRequest request,
        LocalDate admissionDate) {
        if (request == null) {
            return new ChargeItemQueryCommand();
        }
        InpatientSettleChargesQuery query = new InpatientSettleChargesQuery()
            .setEncounterId(request.getEncounterId())
            .setPatientId(request.getPatientId())
            .setPresentationMode(request.getPresentationMode())
            .setChargeDateFrom(request.getChargeDateFrom())
            .setChargeDateTo(request.getChargeDateTo());
        return inpatientSettleReadAssembler.toChargeItemQueryCommand(query, admissionDate);
    }

    /**
     * 收费明细实体 → 结算行 DTO（含目录展示与默认勾选态）。
     *
     * @param chargeItems      收费明细实体列表
     * @param productByKey     目录锚点 → 展示 DTO
     * @param defaultSelected  行默认是否勾选
     * @return 结算费用行列表
     */
    @Override
    public List<InpatientSettleChargeLineDto> toInpatientSettleChargeLineDtoList(
        List<ChargeItem> chargeItems,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        boolean defaultSelected) {
        return inpatientSettleReadAssembler.toInpatientSettleChargeLineDtoList(
            chargeItems, productByKey, defaultSelected);
    }

    /**
     * 结算费用行 → 大类汇总 DTO（按 {@code categoryCode} 分组求和）。
     *
     * @param lines 结算费用行
     * @return 大类汇总列表
     */
    @Override
    public List<InpatientSettleCategorySummaryDto> toInpatientSettleCategorySummaryDtoList(
        List<InpatientSettleChargeLineDto> lines) {
        return inpatientSettleReadAssembler.toInpatientSettleCategorySummaryDtoList(lines);
    }

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
    @Override
    public QueryInpatientSettleChargesResponse toQueryInpatientSettleChargesResponse(
        Long encounterId,
        String mode,
        List<InpatientSettleChargeLineDto> lines,
        List<InpatientSettleCategorySummaryDto> categories,
        BigDecimal total,
        boolean defaultSelectAll) {
        QueryInpatientSettleChargesResponse response = new QueryInpatientSettleChargesResponse();
        response.setEncounterId(encounterId);
        response.setPresentationMode(mode);
        response.setLines(lines);
        response.setCategories(categories);
        response.setPendingSettlementTotal(total);
        response.setDefaultSelectAll(defaultSelectAll);
        return response;
    }

    /**
     * 收费明细 ID 列表 → 批量命令。
     *
     * @param chargeItemIds 收费明细 ID 列表
     * @param operatorId    操作员 ID
     * @return 批量命令
     */
    @Override
    public BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId) {
        BatchChargeItemIdsCommand command = new BatchChargeItemIdsCommand();
        command.setOperatorId(operatorId);
        List<BatchChargeItemIdsCommand.Item> items = new ArrayList<>();
        if (chargeItemIds != null) {
            for (Long chargeItemId : chargeItemIds) {
                if (chargeItemId == null) {
                    continue;
                }
                BatchChargeItemIdsCommand.Item item = new BatchChargeItemIdsCommand.Item();
                item.setChargeItemId(chargeItemId);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

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
    @Override
    public CreatePaymentDraftFromPreSettleCommand toCreatePaymentDraftFromPreSettleCommand(
        PreSettleInpatientChargesRequest request,
        BigDecimal receivableTotal,
        List<InpatientSettlePaymentDetailDto> draftDetails,
        Long operatorId,
        String chargeItemIdsJoined,
        String contractNo) {
        CreatePaymentDraftFromPreSettleCommand command = new CreatePaymentDraftFromPreSettleCommand();
        if (request != null) {
            command.setEncounterId(request.getEncounterId());
            command.setPatientId(request.getPatientId());
        }
        command.setChargeItemIds(chargeItemIdsJoined);
        command.setKindEnum(PaymentKind.INPATIENT_CLINIC);
        command.setPaymentEnum(PaymentChannel.CASHIER.getCode());
        command.setEntererId(operatorId);
        command.setTenderedAmount(receivableTotal);
        command.setDisplayAmount(receivableTotal);
        command.setContractNo(contractNo);
        return command;
    }

    /**
     * 预结算结果 → 响应。
     *
     * @param paymentDraftId  支付草稿 ID
     * @param receivableTotal 应收合计
     * @return 预结算响应
     */
    @Override
    public PreSettleInpatientChargesResponse toPreSettleInpatientChargesResponse(
        Long paymentDraftId,
        BigDecimal receivableTotal) {
        PreSettleInpatientChargesResponse response = new PreSettleInpatientChargesResponse();
        response.setPaymentDraftId(paymentDraftId);
        response.setReceivableTotal(receivableTotal);
        response.setPreviewText("应收合计：" + receivableTotal + " 元");
        return response;
    }

    /**
     * 正式结算请求 → 住院支付结算命令。
     *
     * @param request    正式结算请求
     * @param operatorId 操作员 ID（审计用，命令体不含明细）
     * @return 住院支付结算命令
     */
    @Override
    public SettleInpatientPaymentCommand toSettleInpatientPaymentCommand(
        ConfirmInpatientSettleRequest request,
        Long operatorId) {
        SettleInpatientPaymentCommand command = new SettleInpatientPaymentCommand();
        if (request != null) {
            command.setPaymentId(request.getPaymentDraftId());
            LocalDateTime billDate = request.getBillDate();
            if (billDate == null) {
                billDate = LocalDateTime.now();
            }
            command.setBillDate(billDate);
        }
        command.setOutcomeEnum(PaymentStatus.SUCCESS.getCode());
        return command;
    }

    /**
     * 支付流水 + 支付 ID → 发票草稿命令。
     *
     * @param payment   支付流水读模型
     * @param paymentId 支付主键
     * @return 发票草稿命令
     */
    @Override
    public CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(
        PaymentReconciliationQueryModel payment,
        Long paymentId) {
        CreateDraftInvoiceCommand command = new CreateDraftInvoiceCommand();
        if (payment != null) {
            command.setPatientId(payment.getPatientId());
            command.setAmount(payment.getDisplayAmount());
            command.setOperatorId(payment.getEntererId());
        }
        command.setPaymentId(paymentId);
        command.setTypeCode(InpatientSettleConstants.INVOICE_TYPE_INPATIENT);
        return command;
    }

    /**
     * 出院全结 → 就诊财务终态命令。
     *
     * @param encounterId    就诊 ID
     * @param settlementTime 结算时间
     * @param operatorId     操作员 ID
     * @return 出院财务全结命令
     */
    @Override
    public CompleteInpatientFinancialSettlementCommand toCompleteInpatientFinancialSettlementCommand(
        Long encounterId,
        LocalDateTime settlementTime,
        Long operatorId) {
        CompleteInpatientFinancialSettlementCommand command = new CompleteInpatientFinancialSettlementCommand();
        command.setEncounterId(encounterId);
        command.setSettlementTime(settlementTime);
        command.setOperatorId(operatorId);
        return command;
    }

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
    @Override
    public ConfirmInpatientSettleResponse toConfirmInpatientSettleResponse(
        Long paymentId,
        Long invoiceDraftId,
        boolean dischargeFullSettle,
        Long advanceRefundPaymentId,
        String ybSetlId) {
        ConfirmInpatientSettleResponse response = new ConfirmInpatientSettleResponse();
        response.setPaymentId(paymentId);
        response.setInvoiceDraftId(invoiceDraftId);
        response.setInvoiceStatus(InvoiceStatus.DRAFT.getCode());
        response.setDischargeFullSettle(dischargeFullSettle);
        response.setAdvanceRefundPaymentId(advanceRefundPaymentId);
        response.setSuccessMessageCode(InpatientSettleConstants.MESSAGE_CODE_SETTLE_SUCCESS);
        if (StringUtils.isNotBlank(ybSetlId)) {
            response.setYbSetlId(ybSetlId.trim());
            response.setInsuranceSettled(Boolean.TRUE);
        }
        return response;
    }

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
    @Override
    public RefundAdvanceDepositCommand toDischargeDepositRefundCommand(
        Long encounterId,
        Long patientId,
        Long accountId,
        BigDecimal refundAmount,
        Long operatorId) {
        RefundAdvanceDepositCommand command = new RefundAdvanceDepositCommand();
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        command.setEntererId(operatorId);
        command.setTenderedAmount(refundAmount);
        command.setDisplayAmount(refundAmount);
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        command.setPaymentEnum(PaymentType.UN_PAY.getCode());
        PaymentDetailItemCommand detail = new PaymentDetailItemCommand();
        detail.setPayEnum(PaymentChannel.CASHIER.getCode());
        detail.setAmount(refundAmount);
        detail.setAccountId(accountId);
        List<PaymentDetailItemCommand> details = new ArrayList<>();
        details.add(detail);
        command.setPaymentDetails(details);
        return command;
    }

    /**
     * 结算少补 → 收取预交金领域命令（复用用户渠道实付明细）。
     *
     * @param encounterId      就诊 ID
     * @param patientId        患者 ID
     * @param accountId        登记个人现金户 ID
     * @param supplementAmount 少补金额（正数）
     * @param paymentDetails   用户渠道实付明细
     * @param operatorId       操作员 ID
     * @return 收取命令
     */
    @Override
    public CollectAdvanceDepositCommand toSettleSupplementCollectCommand(
        Long encounterId,
        Long patientId,
        Long accountId,
        BigDecimal supplementAmount,
        List<InpatientSettlePaymentDetailDto> paymentDetails,
        Long operatorId) {
        CollectAdvanceDepositCommand command = new CollectAdvanceDepositCommand();
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        command.setEntererId(operatorId);
        command.setTenderedAmount(supplementAmount);
        command.setDisplayAmount(supplementAmount);
        command.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        command.setPaymentEnum(PaymentType.PAY.getCode());
        List<PaymentDetailItemCommand> details = new ArrayList<>();
        if (paymentDetails != null) {
            for (InpatientSettlePaymentDetailDto row : paymentDetails) {
                // 跳过空行或零金额，避免落无效渠道明细。
                if (row == null || row.getAmount() == null) {
                    continue;
                }
                PaymentDetailItemCommand item = new PaymentDetailItemCommand();
                item.setPayEnum(row.getPayChannelCode());
                item.setAmount(row.getAmount());
                item.setAccountId(accountId);
                details.add(item);
            }
        }
        command.setPaymentDetails(details);
        return command;
    }

    /**
     * 住院结算/反结历史 + 渠道展示 → 摘要 DTO 列表（含 NET 与可反结门禁）。
     *
     * @param payments                          支付头列表
     * @param payMethodTexts                    paymentId → 渠道展示
     * @param encounter                         就诊快照
     * @param paymentReconciliationQueryService 支付读侧
     * @return 已结算单摘要列表
     */
    @Override
    public List<SettledPaymentBriefDto> toSettledPaymentBriefDtoList(
        List<PaymentReconciliationQueryModel> payments,
        Map<Long, String> payMethodTexts,
        EncounterDetailQueryModel encounter,
        PaymentReconciliationQueryService paymentReconciliationQueryService) {
        return inpatientSettleReadAssembler.toSettledPaymentBriefDtoList(
            payments, payMethodTexts, encounter, paymentReconciliationQueryService);
    }

    /**
     * 已结算单查阅 → 响应（含 netSettleTotal）。
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
    @Override
    public LoadSettledInpatientPaymentViewResponse toLoadSettledInpatientPaymentViewResponse(
        Long encounterId,
        List<SettledPaymentBriefDto> payments,
        Long activePaymentId,
        LocalDateTime settleTime,
        String operatorName,
        BigDecimal receivableTotal,
        String payMethodText,
        List<InpatientSettleChargeLineDto> lines,
        List<InpatientSettleCategorySummaryDto> categories) {
        LoadSettledInpatientPaymentViewResponse response = new LoadSettledInpatientPaymentViewResponse();
        response.setEncounterId(encounterId);
        response.setPayments(payments);
        response.setActivePaymentId(activePaymentId);
        response.setSettleTime(settleTime);
        response.setOperatorName(operatorName);
        response.setReceivableTotal(receivableTotal);
        response.setPayMethodText(payMethodText);
        response.setLines(lines);
        response.setCategories(categories);
        response.setNetSettleTotal(inpatientSettleReadAssembler.sumNetSettleTotal(payments));
        return response;
    }

    /**
     * 原正向结算支付单 + 渠道明细 → 住院反结逆向单领域命令（DM-FIN-002 §6.1.7）。
     *
     * @param originalPayment 成功结算支付单读模型
     * @param paymentDetails  原单渠道明细；调用方保证非 null
     * @param operatorId      操作员 ID
     * @return 逆向单创建命令
     */
    @Override
    public CancelInpatientSettleAsRefundCommand toCancelInpatientSettleAsRefundCommand(
        PaymentReconciliationQueryModel originalPayment,
        List<PaymentRecDetailQueryModel> paymentDetails,
        Long operatorId) {
        CancelInpatientSettleAsRefundCommand command = new CancelInpatientSettleAsRefundCommand();
        command.setRelationId(originalPayment.getId());
        command.setEncounterId(originalPayment.getEncounterId());
        command.setPatientId(originalPayment.getPatientId());
        command.setEntererId(operatorId);
        command.setTenderedAmount(originalPayment.getTenderedAmount());
        command.setDisplayAmount(originalPayment.getDisplayAmount());
        command.setChargeItemIds(originalPayment.getChargeItemIds());
        command.setKindEnum(originalPayment.getKindEnum());
        // 逆向反结单须 UN_PAY，与门诊退费/预交金退还口径一致（DM-FIN-002 §6.1.7）
        command.setPaymentEnum(PaymentType.UN_PAY.getCode());
        command.setPaymentDetails(this.toRefundPaymentDetailItemCommands(paymentDetails));
        return command;
    }

    /**
     * 反结结果 → 响应。
     *
     * @param refundPaymentId 逆向反结支付单 ID
     * @return 住院反结响应
     */
    @Override
    public ReverseInpatientSettleResponse toReverseInpatientSettleResponse(Long refundPaymentId) {
        ReverseInpatientSettleResponse response = new ReverseInpatientSettleResponse();
        response.setRefundPaymentId(refundPaymentId);
        response.setSucceeded(Boolean.TRUE);
        return response;
    }

    /**
     * 出院嘱退回草稿 → 领域命令（BR-IP-RVS-007）。
     *
     * @param serviceRequestId 出院类诊疗 ID
     * @param operatorId       操作员 ID
     * @param performerCheckId 校对人 ID
     * @param checkTime        校对时间
     * @return 退回草稿命令
     */
    @Override
    public RejectServiceRequestToDraftCommand toRejectDischargeServiceRequestToDraftCommand(
        Long serviceRequestId,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime) {
        RejectServiceRequestToDraftCommand command = new RejectServiceRequestToDraftCommand();
        command.setOperatorId(operatorId);
        command.setPerformerCheckId(performerCheckId);
        command.setCheckTime(checkTime);
        if (serviceRequestId == null) {
            return command;
        }
        RejectServiceRequestToDraftCommand.RejectServiceRequestToDraftItemCommand line =
            new RejectServiceRequestToDraftCommand.RejectServiceRequestToDraftItemCommand();
        line.setId(serviceRequestId);
        line.setReason(InpatientSettleConstants.DISCHARGE_SERVICE_REJECT_REASON_INPATIENT_REVERSE);
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 反结收口 → 领域命令。
     *
     * @param originalPaymentId 原结算支付单 ID
     * @param reversePaymentId  逆向反结支付单 ID
     * @param ybSetlId          撤销后的医保结算 ID；无医保时为 null
     * @return 收口命令
     */
    @Override
    public CompleteInpatientSettleCancelRefundCommand toCompleteInpatientSettleCancelRefundCommand(
        Long originalPaymentId,
        Long reversePaymentId,
        String ybSetlId) {
        CompleteInpatientSettleCancelRefundCommand command = new CompleteInpatientSettleCancelRefundCommand();
        command.setOriginalPaymentId(originalPaymentId);
        command.setReversePaymentId(reversePaymentId);
        command.setYbSettlements(PaymentYbSettlementSupport.optionalConfirmedItem(ybSetlId));
        return command;
    }

    /**
     * 按支付单查询关联票据。
     *
     * @param paymentId 结算支付单 ID
     * @return 票据查询命令
     */
    @Override
    public InvoiceQueryCommand toInvoiceQueryByPaymentId(Long paymentId) {
        InvoiceQueryCommand command = new InvoiceQueryCommand();
        command.setPaymentId(paymentId);
        return command;
    }

    /**
     * 构建票据作废/冲红命令。
     *
     * @param invoiceId  票据 ID
     * @param reason     作废或冲红原因
     * @param operatorId 操作员 ID
     * @return 作废/冲红命令
     */
    @Override
    public CancelInvoiceCommand toCancelInvoiceCommand(Long invoiceId, String reason, Long operatorId) {
        CancelInvoiceCommand command = new CancelInvoiceCommand();
        command.setInvoiceId(invoiceId);
        command.setReason(reason);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 原单渠道明细 → 逆向退款明细命令项。
     *
     * @param paymentDetails 原单渠道明细
     * @return 逆向退款明细命令列表
     */
    private List<PaymentDetailItemCommand> toRefundPaymentDetailItemCommands(
        List<PaymentRecDetailQueryModel> paymentDetails) {
        List<PaymentDetailItemCommand> items = new ArrayList<>();
        if (paymentDetails == null) {
            return items;
        }
        for (PaymentRecDetailQueryModel detail : paymentDetails) {
            if (detail == null) {
                continue;
            }
            PaymentDetailItemCommand item = new PaymentDetailItemCommand();
            item.setPredecessorId(detail.getId());
            item.setTargetEnum(detail.getTargetEnum());
            item.setAccountId(detail.getAccountId());
            item.setAccountCode(detail.getAccountCode());
            item.setPayEnum(detail.getPayEnum());
            item.setPayLevelEnum(detail.getPayLevelEnum());
            item.setAmount(detail.getAmount());
            item.setReturnAmount(detail.getReturnAmount());
            item.setChargeAmount(detail.getChargeAmount());
            item.setPayTransNo(detail.getPayTransNo());
            item.setPayTransText(detail.getPayTransText());
            item.setPayTransDate(detail.getPayTransDate());
            item.setBeforeBalance(detail.getBeforeBalance());
            item.setAfterBalance(detail.getAfterBalance());
            item.setResultEnum(detail.getResultEnum());
            items.add(item);
        }
        return items;
    }
}
