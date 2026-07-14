/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import com.openhis.chs.domain.share.enums.PsnCertType;
import com.openhis.chs.domain.share.enums.HiFeesetlType;
import com.openhis.chs.domain.share.enums.MdtrtSetlType;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3505Command;
import com.openhis.chs.domain.inventory.command.InvUpload3506Command;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
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
import com.openhis.chs.bridge.inventory.assembler.InventorySaleUploadReferenceResolver;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.support.InventoryTracePolicySupport;
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.inventory.assembler.ChsTraceCodeExtractAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.openhis.chs.domain.inventory.command.UploadAfterDispenseCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterReturnCommand;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HIS 主数据组装进销存销售/销售退货上报请求（发药 dispense → CHS 3505/3506）。
 *
 * <p>加载来源：{@link com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense}、
 * {@link com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest}、
 * 就诊、医保结算回执（{@code yb_settlement_record}）、
 * {@link com.openhis.health.domain.administration.chargeitem.entity.ChargeItem} 单价、
 * {@link com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem} 批号效期、
 * 追溯码流水（{@code trace_number_record}）及患者证件信息。</p>
 *
 * <p>契约未显式传入时，从就诊支付单（{@code payment_reconciliation}）回落解析。</p>
 */
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
    private final TraceNumberQueryService traceNumberQueryService;
    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final ChsInsuranceContractReaderImpl contractReader;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChsInventoryProperties inventoryProperties;
    private final InventorySaleUploadReferenceResolver saleUploadReferenceResolver;
    private final PatientQueryService patientQueryService;

    /**
     * 组装 3505 销售上报命令（发药出库）。
     *
     * @param command 就诊 ID 及发药行 dispenseIds
     * @return 含契约、结算关联及逐行销售明细的上报命令
     */
    public InvUpload3505Command build3505Request(UploadAfterDispenseCommand command) {
        Contract contract = resolveContract(command.getContractBusNo(), command.getContractId(), command.getEncounterId(), command.getDispenseIds());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(command.getEncounterId());
        YbSettlementRecord settlement = resolveSettlement(command.getEncounterId(), contract);
        Map<Long, ChargeItem> chargeByDispenseId = mapChargeItemsByDispenseId(command.getEncounterId(), command.getDispenseIds());

        InvUpload3505Command request = new InvUpload3505Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
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

    /**
     * 组装 3506 销售退货上报命令（退药入库）。
     *
     * @param command 就诊 ID 及退药发药行 dispenseIds
     * @return 含原销售流水关联及逐行退货明细的上报命令
     */
    public InvUpload3506Command build3506Request(UploadAfterReturnCommand command) {
        Contract contract = resolveContract(command.getContractBusNo(), command.getContractId(), command.getEncounterId(), command.getDispenseIds());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(command.getEncounterId());
        YbSettlementRecord settlement = resolveSettlement(command.getEncounterId(), contract);
        Map<Long, ChargeItem> chargeByDispenseId = mapChargeItemsByDispenseId(command.getEncounterId(), command.getDispenseIds());

        InvUpload3506Command request = new InvUpload3506Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contract));
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

    /**
     * 将发药行组装为销售或退货上报明细。
     *
     * @param dispense    发药行
     * @param encounter   就诊信息
     * @param contract    契约
     * @param settlement  医保结算记录
     * @param chargeItem  收费项
     * @param returnFlow  是否为退药流程
     * @return 3505/3506 上报行命令
     */
    private InvUploadLineCommand buildLine(
        MedicationDispense dispense,
        EncounterDetailQueryModel encounter,
        Contract contract,
        YbSettlementRecord settlement,
        ChargeItem chargeItem,
        boolean returnFlow
    ) {
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(dispense.getMedicationId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存 " + dispense.getMedicationId()));
        MedicationRequest medicationRequest = medicationRequestQueryService.findById(dispense.getMedReqId());

        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setSourceLineId(dispense.getId());
        line.setMedListCodg(trimToNull(medication.getYbNo()));
        line.setFixmedinsHilistId(medication.getBusNo());
        line.setFixmedinsHilistName(medication.getName());
        line.setFixmedinsBchno(InventoryUploadBatchNoAssembler.buildFixmedinsBchno(medication.getBusNo(), dispense.getLotNumber()));
        line.setManuLotnum(dispense.getLotNumber());
        line.setTrdnFlag(InventoryTracePolicySupport.resolveTrdnFlag(
            medication.getDefEncounterUnitCode(), dispense.getUnitCode()));
        line.setTracePolicy(InventoryTracePolicySupport.resolve(
            medication.getYbNo(),
            medication.getCategoryCode(),
            medication.getDefEncounterUnitCode(),
            dispense.getUnitCode(),
            inventoryProperties));
        if (returnFlow) {
            line.setDrugTracCodgs(loadReturnTraceCodes(dispense));
            line.setMedinsProdSelNo(saleUploadReferenceResolver.resolveMedinsProdSelNo(
                dispense.getId(), dispense.getBusNo()));
            line.setOriginalFixmedinsBchno(line.getFixmedinsBchno());
            line.setSelRetnCnt(InventoryUploadQuantityAssembler.resolveSelRetnCnt(
                medication, dispense, line.getTrdnFlag()));
            if (dispense.getStatusChangedTime() != null) {
                line.setSelRetnTime(dispense.getStatusChangedTime().format(DATE_TIME_FMT));
            }
        } else {
            line.setDrugTracCodgs(loadOutboundTraceCodes(dispense));
            line.setSelRetnCnt(InventoryUploadQuantityAssembler.resolveSelRetnCnt(
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
        line.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(medication.getRxFlag()));
        fillInventoryDates(line, dispense);
        fillSettlementFields(line, encounter, contract, settlement);
        fillPatientFields(line, encounter, settlement);
        return line;
    }

    /**
     * 从库存项填充生产日期与有效期。
     *
     * @param line     上报行命令
     * @param dispense 发药行
     */
    private void fillInventoryDates(InvUploadLineCommand line, MedicationDispense dispense) {
        if (isEmpty(dispense.getLotNumber())) {
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

    /**
     * 填充就诊结算类型与医保流水号。
     *
     * @param line       上报行命令
     * @param encounter  就诊信息
     * @param contract   契约
     * @param settlement 医保结算记录
     */
    private void fillSettlementFields(
        InvUploadLineCommand line,
        EncounterDetailQueryModel encounter,
        Contract contract,
        YbSettlementRecord settlement
    ) {
        boolean insurance = isInsuranceContract(contract);
        if (insurance) {
            line.setMdtrtSetlType(MdtrtSetlType.INSURANCE.getCode());
            line.setHiFeesetlType(HiFeesetlType.LOCAL.getCode());
            if (settlement != null) {
                line.setSetlId(settlement.getSetlId());
                line.setMdtrtSn(settlement.getMdtrtId());
            } else if (encounter != null) {
                line.setMdtrtSn(encounter.getBusNo());
            }
        } else {
            line.setMdtrtSetlType(MdtrtSetlType.SELF_PAY.getCode());
            line.setHiFeesetlType(HiFeesetlType.SELF.getCode());
            if (encounter != null) {
                line.setMdtrtSn(encounter.getBusNo());
            }
        }
    }

    /**
     * 填充患者人员编号、姓名与证件信息。
     *
     * @param line       上报行命令
     * @param encounter  就诊信息
     * @param settlement 医保结算记录
     */
    private void fillPatientFields(
        InvUploadLineCommand line,
        EncounterDetailQueryModel encounter,
        YbSettlementRecord settlement
    ) {
        if (settlement != null && isNotEmpty(settlement.getPsnNo())) {
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
        if (isNotEmpty(patient.getName())) {
            line.setPsnName(patient.getName().trim());
        }
        if (isNotEmpty(patient.getIdCard())) {
            line.setCertno(patient.getIdCard().trim());
            if (isEmpty(line.getPsnCertType())) {
                line.setPsnCertType(PsnCertType.ID_CARD.getCode());
            }
        }
    }

    /**
     * 加载发药出库追溯码。
     *
     * @param dispense 发药行
     * @return 追溯码列表
     */
    private List<String> loadOutboundTraceCodes(MedicationDispense dispense) {
        if (isEmpty(dispense.getLotNumber())) {
            return ChsTraceCodeExtractAssembler.splitTraceNoCommaString(dispense.getTraceNo());
        }
        List<TraceNumberRecordQueryModel> flowRecords = traceNumberQueryService.listFlowRecordsByInventoryItemKey(
            dispense.getLocationId(),
            TableNameConstants.MED_MEDICATION_DEFINITION,
            dispense.getMedicationId(),
            dispense.getLotNumber());
        return ChsTraceCodeExtractAssembler.extractOutboundCodes(
            dispense.getTraceNo(),
            flowRecords,
            TraceDirection.OUT.getCode(),
            dispense.getBusNo());
    }

    /**
     * 加载退药入库追溯码。
     *
     * @param dispense 发药行
     * @return 追溯码列表
     */
    private List<String> loadReturnTraceCodes(MedicationDispense dispense) {
        if (isEmpty(dispense.getLotNumber())) {
            return ChsTraceCodeExtractAssembler.splitTraceNoCommaString(dispense.getTraceNo());
        }
        List<TraceNumberRecordQueryModel> flowRecords = traceNumberQueryService.listFlowRecordsByInventoryItemKey(
            dispense.getLocationId(),
            TableNameConstants.MED_MEDICATION_DEFINITION,
            dispense.getMedicationId(),
            dispense.getLotNumber());
        return ChsTraceCodeExtractAssembler.extractReturnInCodes(
            dispense.getTraceNo(),
            flowRecords,
            TraceDirection.IN.getCode(),
            TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode(),
            dispense.getBusNo());
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

    /**
     * 按从业者 ID 解析药师执业证书编号。
     *
     * @param practitionerId 从业者 ID
     * @return 执业证书编号，不存在时返回 null
     */
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

    /**
     * 解析上报契约，未显式指定时从就诊支付单回落。
     *
     * @param contractBusNo 契约业务编号
     * @param contractId    契约 ID
     * @param encounterId   就诊 ID
     * @param dispenseIds   发药行 ID 列表
     * @return 契约实体
     */
    private Contract resolveContract(String contractBusNo, Long contractId, Long encounterId, List<Long> dispenseIds) {
        if (isNotEmpty(contractBusNo) || contractId != null) {
            return contractReader.resolveOrDefault(contractBusNo, contractId);
        }
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(encounterId);
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment != null && isNotEmpty(payment.getContractNo())) {
                return contractReader.resolveOrDefault(payment.getContractNo(), null);
            }
        }
        return contractReader.resolveOrDefault(null, null);
    }

    /**
     * 按发药行 ID 映射收费项。
     *
     * @param encounterId 就诊 ID
     * @param dispenseIds 发药行 ID 列表
     * @return 发药行 ID 到收费项的映射
     */
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

    /**
     * 解析就诊最新医保结算记录。
     *
     * @param encounterId 就诊 ID
     * @param contract    契约
     * @return 医保结算记录，非医保契约时返回 null
     */
    private YbSettlementRecord resolveSettlement(Long encounterId, Contract contract) {
        if (!isInsuranceContract(contract)) {
            return null;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbSettlementRecord> settlement = settlementRecordQueryService
            .findOptionalLatestByEncounterAndClass(encounterId, ChsSettleEncounterClass.AMB, tenantId);
        return settlement.orElse(null);
    }

    /**
     * 判断契约是否为医保契约。
     *
     * @param contract 契约
     * @return 医保契约时返回 true
     */
    private boolean isInsuranceContract(Contract contract) {
        if (contract == null) {
            return false;
        }
        return InsuranceContractCategorySupport.isInsuranceContract(
            contract.getYbFlag(), contract.getCategoryEnum());
    }
}
