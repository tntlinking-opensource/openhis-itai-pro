/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterTransferCommand;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 从药库出库/药房入库调拨单组装 3502 上报请求。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryTransferUploadContextLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SupplyRequestQueryService supplyRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final InsuranceContractResolver contractResolver;

    public UploadInvChange3502Request build3502Request(UploadAfterTransferCommand command) {
        Contract contract = contractResolver.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadTransferMedicationLines(command);
        UploadInvChange3502Request request = new UploadInvChange3502Request();
        request.setContractBusNo(contractResolver.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_TRANSFER);
        if (!lines.isEmpty()) {
            request.setSourceId(lines.get(0).getId());
        }
        for (SupplyRequestModel line : lines) {
            InvUploadLineCommand cmd = buildTransfer3502Line(line, command);
            if (cmd != null) {
                request.getLines().add(cmd);
            }
        }
        return request;
    }

    private List<SupplyRequestModel> loadTransferMedicationLines(UploadAfterTransferCommand command) {
        if (command == null || StringUtils.isBlank(command.getTransferBusNo())) {
            return List.of();
        }
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(command.getTransferBusNo().trim());
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
            if (StringUtils.isNotBlank(command.getSupplyTypeCode())
                && !command.getSupplyTypeCode().trim().equals(row.getTypeEnum())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    private InvUploadLineCommand buildTransfer3502Line(
        SupplyRequestModel line,
        UploadAfterTransferCommand command
    ) {
        BigDecimal transferQty = line.getItemQuantity();
        if (transferQty == null || transferQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (StringUtils.isBlank(command.getInvChgType())) {
            return null;
        }
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(line.getItemId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存在: " + line.getItemId()));
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(medication.getYbNo()));
        cmd.setFixmedinsHilistId(medication.getBusNo());
        cmd.setFixmedinsHilistName(medication.getName());
        cmd.setFixmedinsBchno(InventoryUploadBatchNoSupport.buildFixmedinsBchno(
            medication.getBusNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setCnt(transferQty);
        cmd.setPric(line.getPrice());
        cmd.setInvChgType(command.getInvChgType().trim());
        cmd.setInvChgTime(resolveTransferDateTime(line));
        cmd.setInvChgOpterName(resolveOperatorName(line));
        cmd.setRxFlag(InventoryPlatformFieldSupport.toRxFlagCode(medication.getRxFlag()));
        cmd.setMemo(line.getRemake());
        if (StringUtils.isBlank(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }
        fillInventoryDates(cmd, line, command.getInvChgType().trim());
        return cmd;
    }

    private void fillInventoryDates(InvUploadLineCommand cmd, SupplyRequestModel line, String invChgType) {
        if (StringUtils.isBlank(line.getLotNumber())) {
            return;
        }
        Long locationId = resolveInventoryLocationId(line, invChgType);
        if (locationId == null) {
            return;
        }
        List<InventoryItem> items = inventoryItemQueryService.listInventoryItemEntitiesByLotLocationItem(
            line.getLotNumber(),
            locationId,
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

    private static Long resolveInventoryLocationId(SupplyRequestModel line, String invChgType) {
        if (ChsInventoryConstants.INV_CHG_TYPE_TRANSFER_OUT.equals(invChgType)) {
            return line.getSourceLocationId();
        }
        return line.getPurposeLocationId();
    }

    private String resolveTransferDateTime(SupplyRequestModel line) {
        if (line.getApprovalTime() != null) {
            return line.getApprovalTime().format(DATE_TIME_FMT);
        }
        if (line.getOccurrenceTime() != null) {
            return line.getOccurrenceTime().format(DATE_TIME_FMT);
        }
        return DATE_TIME_FMT.format(java.time.LocalDateTime.now());
    }

    private String resolveOperatorName(SupplyRequestModel line) {
        Long practitionerId = line.getApproverId();
        if (practitionerId == null) {
            practitionerId = line.getPractitionerId();
        }
        return resolvePractitionerName(practitionerId);
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