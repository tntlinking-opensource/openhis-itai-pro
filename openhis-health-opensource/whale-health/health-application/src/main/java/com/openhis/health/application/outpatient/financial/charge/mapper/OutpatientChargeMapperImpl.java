/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.mapper;

import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.financial.charge.dto.ChargeQueueRowDto;
import com.openhis.health.application.outpatient.financial.charge.dto.PaymentDetailSubmitDto;
import com.openhis.health.application.outpatient.financial.charge.dto.PreSettleChargeLinePreviewDto;
import com.openhis.health.application.outpatient.financial.charge.request.ConfirmOutpatientChargeSettlementRequest;
import com.openhis.health.application.outpatient.financial.charge.response.ConfirmOutpatientChargeSettlementResponse;
import com.openhis.health.application.outpatient.financial.charge.response.LoadChargePageInitResponse;
import com.openhis.health.application.outpatient.financial.charge.response.PageOutpatientChargeQueueResponse;
import com.openhis.health.application.outpatient.financial.charge.response.PreSettleOutpatientChargesResponse;
import com.openhis.health.application.outpatient.financial.charge.response.SwitchEncounterChargeItemsAccountResponse;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.constants.OutpatientChargeQueueTab;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link OutpatientChargeMapper} 实现：门诊收费应用层字段映射器。
 *
 * <p><b>职责</b>：将 HTTP 请求/DTO 装配为领域命令，将领域查询模型/读模型装配为 HTTP 响应；
 * 不包含业务规则与状态机推演，仅做字段搬运与默认值填充。</p>
 *
 * <p><b>对应接口规格</b>：IF-OPC-01 ～ IF-OPC-07（门诊收费队列、预结算、正式结算、转账户）。</p>
 *
 * <p><b>边界假设</b>：
 * <ul>
 *   <li>入参非空校验由 {@code OutpatientChargeAppService} 或校验框架完成，本层仅做防御性空指针回避。</li>
 *   <li>枚举码与展示文本的映射优先使用 {@link com.openhis.health.domain.share.utils.EnumUtil}，避免硬编码中文。</li>
 *   <li>金额字段统一使用 {@link BigDecimal}，不处理分/元换算（由展示层或前端完成）。</li>
 * </ul></p>
 */
@Component
public class OutpatientChargeMapperImpl implements OutpatientChargeMapper {

    /**
     * 装配收费页初始化响应。
     *
     * @param chargeItemStatusOptions 收费项状态下拉选项
     * @return 初始化响应
     */
    @Override
    public LoadChargePageInitResponse toLoadChargePageInitResponse(
        List<OptionDto> chargeItemStatusOptions,
        List<OptionDto> paymentChannelOptions) {
        LoadChargePageInitResponse response = new LoadChargePageInitResponse();
        response.setChargeItemStatusOptions(chargeItemStatusOptions);
        response.setPaymentChannelOptions(paymentChannelOptions);
        return response;
    }

    /**
     * 将门诊信息读模型装配为收费队列行。
     *
     * @param baseRows 门诊信息读模型分页行
     * @param flags 就诊队列标签映射
     * @return 收费队列行列表
     */
    @Override
    public List<ChargeQueueRowDto> toChargeQueueRowDtoList(List<OutpatientEncounterContextQueryModel> baseRows, Map<Long, String> flags) {
        List<ChargeQueueRowDto> rows = new ArrayList<>();
        // 空读模型：与前端表格空数据约定一致，返回空列表而非 null，避免 NPE。
        if (baseRows == null) {
            return rows;
        }
        // 逐行搬运读模型字段到列表行，性别枚举由码转展示文本。
        for (OutpatientEncounterContextQueryModel baseRow : baseRows) {
            ChargeQueueRowDto row = new ChargeQueueRowDto();
            row.setEncounterId(baseRow.getEncounterId());
            row.setEncounterBusNo(baseRow.getEncounterBusNo());
            row.setPatientId(baseRow.getPatientId());
            row.setPatientName(baseRow.getPatientName());
            row.setGenderCode(baseRow.getGenderEnum());
            // 性别码→展示文本：读模型存 code，工作站展示需要中文性别。
            AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, baseRow.getGenderEnum());
            if (gender != null) {
                row.setGenderEnumText(gender.getDisplay());
            }
            row.setAgeText(baseRow.getAgeText());
            row.setOrganizationName(baseRow.getOrganizationName());
            row.setDoctorName(baseRow.getDoctorName());
            row.setStartTime(baseRow.getStartTime());
            row.setTotalAmount(baseRow.getTotalAmount());
            row.setPaidAmount(baseRow.getPaidAmount());
            // 队列标签：由外部 flags 映射提供（如「待收费」「已部分收费」），未命中或 null 时回落为默认待处理标签。
            if (flags != null && baseRow.getEncounterId() != null) {
                row.setQueueLabelCode(flags.get(baseRow.getEncounterId()));
            }
            if (row.getQueueLabelCode() == null) {
                row.setQueueLabelCode(OutpatientChargeQueueTab.PENDING);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 装配收费队列分页响应（IF-OPC-01；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 为 1-based 自然数；Spring Data 页码换算由 {@link PagedResultBuilder} 完成。</p>
     *
     * @param page 分页对象
     * @param records 当前页记录
     * @return 分页响应
     */
    @Override
    public PageOutpatientChargeQueueResponse toPageOutpatientChargeQueueResponse(Page<?> page, List<ChargeQueueRowDto> records) {
        return PagedResultBuilder.build(new PageOutpatientChargeQueueResponse(), records, page);
    }

    /**
     * 装配批量收费项命令。
     *
     * @param chargeItemIds 收费项主键列表
     * @param operatorId 操作人
     * @return 批量命令
     */
    @Override
    public BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId) {
        BatchChargeItemIdsCommand command = new BatchChargeItemIdsCommand();
        command.setOperatorId(operatorId);
        List<BatchChargeItemIdsCommand.Item> items = new ArrayList<>();
        // 将平面 id 列表封装为领域命令要求的 item 结构，保持 id 顺序与传入一致。
        for (Long chargeItemId : chargeItemIds) {
            BatchChargeItemIdsCommand.Item item = new BatchChargeItemIdsCommand.Item();
            item.setChargeItemId(chargeItemId);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 装配创建支付草稿命令（支付头与金额字段）。
     *
     * <p>不设置 {@link com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand#setPaymentDetails}：
     * 明细须由 {@code OutpatientChargeAppService} 在调用
     * {@link com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService#createPaymentDraftFromPreSettle}
     * 之前按收费项账户汇总写入，否则领域侧 {@code replaceDetails} 清空后无插入，正式结算将报 {@code EX-OPC-402}。</p>
     *
     * @param encounterId 就诊主键
     * @param patientId 患者主键
     * @param chargeItemIds 收费项主键串
     * @param amount 展示与实收金额（预结算合计）
     * @param operatorId 操作人主键
     * @param contractNo 合同业务号，可空
     * @return 仅含头字段的创建草稿命令，调用方负责补全 {@code paymentDetails}
     */
    @Override
    public CreatePaymentDraftFromPreSettleCommand toCreatePaymentDraftFromPreSettleCommand(
        Long encounterId,
        Long patientId,
        String chargeItemIds,
        BigDecimal amount,
        Long operatorId,
        String contractNo) {
        CreatePaymentDraftFromPreSettleCommand command = new CreatePaymentDraftFromPreSettleCommand();
        command.setEncounterId(encounterId);
        command.setPatientId(patientId);
        command.setChargeItemIds(chargeItemIds);
        command.setKindEnum(PaymentKind.OUTPATIENT_CLINIC);
        command.setPaymentEnum(PaymentChannel.CASHIER.getCode());
        command.setEntererId(operatorId);
        command.setTenderedAmount(amount);
        command.setDisplayAmount(amount);
        command.setContractNo(contractNo);
        // 支付明细分录由 AppService 按收费项账户汇总后写入，避免落库主单从表为空导致正式结算 EX-OPC-402。
        return command;
    }

    /**
     * 装配门诊正式结算命令。
     *
     * @param request 正式结算请求
     * @return 结算命令
     */
    @Override
    public SettleOutpatientPaymentCommand toSettleOutpatientPaymentCommand(ConfirmOutpatientChargeSettlementRequest request) {
        SettleOutpatientPaymentCommand command = new SettleOutpatientPaymentCommand();
        command.setPaymentId(request.getPaymentDraftId());
        command.setOutcomeEnum(request.getOutcomeEnum());
        command.setBillDate(request.getBillDate());
        List<PaymentDetailItemCommand> items = new ArrayList<>();
        // 支付明细逐条装配：过滤空行，金额与主单结果态保持一致。
        if (request.getPaymentDetails() != null) {
            for (int index = 0; index < request.getPaymentDetails().size(); index++) {
                // 跳过前端可能传入的空占位行（如用户删除某条支付方式后留下的 null）。
                if (request.getPaymentDetails().get(index) == null) {
                    continue;
                }
                PaymentDetailSubmitDto detail = request.getPaymentDetails().get(index);
                PaymentDetailItemCommand item = new PaymentDetailItemCommand();
                // 支付渠道编码落库前做去空白规整，避免同一渠道因前后空格形成脏枚举值。
                item.setPayEnum(detail.getPayChannelCode().trim());
                item.setAmount(detail.getAmount());
                item.setChargeAmount(detail.getAmount());
                item.setPayTransDate(request.getBillDate());
                // 结算提交已给出本次结果枚举，支付明细与主单保持一致，避免明细残留草稿态。
                item.setResultEnum(request.getOutcomeEnum());
                items.add(item);
            }
        }
        command.setPayReceiptDetails(items);
        return command;
    }

    /**
     * 装配创建票据草稿命令。
     *
     * @param payment 支付流水
     * @param paymentId 支付主键
     * @param operatorId 操作人
     * @return 票据草稿命令
     */
    @Override
    public CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(PaymentReconciliationQueryModel payment, Long paymentId, Long operatorId) {
        CreateDraftInvoiceCommand command = new CreateDraftInvoiceCommand();
        command.setPatientId(payment.getPatientId());
        command.setPaymentId(paymentId);
        command.setTypeCode(OutpatientChargeConstants.INVOICE_TYPE_OUTPATIENT);
        command.setAmount(payment.getDisplayAmount());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 装配转账户响应。
     *
     * @param success 是否成功
     * @param messageCode 提示码
     * @return 响应
     */
    @Override
    public SwitchEncounterChargeItemsAccountResponse toSwitchAccountResponse(boolean success, String messageCode) {
        SwitchEncounterChargeItemsAccountResponse response = new SwitchEncounterChargeItemsAccountResponse();
        response.setSuccess(success);
        response.setMessageCode(messageCode);
        return response;
    }

    /**
     * 装配预结算响应。
     *
     * @param paymentDraftId 支付草稿主键
     * @param displayAmount 展示金额
     * @param previews 预结算行
     * @return 响应
     */
    @Override
    public PreSettleOutpatientChargesResponse toPreSettleOutpatientChargesResponse(
        Long paymentDraftId,
        BigDecimal displayAmount,
        List<PreSettleChargeLinePreviewDto> previews) {
        PreSettleOutpatientChargesResponse response = new PreSettleOutpatientChargesResponse();
        response.setPaymentDraftId(paymentDraftId);
        response.setDisplayAmount(displayAmount);
        response.setPreviews(previews);
        return response;
    }

    /**
     * 装配正式结算响应。
     *
     * @param paymentId 支付主键
     * @param invoiceId 票据主键
     * @return 响应
     */
    @Override
    public ConfirmOutpatientChargeSettlementResponse toConfirmOutpatientChargeSettlementResponse(Long paymentId, Long invoiceId) {
        ConfirmOutpatientChargeSettlementResponse response = new ConfirmOutpatientChargeSettlementResponse();
        response.setPaymentId(paymentId);
        response.setInvoiceId(invoiceId);
        response.setInvoiceStatus(InvoiceStatus.DRAFT.getCode());
        response.setSuccessMessageCode(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_SUCCESS);
        return response;
    }
}
