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

package com.openhis.health.application.emergency.doctorstation.advice.service;

import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.emergency.doctorstation.advice.mapper.EmergencyDoctorStationAdviceMapper;
import com.openhis.health.application.emergency.doctorstation.advice.orchestration.EmgAdviceCatalogQueryOrchestrator;
import com.openhis.health.application.emergency.doctorstation.advice.orchestration.EmgAdviceSignOrchestrator;
import com.openhis.health.application.emergency.doctorstation.advice.request.PageEmergencyAdviceCatalogRequest;
import com.openhis.health.application.emergency.doctorstation.support.EmergencyEncounterContextSupport;
import com.openhis.health.application.emergency.doctorstation.support.EmergencySelfPayAccountSupport;
import com.openhis.health.application.emergency.doctorstation.advice.assembler.EmergencyDoctorStationAdviceListAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceResourceKeyDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.query.DoctorStationAdviceOrderBindQueryService;
import com.openhis.health.application.outpatient.doctorstation.advice.request.GetDoctorStationAdviceOrderBindInfoRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.InitDoctorStationAdviceRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.ListDoctorStationAdviceRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SignDoctorStationAdviceBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.UpdateDoctorStationMedicationAdviceGroupsRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.WithdrawDoctorStationAdviceBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.response.GetDoctorStationAdviceOrderBindInfoResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.InitDoctorStationAdviceResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ListDoctorStationAdviceResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.PageDoctorStationAdviceCatalogResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SaveDoctorStationAdviceDraftBatchResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SignDoctorStationAdviceBatchResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.UpdateDoctorStationMedicationAdviceGroupsResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.WithdrawDoctorStationAdviceBatchResponse;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.medication.medicationdispense.command.ReleaseDispenseReserveCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.devicedispense.command.ReleaseDeviceDispenseReserveCommand;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 急诊医生站医嘱开立应用服务（不经门诊信息读模型门控）。
 */
@Service
@RequiredArgsConstructor
public class EmergencyDoctorStationAdviceAppService {

    private final ICurrentUser currentUser;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final EmergencyEncounterContextSupport emergencyEncounterContextSupport;
    private final EmergencyDoctorStationAdviceMapper emergencyDoctorStationAdviceMapper;
    private final EmergencyDoctorStationAdviceListAssembler emergencyDoctorStationAdviceListAssembler;
    private final DoctorStationAdviceOrderBindQueryService doctorStationAdviceOrderBindQueryService;
    private final EmergencySelfPayAccountSupport emergencySelfPayAccountSupport;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final CodeSystemQueryService codeSystemQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final EmgAdviceCatalogQueryOrchestrator emgAdviceCatalogQueryOrchestrator;
    private final EmgAdviceSignOrchestrator emgAdviceSignOrchestrator;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    public InitDoctorStationAdviceResponse initEmergencyDoctorStationAdvice(InitDoctorStationAdviceRequest request) {
        emergencyEncounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        InitDoctorStationAdviceResponse resp = new InitDoctorStationAdviceResponse();
        resp.setMedicationRateOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.RATE_CODE));
        resp.setMedicationRouteOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.METHOD_CODE));
        return resp;
    }

    public ListDoctorStationAdviceResponse listEmergencyDoctorStationAdvice(ListDoctorStationAdviceRequest request) {
        emergencyEncounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        Long uid = currentUser.getCurrentUserId();
        List<DoctorStationAdviceRowDto> rows =
            emergencyDoctorStationAdviceListAssembler.assembleRows(
                request.getEncounterId(), request.getPatientId(), request.getOnlyMine(), uid);
        return PagedResultBuilder.buildList(new ListDoctorStationAdviceResponse(), rows);
    }

    public PageDoctorStationAdviceCatalogResponse pageEmergencyAdviceCatalog(PageEmergencyAdviceCatalogRequest request) {
        emergencyEncounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        Page<DoctorStationAdviceCatalogRowDto> page =
            emgAdviceCatalogQueryOrchestrator.pageEmergencyAdviceCatalog(request);
        return PagedResultBuilder.buildFromPage(new PageDoctorStationAdviceCatalogResponse(), page);
    }

    public GetDoctorStationAdviceOrderBindInfoResponse getEmergencyAdviceOrderBindInfo(GetDoctorStationAdviceOrderBindInfoRequest request) {
        Long itemId = emergencyDoctorStationAdviceMapper.parseOrderBindItemId(request.getItemNo(), request.getItemId());
        GetDoctorStationAdviceOrderBindInfoResponse resp = new GetDoctorStationAdviceOrderBindInfoResponse();
        resp.setBindRows(doctorStationAdviceOrderBindQueryService.listBindings(
            request.getBindType(), itemId, request.getOrganizationUnitId()));
        return resp;
    }

    @Transactional
    public SaveDoctorStationAdviceDraftBatchResponse saveEmergencyDoctorStationAdviceDraftBatch(SaveDoctorStationAdviceDraftBatchRequest request) {
        Encounter encounter = emergencyEncounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = encounter.getOrganizationId();
        Long accountId = emergencySelfPayAccountSupport.requireSelfPayAccountId(
            request.getEncounterId(), request.getPatientId(), operatorId);
        List<Long> newMedIds = new ArrayList<>();
        for (DoctorStationAdviceDraftLineRequest line : request.getLines()) {
            String op = StringUtils.upperCase(StringUtils.trimToEmpty(line.getLineOp()));
            if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                handleCreateLine(request, line, operatorId, orgId, accountId, newMedIds);
            } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                handleUpdateLine(request, line, operatorId, orgId, accountId);
            } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                handleDeleteLine(request, line, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的行操作: " + line.getLineOp());
            }
        }
        SaveDoctorStationAdviceDraftBatchResponse resp = new SaveDoctorStationAdviceDraftBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setNewMedicationRequestIds(newMedIds);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    @Transactional
    public SignDoctorStationAdviceBatchResponse signEmergencyDoctorStationAdviceBatch(SignDoctorStationAdviceBatchRequest request) {
        Encounter encounter = emergencyEncounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        return emgAdviceSignOrchestrator.signBatch(request, encounter, operatorId);
    }

    @Transactional
    public WithdrawDoctorStationAdviceBatchResponse withdrawEmergencyDoctorStationAdviceBatch(WithdrawDoctorStationAdviceBatchRequest request) {
        emergencyEncounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        for (DoctorStationAdviceResourceKeyDto t : request.getTargets()) {
            String pt = normalizeProductTableName(t.getProductTableName());
            if (isMedicationProductTable(pt)) {
                List<Long> medDispenseIds = medicationDispenseQueryService.listMedicationDispenseIdsByMedicationRequest(t.getRequestId());
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, t.getRequestId());
                medicationRequestDomainService.signOffMedicationOrder(
                    emergencyDoctorStationAdviceMapper.toSignOffMedicationOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(t.getRequestId()));
                ReleaseDispenseReserveCommand releaseMed = new ReleaseDispenseReserveCommand();
                releaseMed.setMedReqId(t.getRequestId());
                releaseMed.setOperatorId(operatorId);
                medicationDispenseDomainService.releaseDispenseReserve(releaseMed);
            } else if (isActivityProductTable(pt)) {
                serviceRequestDomainService.withdrawSignedServiceRequest(
                    emergencyDoctorStationAdviceMapper.toWithdrawServiceRequestCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, t.getRequestId());
            } else if (isDeviceProductTable(pt)) {
                List<Long> devDispenseIds = deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(t.getRequestId());
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, t.getRequestId());
                deviceRequestDomainService.signOffDeviceOrder(
                    emergencyDoctorStationAdviceMapper.toSignOffDeviceOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, List.of(t.getRequestId()));
                if (!devDispenseIds.isEmpty()) {
                    ReleaseDeviceDispenseReserveCommand releaseDev = new ReleaseDeviceDispenseReserveCommand();
                    releaseDev.setIdListOrRequestKey(String.valueOf(t.getRequestId()));
                    releaseDev.setOperatorId(operatorId);
                    deviceDispenseDomainService.releaseDeviceDispenseReserve(releaseDev);
                }
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + t.getProductTableName());
            }
        }
        WithdrawDoctorStationAdviceBatchResponse resp = new WithdrawDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    @Transactional
    public UpdateDoctorStationMedicationAdviceGroupsResponse updateEmergencyMedicationAdviceGroups(UpdateDoctorStationMedicationAdviceGroupsRequest request) {
        emergencyEncounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        if (request.getClearGroupIds() != null && !request.getClearGroupIds().isEmpty()) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                emergencyDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(request.getClearGroupIds(), null, operatorId));
        }
        if (request.getAssignGroup() != null && request.getAssignGroup().getMedicationRequestIds() != null) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                emergencyDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                    request.getAssignGroup().getMedicationRequestIds(), request.getAssignGroup().getGroupId(), operatorId));
        }
        UpdateDoctorStationMedicationAdviceGroupsResponse resp = new UpdateDoctorStationMedicationAdviceGroupsResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    private void handleCreateLine(
        SaveDoctorStationAdviceDraftBatchRequest request,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId,
        List<Long> newMedIds
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (isMedicationProductTable(pt)) {
            Long newMedicationRequestId = medicationRequestDomainService.createMedicationOrderDraft(
                emergencyDoctorStationAdviceMapper.toCreateMedicationOrderDraftCommand(request, line, operatorId, orgId));
            if (line.getMedicationPayload() != null && line.getMedicationPayload().getGroupId() != null) {
                medicationRequestDomainService.adjustMedicationOrderGroup(
                    emergencyDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                        List.of(newMedicationRequestId), line.getMedicationPayload().getGroupId(), operatorId));
            }
            createChargeIfPossible(request, newMedicationRequestId, TableNameConstants.MED_MEDICATION_REQUEST,
                TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                accountId, orgId, operatorId);
            newMedIds.add(newMedicationRequestId);
        } else if (isDeviceProductTable(pt)) {
            Long newDeviceRequestId = deviceRequestDomainService.createDeviceOrderDraft(
                emergencyDoctorStationAdviceMapper.toCreateDeviceOrderDraftCommand(request, line, operatorId));
            createChargeIfPossible(request, newDeviceRequestId, TableNameConstants.WKF_DEVICE_REQUEST,
                TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId(),
                line.getDevicePayload().getQuantity(), line.getDevicePayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId()),
                accountId, orgId, operatorId);
        } else if (isActivityProductTable(pt)) {
            ProductDefinitionDisplayDto activityProduct = loadSingleProduct(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            Long newServiceRequestId = serviceRequestDomainService.createServiceRequestDraft(
                emergencyDoctorStationAdviceMapper.toCreateServiceRequestDraftCommand(request, line, activityProduct, operatorId));
            createChargeIfPossible(request, newServiceRequestId, TableNameConstants.WKF_SERVICE_REQUEST,
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                ServiceAdviceDraftCommandSupport.requireQuantity(
                    line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                ServiceAdviceDraftCommandSupport.requireUnitCode(
                    line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), activityProduct),
                activityProduct, accountId, orgId, operatorId);
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    private void handleUpdateLine(
        SaveDoctorStationAdviceDraftBatchRequest request,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (isMedicationProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                emergencyDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            medicationRequestDomainService.changeMedicationOrderDraft(
                emergencyDoctorStationAdviceMapper.toChangeMedicationOrderDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.MED_MEDICATION_REQUEST,
                TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                accountId, orgId, operatorId);
        } else if (isDeviceProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                emergencyDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, line.getRequestId(), operatorId));
            deviceRequestDomainService.changeDeviceOrderDraft(
                emergencyDoctorStationAdviceMapper.toChangeDeviceOrderDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.WKF_DEVICE_REQUEST,
                TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId(),
                line.getDevicePayload().getQuantity(), line.getDevicePayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId()),
                accountId, orgId, operatorId);
        } else if (isActivityProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                emergencyDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
            ProductDefinitionDisplayDto activityProduct = loadSingleProduct(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            serviceRequestDomainService.modifyServiceRequestDraft(
                emergencyDoctorStationAdviceMapper.toModifyServiceRequestDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.WKF_SERVICE_REQUEST,
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                ServiceAdviceDraftCommandSupport.requireQuantity(
                    line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                ServiceAdviceDraftCommandSupport.requireUnitCode(
                    line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), activityProduct),
                activityProduct, accountId, orgId, operatorId);
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    private void handleDeleteLine(SaveDoctorStationAdviceDraftBatchRequest request, DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "DELETE 操作 requestId 不能为空");
        }
        if (isMedicationProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                emergencyDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            medicationRequestDomainService.removeMedicationOrderDraft(
                emergencyDoctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (isDeviceProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                emergencyDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, line.getRequestId(), operatorId));
            deviceRequestDomainService.removeDeviceOrderDraft(
                emergencyDoctorStationAdviceMapper.toRemoveDeviceOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (isActivityProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                emergencyDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
            serviceRequestDomainService.removeServiceRequestDraft(
                emergencyDoctorStationAdviceMapper.toRemoveServiceRequestDraftCommand(line.getRequestId(), operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    private void createChargeIfPossible(
        SaveDoctorStationAdviceDraftBatchRequest request,
        Long requestId,
        String requestTable,
        String definitionTable,
        Long definitionId,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow,
        Long accountId,
        Long orgId,
        Long operatorId
    ) {
        if (definitionId == null) {
            return;
        }
        ChargeItemDefinition priceLine = firstPriceLine(definitionTable, definitionId);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "未配置符合开立规则的收费定价（诊疗须零售价 SALE，药品/耗材须批号售价 LOT_NUMBER_PRICE）。请先维护收费项定价后再保存医嘱。");
        }
        chargeItemDomainService.createDraftChargeItemFromOrder(
            emergencyDoctorStationAdviceMapper.toCreateChargeItemDraftFromOrder(
                request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                requestId, requestTable, definitionTable, definitionId, priceLine, quantity, quantityUnit, productRow));
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
