/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import com.openhis.chs.domain.share.enums.ProdGeayFlag;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3503Command;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.openhis.chs.domain.inventory.command.UploadAfterInboundCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HIS 主数据组装采购入库上报请求（药库进货单 supply_request → CHS 3503）。
 *
 * <p>加载来源：{@link com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel}
 * 进货入库行（{@code CABINET_INBOUND_RECEIPT}）、
 * {@link com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition}、
 * 供应商（{@code supplier}）、{@link com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem}
 * 批号效期及入库操作员信息。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryInboundUploadContextLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SupplyRequestQueryService supplyRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final SupplierQueryService supplierQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final ChsInsuranceContractReaderImpl contractReader;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;

    /** 组装 3503 采购入库上报命令。 */
    public InvUpload3503Command build3503Request(UploadAfterInboundCommand command) {
        Contract contract = contractReader.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadInboundMedicationLines(command);
        InvUpload3503Command request = new InvUpload3503Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setInboundBusNo(command.getInboundBusNo());
        if (!lines.isEmpty()) {
            request.setSourceId(lines.get(0).getId());
        }
        for (SupplyRequestModel line : lines) {
            request.getLines().add(buildPurchaseLine(line));
        }
        return request;
    }

    /**
     * 按入库单号加载进货入库的药品 supply_request 行。
     *
     * @param command 采购入库上报命令
     * @return 可上报的进货入库行列表
     */
    private List<SupplyRequestModel> loadInboundMedicationLines(UploadAfterInboundCommand command) {
        if (command == null || isEmpty(command.getInboundBusNo())) {
            return List.of();
        }
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(command.getInboundBusNo().trim());
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
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(row.getTypeEnum())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 将进货 supply_request 行组装为 3503 上报明细。
     *
     * @param line 进货入库行
     * @return 3503 上报行命令
     */
    private InvUploadLineCommand buildPurchaseLine(SupplyRequestModel line) {
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(line.getItemId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存 " + line.getItemId()));
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(medication.getYbNo()));
        cmd.setFixmedinsHilistId(medication.getBusNo());
        cmd.setFixmedinsHilistName(medication.getName());
        cmd.setFixmedinsBchno(InventoryUploadBatchNoAssembler.buildFixmedinsBchno(medication.getBusNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setAprvno(medication.getApprovalNumber());
        cmd.setProdentpName(medication.getManufacturerText());
        cmd.setPurcRetnCnt(line.getItemQuantity());
        cmd.setFinlTrnsPric(line.getPrice());
        cmd.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(medication.getRxFlag()));
        cmd.setProdGeayFlag(ProdGeayFlag.NO.getCode());
        cmd.setDyntNo(line.getOriginalBusNo());
        cmd.setMemo(line.getRemake());
        uploadTraceLineSupport.applyMedicationSupplyLineTrace(
            medication, line.getUnitCode(), line.getTraceNo(), cmd);
        fillSupplierFields(cmd, line.getSupplierId());
        fillInventoryDates(cmd, line);
        fillOperatorFields(cmd, line);
        return cmd;
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
     * @param line 进货入库行
     */
    private void fillInventoryDates(InvUploadLineCommand cmd, SupplyRequestModel line) {
        if (isEmpty(line.getLotNumber()) || line.getPurposeLocationId() == null) {
            return;
        }
        List<InventoryItem> items = inventoryItemQueryService.listInventoryItemEntitiesByLotLocationItem(
            line.getLotNumber(),
            line.getPurposeLocationId(),
            line.getItemTable(),
            line.getItemId());
        if (items.isEmpty()) {
            return;
        }
        InventoryItem item = items.get(0);
        if (item.getProductionDate() != null) {
            cmd.setManuDate(item.getProductionDate().toLocalDate().format(DATE_FMT));
        }
        if (item.getExpirationDate() != null) {
            cmd.setExpyEnd(item.getExpirationDate().toLocalDate().format(DATE_FMT));
        }
    }

    /**
     * 填充采购入库时间与操作员姓名。
     *
     * @param cmd  上报行命令
     * @param line 进货入库行
     */
    private void fillOperatorFields(InvUploadLineCommand cmd, SupplyRequestModel line) {
        if (line.getApprovalTime() != null) {
            cmd.setPurcRetnStoinTime(line.getApprovalTime().format(DATE_TIME_FMT));
        } else if (line.getOccurrenceTime() != null) {
            cmd.setPurcRetnStoinTime(line.getOccurrenceTime().format(DATE_TIME_FMT));
        }
        Long practitionerId = line.getApproverId();
        if (practitionerId == null) {
            practitionerId = line.getPractitionerId();
        }
        cmd.setPurcRetnOpterName(resolvePractitionerName(practitionerId));
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
