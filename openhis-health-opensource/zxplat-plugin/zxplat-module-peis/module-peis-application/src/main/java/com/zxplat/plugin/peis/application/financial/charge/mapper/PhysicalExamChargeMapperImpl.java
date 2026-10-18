/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.mapper;

import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamChargeItemRowDto;
import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamChargeQueueRowDto;
import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamPaymentDetailSubmitDto;
import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamPreSettleLinePreviewDto;
import com.zxplat.plugin.peis.application.financial.charge.request.AdjustPhysicalExamChargePricesRequest;
import com.zxplat.plugin.peis.application.financial.charge.request.ConfirmPhysicalExamChargeSettlementRequest;
import com.zxplat.plugin.peis.application.financial.charge.response.ConfirmPhysicalExamChargeSettlementResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.LoadPhysicalExamChargePageInitResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.PagePhysicalExamChargeQueueResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.PreSettlePhysicalExamChargesResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.PreviewPhysicalExamRefundResponse;
import com.zxplat.plugin.peis.application.financial.charge.response.RefundPhysicalExamChargesResponse;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.zxplat.domain.administration.chargeitem.command.ManualOverridePlannedChargeItemPricesCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientInpatientProjectionSnapshot;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.domain.share.constants.PhysicalExamChargeConstants;
import com.zxplat.domain.share.constants.PhysicalExamChargeQueueTab;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.AdministrativeGender;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.PaymentChannel;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.share.enums.PaymentType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 体检收费应用层映射器实现。
 */
@Component
@RequiredArgsConstructor
public class PhysicalExamChargeMapperImpl implements PhysicalExamChargeMapper {

    /**
     * 装配页初始化响应。
     *
     * @param chargeItemStatusOptions 收费项状态下拉
     * @param paymentChannelOptions   支付渠道下拉
     * @return 初始化响应
     */
    @Override
    public LoadPhysicalExamChargePageInitResponse toLoadPageInitResponse(
        List<OptionDto> chargeItemStatusOptions,
        List<OptionDto> paymentChannelOptions) {
        LoadPhysicalExamChargePageInitResponse response = new LoadPhysicalExamChargePageInitResponse();
        response.setChargeItemStatusOptions(chargeItemStatusOptions);
        response.setPaymentChannelOptions(paymentChannelOptions);
        return response;
    }

    /**
     * 装配队列分页响应。
     *
     * @param page    分页壳
     * @param records 队列行
     * @return 分页响应
     */
    @Override
    public PagePhysicalExamChargeQueueResponse toPageQueueResponse(Page<?> page, List<PhysicalExamChargeQueueRowDto> records) {
        return PagedResultBuilder.build(new PagePhysicalExamChargeQueueResponse(), records, page);
    }

    /**
     * 装配批量收费项命令。
     *
     * @param chargeItemIds 收费项主键列表
     * @param operatorId    操作人
     * @return 批量命令
     */
    @Override
    public BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId) {
        BatchChargeItemIdsCommand command = new BatchChargeItemIdsCommand();
        command.setOperatorId(operatorId);
        List<BatchChargeItemIdsCommand.Item> items = new ArrayList<>();
        if (chargeItemIds != null) {
            for (Long chargeItemId : chargeItemIds) {
                BatchChargeItemIdsCommand.Item item = new BatchChargeItemIdsCommand.Item();
                item.setChargeItemId(chargeItemId);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

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
        // payment_enum 语义为 PaymentType（PAY/UN_PAY），禁止写入渠道码 CASHIER
        command.setPaymentEnum(PaymentType.PAY.getCode());
        command.setEntererId(operatorId);
        command.setTenderedAmount(amount);
        command.setDisplayAmount(amount);
        command.setContractNo(contractNo);
        return command;
    }

    /**
     * 装配门诊正式结算命令。
     *
     * @param request 正式结算请求
     * @return 结算命令
     */
    @Override
    public SettleOutpatientPaymentCommand toSettleOutpatientPaymentCommand(ConfirmPhysicalExamChargeSettlementRequest request) {
        SettleOutpatientPaymentCommand command = new SettleOutpatientPaymentCommand();
        command.setPaymentId(request.getPaymentDraftId());
        command.setOutcomeEnum(request.getOutcomeEnum());
        command.setBillDate(request.getBillDate());
        List<PaymentDetailItemCommand> items = new ArrayList<>();
        if (request.getPaymentDetails() != null) {
            for (PhysicalExamPaymentDetailSubmitDto detail : request.getPaymentDetails()) {
                if (detail == null) {
                    continue;
                }
                PaymentDetailItemCommand item = new PaymentDetailItemCommand();
                if (detail.getPayChannelCode() != null) {
                    item.setPayEnum(detail.getPayChannelCode().trim());
                }
                item.setAmount(detail.getAmount());
                item.setChargeAmount(detail.getAmount());
                item.setPayTransDate(request.getBillDate());
                item.setResultEnum(request.getOutcomeEnum());
                items.add(item);
            }
        }
        command.setPayReceiptDetails(items);
        return command;
    }

    /**
     * 装配票据草稿命令。
     *
     * @param payment           支付读模型
     * @param paymentId         支付主键
     * @param operatorId        操作人
     * @param invoiceFaceAmount 票面金额（收费项合计结算刻度；含医保已付，非患者现金）
     * @return 票据草稿命令
     */
    @Override
    public CreateDraftInvoiceCommand toCreateDraftInvoiceCommand(
        PaymentReconciliationQueryModel payment,
        Long paymentId,
        Long operatorId,
        BigDecimal invoiceFaceAmount) {
        CreateDraftInvoiceCommand command = new CreateDraftInvoiceCommand();
        command.setPatientId(payment.getPatientId());
        command.setPaymentId(paymentId);
        command.setTypeCode(PhysicalExamChargeConstants.INVOICE_TYPE_OUTPATIENT);
        // 票面按收费项合计；医保全报现金为 0 仍开票（BR-INV-008）
        command.setAmount(invoiceFaceAmount);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 装配预结算响应。
     *
     * @param paymentDraftId 支付草稿主键
     * @param displayAmount  展示金额
     * @param previews       预览行
     * @return 预结算响应
     */
    @Override
    public PreSettlePhysicalExamChargesResponse toPreSettleResponse(
        Long paymentDraftId,
        BigDecimal displayAmount,
        List<PhysicalExamPreSettleLinePreviewDto> previews) {
        PreSettlePhysicalExamChargesResponse response = new PreSettlePhysicalExamChargesResponse();
        response.setPaymentDraftId(paymentDraftId);
        response.setDisplayAmount(MonetaryAmountSupport.scaleSettle(displayAmount));
        response.setPreviews(previews);
        return response;
    }

    /**
     * 装配正式结算响应。
     *
     * @param paymentId 支付主键
     * @param invoiceId 发票主键
     * @return 正式结算响应
     */
    @Override
    public ConfirmPhysicalExamChargeSettlementResponse toConfirmSettlementResponse(Long paymentId, Long invoiceId) {
        ConfirmPhysicalExamChargeSettlementResponse response = new ConfirmPhysicalExamChargeSettlementResponse();
        response.setPaymentId(paymentId);
        response.setInvoiceId(invoiceId);
        response.setSuccess(Boolean.TRUE);
        response.setSuccessMessageCode(PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_SUCCESS);
        return response;
    }

    /**
     * 装配队列行列表。
     *
     * @param encounterIds           就诊主键（分页顺序）
     * @param encounterById          就诊实体映射
     * @param patientSnapshotsById   患者人口学快照
     * @param extByEncounter         体检扩展映射
     * @param chargeItemsByEncounter 收费明细映射
     * @return 队列行
     */
    @Override
    public List<PhysicalExamChargeQueueRowDto> toQueueRows(
        List<Long> encounterIds,
        Map<Long, Encounter> encounterById,
        Map<Long, PatientInpatientProjectionSnapshot> patientSnapshotsById,
        Map<Long, PeisEncounterExtModel> extByEncounter,
        Map<Long, List<ChargeItem>> chargeItemsByEncounter) {
        List<PhysicalExamChargeQueueRowDto> rows = new ArrayList<>();
        if (encounterIds == null) {
            return rows;
        }
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            PhysicalExamChargeQueueRowDto row = new PhysicalExamChargeQueueRowDto();
            row.setEncounterId(encounterId);
            Encounter encounter = encounterById.get(encounterId);
            if (encounter != null) {
                row.setPatientId(encounter.getPatientId());
                row.setStartTime(encounter.getStartTime());
                if (encounter.getPatientId() != null && patientSnapshotsById != null) {
                    PatientInpatientProjectionSnapshot snapshot = patientSnapshotsById.get(encounter.getPatientId());
                    if (snapshot != null) {
                        if (StringUtils.isNotEmpty(snapshot.getPatientName())) {
                            row.setPatientName(snapshot.getPatientName());
                        }
                        if (StringUtils.isNotEmpty(snapshot.getGenderEnum())) {
                            row.setGenderCode(snapshot.getGenderEnum());
                            AdministrativeGender gender =
                                EnumUtil.fromCode(AdministrativeGender.class, snapshot.getGenderEnum());
                            if (gender != null) {
                                row.setGenderEnumText(gender.getDisplay());
                            }
                        }
                        if (StringUtils.isNotEmpty(snapshot.getAgeText())) {
                            row.setAgeText(snapshot.getAgeText());
                        }
                    }
                }
            }
            PeisEncounterExtModel ext = extByEncounter.get(encounterId);
            if (ext != null) {
                row.setBusNo(ext.getBusNo());
                row.setPackageName(resolvePackageName(ext));
                row.setGroupFlag(ext.getGroupFlag());
            }
            List<ChargeItem> chargeItems = chargeItemsByEncounter.get(encounterId);
            BigDecimal planned = BigDecimal.ZERO;
            BigDecimal settled = BigDecimal.ZERO;
            boolean anyPlanned = false;
            if (chargeItems != null) {
                for (ChargeItem chargeItem : chargeItems) {
                    if (chargeItem == null) {
                        continue;
                    }
                    BigDecimal lineTotal = chargeItem.getTotalPrice();
                    if (lineTotal == null) {
                        lineTotal = BigDecimal.ZERO;
                    }
                    if (ChargeItemStatus.PLANNED.getCode().equals(chargeItem.getStatusEnum())) {
                        planned = planned.add(lineTotal);
                        anyPlanned = true;
                    }
                    if (ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
                        settled = settled.add(lineTotal);
                    }
                }
            }
            row.setPlannedAmount(MonetaryAmountSupport.scaleSettle(planned));
            row.setSettledAmount(MonetaryAmountSupport.scaleSettle(settled));
            if (anyPlanned) {
                row.setQueueLabelCode(PhysicalExamChargeQueueTab.WAITING);
            } else {
                row.setQueueLabelCode(PhysicalExamChargeQueueTab.COMPLETE);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 装配明细行列表。
     *
     * @param rows       收费明细
     * @param itemLabels 收费项展示名
     * @return 明细行
     */
    @Override
    public List<PhysicalExamChargeItemRowDto> toChargeItemRows(List<ChargeItem> rows, Map<Long, String> itemLabels) {
        List<PhysicalExamChargeItemRowDto> items = new ArrayList<>();
        if (rows == null) {
            return items;
        }
        for (ChargeItem row : rows) {
            if (row == null) {
                continue;
            }
            PhysicalExamChargeItemRowDto item = new PhysicalExamChargeItemRowDto();
            item.setChargeItemId(row.getId());
            item.setStatus(row.getStatusEnum());
            String label = PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
            if (itemLabels != null && row.getId() != null && StringUtils.isNotEmpty(itemLabels.get(row.getId()))) {
                label = itemLabels.get(row.getId());
            }
            item.setItemLabel(label);
            item.setTotalPrice(row.getTotalPrice());
            item.setUnitPrice(row.getUnitPrice());
            item.setQuantity(row.getQuantityValue());
            item.setCatalogUnitPrice(row.getBaseAmount());
            item.setOverrideReasonCode(row.getOverrideReasonCode());
            item.setServiceTable(row.getServiceTable());
            boolean priceEditable = false;
            // 仅待收费可改价；套餐整单锚 AD，加项锚 SR
            if (ChargeItemStatus.PLANNED.getCode().equals(row.getStatusEnum())) {
                if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())) {
                    priceEditable = true;
                } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getServiceTable())) {
                    priceEditable = true;
                }
            }
            item.setPriceEditable(priceEditable);
            // 加项行带回 SR，供登记台按服务申请改价
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())) {
                item.setServiceRequestId(row.getServiceId());
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 装配预结算预览行。
     *
     * @param chargeItems 收费明细
     * @param itemLabels  展示名映射
     * @return 预览行
     */
    @Override
    public List<PhysicalExamPreSettleLinePreviewDto> toPreSettlePreviews(
        List<ChargeItem> chargeItems,
        Map<Long, String> itemLabels) {
        List<PhysicalExamPreSettleLinePreviewDto> previews = new ArrayList<>();
        if (chargeItems == null) {
            return previews;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            PhysicalExamPreSettleLinePreviewDto preview = new PhysicalExamPreSettleLinePreviewDto();
            preview.setChargeItemId(chargeItem.getId());
            String label = PhysicalExamChargeConstants.CHARGE_ITEM_DISPLAY_FALLBACK;
            if (itemLabels != null && chargeItem.getId() != null && StringUtils.isNotEmpty(itemLabels.get(chargeItem.getId()))) {
                label = itemLabels.get(chargeItem.getId());
            }
            preview.setItemLabel(label);
            preview.setTotalPrice(chargeItem.getTotalPrice());
            previews.add(preview);
        }
        return previews;
    }

    /**
     * 装配手工改价领域命令。
     *
     * @param request    改价请求
     * @param operatorId 操作人
     * @return 改价命令
     */
    @Override
    public ManualOverridePlannedChargeItemPricesCommand toManualOverridePlannedChargeItemPricesCommand(
        AdjustPhysicalExamChargePricesRequest request,
        Long operatorId) {
        ManualOverridePlannedChargeItemPricesCommand cmd = new ManualOverridePlannedChargeItemPricesCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(request.getEncounterId());
        List<ManualOverridePlannedChargeItemPricesCommand.Item> items = new ArrayList<>();
        if (request.getItems() != null) {
            for (AdjustPhysicalExamChargePricesRequest.Item row : request.getItems()) {
                if (row == null) {
                    continue;
                }
                ManualOverridePlannedChargeItemPricesCommand.Item item =
                    new ManualOverridePlannedChargeItemPricesCommand.Item();
                item.setChargeItemId(row.getChargeItemId());
                item.setUnitPrice(row.getUnitPrice());
                String reasonCode = row.getOverrideReasonCode();
                if (StringUtils.isEmpty(reasonCode)) {
                    reasonCode = PhysicalExamChargeConstants.OVERRIDE_REASON_PEIS_MANUAL;
                }
                item.setOverrideReasonCode(reasonCode);
                item.setOverrideReasonText(row.getOverrideReasonText());
                items.add(item);
            }
        }
        cmd.setItems(items);
        return cmd;
    }

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
    @Override
    public PreviewPhysicalExamRefundResponse toPreviewRefundResponse(
        Long originalPaymentId,
        BigDecimal refundableAmount,
        List<String> originalPayChannelCodes,
        List<Long> refundingChargeItemIds,
        List<PhysicalExamPreSettleLinePreviewDto> previews) {
        PreviewPhysicalExamRefundResponse response = new PreviewPhysicalExamRefundResponse();
        response.setOriginalPaymentId(originalPaymentId);
        response.setRefundableAmount(refundableAmount);
        response.setOriginalPayChannelCodes(originalPayChannelCodes);
        response.setRefundingChargeItemIds(refundingChargeItemIds);
        response.setPreviews(previews);
        return response;
    }

    /**
     * 装配退费执行响应。
     *
     * @param originalPaymentId 原支付主键
     * @param reversePaymentId  逆向支付主键
     * @return 执行响应
     */
    @Override
    public RefundPhysicalExamChargesResponse toRefundChargesResponse(Long originalPaymentId, Long reversePaymentId) {
        RefundPhysicalExamChargesResponse response = new RefundPhysicalExamChargesResponse();
        response.setSuccess(Boolean.TRUE);
        response.setOriginalPaymentId(originalPaymentId);
        response.setReversePaymentId(reversePaymentId);
        return response;
    }

    /**
     * 从体检扩展解析套餐展示名。
     *
     * @param ext 体检扩展
     * @return 套餐名；无则 null
     */
    private String resolvePackageName(PeisEncounterExtModel ext) {
        if (ext == null) {
            return null;
        }
        String fromSnapshot = extractPackageNameFromSnapshot(ext.getPackageSnapshotJson());
        if (StringUtils.isNotEmpty(fromSnapshot)) {
            return fromSnapshot;
        }
        if (ext.getPackageActivityId() != null) {
            return "套餐#" + ext.getPackageActivityId();
        }
        return null;
    }

    /**
     * 从套餐快照 JSON 提取 name 字段。
     *
     * @param snapshotJson 快照 JSON
     * @return 套餐名；解析失败返回 null
     */
    private String extractPackageNameFromSnapshot(String snapshotJson) {
        if (StringUtils.isEmpty(snapshotJson)) {
            return null;
        }
        String marker = "\"name\":\"";
        int start = snapshotJson.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < snapshotJson.length(); index++) {
            char ch = snapshotJson.charAt(index);
            if (ch == '\\' && index + 1 < snapshotJson.length()) {
                builder.append(snapshotJson.charAt(index + 1));
                index++;
                continue;
            }
            if (ch == '"') {
                break;
            }
            builder.append(ch);
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }
}
