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

import com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleQueueItemDto;
import com.openhis.health.application.inpatient.financial.settle.dto.SettledPaymentBriefDto;
import com.openhis.health.application.inpatient.financial.settle.request.ConfirmInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PageInpatientSettleQueueRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PreSettleInpatientChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
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
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link InpatientSettleMapper} 的 Spring 实现：字段映射；禁止承载业务流程判断。
 */
@Component
public class InpatientSettleMapperImpl implements InpatientSettleMapper {

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
     * @param accountBalance          展示余额；调用方经 {@code InpatientDisplayAccountBalanceAssembler} 计算
     * @return 队列行 DTO
     */
    @Override
    public InpatientSettleQueueItemDto toInpatientSettleQueueItemDto(
        InpatientEncounterContextQueryModel contextRow,
        Account account,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
        BigDecimal pendingSettlementTotal,
        LocalDateTime lastSettleTime,
        BigDecimal accountBalance) {
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
        dto.setEncounterBusNo(this.firstNonBlank(contextRow.getEncounterBusNo(), overlayBusNo));
        dto.setPatientName(this.firstNonBlank(contextRow.getPatientName(), overlayPatientName));
        String genderCode = this.firstNonBlank(contextRow.getGenderEnum(), overlayGenderEnum);
        dto.setGenderEnum(genderCode);
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderCode);
        if (gender != null) {
            dto.setGenderEnumText(gender.getDisplay());
        }
        dto.setAgeText(this.firstNonBlank(contextRow.getAgeText(), overlayAgeText));
        dto.setOrganizationName(this.firstNonBlank(contextRow.getOrganizationName(), overlayOrgName));
        dto.setWardName(this.firstNonBlank(contextRow.getWardName(), overlayWardName));
        dto.setBedName(this.firstNonBlank(contextRow.getBedName(), overlayBedName));
        dto.setStatusEnum(contextRow.getStatusEnum());
        String statusText = this.firstNonBlank(contextRow.getStatusEnumText(), overlayStatusText);
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
        dto.setContractDisplay(this.firstNonBlank(overlayContract, null));
        dto.setAccountBalance(accountBalance);
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
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        if (request == null) {
            return command;
        }
        command.setEncounterId(request.getEncounterId());
        List<String> statusIn = new ArrayList<>();
        statusIn.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        command.setStatusEnumIn(statusIn);
        command.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        LocalDate from = request.getChargeDateFrom();
        if (from == null) {
            from = admissionDate;
        }
        LocalDate to = request.getChargeDateTo();
        if (to == null) {
            to = LocalDate.now();
        }
        command.setBusinessDateFrom(from);
        command.setBusinessDateTo(to);
        return command;
    }

    /**
     * 收费明细 + 产品展示 DTO → 费用行 DTO 列表。
     *
     * @param chargeItems  收费明细列表
     * @param productByKey 产品展示 DTO 索引（复合键）
     * @param defaultSelected 是否默认勾选
     * @return 费用行 DTO 列表
     */
    @Override
    public List<InpatientSettleChargeLineDto> toInpatientSettleChargeLineDtoList(
        List<ChargeItem> chargeItems,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        boolean defaultSelected) {
        List<InpatientSettleChargeLineDto> lines = new ArrayList<>();
        if (chargeItems == null) {
            return lines;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            InpatientSettleChargeLineDto dto = new InpatientSettleChargeLineDto();
            dto.setChargeItemId(chargeItem.getId());
            dto.setUnitPrice(chargeItem.getUnitPrice());
            dto.setQuantityValue(chargeItem.getQuantityValue());
            dto.setQuantityUnit(chargeItem.getQuantityUnit());
            dto.setLineAmount(chargeItem.getTotalPrice());
            dto.setStatusEnum(chargeItem.getStatusEnum());
            ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, chargeItem.getStatusEnum());
            if (status != null) {
                dto.setStatusEnumText(status.getDisplay());
            }
            dto.setOccurrenceTime(chargeItem.getOccurrenceTime());
            GenerateSource source = EnumUtil.fromCode(GenerateSource.class, chargeItem.getGenerateSourceEnum());
            if (source != null) {
                dto.setSourceText(source.getDisplay());
            }
            ProductDefinitionDisplayDto product = null;
            if (productByKey != null && chargeItem.getProductTable() != null && chargeItem.getProductId() != null) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    chargeItem.getProductTable(), chargeItem.getProductId());
                product = productByKey.get(key);
            }
            if (product != null) {
                dto.setItemName(product.getProductName());
                dto.setSpecText(product.getTotalVolume());
                dto.setCategoryCode(product.getCategoryCode());
                dto.setCategoryDisplay(product.getCategoryCodeDictText());
            }
            dto.setSelected(defaultSelected);
            lines.add(dto);
        }
        return lines;
    }

    /**
     * 费用行列表 → 大类汇总 DTO 列表。
     *
     * @param lines 费用行 DTO 列表
     * @return 大类汇总列表
     */
    @Override
    public List<InpatientSettleCategorySummaryDto> toInpatientSettleCategorySummaryDtoList(
        List<InpatientSettleChargeLineDto> lines) {
        Map<String, InpatientSettleCategorySummaryDto> accumulator = new LinkedHashMap<>();
        if (lines == null) {
            return new ArrayList<>();
        }
        for (InpatientSettleChargeLineDto line : lines) {
            if (line == null) {
                continue;
            }
            String categoryCode = line.getCategoryCode();
            if (StringUtils.isBlank(categoryCode)) {
                categoryCode = "_UNKNOWN";
            }
            InpatientSettleCategorySummaryDto summary = accumulator.get(categoryCode);
            if (summary == null) {
                summary = new InpatientSettleCategorySummaryDto();
                summary.setCategoryCode(categoryCode);
                summary.setCategoryDisplay(line.getCategoryDisplay());
                summary.setAmount(BigDecimal.ZERO);
                summary.setLineCount(0);
                accumulator.put(categoryCode, summary);
            }
            BigDecimal lineAmount = line.getLineAmount();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            summary.setAmount(summary.getAmount().add(lineAmount));
            summary.setLineCount(summary.getLineCount() + 1);
        }
        return new ArrayList<>(accumulator.values());
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
     * @return 正式结算响应
     */
    @Override
    public ConfirmInpatientSettleResponse toConfirmInpatientSettleResponse(
        Long paymentId,
        Long invoiceDraftId,
        boolean dischargeFullSettle,
        Long advanceRefundPaymentId) {
        ConfirmInpatientSettleResponse response = new ConfirmInpatientSettleResponse();
        response.setPaymentId(paymentId);
        response.setInvoiceDraftId(invoiceDraftId);
        response.setDischargeFullSettle(dischargeFullSettle);
        response.setAdvanceRefundPaymentId(advanceRefundPaymentId);
        response.setSuccessMessageCode(InpatientSettleConstants.MESSAGE_CODE_SETTLE_SUCCESS);
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
     * 成功结算单列表 + 渠道展示 → 摘要 DTO 列表。
     *
     * @param payments          支付头列表
     * @param payMethodTexts    paymentId → 渠道展示
     * @return 已结算单摘要列表
     */
    @Override
    public List<SettledPaymentBriefDto> toSettledPaymentBriefDtoList(
        List<PaymentReconciliationQueryModel> payments,
        Map<Long, String> payMethodTexts) {
        List<SettledPaymentBriefDto> rows = new ArrayList<>();
        if (payments == null) {
            return rows;
        }
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null) {
                continue;
            }
            SettledPaymentBriefDto dto = new SettledPaymentBriefDto();
            dto.setPaymentId(payment.getId());
            dto.setSettleTime(payment.getBillDate());
            dto.setReceivableTotal(payment.getDisplayAmount());
            if (payment.getId() != null && payMethodTexts != null) {
                dto.setPayMethodText(payMethodTexts.get(payment.getId()));
            }
            rows.add(dto);
        }
        return rows;
    }

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
        return response;
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param primary   优先值
     * @param fallback  回落值
     * @return 非空白字符串或 null
     */
    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.isNotBlank(primary)) {
            return primary;
        }
        if (StringUtils.isNotBlank(fallback)) {
            return fallback;
        }
        return null;
    }
}
