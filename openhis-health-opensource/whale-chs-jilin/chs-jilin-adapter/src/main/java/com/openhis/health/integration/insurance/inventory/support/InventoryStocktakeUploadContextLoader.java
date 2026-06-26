/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.chs.domain.inventory.dto.request.UploadStocktake3501Request;
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
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterStocktakeCommand;
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

/** 从药库/药房/科室盘点单组装 3501/3502 上报请求。 */
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
    private final InsuranceContractResolver contractResolver;

    public UploadStocktake3501Request build3501Request(UploadAfterStocktakeCommand command) {
        Contract contract = contractResolver.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadStocktakeLines(command);
        UploadStocktake3501Request request = new UploadStocktake3501Request();
        request.setContractBusNo(contractResolver.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        if (!lines.isEmpty()) {
            request.setSourceId(lines.get(0).getId());
        }
        for (SupplyRequestModel line : lines) {
            InvUploadLineCommand cmd = buildStocktake3501Line(line);
            if (cmd != null) {
                request.getLines().add(cmd);
            }
        }
        return request;
    }

    public UploadInvChange3502Request build3502Request(UploadAfterStocktakeCommand command) {
        Contract contract = contractResolver.resolveOrDefault(command.getContractBusNo(), command.getContractId());
        List<SupplyRequestModel> lines = loadStocktakeLines(command);
        UploadInvChange3502Request request = new UploadInvChange3502Request();
        request.setContractBusNo(contractResolver.requireContractBusNo(contract));
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

    private List<SupplyRequestModel> loadStocktakeLines(UploadAfterStocktakeCommand command) {
        if (command == null || StringUtils.isBlank(command.getStocktakeBusNo())) {
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
            if (StringUtils.isNotBlank(expectedType) && !expectedType.equals(row.getTypeEnum())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    private static boolean isUploadableItemTable(String itemTable) {
        return TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable);
    }

    private static String resolveSourceType(UploadAfterStocktakeCommand command) {
        if (command != null && StringUtils.isNotBlank(command.getSourceType())) {
            return command.getSourceType().trim();
        }
        return ChsInventoryConstants.SOURCE_TYPE_STOCKTAKE;
    }

    private static String resolveExpectedSupplyType(UploadAfterStocktakeCommand command) {
        if (command == null || StringUtils.isBlank(command.getSupplyTypeCode())) {
            return SupplyType.CABINET_STOCKTAKE.getCode();
        }
        return command.getSupplyTypeCode().trim();
    }

    private InvUploadLineCommand buildStocktake3501Line(SupplyRequestModel line) {
        BigDecimal actualQty = resolveActualQuantity(line);
        if (actualQty == null) {
            return null;
        }
        StocktakeProductSnapshot product = resolveProduct(line);
        InvUploadLineCommand cmd = new InvUploadLineCommand();
        cmd.setSourceLineId(line.getId());
        cmd.setMedListCodg(trimToNull(product.ybNo()));
        cmd.setFixmedinsHilistId(product.busNo());
        cmd.setFixmedinsHilistName(product.name());
        cmd.setFixmedinsBchno(InventoryUploadBatchNoSupport.buildFixmedinsBchno(
            product.busNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setInvCnt(actualQty);
        cmd.setInvdate(resolveStocktakeDate(line));
        cmd.setRxFlag(InventoryPlatformFieldSupport.toRxFlagCode(product.rxFlag()));
        cmd.setMemo(line.getRemake());
        if (StringUtils.isBlank(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }
        fillInventoryDates(cmd, line);
        return cmd;
    }

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
        cmd.setFixmedinsBchno(InventoryUploadBatchNoSupport.buildFixmedinsBchno(
            product.busNo(), line.getLotNumber()));
        cmd.setManuLotnum(line.getLotNumber());
        cmd.setCnt(profitLossQty.abs());
        cmd.setPric(line.getPrice());
        cmd.setInvChgType(InventoryInvChgTypeResolver.resolveInvChgType(line));
        cmd.setInvChgTime(resolveStocktakeDateTime(line));
        cmd.setInvChgOpterName(resolveOperatorName(line));
        cmd.setRxFlag(InventoryPlatformFieldSupport.toRxFlagCode(product.rxFlag()));
        cmd.setMemo(line.getRemake());
        if (StringUtils.isBlank(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
        }
        fillInventoryDates(cmd, line);
        return cmd;
    }

    private StocktakeProductSnapshot resolveProduct(SupplyRequestModel line) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable())) {
            MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(line.getItemId())
                .orElseThrow(() -> new IllegalStateException("药品定义不存在: " + line.getItemId()));
            return new StocktakeProductSnapshot(
                medication.getBusNo(),
                medication.getName(),
                medication.getYbNo(),
                medication.getRxFlag());
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(line.getItemTable())) {
            DeviceDefinition device = deviceDefinitionQueryService.getDeviceDefinition(line.getItemId());
            if (device == null) {
                throw new IllegalStateException("耗材定义不存在: " + line.getItemId());
            }
            return new StocktakeProductSnapshot(
                device.getBusNo(),
                device.getName(),
                device.getYbNo(),
                device.getRxFlag());
        }
        throw new IllegalStateException("不支持的盘点品种表: " + line.getItemTable());
    }

    private record StocktakeProductSnapshot(String busNo, String name, String ybNo, Boolean rxFlag) {
    }

    private static BigDecimal resolveActualQuantity(SupplyRequestModel line) {
        if (line.getTotalQuantity() != null) {
            return line.getTotalQuantity();
        }
        return null;
    }

    private String resolveStocktakeDate(SupplyRequestModel line) {
        if (line.getApprovalTime() != null) {
            return line.getApprovalTime().toLocalDate().format(DATE_FMT);
        }
        if (line.getOccurrenceTime() != null) {
            return line.getOccurrenceTime().toLocalDate().format(DATE_FMT);
        }
        return DATE_FMT.format(java.time.LocalDate.now());
    }

    private String resolveStocktakeDateTime(SupplyRequestModel line) {
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