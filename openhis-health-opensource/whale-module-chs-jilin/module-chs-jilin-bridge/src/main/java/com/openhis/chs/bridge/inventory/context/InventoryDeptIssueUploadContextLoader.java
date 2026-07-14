/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.share.enums.InvChgType;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDeptIssueCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDeptIssueReturnCommand;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HIS 主数据组装科室领用/退领库存变更上报请求（{@link SupplyType#DEPT_ISSUE} /
 * {@link SupplyType#DEPT_ISSUE_RETURN} → CHS 3502）。
 *
 * <p>领用为科室药房扣减（107 其他出库）；退领为科室药房加回（106 其他入库）。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryDeptIssueUploadContextLoader {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern(GeneralSharedConstants.DATE_DISPLAY_PATTERN);
    private static final DateTimeFormatter DATE_TIME_FMT =
        DateTimeFormatter.ofPattern(GeneralSharedConstants.DATE_TIME_PATTERN);

    private final SupplyRequestQueryService supplyRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final ChsInsuranceContractReaderImpl contractReader;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;

    /**
     * 组装科室领用 3502 其他出库上报命令。
     *
     * @param command 领用单号与供应行 id
     * @return 3502 上报命令
     */
    public InvUpload3502Command build3502DeptIssueRequest(UploadAfterDeptIssueCommand command) {
        Contract contract = contractReader.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadMedicationLines(
            command.getDeptIssueBusNo(), SupplyType.DEPT_ISSUE.getCode(), command.getSupplyRequestLineIds());
        InvUpload3502Command request = new InvUpload3502Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_DEPT_ISSUE);
        if (!lines.isEmpty()) {
            request.setSourceId(lines.get(0).getId());
        }
        for (SupplyRequestModel line : lines) {
            InvUploadLineCommand cmd = buildLine(line, InvChgType.OTHER_OUT.getCode());
            if (cmd != null) {
                request.getLines().add(cmd);
            }
        }
        return request;
    }

    /**
     * 组装科室退领 3502 其他入库上报命令。
     *
     * @param command 退领单号与供应行 id
     * @return 3502 上报命令
     */
    public InvUpload3502Command build3502DeptIssueReturnRequest(UploadAfterDeptIssueReturnCommand command) {
        Contract contract = contractReader.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadMedicationLines(
            command.getDeptIssueReturnBusNo(),
            SupplyType.DEPT_ISSUE_RETURN.getCode(),
            command.getSupplyRequestLineIds());
        InvUpload3502Command request = new InvUpload3502Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_DEPT_ISSUE_RETURN);
        if (!lines.isEmpty()) {
            request.setSourceId(lines.get(0).getId());
        }
        for (SupplyRequestModel line : lines) {
            InvUploadLineCommand cmd = buildLine(line, InvChgType.OTHER_IN.getCode());
            if (cmd != null) {
                request.getLines().add(cmd);
            }
        }
        return request;
    }

    private List<SupplyRequestModel> loadMedicationLines(
        String busNo,
        String supplyTypeCode,
        List<Long> lineIds
    ) {
        if (isEmpty(busNo)) {
            return List.of();
        }
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(busNo.trim());
        List<SupplyRequestModel> rows = supplyRequestQueryService.getSupplyByBusNo(query);
        Set<Long> filterIds = new HashSet<>();
        if (lineIds != null) {
            filterIds.addAll(lineIds);
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
            if (isNotEmpty(supplyTypeCode) && !supplyTypeCode.trim().equals(row.getTypeEnum())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    private InvUploadLineCommand buildLine(SupplyRequestModel line, String invChgType) {
        BigDecimal qty = line.getItemQuantity();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        MedicationDefinition medication = medicationDefinitionQueryService
            .findOptionalMedicationDefinitionEntityById(line.getItemId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存在 " + line.getItemId()));
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(medication.getYbNo()));
        cmd.setFixmedinsHilistId(medication.getBusNo());
        cmd.setFixmedinsHilistName(medication.getName());
        cmd.setFixmedinsBchno(InventoryUploadBatchNoAssembler.buildFixmedinsBchno(
            medication.getBusNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setCnt(qty);
        cmd.setPric(line.getPrice());
        cmd.setInvChgType(invChgType);
        cmd.setInvChgTime(resolveChangeDateTime(line));
        cmd.setInvChgOpterName(resolveOperatorName(line));
        cmd.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(medication.getRxFlag()));
        cmd.setMemo(line.getRemake());
        uploadTraceLineSupport.applyMedicationSupplyLineTrace(
            medication, line.getUnitCode(), line.getTraceNo(), cmd);
        fillInventoryDates(cmd, line);
        return cmd;
    }

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

    private String resolveChangeDateTime(SupplyRequestModel line) {
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
