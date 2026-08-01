/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import com.openhis.chs.domain.share.enums.MdtrtSetlType;
import com.openhis.chs.domain.share.enums.RxCircFlag;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3505Command;
import com.openhis.chs.domain.inventory.command.InvUpload3506Command;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
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
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.chs.bridge.inventory.assembler.InventorySaleUploadReferenceResolver;
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.inventory.assembler.ChsTraceCodeExtractAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryPlatformFieldAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadHiFeesetlAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadPsnCertTypeAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadRtalDocnoAssembler;
import com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport;
import com.openhis.health.domain.administration.patient.entity.PatientIdentifier;
import com.openhis.health.domain.administration.patient.repository.PatientIdentifierRepository;
import com.openhis.chs.domain.inventory.command.UploadAfterDispenseCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterReturnCommand;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * <p>契约拆分（对齐 openhis 发药进销存）：</p>
 * <ul>
 *   <li><b>出站契约</b>：须为省/市等医保凭证（网关配置）；支付单为自费时回落
 *       {@code default-contract-bus-no} / 租户首条非自费医保契约</li>
 *   <li><b>结算性质</b>：跟支付单费用性质写 {@code mdtrt_setl_type}/{@code hi_feesetl_type}/
 *       {@code mdtrt_sn}（自费=2、院内就诊流水）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryUploadContextLoader {

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
    private final InventorySaleUploadReferenceResolver saleUploadReferenceResolver;
    private final PatientQueryService patientQueryService;
    private final PatientIdentifierRepository patientIdentifierRepository;
    private final InventoryUploadTraceLineSupport uploadTraceLineSupport;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final YbPersonCacheDomainService personCacheDomainService;

    /**
     * 组装 3505 销售上报命令（发药出库）。
     *
     * @param command 就诊 ID 及发药行 dispenseIds
     * @return 含契约、结算关联及逐行销售明细的上报命令
     */
    public InvUpload3505Command build3505Request(UploadAfterDispenseCommand command) {
        SaleContractContext contracts = resolveSaleContracts(
            command.getContractBusNo(), command.getContractId(), command.getEncounterId(), command.getDispenseIds());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(command.getEncounterId());
        YbSettlementRecord settlement = resolveSettlement(command.getEncounterId(), contracts.settlementNature());
        Map<Long, ChargeItem> chargeByDispenseId = mapChargeItemsByDispenseId(command.getEncounterId(), command.getDispenseIds());

        InvUpload3505Command request = new InvUpload3505Command();
        // 出站/审计挂医保凭证（自费支付不会写 bus_no=1）
        request.setContractBusNo(contractReader.requireContractBusNo(contracts.gateway()));
        request.setContractId(contracts.gateway().getId());
        request.setEncounterId(command.getEncounterId());
        request.setSourceId(command.getEncounterId());

        for (Long dispenseId : command.getDispenseIds()) {
            MedicationDispense dispense = medicationDispenseQueryService.mustGetMedicationDispense(dispenseId);
            InvUploadLineCommand line = buildLine(
                dispense,
                encounter,
                contracts.gateway(),
                contracts.settlementNature(),
                settlement,
                chargeByDispenseId.get(dispenseId),
                false,
                null,
                null,
                null);
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
        SaleContractContext contracts = resolveSaleContracts(
            command.getContractBusNo(), command.getContractId(), command.getEncounterId(), command.getDispenseIds());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(command.getEncounterId());
        YbSettlementRecord settlement = resolveSettlement(command.getEncounterId(), contracts.settlementNature());
        Map<Long, ChargeItem> chargeByDispenseId = mapChargeItemsByDispenseId(command.getEncounterId(), command.getDispenseIds());

        InvUpload3506Command request = new InvUpload3506Command();
        request.setContractBusNo(contractReader.requireContractBusNo(contracts.gateway()));
        request.setContractId(contracts.gateway().getId());
        request.setEncounterId(command.getEncounterId());
        request.setSourceId(command.getEncounterId());
        // 异步 3511 须沿用调用线程操作人（@Async 无登录态）
        request.setOperatorId(command.getOperatorId());
        request.setOperatorName(command.getOperatorName());

        // 同批退药：一药多发时避免多行都绑到 max(id) 原发药
        Set<Long> claimedOriginDispenseIds = new HashSet<>();
        for (Long dispenseId : command.getDispenseIds()) {
            MedicationDispense dispense = medicationDispenseQueryService.mustGetMedicationDispense(dispenseId);
            InvUploadLineCommand line = buildLine(
                dispense,
                encounter,
                contracts.gateway(),
                contracts.settlementNature(),
                settlement,
                chargeByDispenseId.get(dispenseId),
                true,
                claimedOriginDispenseIds,
                command.getOperatorId(),
                command.getOperatorName());
            request.getLines().add(line);
        }
        return request;
    }

    /**
     * 将发药行组装为销售或退货上报明细（供同包出院汇总 Loader 复用后覆盖净量/追溯码）。
     *
     * @param dispense                  发药行
     * @param encounter                 就诊信息
     * @param gatewayContract           出站医保契约（3511/审计 contract_bus_no）
     * @param settlementNatureContract  结算性质契约（支付费用性质；可自费）
     * @param settlement                医保结算记录
     * @param chargeItem                收费项
     * @param returnFlow                是否为退药流程
     * @param claimedOriginDispenseIds  本批已占用的原发药行（退药多行防串号）；发药传 null
     * @param operatorId                异步操作人；退药查 3511 用
     * @param operatorName              操作人姓名
     * @return 3505/3506 上报行命令
     */
    InvUploadLineCommand buildLine(
        MedicationDispense dispense,
        EncounterDetailQueryModel encounter,
        Contract gatewayContract,
        Contract settlementNatureContract,
        YbSettlementRecord settlement,
        ChargeItem chargeItem,
        boolean returnFlow,
        Set<Long> claimedOriginDispenseIds,
        Long operatorId,
        String operatorName
    ) {
        MedicationDefinition medication = medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(dispense.getMedicationId())
            .orElseThrow(() -> new IllegalStateException("药品定义不存 " + dispense.getMedicationId()));
        MedicationRequest medicationRequest = medicationRequestQueryService.findById(dispense.getMedReqId());

        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setSourceLineId(dispense.getId());
        line.setMedListCodg(trimToNull(medication.getYbNo()));
        line.setFixmedinsHilistId(medication.getBusNo());
        line.setFixmedinsHilistName(medication.getName());
        // fixmedins_bchno：发药/退药业务取发放行 busNo（非批号、非品种目录编码）
        line.setFixmedinsBchno(InventoryUploadBatchNoAssembler.buildFixmedinsBchno(dispense.getBusNo()));
        line.setManuLotnum(dispense.getLotNumber());
        if (returnFlow) {
            line.setDrugTracCodgs(loadReturnTraceCodes(dispense));
            // 暂存退药单号；吉林口径随后会用原 3505 批次号覆盖 fixmedins_bchno
            line.setOriginalFixmedinsBchno(line.getFixmedinsBchno());
            // 退药数量与单位一对，不换算
            line.setSelRetnCnt(InventoryUploadQuantityAssembler.resolveDispenseSelRetnCnt(dispense));
            // 退货时间：退药单 dispense_time（确认退药时写入）
            if (dispense.getDispenseTime() != null) {
                line.setSelRetnTime(dispense.getDispenseTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
            }
        } else {
            line.setDrugTracCodgs(loadOutboundTraceCodes(dispense));
            line.setSelRetnCnt(InventoryUploadQuantityAssembler.resolveDispenseSelRetnCnt(dispense));
            if (dispense.getDispenseTime() != null) {
                line.setSelRetnTime(dispense.getDispenseTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
            }
        }
        // 拆零标志：操作单位 vs 追溯码主档单位
        uploadTraceLineSupport.applyTrdnFlagAndPolicy(medication, dispense.getUnitCode(), line);
        // 药师=发药人/退药确认人；经办人=配药人（退药行无配药人时回落确认人）；同人则 FAILED
        Long dispenserId = dispense.getPractitionerId();
        Long preparerId = dispense.getPreparerId();
        line.setPharName(resolvePractitionerName(dispenserId));
        line.setPharPracCertNo(resolvePharPracCertNo(dispenserId));
        Long opterId = preparerId;
        if (opterId == null) {
            opterId = dispenserId;
        }
        line.setSelRetnOpterName(resolvePractitionerName(opterId));
        if (preparerId != null && dispenserId != null && preparerId.equals(dispenserId)) {
            line.setPreUploadFailureMessage("销售经办人须为配药人，且不得与发药人(药师)为同一人");
        }
        Long requesterId;
        if (medicationRequest == null) {
            requesterId = null;
        } else {
            requesterId = medicationRequest.getRequesterId();
        }
        line.setPrscDrName(resolvePractitionerName(requesterId));
        // 暂无外购业务：外购处方标志固定否
        line.setRxCircFlag(RxCircFlag.NO.getCode());
        if (chargeItem != null && chargeItem.getUnitPrice() != null) {
            line.setFinlTrnsPric(chargeItem.getUnitPrice());
        }
        line.setRxFlag(InventoryPlatformFieldAssembler.toRxFlagCode(medication.getRxFlag()));
        fillInventoryDates(line, dispense);
        // 报文结算类型跟支付费用性质（自费≠出站契约）
        fillSettlementFields(line, encounter, settlementNatureContract, settlement);
        fillPatientFields(line, encounter, settlement);
        applyHiFeesetlType(line, settlementNatureContract, encounter);
        if (returnFlow) {
            // 3506：吉林口径对单/单据号均用原 3505 fixmedins_bchno；mdtrt_sn 与原销售一致
            applyReturnSaleAssociation(
                line, dispense, encounter, gatewayContract, claimedOriginDispenseIds, operatorId, operatorName);
        } else {
            // 3505：rtal_docno = 省平台 feedetl_sn（与 2204/2301 同源），禁止用发放 busNo
            line.setRtalDocno(InventoryUploadRtalDocnoAssembler.resolveFromChargeItem(chargeItem));
        }
        return line;
    }

    /**
     * 3506 关联原 3505（吉林成功口径：单据号=原 MDP；对单=3511 平台销售流水）。
     *
     * <p>原销售定位以退药行 {@code med_req_id} 为起点，门诊/住院分流：
     * 门诊经负向药嘱 {@code refund_medicine_id} 找原药嘱再取正向 COMPLETED 发药与原收费项；
     * 住院同药嘱下查 {@code refund_id} 非空的新账单再反查原收费项（无冲销行时回落同 req 正向发药）。
     * 一药多发时按追溯码/批号/数量与本批占用集一对一，禁止多行都绑 max(id)。
     * {@code fixmedins_bchno}=原 3505 批次号；{@code medins_prod_sel_no}=3511 {@code medinsProdSelNo}；
     * 对人 {@code mdtrt_sn} 与原销售一致。本地幂等靠不同 {@code source_line_id}（退药行）。</p>
     *
     * @param line                     退货上报行（{@code originalFixmedinsBchno} 已暂存退药单号）
     * @param dispense                 退药发放行（REFUND 对冲行）
     * @param encounter                就诊（用 classEnum 区分 AMB/IMP）
     * @param contract                 当前退药契约（无成功 3505 审计时供 3511 出站）
     * @param claimedOriginDispenseIds 本批已占用原发药行
     * @param operatorId               异步操作人
     * @param operatorName             操作人姓名
     */
    private void applyReturnSaleAssociation(
        InvUploadLineCommand line,
        MedicationDispense dispense,
        EncounterDetailQueryModel encounter,
        Contract contract,
        Set<Long> claimedOriginDispenseIds,
        Long operatorId,
        String operatorName
    ) {
        // Pre：按就诊类别解析原销售发放行（一药多发一对一）
        OriginSaleRef originRef = this.resolveOriginSaleForReturn(dispense, encounter, claimedOriginDispenseIds);
        Long originDispenseId = originRef.originDispenseId();
        // 无成功 3505 审计时回落原发药行 busNo，禁止回落到退药单号
        String fallbackSaleBchno = null;
        if (originDispenseId != null) {
            MedicationDispense originDispense = medicationDispenseQueryService.findById(originDispenseId);
            if (originDispense != null) {
                fallbackSaleBchno = InventoryUploadBatchNoAssembler.buildFixmedinsBchno(originDispense.getBusNo());
            }
        }
        String saleBchno = saleUploadReferenceResolver.resolveSaleFixmedinsBchnoForReturn(
            originDispenseId, fallbackSaleBchno);
        // 吉林：fixmedins_bchno = 原销售 MDP；medins_prod_sel_no = 3511 平台数字流水
        line.setFixmedinsBchno(saleBchno);
        // 退药反找原药嘱账单单价（门诊收费挂原发药行）；伴生 3502 平台 pric 必填
        if (line.getFinlTrnsPric() == null && originRef.unitPrice() != null) {
            line.setFinlTrnsPric(originRef.unitPrice());
        }
        String fallbackContractBusNo = null;
        Long fallbackContractId = null;
        if (contract != null) {
            fallbackContractBusNo = contractReader.requireContractBusNo(contract);
            fallbackContractId = contract.getId();
        }
        // 有 MDP 即查 3511（异步带 operatorId；不因本地无成功 3505 而跳过）
        String platformSelNo = null;
        try {
            platformSelNo = saleUploadReferenceResolver.resolveMedinsProdSelNoForReturn(
                originDispenseId,
                saleBchno,
                fallbackContractBusNo,
                fallbackContractId,
                operatorId,
                operatorName);
        } catch (RuntimeException ex) {
            // 3511 平台拒查（如日期窗>30天）时保留平台原文，避免只剩笼统「无法解析」
            String detail = ex.getMessage();
            if (isEmpty(detail)) {
                detail = ex.getClass().getSimpleName();
            }
            line.setPreUploadFailureMessage(
                "无法解析平台商品销售流水号(medins_prod_sel_no)：3511失败 " + detail.trim());
        }
        line.setMedinsProdSelNo(platformSelNo);
        if (isEmpty(platformSelNo) && isEmpty(line.getPreUploadFailureMessage())) {
            line.setPreUploadFailureMessage(
                "无法解析平台商品销售流水号(medins_prod_sel_no)，请先确认原3505已成功且3511可查");
        }
        String saleMdtrtSn = saleUploadReferenceResolver.resolveMdtrtSnFromSale(originDispenseId);
        // 有成功 3505 审计时覆盖，确保与原销售对人一致
        if (isNotEmpty(saleMdtrtSn)) {
            line.setMdtrtSn(saleMdtrtSn);
        }
    }

    /**
     * 按就诊类别解析 3506 原销售锚点（原发药行 + feedetl_sn 回落）。
     *
     * @param refundDispense           退药对冲发放行
     * @param encounter                就诊详情
     * @param claimedOriginDispenseIds 本批已占用原发药行
     * @return 原销售引用；无法定位时字段均为 null
     */
    private OriginSaleRef resolveOriginSaleForReturn(
        MedicationDispense refundDispense,
        EncounterDetailQueryModel encounter,
        Set<Long> claimedOriginDispenseIds
    ) {
        // Gate：退药行与药嘱主键缺失则无法对单
        if (refundDispense == null || refundDispense.getMedReqId() == null) {
            return OriginSaleRef.empty();
        }
        String classEnum = null;
        if (encounter != null) {
            classEnum = encounter.getClassEnum();
        }
        // 住院：新账单 refund_id → 原账单
        if (EncounterClass.IMP.getCode().equals(classEnum)) {
            return this.resolveOriginSaleForInpatientReturn(refundDispense, claimedOriginDispenseIds);
        }
        // 门诊（AMB）及未识别 class：负向药嘱 refund_medicine_id → 原药嘱
        return this.resolveOriginSaleForOutpatientReturn(refundDispense, claimedOriginDispenseIds);
    }

    /**
     * 门诊 3506：退药 {@code med_req_id} → 负向药嘱 {@code refund_medicine_id} → 原正向发药/收费项。
     *
     * <p>门诊退费不新建 ChargeItem，原收费项仍挂原发药行；禁止用退药 dispense_id 直接 map 收费项。</p>
     *
     * @param refundDispense           退药对冲发放行
     * @param claimedOriginDispenseIds 本批已占用原发药行
     * @return 原销售引用
     */
    private OriginSaleRef resolveOriginSaleForOutpatientReturn(
        MedicationDispense refundDispense,
        Set<Long> claimedOriginDispenseIds
    ) {
        MedicationRequest negReq = medicationRequestQueryService.findById(refundDispense.getMedReqId());
        // 负向药嘱缺失或未锚原药嘱则无法对单
        if (negReq == null || negReq.getRefundMedicineId() == null) {
            return OriginSaleRef.empty();
        }
        Long originMedReqId = negReq.getRefundMedicineId();
        Long originDispenseId = this.resolveForwardCompletedDispenseId(
            originMedReqId, refundDispense, claimedOriginDispenseIds);
        ChargeItem originCharge = this.loadChargeByDispenseId(refundDispense.getEncounterId(), originDispenseId);
        return OriginSaleRef.of(
            originDispenseId,
            InventoryUploadRtalDocnoAssembler.resolveFromChargeItem(originCharge),
            this.resolveChargeUnitPrice(originCharge));
    }

    /**
     * 住院 3506：同药嘱下查 {@code refund_id} 非空新账单 → 原收费项；无冲销行时回落同 req 正向发药。
     *
     * <p>住院退药不造负向 Request，退药行 {@code med_req_id} 即原药嘱主键。</p>
     *
     * @param refundDispense           退药对冲发放行
     * @param claimedOriginDispenseIds 本批已占用原发药行
     * @return 原销售引用
     */
    private OriginSaleRef resolveOriginSaleForInpatientReturn(
        MedicationDispense refundDispense,
        Set<Long> claimedOriginDispenseIds
    ) {
        Long medReqId = refundDispense.getMedReqId();
        List<Long> serviceIds = new ArrayList<>();
        serviceIds.add(medReqId);
        // 同就诊 + 药嘱锚点加载收费行，挑 refund_id 非空的冲销/再生行
        List<ChargeItem> anchoredCharges = chargeItemQueryService.listByEncounterAndServiceAnchors(
            refundDispense.getEncounterId(),
            TableNameConstants.MED_MEDICATION_REQUEST,
            serviceIds);
        ChargeItem offsetCharge = this.pickLatestOffsetCharge(anchoredCharges);
        // 有新账单则经 refund_id 反查原账单
        if (offsetCharge != null && offsetCharge.getRefundId() != null) {
            ChargeItem originCharge = this.loadChargeById(offsetCharge.getRefundId());
            Long originDispenseId = null;
            String fallbackFeedetlSn = null;
            BigDecimal unitPrice = null;
            if (originCharge != null) {
                originDispenseId = originCharge.getDispenseId();
                fallbackFeedetlSn = InventoryUploadRtalDocnoAssembler.resolveFromChargeItem(originCharge);
                unitPrice = this.resolveChargeUnitPrice(originCharge);
            }
            // 原账单无 dispense 锚点时，回落同药嘱正向发药行供 3505 审计查询
            if (originDispenseId == null) {
                originDispenseId = this.resolveForwardCompletedDispenseId(
                    medReqId, refundDispense, claimedOriginDispenseIds);
            } else if (claimedOriginDispenseIds != null) {
                // 收费项已锚原发药：仍记占用，避免同批其它行再抢
                claimedOriginDispenseIds.add(originDispenseId);
            }
            // 原账单无单价且已回落发药行时，再按原发药取账单价
            if (unitPrice == null && originDispenseId != null) {
                unitPrice = this.resolveChargeUnitPrice(
                    this.loadChargeByDispenseId(refundDispense.getEncounterId(), originDispenseId));
            }
            return OriginSaleRef.of(originDispenseId, fallbackFeedetlSn, unitPrice);
        }
        // 无冲销新账单（如取消执行仅 void 费用）：回落同 medReq 正向 COMPLETED 发药 + 原收费项
        Long originDispenseId = this.resolveForwardCompletedDispenseId(
            medReqId, refundDispense, claimedOriginDispenseIds);
        ChargeItem originCharge = this.loadChargeByDispenseId(refundDispense.getEncounterId(), originDispenseId);
        return OriginSaleRef.of(
            originDispenseId,
            InventoryUploadRtalDocnoAssembler.resolveFromChargeItem(originCharge),
            this.resolveChargeUnitPrice(originCharge));
    }

    /**
     * 按原药嘱定位正向 COMPLETED/PART_COMPLETED 发药行；一药多发时按退药行拷贝字段一对一。
     *
     * <p>匹配优先级：未占用 → 追溯码全等 → 批号+数量 → 批号 → 数量 → 主键升序最小未占用。
     * 禁止旧逻辑「多条取 max(id)」导致三行退药都绑到同一 MDP。</p>
     *
     * @param originMedReqId           原药品申请主键
     * @param refundDispense           退药对冲行（批号/追溯码/数量已从原发药拷贝）
     * @param claimedOriginDispenseIds 本批已占用原发药行；可空
     * @return 原发药行主键；无则 null
     */
    private Long resolveForwardCompletedDispenseId(
        Long originMedReqId,
        MedicationDispense refundDispense,
        Set<Long> claimedOriginDispenseIds
    ) {
        if (originMedReqId == null) {
            return null;
        }
        List<MedicationDispense> rows = medicationDispenseQueryService.listByMedReqIds(List.of(originMedReqId));
        List<MedicationDispense> candidates = new ArrayList<>();
        for (MedicationDispense row : rows) {
            // 跳过退药对冲行
            if (row == null || MedicationDispenseType.REFUND.equals(row.getDispenseEnum())) {
                continue;
            }
            DispenseStatus status = row.getStatusEnum();
            // 仅已发药正向行可作为 3505 sourceLineId
            if (!DispenseStatus.COMPLETED.equals(status) && !DispenseStatus.PART_COMPLETED.equals(status)) {
                continue;
            }
            // 本批其它退药行已占用则跳过
            if (claimedOriginDispenseIds != null
                && row.getId() != null
                && claimedOriginDispenseIds.contains(row.getId())) {
                continue;
            }
            candidates.add(row);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        MedicationDispense chosen = this.pickBestOriginDispenseMatch(candidates, refundDispense);
        if (chosen == null || chosen.getId() == null) {
            return null;
        }
        if (claimedOriginDispenseIds != null) {
            claimedOriginDispenseIds.add(chosen.getId());
        }
        return chosen.getId();
    }

    /**
     * 在候选原发药行中按退药行拷贝字段打分选最优。
     *
     * @param candidates     未占用正向发药候选
     * @param refundDispense 退药行
     * @return 最优原发药；候选空则 null
     */
    private MedicationDispense pickBestOriginDispenseMatch(
        List<MedicationDispense> candidates,
        MedicationDispense refundDispense
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        String refundTrace = null;
        String refundLot = null;
        BigDecimal refundQty = null;
        if (refundDispense != null) {
            refundTrace = trimToNull(refundDispense.getTraceNo());
            refundLot = trimToNull(refundDispense.getLotNumber());
            refundQty = refundDispense.getQuantity();
        }
        MedicationDispense best = null;
        int bestScore = -1;
        for (MedicationDispense row : candidates) {
            int score = 0;
            // 追溯码全等（创建待退时从原发药拷贝，区分同药多发最稳）
            if (refundTrace != null && refundTrace.equals(trimToNull(row.getTraceNo()))) {
                score = score + 100;
            }
            // 批号
            if (refundLot != null && refundLot.equals(trimToNull(row.getLotNumber()))) {
                score = score + 10;
            }
            // 数量
            if (refundQty != null && row.getQuantity() != null && refundQty.compareTo(row.getQuantity()) == 0) {
                score = score + 1;
            }
            if (best == null) {
                best = row;
                bestScore = score;
                continue;
            }
            if (score > bestScore) {
                best = row;
                bestScore = score;
                continue;
            }
            // 同分：取主键较小者（FIFO），避免总绑最新一行
            if (score == bestScore
                && row.getId() != null
                && best.getId() != null
                && row.getId() < best.getId()) {
                best = row;
            }
        }
        return best;
    }

    /**
     * 在同药嘱收费行中挑选主键最大的冲销/再生行（{@code refund_id} 非空）。
     *
     * @param rows 同就诊药嘱锚点下的收费行
     * @return 最新冲销行；无则 null
     */
    private ChargeItem pickLatestOffsetCharge(List<ChargeItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        ChargeItem chosen = null;
        for (ChargeItem row : rows) {
            // 仅冲销/再生行带 refund_id
            if (row == null || row.getRefundId() == null) {
                continue;
            }
            if (chosen == null) {
                chosen = row;
                continue;
            }
            if (row.getId() != null && chosen.getId() != null && row.getId() > chosen.getId()) {
                chosen = row;
            }
        }
        return chosen;
    }

    /**
     * 按原发药行加载收费项（门诊同 ChargeItem 仍挂原 dispense_id）。
     *
     * @param encounterId 就诊主键
     * @param dispenseId  原正向发放行主键
     * @return 收费项；无则 null
     */
    private ChargeItem loadChargeByDispenseId(Long encounterId, Long dispenseId) {
        if (encounterId == null || dispenseId == null) {
            return null;
        }
        Map<Long, ChargeItem> byDispenseId = this.mapChargeItemsByDispenseId(encounterId, List.of(dispenseId));
        return byDispenseId.get(dispenseId);
    }

    /**
     * 按主键加载收费明细。
     *
     * @param chargeItemId 收费明细主键
     * @return 收费项；无则 null
     */
    private ChargeItem loadChargeById(Long chargeItemId) {
        if (chargeItemId == null) {
            return null;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setIds(List.of(chargeItemId));
        List<ChargeItem> rows = chargeItemQueryService.loadChargeItemsByIds(cmd);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    /**
     * 3506 原销售解析结果：原发药行 + feedetl_sn 回落 + 原账单单价。
     *
     * @param originDispenseId  原正向发放行主键（3505 sourceLineId）
     * @param fallbackFeedetlSn 无成功 3505 审计时的 rtal_docno 回落（收费项 busNo/id）
     * @param unitPrice         原收费项 {@code unit_price}；供伴生 3502 {@code pric}
     */
    private record OriginSaleRef(Long originDispenseId, String fallbackFeedetlSn, BigDecimal unitPrice) {

        /**
         * 空引用（无法对单）。
         *
         * @return 字段均为 null 的引用
         */
        private static OriginSaleRef empty() {
            return new OriginSaleRef(null, null, null);
        }

        /**
         * 组装原销售引用。
         *
         * @param originDispenseId  原发药行主键
         * @param fallbackFeedetlSn feedetl_sn 回落
         * @param unitPrice         原账单单价；可空
         * @return 引用实例
         */
        private static OriginSaleRef of(
            Long originDispenseId,
            String fallbackFeedetlSn,
            BigDecimal unitPrice
        ) {
            return new OriginSaleRef(originDispenseId, fallbackFeedetlSn, unitPrice);
        }
    }

    /**
     * 取收费项业务单位单价（退药反找原药嘱账单后写入 3506/伴生 3502）。
     *
     * @param charge 原销售收费项；可空
     * @return {@code unit_price}；收费项或单价空时 null
     */
    private BigDecimal resolveChargeUnitPrice(ChargeItem charge) {
        if (charge == null) {
            return null;
        }
        return charge.getUnitPrice();
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
            line.setManuDate(item.getProductionDate().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        if (item.getExpirationDate() != null) {
            line.setExpyEnd(item.getExpirationDate().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
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
            if (settlement != null) {
                line.setSetlId(settlement.getSetlId());
                line.setMdtrtSn(settlement.getMdtrtId());
            } else if (encounter != null) {
                line.setMdtrtSn(encounter.getBusNo());
            }
        } else {
            line.setMdtrtSetlType(MdtrtSetlType.SELF_PAY.getCode());
            if (encounter != null) {
                line.setMdtrtSn(encounter.getBusNo());
            }
        }
    }

    /**
     * 填写医保费用结算类型：自费=0；医保按参保地/就医地对比，判不出默认本地。
     *
     * @param line      上报行
     * @param contract  契约
     * @param encounter 就诊
     */
    private void applyHiFeesetlType(
        InvUploadLineCommand line,
        Contract contract,
        EncounterDetailQueryModel encounter
    ) {
        if (!MdtrtSetlType.INSURANCE.getCode().equals(line.getMdtrtSetlType())) {
            line.setHiFeesetlType(InventoryUploadHiFeesetlAssembler.resolveSelfPay());
            return;
        }
        String mdtrtarea = null;
        if (contract != null && isNotEmpty(contract.getBusNo())) {
            mdtrtarea = contractPropertiesAccess.findAdmVs(contract.getBusNo()).orElse(null);
        }
        String insuplc = resolveInsuplcAdmdvs(contract, encounter);
        line.setHiFeesetlType(InventoryUploadHiFeesetlAssembler.resolveInsurance(insuplc, mdtrtarea));
    }

    /**
     * 从人员缓存取参保地区划。
     *
     * @param contract  契约
     * @param encounter 就诊
     * @return 参保地区划；无则 null
     */
    private String resolveInsuplcAdmdvs(Contract contract, EncounterDetailQueryModel encounter) {
        if (contract == null || contract.getId() == null || encounter == null || encounter.getPatientId() == null) {
            return null;
        }
        Optional<Patient> patientOpt = patientQueryService.findOptionalPatientEntityById(encounter.getPatientId());
        if (patientOpt.isEmpty() || isEmpty(patientOpt.get().getIdCard())) {
            return null;
        }
        Optional<YbPersonCache> cacheOpt = personCacheDomainService.findValidByContractIdAndIdCard(
            contract.getId(),
            patientOpt.get().getIdCard(),
            TenantUtils.getCurrentTenantId());
        if (cacheOpt.isEmpty()) {
            return null;
        }
        return cacheOpt.get().getInsuplcAdmdvs();
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
        // 证件类型/号码跟患者主档
        List<PatientIdentifier> identifiers = patientIdentifierRepository.findAllByPatientId(patient.getId());
        line.setPsnCertType(InventoryUploadPsnCertTypeAssembler.resolve(patient, identifiers));
        line.setCertno(InventoryUploadPsnCertTypeAssembler.resolveCertno(patient, identifiers));
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
     * 解析销售上报契约对：出站医保凭证 + 报文结算性质。
     *
     * <p>对齐 openhis：自费支付单仅决定 {@code mdtrt_setl_type=2}；出站契约置空后回落省/市医保配置。</p>
     *
     * @param contractBusNo 显式契约业务编号；可空
     * @param contractId    显式契约 ID；可空
     * @param encounterId   就诊 ID
     * @param dispenseIds   发药行 ID 列表（预留，与历史签名兼容）
     * @return 出站契约 + 结算性质契约
     */
    SaleContractContext resolveSaleContracts(
        String contractBusNo,
        Long contractId,
        Long encounterId,
        List<Long> dispenseIds
    ) {
        Contract paymentContract = findPaymentContract(encounterId);
        if (isNotEmpty(contractBusNo) || contractId != null) {
            Contract explicit = contractReader.resolveOrDefault(contractBusNo, contractId);
            Contract gateway = explicit;
            // 显式传入自费：不能出站，回落医保凭证
            if (!isInsuranceContract(explicit)) {
                gateway = contractReader.resolveOrDefault(null, null);
            }
            Contract nature = paymentContract;
            if (nature == null) {
                nature = explicit;
            }
            return new SaleContractContext(gateway, nature);
        }
        // 支付单为医保：出站与结算性质同一契约
        if (paymentContract != null && isInsuranceContract(paymentContract)) {
            return new SaleContractContext(paymentContract, paymentContract);
        }
        // 自费或无支付单：出站回落省/市；结算性质=支付单（空则 fillSettlement 按自费）
        Contract gateway = contractReader.resolveOrDefault(null, null);
        return new SaleContractContext(gateway, paymentContract);
    }

    /**
     * 兼容清床汇总等调用方：仅返回出站医保契约（不含结算性质）。
     *
     * @param contractBusNo 契约业务编号
     * @param contractId    契约 ID
     * @param encounterId   就诊 ID
     * @param dispenseIds   发药行 ID 列表
     * @return 出站用医保契约
     */
    Contract resolveContract(String contractBusNo, Long contractId, Long encounterId, List<Long> dispenseIds) {
        return resolveSaleContracts(contractBusNo, contractId, encounterId, dispenseIds).gateway();
    }

    /**
     * 从就诊成功/退费中支付单解析费用性质契约（可自费）。
     *
     * @param encounterId 就诊 ID
     * @return 支付单 contractNo 对应契约；无则 null
     */
    Contract findPaymentContract(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(encounterId);
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null || isEmpty(payment.getContractNo())) {
                continue;
            }
            return contractReader.resolveOrDefault(payment.getContractNo(), null);
        }
        return null;
    }

    /**
     * 销售上报契约对：网关出站 vs 报文结算类型来源。
     *
     * @param gateway           出站医保契约（非空）
     * @param settlementNature  结算性质（可自费；可空，空按自费写报文）
     */
    record SaleContractContext(Contract gateway, Contract settlementNature) {
    }

    /**
     * 按发药行 ID 映射收费项。
     *
     * @param encounterId 就诊 ID
     * @param dispenseIds 发药行 ID 列表
     * @return 发药行 ID 到收费项的映射
     */
    Map<Long, ChargeItem> mapChargeItemsByDispenseId(Long encounterId, List<Long> dispenseIds) {
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
    YbSettlementRecord resolveSettlement(Long encounterId, Contract contract) {
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
