/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.context;

import com.zxplat.plugin.chs.domain.share.enums.ProdGeayFlag;
import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3504Command;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.inventoryitem.entity.InventoryItem;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.plugin.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryPurchaseUploadReferenceResolver;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadQtyPriceResult;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.zxplat.plugin.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterPurchaseReturnCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HIS 主数据组装采购退货上报请求（向供应商退货 supply_request → CHS 3504）。
 *
 * <p>加载来源：{@link com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel}
 * 退货出库行（{@code CABINET_RETURN_TO_SUPPLIER}）、
 * {@link com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition}、
 * 供应商、{@link com.zxplat.domain.workflow.inventoryitem.entity.InventoryItem} 批号效期，
 * 并通过 {@link com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryPurchaseUploadReferenceResolver}
 * 解析原 3503 采购批次号。</p>
 *
 * <p>吉林省口径：{@code fixmedins_bchno} 与 {@code medins_prod_purc_no} 均填原 3503 的
 * {@code fixmedins_bchno}（进货单号）；不得用退供单 CRT，否则平台报「未找到商品采购信息」。
 * {@code purc_invo_no} 取退货/进货 {@code invoice_no}，均空则默认 {@code 0}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryPurchaseReturnUploadContextLoader {

    private final SupplyRequestQueryService supplyRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final SupplierQueryService supplierQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final ChsInsuranceContractReaderImpl contractReader;
    private final InventoryPurchaseUploadReferenceResolver purchaseUploadReferenceResolver;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;

    /** 组装 3504 采购退货上报命令。 */
    public InvUpload3504Command build3504Request(UploadAfterPurchaseReturnCommand command) {
        Contract contract = contractReader.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadReturnToSupplierLines(command);
        InvUpload3504Command request = new InvUpload3504Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setInboundBusNo(command.getReturnBusNo());
        if (!lines.isEmpty()) {
            request.setSourceId(lines.get(0).getId());
        }
        for (SupplyRequestModel line : lines) {
            request.getLines().add(buildReturnLine(line));
        }
        return request;
    }

    /**
     * 按退货单号加载向供应商退货的药品 supply_request 行。
     *
     * @param command 采购退货上报命令
     * @return 可上报的退货出库行列表
     */
    private List<SupplyRequestModel> loadReturnToSupplierLines(UploadAfterPurchaseReturnCommand command) {
        if (command == null || isEmpty(command.getReturnBusNo())) {
            return List.of();
        }
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(command.getReturnBusNo().trim());
        List<SupplyRequestModel> rows = supplyRequestQueryService.getSupplyByBusNo(query);
        Set<Long> filterIds = new HashSet<>();
        if (command.getSupplyRequestLineIds() != null) {
            filterIds.addAll(command.getSupplyRequestLineIds());
        }
        List<SupplyRequestModel> result = new ArrayList<>();
        for (SupplyRequestModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!filterIds.isEmpty() && !filterIds.contains(row.getId())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                continue;
            }
            if (!SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(row.getTypeEnum())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 将退货 supply_request 行组装为 3504 上报明细。
     *
     * @param line 退货出库行
     * @return 3504 上报行命令
     */
    private InvUploadLineCommand buildReturnLine(SupplyRequestModel line) {
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(line.getItemId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存 " + line.getItemId()));
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(medication.getYbNo()));
        cmd.setFixmedinsHilistId(medication.getBusNo());
        cmd.setFixmedinsHilistName(medication.getName());
        // 回落用进货单 busNo（originalBusNo=CIR…），禁止回落到退供 CRT
        String inboundFallbackBchno = InventoryUploadBatchNoAssembler.buildFixmedinsBchno(line.getOriginalBusNo());
        // 吉林：3504 定位采购靠原 3503 fixmedins_bchno，与 medins_prod_purc_no 同值
        String purchaseBchno = purchaseUploadReferenceResolver.resolvePurchaseFixmedinsBchnoForReturn(
            line, inboundFallbackBchno);
        cmd.setFixmedinsBchno(purchaseBchno);
        cmd.setMedinsProdPurcNo(purchaseBchno);
        cmd.setManuLotnum(line.getLotNumber());
        // aprvno ← 批准文号；空则占位，与进货 3503 同口径
        cmd.setAprvno(resolveAprvnoOrDefault(medication.getApprovalNumber()));
        // 生产厂家：目录已无 manufacturer_text，按 manufacturerId 解析 cus_supplier.name
        cmd.setProdentpName(this.resolveManufacturerName(medication.getManufacturerId()));
        InventoryUploadQtyPriceResult qtyPrice = InventoryUploadQuantityAssembler.resolveWarehouseQtyAndPrice(
            medication, line.getUnitCode(), line.getItemQuantity(), line.getPrice(), line.getUnitCode());
        cmd.setPurcRetnCnt(qtyPrice.getOperationQuantity());
        cmd.setFinlTrnsPric(qtyPrice.getOperationUnitPrice());
        cmd.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(medication.getRxFlag()));
        cmd.setProdGeayFlag(ProdGeayFlag.NO.getCode());
        cmd.setDyntNo(line.getOriginalBusNo());
        cmd.setMemo(line.getRemake());
        // purc_invo_no：退货行 → 关联进货单 → 默认占位（不因缺发票拦退货）
        cmd.setPurcInvoNo(resolvePurcInvoNo(line));
        uploadTraceLineSupport.applyMedicationSupplyLineTrace(
            medication, line.getUnitCode(), line.getTraceNo(), line.getItemQuantity(), cmd);
        fillSupplierFields(cmd, line.getSupplierId());
        fillInventoryDates(cmd, line);
        fillOperatorFields(cmd, line);
        return cmd;
    }

    /**
     * 解析采购发票号：退货行 → 关联进货单 → {@link ChsInventoryConstants#DEFAULT_PURC_INVO_NO}。
     *
     * @param line 退货出库行
     * @return 非空发票号或默认占位
     */
    private String resolvePurcInvoNo(SupplyRequestModel line) {
        String fromReturn = trimToNull(line.getInvoiceNo());
        // 退货单自身已填发票号
        if (fromReturn != null) {
            return fromReturn;
        }
        // 无关联进货单号：直接占位（规范 3504 要求非空）
        if (isEmpty(line.getOriginalBusNo())) {
            return ChsInventoryConstants.DEFAULT_PURC_INVO_NO;
        }
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(line.getOriginalBusNo().trim());
        List<SupplyRequestModel> inboundRows = supplyRequestQueryService.getSupplyByBusNo(query);
        // 回落关联进货入库行的发票号
        for (SupplyRequestModel inbound : inboundRows) {
            if (inbound == null) {
                continue;
            }
            // 仅认药库进货类型行
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(inbound.getTypeEnum())) {
                continue;
            }
            String fromInbound = trimToNull(inbound.getInvoiceNo());
            // 进货单有真实发票号则沿用
            if (fromInbound != null) {
                return fromInbound;
            }
        }
        // 进货侧亦无：占位 0，不拦退货业务（与 DEFAULT_PURC_INVO_NO / 进货落库同口径）
        return ChsInventoryConstants.DEFAULT_PURC_INVO_NO;
    }

    /**
     * 解析批准文号：有值用主数据，空则平台占位 {@link ChsInventoryConstants#DEFAULT_APRVNO}。
     *
     * @param approvalNumber 药品定义批准文号
     * @return 非空批准文号或默认占位
     */
    private String resolveAprvnoOrDefault(String approvalNumber) {
        String trimmed = trimToNull(approvalNumber);
        // 主数据已维护真实批准文号 / 国药准字
        if (trimmed != null) {
            return trimmed;
        }
        // 中药等无准字：占位上报，避免 3504 必填字段拒收
        return ChsInventoryConstants.DEFAULT_APRVNO;
    }

    /**
     * 按生产厂家主键解析平台生产企业名称（{@code prodentp_name}）。
     *
     * @param manufacturerId 药品定义 {@code manufacturer_id}（cus_supplier 生产厂家）
     * @return 供应商名称；无主键或未命中时为 {@code null}
     */
    private String resolveManufacturerName(Long manufacturerId) {
        if (manufacturerId == null) {
            return null;
        }
        return supplierQueryService.findSupplierNameIfPresent(manufacturerId).orElse(null);
    }

    /**
     * 填充供应商名称与信用代码。
     *
     * @param cmd        上报行命令
     * @param supplierId 供应商 ID
     */
    private void fillSupplierFields(InvUploadLineCommand cmd, Long supplierId) {
        if (supplierId == null) {
            cmd.setSplerName("未知供应商");
            return;
        }
        try {
            SupplierDetailQueryModel supplier = supplierQueryService.findSupplierById(supplierId);
            cmd.setSplerName(supplier.getName());
            cmd.setSplerPmtno(supplier.getCreditCode());
        } catch (RuntimeException ex) {
            cmd.setSplerName("未知供应商");
        }
    }

    /**
     * 从库存项填充生产日期与有效期。
     *
     * @param cmd  上报行命令
     * @param line 退货出库行
     */
    private void fillInventoryDates(InvUploadLineCommand cmd, SupplyRequestModel line) {
        if (isEmpty(line.getLotNumber()) || line.getSourceLocationId() == null) {
            return;
        }
        List<InventoryItem> items = inventoryItemQueryService.listInventoryItemEntitiesByLotLocationItem(
            line.getLotNumber(),
            line.getSourceLocationId(),
            line.getItemTable(),
            line.getItemId());
        if (items.isEmpty()) {
            return;
        }
        InventoryItem item = items.get(0);
        if (item.getProductionDate() != null) {
            cmd.setManuDate(item.getProductionDate().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        if (item.getExpirationDate() != null) {
            cmd.setExpyEnd(item.getExpirationDate().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
    }

    /**
     * 填充退货入库时间与操作员姓名。
     *
     * @param cmd  上报行命令
     * @param line 退货出库行
     */
    private void fillOperatorFields(InvUploadLineCommand cmd, SupplyRequestModel line) {
        // 3504：退货时间/经办人 = 审批时间/审批人（与 3503 同口径）
        if (line.getApprovalTime() != null) {
            cmd.setPurcRetnStoinTime(line.getApprovalTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        cmd.setPurcRetnOpterName(resolvePractitionerName(line.getApproverId()));
    }

    /**
     * 按从业者 ID 解析姓名。
     *
     * @param practitionerId 从业者 ID
     * @return 从业者姓名，不存在时返回 null
     */
    private String resolvePractitionerName(Long practitionerId) {
        if (practitionerId == null) {
            return null;
        }
        Practitioner practitioner = practitionerQueryService.findById(practitionerId);
        if (practitioner == null) {
            return null;
        }
        return practitioner.getName();
    }
}
