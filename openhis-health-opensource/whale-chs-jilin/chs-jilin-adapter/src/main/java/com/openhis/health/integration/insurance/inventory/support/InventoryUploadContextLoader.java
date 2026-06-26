/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSale3505Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSaleReturn3506Request;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.chs.domain.settlement.ChsSettleEncounterClass;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.entity.TraceNumberRecord;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterDispenseCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterReturnCommand;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 从 HIS 主数据组装进销存上报请求。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryUploadContextLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final TraceNumberRecordQueryService traceNumberRecordQueryService;
    private final YbSettlementRecordRepository settlementRecordRepository;
    private final InsuranceContractResolver contractResolver;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InventoryTracePolicyResolver tracePolicyResolver;
    private final InventorySaleUploadReferenceLoader saleUploadReferenceLoader;
    private final PatientQueryService patientQueryService;

    public UploadSale3505Request build3505Request(UploadAfterDispenseCommand command) {
        Contract contract = resolveContract(command.getContractBusNo(), command.getContractId(), command.getEncounterId(), command.getDispenseIds());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(command.getEncounterId());
        YbSettlementRecord settlement = resolveSettlement(command.getEncounterId(), contract);
        Map<Long, ChargeItem> chargeByDispenseId = mapChargeItemsByDispenseId(command.getEncounterId(), command.getDispenseIds());

        UploadSale3505Request request = new UploadSale3505Request();
        request.setContractBusNo(contractResolver.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setEncounterId(command.getEncounterId());
        request.setSourceId(command.getEncounterId());

        for (Long dispenseId : command.getDispenseIds()) {
            MedicationDispense dispense = medicationDispenseQueryService.mustGetMedicationDispense(dispenseId);
            InvUploadLineCommand line = buildLine(dispense, encounter, contract, settlement, chargeByDispenseId.get(dispenseId), false);
            request.getLines().add(line);
        }
        return request;
    }

    /** 发药伴生 3502（inv_chg_type=107）；仅在 {@code sale-also-inv-change3502=true} 时由 Port 调用。 */
    public UploadInvChange3502Request build3502AfterDispenseRequest(UploadAfterDispenseCommand command) {
        UploadSale3505Request saleRequest = build3505Request(command);
        UploadInvChange3502Request request = new UploadInvChange3502Request();
        request.setContractBusNo(saleRequest.getContractBusNo());
        request.setContractId(saleRequest.getContractId());
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_DISPENSE);
        request.setSourceId(command.getEncounterId());
        if (saleRequest.getLines() == null) {
            return request;
        }
        for (InvUploadLineCommand saleLine : saleRequest.getLines()) {
            InvUploadLineCommand invLine = toInvChangeLineFromSaleLine(saleLine);
            if (invLine != null) {
                request.getLines().add(invLine);
            }
        }
        return request;
    }

    private static InvUploadLineCommand toInvChangeLineFromSaleLine(InvUploadLineCommand saleLine) {
        if (saleLine == null || saleLine.getSelRetnCnt() == null
            || saleLine.getSelRetnCnt().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setSourceLineId(saleLine.getSourceLineId());
        line.setMedListCodg(saleLine.getMedListCodg());
        line.setFixmedinsHilistId(saleLine.getFixmedinsHilistId());
        line.setFixmedinsHilistName(saleLine.getFixmedinsHilistName());
        line.setFixmedinsBchno(saleLine.getFixmedinsBchno());
        line.setManuLotnum(saleLine.getManuLotnum());
        line.setManuDate(saleLine.getManuDate());
        line.setExpyEnd(saleLine.getExpyEnd());
        line.setCnt(saleLine.getSelRetnCnt());
        line.setPric(saleLine.getFinlTrnsPric());
        line.setInvChgType(ChsInventoryConstants.INV_CHG_TYPE_OTHER_OUT);
        line.setInvChgTime(saleLine.getSelRetnTime());
        line.setInvChgOpterName(saleLine.getSelRetnOpterName());
        line.setTrdnFlag(saleLine.getTrdnFlag());
        line.setTracePolicy(saleLine.getTracePolicy());
        line.setRxFlag(saleLine.getRxFlag());
        line.setDrugTracCodgs(saleLine.getDrugTracCodgs());
        return line;
    }

    public UploadSaleReturn3506Request build3506Request(UploadAfterReturnCommand command) {
        Contract contract = resolveContract(command.getContractBusNo(), command.getContractId(), command.getEncounterId(), command.getDispenseIds());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(command.getEncounterId());
        YbSettlementRecord settlement = resolveSettlement(command.getEncounterId(), contract);
        Map<Long, ChargeItem> chargeByDispenseId = mapChargeItemsByDispenseId(command.getEncounterId(), command.getDispenseIds());

        UploadSaleReturn3506Request request = new UploadSaleReturn3506Request();
        request.setContractBusNo(contractResolver.requireContractBusNo(contract));
        request.setContractId(contract.getId());
        request.setEncounterId(command.getEncounterId());
        request.setSourceId(command.getEncounterId());

        for (Long dispenseId : command.getDispenseIds()) {
            MedicationDispense dispense = medicationDispenseQueryService.mustGetMedicationDispense(dispenseId);
            InvUploadLineCommand line = buildLine(dispense, encounter, contract, settlement, chargeByDispenseId.get(dispenseId), true);
            request.getLines().add(line);
        }
        return request;
    }

    private InvUploadLineCommand buildLine(
        MedicationDispense dispense,
        EncounterDetailQueryModel encounter,
        Contract contract,
        YbSettlementRecord settlement,
        ChargeItem chargeItem,
        boolean returnFlow
    ) {
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(dispense.getMedicationId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存在: " + dispense.getMedicationId()));
        MedicationRequest medicationRequest = medicationRequestQueryService.findById(dispense.getMedReqId());

        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setSourceLineId(dispense.getId());
        line.setMedListCodg(trimToNull(medication.getYbNo()));
        line.setFixmedinsHilistId(medication.getBusNo());
        line.setFixmedinsHilistName(medication.getName());
        line.setFixmedinsBchno(InventoryUploadBatchNoSupport.buildFixmedinsBchno(medication.getBusNo(), dispense.getLotNumber()));
        line.setManuLotnum(dispense.getLotNumber());
        line.setTrdnFlag(tracePolicyResolver.resolveTrdnFlag(medication, dispense));
        line.setTracePolicy(tracePolicyResolver.resolve(medication, dispense));
        if (returnFlow) {
            line.setDrugTracCodgs(loadReturnTraceCodes(dispense));
            line.setMedinsProdSelNo(saleUploadReferenceLoader.resolveMedinsProdSelNo(
                dispense.getId(), dispense.getBusNo()));
            line.setOriginalFixmedinsBchno(line.getFixmedinsBchno());
            line.setSelRetnCnt(InventoryUploadQuantitySupport.resolveSelRetnCnt(
                medication, dispense, line.getTrdnFlag()));
            if (dispense.getStatusChangedTime() != null) {
                line.setSelRetnTime(dispense.getStatusChangedTime().format(DATE_TIME_FMT));
            }
        } else {
            line.setDrugTracCodgs(loadOutboundTraceCodes(dispense));
            line.setSelRetnCnt(InventoryUploadQuantitySupport.resolveSelRetnCnt(
                medication, dispense, line.getTrdnFlag()));
            if (dispense.getDispenseTime() != null) {
                line.setSelRetnTime(dispense.getDispenseTime().format(DATE_TIME_FMT));
            }
        }
        line.setSelRetnOpterName(resolvePractitionerName(dispense.getPractitionerId()));
        Long requesterId;
        if (medicationRequest == null) {
            requesterId = null;
        } else {
            requesterId = medicationRequest.getRequesterId();
        }
        line.setPrscDrName(resolvePractitionerName(requesterId));
        line.setPharName(resolvePractitionerName(dispense.getPractitionerId()));
        line.setPharPracCertNo(resolvePharPracCertNo(dispense.getPractitionerId()));
        line.setRtalDocno(dispense.getBusNo());
        if (chargeItem != null && chargeItem.getUnitPrice() != null) {
            line.setFinlTrnsPric(chargeItem.getUnitPrice());
        }
        line.setRxFlag(InventoryPlatformFieldSupport.toRxFlagCode(medication.getRxFlag()));
        fillInventoryDates(line, dispense);
        fillSettlementFields(line, encounter, contract, settlement);
        fillPatientFields(line, encounter, settlement);
        return line;
    }

    private void fillInventoryDates(InvUploadLineCommand line, MedicationDispense dispense) {
        if (StringUtils.isBlank(dispense.getLotNumber())) {
            return;
        }
        List<InventoryItem> items = inventoryItemQueryService.listInventoryItemEntitiesByLotLocationItem(
            dispense.getLotNumber(),
            dispense.getLocationId(),
            TableNameConstants.MED_MEDICATION_DEFINITION,
            dispense.getMedicationId());
        if (items.isEmpty()) {
            return;
        }
        InventoryItem item = items.get(0);
        if (item.getProductionDate() != null) {
            line.setManuDate(item.getProductionDate().toLocalDate().format(DATE_FMT));
        }
        if (item.getExpirationDate() != null) {
            line.setExpyEnd(item.getExpirationDate().toLocalDate().format(DATE_FMT));
        }
    }

    private void fillSettlementFields(
        InvUploadLineCommand line,
        EncounterDetailQueryModel encounter,
        Contract contract,
        YbSettlementRecord settlement
    ) {
        boolean insurance = isInsuranceContract(contract);
        if (insurance) {
            line.setMdtrtSetlType(ChsInventoryConstants.MDTRT_SETL_TYPE_INSURANCE);
            line.setHiFeesetlType(ChsInventoryConstants.HI_FEESETL_TYPE_LOCAL);
            if (settlement != null) {
                line.setSetlId(settlement.getSetlId());
                line.setMdtrtSn(settlement.getMdtrtId());
            } else if (encounter != null) {
                line.setMdtrtSn(encounter.getBusNo());
            }
        } else {
            line.setMdtrtSetlType(ChsInventoryConstants.MDTRT_SETL_TYPE_SELF_PAY);
            line.setHiFeesetlType(ChsInventoryConstants.HI_FEESETL_TYPE_SELF);
            if (encounter != null) {
                line.setMdtrtSn(encounter.getBusNo());
            }
        }
    }

    private void fillPatientFields(
        InvUploadLineCommand line,
        EncounterDetailQueryModel encounter,
        YbSettlementRecord settlement
    ) {
        if (settlement != null && StringUtils.isNotBlank(settlement.getPsnNo())) {
            line.setPsnNo(settlement.getPsnNo().trim());
        }
        if (encounter == null || encounter.getPatientId() == null) {
            return;
        }
        Optional<Patient> patientOpt = patientQueryService.findOptionalPatientEntityById(encounter.getPatientId());
        if (patientOpt.isEmpty()) {
            return;
        }
        Patient patient = patientOpt.get();
        if (StringUtils.isNotBlank(patient.getName())) {
            line.setPsnName(patient.getName().trim());
        }
        if (StringUtils.isNotBlank(patient.getIdCard())) {
            line.setCertno(patient.getIdCard().trim());
            if (StringUtils.isBlank(line.getPsnCertType())) {
                line.setPsnCertType(ChsInventoryConstants.PSN_CERT_TYPE_ID_CARD);
            }
        }
    }

    private List<String> loadOutboundTraceCodes(MedicationDispense dispense) {
        List<String> codes = new ArrayList<>();
        if (StringUtils.isBlank(dispense.getLotNumber())) {
            return codes;
        }
        List<TraceNumberRecord> rows = traceNumberRecordQueryService.listEntitiesByItemTableAndItemIdAndLotNumberAndLocationId(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            dispense.getMedicationId(),
            dispense.getLotNumber(),
            dispense.getLocationId());
        for (TraceNumberRecord row : rows) {
            if (row == null || StringUtils.isBlank(row.getTraceNo())) {
                continue;
            }
            if (!TraceDirection.OUT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (StringUtils.isNotBlank(dispense.getBusNo()) && !dispense.getBusNo().equals(row.getBusNo())) {
                continue;
            }
            codes.add(row.getTraceNo().trim());
        }
        return codes;
    }

    private List<String> loadReturnTraceCodes(MedicationDispense dispense) {
        List<String> codes = new ArrayList<>();
        if (StringUtils.isBlank(dispense.getLotNumber())) {
            return codes;
        }
        List<TraceNumberRecord> rows = traceNumberRecordQueryService.listEntitiesByItemTableAndItemIdAndLotNumberAndLocationId(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            dispense.getMedicationId(),
            dispense.getLotNumber(),
            dispense.getLocationId());
        for (TraceNumberRecord row : rows) {
            if (row == null || StringUtils.isBlank(row.getTraceNo())) {
                continue;
            }
            if (!TraceDirection.IN.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (!TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode().equals(row.getOperationType())) {
                continue;
            }
            if (StringUtils.isNotBlank(dispense.getBusNo()) && !dispense.getBusNo().equals(row.getBusNo())) {
                continue;
            }
            codes.add(row.getTraceNo().trim());
        }
        return codes;
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

    private String resolvePharPracCertNo(Long practitionerId) {
        if (practitionerId == null) {
            return null;
        }
        Practitioner practitioner = practitionerQueryService.findById(practitionerId);
        if (practitioner == null) {
            return null;
        }
        return practitioner.getPharPracCertNo();
    }

    private Contract resolveContract(String contractBusNo, Long contractId, Long encounterId, List<Long> dispenseIds) {
        if (StringUtils.isNotBlank(contractBusNo) || contractId != null) {
            return contractResolver.resolveOrDefault(contractBusNo, contractId);
        }
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(encounterId);
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment != null && StringUtils.isNotBlank(payment.getContractNo())) {
                return contractResolver.resolveOrDefault(payment.getContractNo(), null);
            }
        }
        return contractResolver.resolveOrDefault(null, null);
    }

    private Map<Long, ChargeItem> mapChargeItemsByDispenseId(Long encounterId, List<Long> dispenseIds) {
        Map<Long, ChargeItem> result = new HashMap<>();
        if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
            return result;
        }
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsForMedicationDispensePricing(encounterId, dispenseIds);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getDispenseId() == null) {
                continue;
            }
            result.putIfAbsent(chargeItem.getDispenseId(), chargeItem);
        }
        return result;
    }

    private YbSettlementRecord resolveSettlement(Long encounterId, Contract contract) {
        if (!isInsuranceContract(contract)) {
            return null;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbSettlementRecord> settlement = settlementRecordRepository
            .findFirstByEncounterIdAndEncounterClassAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                encounterId, ChsSettleEncounterClass.AMB, tenantId);
        return settlement.orElse(null);
    }

    private boolean isInsuranceContract(Contract contract) {
        if (contract == null) {
            return false;
        }
        return InsuranceContractCategorySupport.isInsuranceContract(
            contract.getYbFlag(), contract.getCategoryEnum());
    }
}