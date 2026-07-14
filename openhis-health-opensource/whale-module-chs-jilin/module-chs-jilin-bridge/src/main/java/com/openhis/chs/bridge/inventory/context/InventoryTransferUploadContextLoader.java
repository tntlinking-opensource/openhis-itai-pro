/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.share.enums.InvChgType;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
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
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.openhis.chs.domain.inventory.command.UploadAfterTransferCommand;
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
 * HIS 主数据组装调拨库存变更上报请求（药库↔药房调拨 supply_request → CHS 3502）。
 *
 * <p>加载来源：{@link com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel}
 * 调拨单行、{@link com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition}、
 * {@link com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem} 批号效期及操作员信息。</p>
 *
 * <p>Health 业务 AppService 不再自动触发院内调拨 101/102；本 Loader 供 Extension {@code uploadAfterTransferAgree}
 * 与运维台手工 3502 补传使用。</p>
 */
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
    private final ChsInsuranceContractReaderImpl contractReader;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;

    /** 组装 3502 调拨库存变更上报命令。 */
    public InvUpload3502Command build3502Request(UploadAfterTransferCommand command) {
        Contract contract = contractReader.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadTransferMedicationLines(command);
        InvUpload3502Command request = new InvUpload3502Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
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

    /**
     * 按调拨单号加载调拨的药品 supply_request 行。
     *
     * @param command 调拨上报命令
     * @return 可上报的调拨行列表
     */
    private List<SupplyRequestModel> loadTransferMedicationLines(UploadAfterTransferCommand command) {
        if (command == null || isEmpty(command.getTransferBusNo())) {
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
            if (isNotEmpty(command.getSupplyTypeCode())
                && !command.getSupplyTypeCode().trim().equals(row.getTypeEnum())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 将调拨 supply_request 行组装为 3502 库存变更上报明细。
     *
     * @param line    调拨行
     * @param command 调拨上报命令
     * @return 3502 上报行命令，数量或变更类型无效时返回 null
     */
    private InvUploadLineCommand buildTransfer3502Line(
        SupplyRequestModel line,
        UploadAfterTransferCommand command
    ) {
        BigDecimal transferQty = line.getItemQuantity();
        if (transferQty == null || transferQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (isEmpty(command.getInvChgType())) {
            return null;
        }
        InvChgType invChgType = InvChgType.fromCodeOptional(command.getInvChgType());
        if (invChgType == null) {
            return null;
        }
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(line.getItemId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存 " + line.getItemId()));
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(medication.getYbNo()));
        cmd.setFixmedinsHilistId(medication.getBusNo());
        cmd.setFixmedinsHilistName(medication.getName());
        cmd.setFixmedinsBchno(InventoryUploadBatchNoAssembler.buildFixmedinsBchno(
            medication.getBusNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setCnt(transferQty);
        cmd.setPric(line.getPrice());
        cmd.setInvChgType(invChgType.getCode());
        cmd.setInvChgTime(resolveTransferDateTime(line));
        cmd.setInvChgOpterName(resolveOperatorName(line));
        cmd.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(medication.getRxFlag()));
        cmd.setMemo(line.getRemake());
        uploadTraceLineSupport.applyMedicationSupplyLineTrace(
            medication, line.getUnitCode(), line.getTraceNo(), cmd);
        fillInventoryDates(cmd, line, invChgType);
        return cmd;
    }

    /**
     * 按调拨方向从库存项填充生产日期与有效期。
     *
     * @param cmd        上报行命令
     * @param line       调拨行
     * @param invChgType 库存变更类型
     */
    private void fillInventoryDates(InvUploadLineCommand cmd, SupplyRequestModel line, InvChgType invChgType) {
        if (isEmpty(line.getLotNumber())) {
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

    /**
     * 按库存变更类型解析库存位置 ID。
     *
     * @param line       调拨行
     * @param invChgType 库存变更类型
     * @return 源库位或目标库位 ID
     */
    private static Long resolveInventoryLocationId(SupplyRequestModel line, InvChgType invChgType) {
        if (InvChgType.TRANSFER_OUT == invChgType) {
            return line.getSourceLocationId();
        }
        return line.getPurposeLocationId();
    }

    /**
     * 解析调拨库存变更时间。
     *
     * @param line 调拨行
     * @return 格式化后的变更时间
     */
    private String resolveTransferDateTime(SupplyRequestModel line) {
        if (line.getApprovalTime() != null) {
            return line.getApprovalTime().format(DATE_TIME_FMT);
        }
        if (line.getOccurrenceTime() != null) {
            return line.getOccurrenceTime().format(DATE_TIME_FMT);
        }
        return DATE_TIME_FMT.format(java.time.LocalDateTime.now());
    }

    /**
     * 解析调拨操作员姓名。
     *
     * @param line 调拨行
     * @return 操作员姓名
     */
    private String resolveOperatorName(SupplyRequestModel line) {
        Long practitionerId = line.getApproverId();
        if (practitionerId == null) {
            practitionerId = line.getPractitionerId();
        }
        return resolvePractitionerName(practitionerId);
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
