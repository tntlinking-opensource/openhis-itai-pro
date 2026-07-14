/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
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
import com.openhis.chs.bridge.inventory.assembler.InventoryInvChgTypeResolver;
import com.openhis.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.openhis.chs.domain.inventory.command.UploadAfterStocktakeCommand;
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
 * HIS 主数据组装盘点盈亏上报请求（药房/科室盘点 supply_request → CHS 3502）。
 *
 * <p>加载来源：{@link com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel}
 * 盘点单行（{@code CABINET_STOCKTAKE}）、药品/耗材定义、
 * {@link com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem} 批号效期及操作员信息。</p>
 *
 * <p>日常盘点仅上报 3502 盘盈/盘损变动；3501 盘存总量由运维批初始化 {@code initializeInventory} 一次性上报。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryStocktakeUploadContextLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SupplyRequestQueryService supplyRequestQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final ChsInsuranceContractReaderImpl contractReader;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;

    /** 组装 3502 盘盈盘亏库存变更上报命令。 */
    public InvUpload3502Command build3502Request(UploadAfterStocktakeCommand command) {
        Contract contract = contractReader.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadStocktakeLines(command);
        InvUpload3502Command request = new InvUpload3502Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setSourceType(resolveSourceType(command));
        if (!lines.isEmpty()) {
            request.setSourceId(lines.get(0).getId());
        }
        for (SupplyRequestModel line : lines) {
            InvUploadLineCommand cmd = buildStocktake3502Line(line);
            if (cmd != null) {
                request.getLines().add(cmd);
            }
        }
        return request;
    }

    /**
     * 按盘点单号加载可上报的 supply_request 行。
     *
     * @param command 盘点上报命令
     * @return 可上报的盘点行列表
     */
    private List<SupplyRequestModel> loadStocktakeLines(UploadAfterStocktakeCommand command) {
        if (command == null || isEmpty(command.getStocktakeBusNo())) {
            return List.of();
        }
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(command.getStocktakeBusNo().trim());
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
            if (!isUploadableItemTable(row.getItemTable())) {
                continue;
            }
            String expectedType = resolveExpectedSupplyType(command);
            if (isNotEmpty(expectedType) && !expectedType.equals(row.getTypeEnum())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 判断品种表是否支持进销存上报。
     *
     * @param itemTable 品种表名
     * @return 药品或耗材定义表时返回 true
     */
    private static boolean isUploadableItemTable(String itemTable) {
        return TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable);
    }

    /**
     * 解析上报来源类型。
     *
     * @param command 盘点上报命令
     * @return 来源类型，缺省时为 STOCKTAKE
     */
    private static String resolveSourceType(UploadAfterStocktakeCommand command) {
        if (command != null && isNotEmpty(command.getSourceType())) {
            return command.getSourceType().trim();
        }
        return ChsInventoryConstants.SOURCE_TYPE_STOCKTAKE;
    }

    /**
     * 解析期望的 supply_request 类型编码。
     *
     * @param command 盘点上报命令
     * @return 供应类型编码
     */
    private static String resolveExpectedSupplyType(UploadAfterStocktakeCommand command) {
        if (command == null || isEmpty(command.getSupplyTypeCode())) {
            return SupplyType.CABINET_STOCKTAKE.getCode();
        }
        return command.getSupplyTypeCode().trim();
    }

    /**
     * 将盘点行组装为 3502 盘盈盘亏上报明细。
     *
     * @param line 盘点行
     * @return 3502 上报行命令，盈亏数量为零时返回 null
     */
    private InvUploadLineCommand buildStocktake3502Line(SupplyRequestModel line) {
        BigDecimal profitLossQty = line.getItemQuantity();
        if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        StocktakeProductSnapshot product = resolveProduct(line);
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(product.ybNo()));
        cmd.setFixmedinsHilistId(product.busNo());
        cmd.setFixmedinsHilistName(product.name());
        cmd.setFixmedinsBchno(InventoryUploadBatchNoAssembler.buildFixmedinsBchno(
            product.busNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setCnt(profitLossQty.abs());
        cmd.setPric(line.getPrice());
        cmd.setInvChgType(InventoryInvChgTypeResolver.resolveInvChgType(line));
        cmd.setInvChgTime(resolveStocktakeDateTime(line));
        cmd.setInvChgOpterName(resolveOperatorName(line));
        cmd.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(product.rxFlag()));
        cmd.setMemo(line.getRemake());
        applyMedicationTraceIfPresent(line, cmd);
        fillInventoryDates(cmd, line);
        return cmd;
    }

    /**
     * 药品盘点行填充追溯策略与码列表；非药品仅标记无医保码策略。
     *
     * @param line 盘点行
     * @param cmd  上报行命令
     */
    private void applyMedicationTraceIfPresent(SupplyRequestModel line, InvUploadLineCommand cmd) {
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable())) {
            if (isEmpty(cmd.getMedListCodg())) {
                cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
            }
            return;
        }
        MedicationDefinition medication = medicationDefinitionQueryService
            .findOptionalMedicationDefinitionEntityById(line.getItemId())
            .orElse(null);
        if (medication == null) {
            return;
        }
        uploadTraceLineSupport.applyMedicationSupplyLineTrace(
            medication, line.getUnitCode(), line.getTraceNo(), cmd);
    }

    /**
     * 按品种表解析盘点品种快照信息。
     *
     * @param line 盘点行
     * @return 品种业务编码、名称、医保编码及处方药标志
     */
    private StocktakeProductSnapshot resolveProduct(SupplyRequestModel line) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable())) {
            MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(line.getItemId())
                .orElseThrow(() -> new IllegalStateException("药品定义不存 " + line.getItemId()));
            return new StocktakeProductSnapshot(
                medication.getBusNo(),
                medication.getName(),
                medication.getYbNo(),
                medication.getRxFlag());
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(line.getItemTable())) {
            DeviceDefinition device = deviceDefinitionQueryService.getDeviceDefinition(line.getItemId());
            if (device == null) {
                throw new IllegalStateException("耗材定义不存 " + line.getItemId());
            }
            return new StocktakeProductSnapshot(
                device.getBusNo(),
                device.getName(),
                device.getYbNo(),
                device.getRxFlag());
        }
        throw new IllegalStateException("不支持的盘点品种 " + line.getItemTable());
    }

    /** 盘点品种快照（业务编码、名称、医保编码、处方药标志）。 */
    private record StocktakeProductSnapshot(String busNo, String name, String ybNo, Boolean rxFlag) {
    }

    /**
     * 解析盘点库存变更时间。
     *
     * @param line 盘点行
     * @return 格式化后的变更时间
     */
    private String resolveStocktakeDateTime(SupplyRequestModel line) {
        if (line.getApprovalTime() != null) {
            return line.getApprovalTime().format(DATE_TIME_FMT);
        }
        if (line.getOccurrenceTime() != null) {
            return line.getOccurrenceTime().format(DATE_TIME_FMT);
        }
        return DATE_TIME_FMT.format(java.time.LocalDateTime.now());
    }

    /**
     * 解析盘点操作员姓名。
     *
     * @param line 盘点行
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
     * 从库存项填充生产日期与有效期。
     *
     * @param cmd  上报行命令
     * @param line 盘点行
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
