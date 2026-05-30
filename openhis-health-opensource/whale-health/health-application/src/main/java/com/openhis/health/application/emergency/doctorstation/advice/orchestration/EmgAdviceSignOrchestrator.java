/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.emergency.doctorstation.advice.orchestration;

import com.openhis.health.application.emergency.doctorstation.advice.constants.EmergencyDoctorStationAdviceConstants;
import com.openhis.health.application.emergency.doctorstation.advice.mapper.EmergencyDoctorStationAdviceMapper;
import com.openhis.health.application.emergency.doctorstation.support.EmergencySelfPayAccountSupport;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceResourceKeyDto;
import com.openhis.health.application.outpatient.doctorstation.advice.port.LisPacsAdviceApplyCommand;
import com.openhis.health.application.outpatient.doctorstation.advice.port.LisPacsApplyGateway;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SignDoctorStationAdviceBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SignDoctorStationAdviceBatchResponse;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.workflow.devicedispense.command.ReplaceFifoDeviceDispenseDraftsCommand;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestFifoSignContext;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 急诊医嘱签发：不进行 FIFO 库存拆分，药/材使用单段发放（默认急诊库位占位）。
 */
@Component
@RequiredArgsConstructor
public class EmgAdviceSignOrchestrator {

    private final EmergencyDoctorStationAdviceMapper emergencyDoctorStationAdviceMapper;
    private final EmergencySelfPayAccountSupport emergencySelfPayAccountSupport;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final BusinessSequenceService businessSequenceService;
    private final LisPacsApplyGateway lisPacsApplyGateway;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    public SignDoctorStationAdviceBatchResponse signBatch(SignDoctorStationAdviceBatchRequest request, Encounter encounter, Long operatorId) {
        Long orgId = encounter.getOrganizationId();
        Long accountId = emergencySelfPayAccountSupport.requireSelfPayAccountId(
            request.getEncounterId(), request.getPatientId(), operatorId);
        String signBatch;
        if (StringUtils.isNotBlank(request.getSignBatchCode())) {
            signBatch = request.getSignBatchCode();
        } else {
            signBatch = businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        }
        Map<Long, String> rxMap = new HashMap<>();
        for (DoctorStationAdviceResourceKeyDto t : request.getSignTargets()) {
            String pt = normalizeProductTableName(t.getProductTableName());
            if (isMedicationProductTable(pt)) {
                String prescriptionNo =
                    businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
                medicationRequestDomainService.signMedicationOrder(
                    emergencyDoctorStationAdviceMapper.toSignMedicationOrderCommand(t.getRequestId(), signBatch, prescriptionNo, operatorId));
                rxMap.put(t.getRequestId(), prescriptionNo);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(t.getRequestId()));
                MedicationRequestFifoSignContext fifoMedCtx =
                    medicationRequestQueryService.loadFifoSignContextOrThrow(t.getRequestId());
                ReplaceFifoMedicationDispenseDraftsCommand.Segment seg = new ReplaceFifoMedicationDispenseDraftsCommand.Segment();
                seg.setLocationId(EmergencyDoctorStationAdviceConstants.DEFAULT_EMERGENCY_DISPENSE_LOCATION_ID);
                seg.setQuantity(fifoMedCtx.getQuantity());
                seg.setLotNumber(null);
                ReplaceFifoMedicationDispenseDraftsCommand fifoMedCmd = new ReplaceFifoMedicationDispenseDraftsCommand();
                fifoMedCmd.setOperatorId(operatorId);
                fifoMedCmd.setMedReqId(fifoMedCtx.getMedReqId());
                fifoMedCmd.setMedicationId(fifoMedCtx.getMedicationId());
                fifoMedCmd.setEncounterId(fifoMedCtx.getEncounterId());
                fifoMedCmd.setPatientId(fifoMedCtx.getPatientId());
                fifoMedCmd.setUnitCode(fifoMedCtx.getUnitCode());
                fifoMedCmd.setDispenseEnum(MedicationDispenseType.OUTPATIENT);
                fifoMedCmd.setSegments(List.of(seg));
                List<Long> medDispenseIds = medicationDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoMedCmd);
                List<BigDecimal> medQtys = List.of(fifoMedCtx.getQuantity());
                createDispenseSegmentChargeDrafts(
                    request.getEncounterId(),
                    request.getPatientId(),
                    accountId,
                    orgId,
                    operatorId,
                    fifoMedCtx.getMedReqId(),
                    TableNameConstants.MED_MEDICATION_REQUEST,
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    fifoMedCtx.getMedicationId(),
                    TableNameConstants.MED_MEDICATION_DISPENSE,
                    medDispenseIds,
                    medQtys,
                    fifoMedCtx.getUnitCode());
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
            } else if (isActivityProductTable(pt)) {
                serviceRequestDomainService.signServiceRequest(
                    emergencyDoctorStationAdviceMapper.toSignServiceRequestCommand(t.getRequestId(), signBatch, operatorId));
                LisPacsAdviceApplyCommand lisPacsStub = new LisPacsAdviceApplyCommand();
                lisPacsStub.setEncounterId(request.getEncounterId());
                lisPacsStub.setPatientId(request.getPatientId());
                lisPacsApplyGateway.submitIfLabOrImaging(lisPacsStub);
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, t.getRequestId());
            } else if (isDeviceProductTable(pt)) {
                deviceRequestDomainService.signDeviceOrder(emergencyDoctorStationAdviceMapper.toSignDeviceOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, List.of(t.getRequestId()));
                DeviceRequestFifoSignContext fifoDevCtx =
                    deviceRequestQueryService.loadFifoSignContextOrThrow(t.getRequestId());
                ReplaceFifoDeviceDispenseDraftsCommand.Segment seg = new ReplaceFifoDeviceDispenseDraftsCommand.Segment();
                seg.setLocationId(EmergencyDoctorStationAdviceConstants.DEFAULT_EMERGENCY_DISPENSE_LOCATION_ID);
                seg.setQuantity(fifoDevCtx.getQuantity());
                seg.setLotNumber(null);
                ReplaceFifoDeviceDispenseDraftsCommand fifoDevCmd = new ReplaceFifoDeviceDispenseDraftsCommand();
                fifoDevCmd.setOperatorId(operatorId);
                fifoDevCmd.setDeviceReqId(fifoDevCtx.getDeviceReqId());
                fifoDevCmd.setDeviceDefId(fifoDevCtx.getDeviceDefId());
                fifoDevCmd.setEncounterId(fifoDevCtx.getEncounterId());
                fifoDevCmd.setPatientId(fifoDevCtx.getPatientId());
                fifoDevCmd.setUnitCode(fifoDevCtx.getUnitCode());
                fifoDevCmd.setSegments(List.of(seg));
                List<Long> devDispenseIds = deviceDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoDevCmd);
                List<BigDecimal> devQtys = List.of(fifoDevCtx.getQuantity());
                createDispenseSegmentChargeDrafts(
                    request.getEncounterId(),
                    request.getPatientId(),
                    accountId,
                    orgId,
                    operatorId,
                    fifoDevCtx.getDeviceReqId(),
                    TableNameConstants.WKF_DEVICE_REQUEST,
                    TableNameConstants.ADM_DEVICE_DEFINITION,
                    fifoDevCtx.getDeviceDefId(),
                    TableNameConstants.WKF_DEVICE_DISPENSE,
                    devDispenseIds,
                    devQtys,
                    fifoDevCtx.getUnitCode());
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + t.getProductTableName());
            }
        }
        SignDoctorStationAdviceBatchResponse resp = new SignDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setSignBatchCode(signBatch);
        resp.setPrescriptionNoByMedicationRequestId(rxMap);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    private void createDispenseSegmentChargeDrafts(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        List<Long> dispenseIds,
        List<BigDecimal> segmentQuantities,
        String quantityUnit
    ) {
        if (dispenseIds == null || segmentQuantities == null || dispenseIds.size() != segmentQuantities.size()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "发放分段与费用草稿数量不一致");
        }
        if (dispenseIds.isEmpty()) {
            return;
        }
        ChargeItemDefinition priceLine = firstPriceLine(productDefinitionTable, productDefinitionId);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "未配置符合开立规则的收费定价（药品/耗材须批号售价 LOT_NUMBER_PRICE）。请先维护收费项定价后再签发医嘱。");
        }
        ProductDefinitionDisplayDto productRow = loadSingleProduct(productDefinitionTable, productDefinitionId);
        List<CreateChargeItemCommand.Item> draftItems = new ArrayList<>();
        for (int i = 0; i < dispenseIds.size(); i++) {
            draftItems.add(emergencyDoctorStationAdviceMapper.toCreateChargeItemDraftItemForFifoDispenseSegment(
                encounterId,
                patientId,
                accountId,
                orgId,
                requestId,
                requestServiceTable,
                productDefinitionTable,
                productDefinitionId,
                dispenseTable,
                dispenseIds.get(i),
                priceLine,
                segmentQuantities.get(i),
                quantityUnit,
                productRow));
        }
        CreateChargeItemCommand batchCmd = new CreateChargeItemCommand();
        batchCmd.setOperatorId(operatorId);
        batchCmd.setItems(draftItems);
        chargeItemDomainService.createDraftChargeItemFromOrder(batchCmd);
    }

    private ChargeItemDefinition firstPriceLine(String instanceTable, Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(instanceTable);
        priceQuery.setInstanceIds(List.of(instanceId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        return DoctorStationAdviceCatalogAssembler.selectFirstPriceLineForChargeDraft(instanceTable, priceLines);
    }

    private ProductDefinitionDisplayDto loadSingleProduct(String table, Long id) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(table);
        lookup.setProductDefinitionId(id);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, id);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_ACTIVITY_PRODUCT_DEFINITION_NOT_FOUND);
        }
        return row;
    }

    private static String normalizeProductTableName(String productTableName) {
        return StringUtils.trimToEmpty(productTableName);
    }

    private static boolean isMedicationProductTable(String productTableName) {
        return TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }

    private static boolean isDeviceProductTable(String productTableName) {
        return TableNameConstants.ADM_DEVICE_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }

    private static boolean isActivityProductTable(String productTableName) {
        return TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }
}
