/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.health.domain.custom.tracenumberrecord.entity.TraceNumberRecord;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.whale.chs.jilin.contracts.client.ChsDispenseTraceGateClient;
import com.openhis.whale.chs.jilin.contracts.command.DispenseTraceGateCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 发药前追溯码门控（§4.8.1 类型 D + block-settle-without-trace）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryTraceDispenseGateSupport implements ChsDispenseTraceGateClient {

    private final InventoryTracePolicyResolver tracePolicyResolver;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final TraceNumberRecordQueryService traceNumberRecordQueryService;

    @Value("${openhis.chs.inventory.block-settle-without-trace:false}")
    private boolean blockSettleWithoutTrace;

    @Override
    public void assertTraceAllowedForPrepare(
        DispenseTraceGateCommand command,
        List<String> submittedTraceCodes
    ) {
        if (!blockSettleWithoutTrace || command == null || command.getMedicationId() == null) {
            return;
        }
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(command.getMedicationId())
            .orElse(null);
        if (medication == null) {
            return;
        }
        String tracePolicy = tracePolicyResolver.resolve(medication, command.getUnitCode());
        if (!ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED.equals(tracePolicy)) {
            return;
        }
        if (hasNonBlankTraceCode(submittedTraceCodes)) {
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED,
            "应扫码品种未录入追溯码，发药已阻断（block-settle-without-trace）");
    }

    @Override
    public void assertTraceAllowedForDispense(DispenseTraceGateCommand command) {
        if (!blockSettleWithoutTrace || command == null || command.getMedicationId() == null) {
            return;
        }
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(command.getMedicationId())
            .orElse(null);
        if (medication == null) {
            return;
        }
        String tracePolicy = tracePolicyResolver.resolve(medication, command.getUnitCode());
        if (!ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED.equals(tracePolicy)) {
            return;
        }
        if (!loadOutboundTraceCodes(command).isEmpty()) {
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.EX_OPP_203_DISPENSE_STATUS_NOT_ALLOWED,
            "应扫码品种未录入追溯码，发药已阻断（block-settle-without-trace）");
    }

    private static boolean hasNonBlankTraceCode(List<String> traceCodes) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return false;
        }
        for (String code : traceCodes) {
            if (StringUtils.isNotBlank(code)) {
                return true;
            }
        }
        return false;
    }

    private List<String> loadOutboundTraceCodes(DispenseTraceGateCommand command) {
        List<String> codes = new ArrayList<>();
        if (StringUtils.isBlank(command.getLotNumber())) {
            return codes;
        }
        List<TraceNumberRecord> rows = traceNumberRecordQueryService.listEntitiesByItemTableAndItemIdAndLotNumberAndLocationId(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            command.getMedicationId(),
            command.getLotNumber(),
            command.getLocationId());
        for (TraceNumberRecord row : rows) {
            if (row == null || StringUtils.isBlank(row.getTraceNo())) {
                continue;
            }
            if (!TraceDirection.OUT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (StringUtils.isNotBlank(command.getBusNo()) && !command.getBusNo().equals(row.getBusNo())) {
                continue;
            }
            codes.add(row.getTraceNo().trim());
        }
        return codes;
    }
}
