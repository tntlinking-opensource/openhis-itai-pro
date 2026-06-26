/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchase3503Request;
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
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterInboundCommand;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 从进货单组装 3503/3504 上报请求。 */
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
    private final InsuranceContractResolver contractResolver;

    public UploadPurchase3503Request build3503Request(UploadAfterInboundCommand command) {
        Contract contract = contractResolver.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadInboundMedicationLines(command);
        UploadPurchase3503Request request = new UploadPurchase3503Request();
        request.setContractBusNo(contractResolver.requireContractBusNo(contract));
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

    private List<SupplyRequestModel> loadInboundMedicationLines(UploadAfterInboundCommand command) {
        if (command == null || StringUtils.isBlank(command.getInboundBusNo())) {
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

    private InvUploadLineCommand buildPurchaseLine(SupplyRequestModel line) {
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(line.getItemId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存在: " + line.getItemId()));
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(medication.getYbNo()));
        cmd.setFixmedinsHilistId(medication.getBusNo());
        cmd.setFixmedinsHilistName(medication.getName());
        cmd.setFixmedinsBchno(InventoryUploadBatchNoSupport.buildFixmedinsBchno(medication.getBusNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setAprvno(medication.getApprovalNumber());
        cmd.setProdentpName(medication.getManufacturerText());
        cmd.setPurcRetnCnt(line.getItemQuantity());
        cmd.setFinlTrnsPric(line.getPrice());
        cmd.setRxFlag(InventoryPlatformFieldSupport.toRxFlagCode(medication.getRxFlag()));
        cmd.setProdGeayFlag(ChsInventoryConstants.PROD_GEAY_FLAG_NO);
        cmd.setDyntNo(line.getOriginalBusNo());
        cmd.setMemo(line.getRemake());
        if (StringUtils.isBlank(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }
        fillSupplierFields(cmd, line.getSupplierId());
        fillInventoryDates(cmd, line);
        fillOperatorFields(cmd, line);
        return cmd;
    }

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

    private void fillInventoryDates(InvUploadLineCommand cmd, SupplyRequestModel line) {
        if (StringUtils.isBlank(line.getLotNumber()) || line.getPurposeLocationId() == null) {
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