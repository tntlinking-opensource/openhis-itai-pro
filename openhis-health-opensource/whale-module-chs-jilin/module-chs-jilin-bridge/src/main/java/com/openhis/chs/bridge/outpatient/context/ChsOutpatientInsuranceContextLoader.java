/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.context;

import com.openhis.chs.domain.outpatient.command.OpClinicDiagnosisLine;
import com.openhis.chs.domain.outpatient.command.OpPlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpSettleChargeLine;
import com.openhis.chs.domain.share.enums.DiagType;
import com.openhis.chs.domain.outpatient.loader.OpPreSettleContextLoader;
import com.openhis.chs.domain.settlement.support.SettlementTraceCountSupport;
import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.product.service.ProductDefinitionQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.chs.domain.share.constants.ChsDetailAuditConstants;
import com.openhis.chs.domain.share.constants.ChsOutpatientRegisterFeeConstants;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.infrastructure.integration.elep.ElepPrescriptionExtension;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepRxNoByPrescriptionNoExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepRxNoQueryExtensionOut;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.notice.CatalogNoticeComplianceAssertSupport;
import com.openhis.chs.bridge.outpatient.assembler.InsuranceDiagnosisCodeAssembler;
import com.openhis.chs.bridge.settlement.support.ChsActivityPackageFeeExpandSupport;
import com.openhis.chs.bridge.settlement.support.ChsFeeRefundInitFeedetlSupport;
import com.openhis.chs.bridge.settlement.support.ChsFeeUploadSlice;
import com.openhis.chs.bridge.settlement.support.ChsSettlementDrugTraceRatioResolver;
import com.openhis.chs.bridge.settlement.support.SettlementFeeUnitConvertSupport;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemLookup;
import com.openhis.chs.domain.catalog.support.NationalCatalogPackHint;
import com.openhis.chs.bridge.settlement.support.SettlementRetailUploadDiffSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * HIS 主数据组装 {@link OpPreSettleCommand}（门诊 bridge HIS→CHS 上下文加载）。
 *
 * <p>加载范围：就诊、科室/医师 yb_no、收费项、国家目录编码、诊断、追溯码数量、ELEP hi_rxno 等；
 * 依赖 health-domain Encounter/ChargeItem/ProductDefinition/Condition 等查询服务；
 * 实现 {@link OpPreSettleContextLoader}。</p>
 *
 * <p>缺少 yb_no 时抛 INS-011，阻止无编码的 2204 上传。
 * 诊疗套餐有子项时经 {@link ChsActivityPackageFeeExpandSupport} 只上传子项行。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsOutpatientInsuranceContextLoader implements OpPreSettleContextLoader {

    private final EncounterContextQueryService encounterContextQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionQueryService productDefinitionQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ChsInsuranceContractReaderImpl contractReader;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final ObjectProvider<ElepPrescriptionExtension> elepPrescriptionExtensionProvider;
    private final CatalogNoticeComplianceAssertSupport catalogNoticeComplianceAssertSupport;
    private final ChsActivityPackageFeeExpandSupport activityPackageFeeExpandSupport;
    private final ChsSettlementDrugTraceRatioResolver settlementDrugTraceRatioResolver;
    private final YbNationalCatalogItemLookup nationalCatalogItemLookup;

    /**
     * 单笔预结算上下文：加载 chargeItems 并组装完整 {@link OpPreSettleCommand}。
     *
     * <p>医生站签发门禁（trig=2）可不传 chargeItemIds：按就诊装载 PLANNED 待收费行。</p>
     *
     * @param partial 调用方传入的预结算部分字段（含 chargeItemIds，可空）
     * @return 含收费明细、诊断、追溯码计数的完整预结算上下文
     */
    @Override
    public OpPreSettleCommand enrichPreSettle(OpPreSettleCommand partial) {
        List<Long> chargeItemIds = partial.getChargeItemIds();
        // Pre：未传 ids 时按就诊装载 PLANNED（签发事前提醒）
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            List<ChargeItem> plannedItems = this.loadPlannedChargeItemsByEncounter(partial.getEncounterId());
            List<Long> plannedIds = new ArrayList<>(plannedItems.size());
            for (ChargeItem item : plannedItems) {
                plannedIds.add(item.getId());
            }
            partial.setChargeItemIds(plannedIds);
            return this.buildContext(partial, plannedItems, partial.getMedType(), null, null);
        }
        List<ChargeItem> chargeItems = this.loadChargeItems(chargeItemIds);
        return this.buildContext(partial, chargeItems, partial.getMedType(), null, null);
    }

    /**
     * 补全门诊预结算上下文，复用调用方已装载的收费项（避免 Gate med_type 解析与 enrich 双次扫库）。
     *
     * <p>bridge 专用重载（domain 接口不暴露 health {@code ChargeItem} 类型）。</p>
     *
     * @param partial               调用方已填写的部分命令
     * @param preloadedChargeItems  已按 ids 装载的收费项；空则回落 {@link #enrichPreSettle(OpPreSettleCommand)}
     * @return 补全后的预结算命令
     */
    public OpPreSettleCommand enrichPreSettle(
        OpPreSettleCommand partial,
        List<ChargeItem> preloadedChargeItems) {
        // Pre：无预加载行时回落标准路径（含 PLANNED 按就诊装载）
        if (preloadedChargeItems == null || preloadedChargeItems.isEmpty()) {
            return this.enrichPreSettle(partial);
        }
        List<Long> ids = new ArrayList<>(preloadedChargeItems.size());
        for (ChargeItem item : preloadedChargeItems) {
            if (item != null && item.getId() != null) {
                ids.add(item.getId());
            }
        }
        // Pre：回写 ids，供后续编排/日志对齐
        if (!ids.isEmpty()) {
            partial.setChargeItemIds(ids);
        }
        return this.buildContext(partial, preloadedChargeItems, partial.getMedType(), null, null);
    }

    /**
     * 挂号/2201 登记上下文：不含收费明细，medfeeSumamt=0。
     *
     * @param partial 调用方传入的登记部分字段
     * @return 不含收费明细的 2201 登记上下文（medfeeSumamt=0）
     */
    @Override
    public OpPreSettleCommand enrichRegistration(OpPlatformRegistrationCommand partial) {
        Contract contract = contractReader.requireContractEntity(null, partial.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        OutpatientEncounterContextQueryModel encounter = encounterContextQueryService
            .mustGetOutpatientContextByEncounterId(partial.getEncounterId());

        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        // Pre：2201 医师仅取就诊医生或科室默认医师，不上传操作员
        Practitioner doctor = this.resolveOutpatientDoctor(encounter.getDoctorId(), organization);
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
        String medType = MedType.GENERAL_OUTPATIENT.getCode();
        if (isNotEmpty(partial.getMedType())) {
            medType = partial.getMedType().trim();
        }
        // Pre：2201 兼容路径不强制临床诊断（按挂号费策略可默认 Z00.001）；ctx.medType 仍用请求值
        DiagnosisBundle diagnosisBundle = this.resolveDiagnosisBundle(
            partial.getEncounterId(),
            null,
            true,
            contractBusNo,
            null,
            null);

        OpPreSettleCommand ctx = new OpPreSettleCommand();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(partial.getEncounterId());
        ctx.setEncounterBusNo(encounter.getEncounterBusNo());
        ctx.setPatientId(encounter.getPatientId());
        ctx.setPatientName(encounter.getPatientName());
        ctx.setPatientCertNo(encounter.getIdCard());
        ctx.setPatientGender(encounter.getGenderEnum());
        // Pre：3101 brdy — Encounter 患者出生日期
        if (encounter.getBirthDate() != null) {
            ctx.setBrdy(encounter.getBirthDate().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        ctx.setEncounterStartTime(encounter.getStartTime());
        ctx.setOrganizationId(encounter.getOrganizationId());
        if (organization != null) {
            ctx.setDeptCode(resolveDeptPlatformCodg(organization));
            ctx.setDeptName(organization.getName());
            ctx.setCaty(resolveDeptPlatformCodg(organization));
        }
        if (doctor != null) {
            ctx.setAtddrNo(doctor.getYbNo());
            ctx.setDrName(doctor.getName());
        }
        ctx.setMdtrtCertType(partial.getMdtrtCertType());
        ctx.setMdtrtCertNo(partial.getMdtrtCertNo());
        ctx.setPsnNo(partial.getPsnNo());
        ctx.setInsuplcAdmdvs(partial.getInsuplcAdmdvs());
        ctx.setInsutype(partial.getInsutype());
        ctx.setMedType(medType);
        ctx.setRegisterFeePath(Boolean.TRUE);
        ctx.setMainDiagCode(diagnosisBundle.mainDiagCode());
        ctx.setMainDiagName(diagnosisBundle.mainDiagName());
        ctx.setClinicDiagnoses(diagnosisBundle.clinicDiagnoses());
        ctx.setChargeLines(List.of());
        ctx.setMedfeeSumamt(BigDecimal.ZERO);
        ctx.setOperatorId(operatorId);
        ctx.setTenantId(TenantUtils.getCurrentTenantId());
        // Gate）201 必填科室/医师医保编码，禁blankToDash 后以 "-" 上传平台
        if (organization == null || isEmpty(organization.getYbNo())) {
            String deptHint;
            if (organization != null) {
                deptHint = organization.getName();
            } else {
                deptHint = String.valueOf(encounter.getOrganizationId());
            }
            throw new BusinessException(
                "INS-011",
                "2201 挂号科室缺少医保编码(yb_no): " + deptHint);
        }
        this.requireDoctorFor2201(doctor);
        return ctx;
    }

    /**
     * 批量预结算单组上下文：使用分组后的 chargeItems 与组级 medType/diseCodg。
     *
     * @param partial 调用方传入的组级部分字段
     * @param groupChargeItemIds 本组收费项 ID 列表
     * @param groupMedType 本组医疗类别
     * @param groupDiseCodg 本组病种编码（慢病）
     * @param groupDiseName 本组病种名称
     * @return 本组完整预结算上下文
     */
    @Override
    public OpPreSettleCommand enrichPreSettleForGroup(
        OpPreSettleCommand partial,
        List<Long> groupChargeItemIds,
        String groupMedType,
        String groupDiseCodg,
        String groupDiseName) {
        List<ChargeItem> groupChargeItems = loadChargeItems(groupChargeItemIds);
        return this.buildContext(partial, groupChargeItems, groupMedType, groupDiseCodg, groupDiseName);
    }

    /**
     * 按就诊装载 PLANNED 待收费明细（签发门禁未传 chargeItemIds 时使用）。
     *
     * @param encounterId 就诊主键
     * @return 非空 PLANNED 收费项列表
     */
    private List<ChargeItem> loadPlannedChargeItemsByEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "就诊标识不能为空");
        }
        ChargeItemQueryCommand byEncounter = new ChargeItemQueryCommand();
        byEncounter.setEncounterId(encounterId);
        byEncounter.setStatusEnumIn(List.of(ChargeItemStatus.PLANNED.getCode()));
        List<ChargeItem> plannedItems = chargeItemQueryService.listChargeItemsByEncounterId(byEncounter);
        if (plannedItems == null || plannedItems.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "就诊下无待收费明细，无法做事前审核");
        }
        return plannedItems;
    }

    /**
     * 按 ID 批量加载收费项，数量与请求不一致时抛错。
     *
     * @param chargeItemIds 收费项 ID 列表（不可空）
     * @return 与请求 ID 一一对应的收费项实体
     */
    private List<ChargeItem> loadChargeItems(List<Long> chargeItemIds) {
        // Gate：禁止对空列表调 size，避免 NPE
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项 ID 不能为空");
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != chargeItemIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不完整");
        }
        return chargeItems;
    }

    /**
     * 解析门诊医保上传医师：仅用于平台字段（atddr_no/dr_name），不写本地挂号参与者。
     *
     * <p>优先级：就诊已选医生 → 科室「默认医师」；均无则返回 null，由调用方报错，禁止回落操作员。</p>
     *
     * @param encounterDoctorId 就诊上下文医生主键（可空）
     * @param organization      挂号科室（可空；提供 {@code defDoctorId} 兜底）
     * @return 医师实体；均不可得时为 {@code null}
     */
    private Practitioner resolveOutpatientDoctor(Long encounterDoctorId, Organization organization) {
        // Pre：挂号已选医生优先，与本地 REGISTRATION_DOCTOR 一致
        if (encounterDoctorId != null) {
            Practitioner doctor = practitionerQueryService.findById(encounterDoctorId);
            if (doctor != null) {
                return doctor;
            }
        }
        // Pre：未选医生 → 仅用科室默认医师（Organization.defDoctorId），不上传操作员
        if (organization == null || organization.getDefDoctorId() == null) {
            return null;
        }
        return practitionerQueryService.findById(organization.getDefDoctorId());
    }

    /**
     * 2201/费用上传前校验医师及医保编码已维护。
     *
     * @param doctor 已解析医师（可空）
     */
    private void requireDoctorFor2201(Practitioner doctor) {
        // Gate：未选医生且科室未维护默认医师 → 拒绝上传，禁止操作员兜底
        if (doctor == null) {
            throw new BusinessException(
                "INS-011",
                "2201 医保挂号须选择接诊医师，或在科室维护中配置默认医师");
        }
        if (isEmpty(doctor.getYbNo())) {
            throw new BusinessException(
                "INS-011",
                "2201 挂号医师缺少医保编码(yb_no): " + doctor.getName());
        }
    }

    /**
     * 核心组装：契约、就诊、收费明细行、诊断、追溯码计数、操作员等写入 {@link OpPreSettleCommand}。
     *
     * @param partial 调用方已填写的参保人/凭证等字段
     * @param chargeItems 本组收费项（单笔或批量分组）
     * @param groupMedType 医疗类别；为空时用普通门诊
     * @param groupDiseCodg 慢病病种编码；有值时写入 2204 费用行，可空则传空（不回填主诊）
     * @param groupDiseName 慢病病种名称；有值时覆盖主诊展示名，可空则用临床主诊名
     * @return 可供 2203/2204/2206 使用的完整上下文
     */
    private OpPreSettleCommand buildContext(
        OpPreSettleCommand partial,
        List<ChargeItem> chargeItems,
        String groupMedType,
        String groupDiseCodg,
        String groupDiseName) {
        Contract contract = contractReader.requireContractEntity(null, partial.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        OutpatientEncounterContextQueryModel encounter = encounterContextQueryService
            .mustGetOutpatientContextByEncounterId(partial.getEncounterId());

        // Pre：先定 med_type，再决定诊断策略（挂号费批次可默认码；诊疗须全量诊断）
        String medType = MedType.GENERAL_OUTPATIENT.getCode();
        if (isNotEmpty(groupMedType)) {
            medType = groupMedType.trim();
        }
        boolean registerFeePath = this.isRegisterFeeBatch(chargeItems, medType);
        DiagnosisBundle diagnosisBundle = this.resolveDiagnosisBundle(
            partial.getEncounterId(), chargeItems, registerFeePath, contractBusNo, groupDiseCodg, groupDiseName);
        String mainDiagCode = diagnosisBundle.mainDiagCode();
        String mainDiagName = diagnosisBundle.mainDiagName();
        List<OpClinicDiagnosisLine> clinicDiagnoses = diagnosisBundle.clinicDiagnoses();

        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        // Pre：费用上传医师：就诊医生 → 科室默认医师（不上传操作员）
        Practitioner doctor = this.resolveOutpatientDoctor(encounter.getDoctorId(), organization);

        Map<Long, ChargeItemDefinition> definitionMap = buildDefinitionMap(chargeItems);
        Map<String, ProductDefinitionCatalogQueryModel> productMap = loadProducts(chargeItems, definitionMap);
        // Pre：国家目录包装/计价单位（2204 限价折算优先于院内药典）
        Map<String, NationalCatalogPackHint> packHintByYbNo = nationalCatalogItemLookup
            .loadPackHintsByHilistCodes(
                contract.getId(),
                TenantUtils.getCurrentTenantId(),
                collectYbNos(productMap));
        // Pre：收费行→药品申请/发放批号，供 DD §4.3 主表比例优先
        ChsSettlementDrugTraceRatioResolver.SettlementDrugTraceBatchContext drugTraceContext =
            settlementDrugTraceRatioResolver.prepare(chargeItems);
        // Pre：父产品映射 → 套餐子项/售价一次预取，消除按套餐 N+1
        Map<Long, ProductDefinitionCatalogQueryModel> parentProductByItemId = new HashMap<>();
        for (ChargeItem item : chargeItems) {
            if (item == null || item.getId() == null) {
                continue;
            }
            parentProductByItemId.put(item.getId(), this.resolveProduct(item, productMap, definitionMap));
        }
        ChsActivityPackageFeeExpandSupport.PackageExpandBatchContext packageBatch =
            activityPackageFeeExpandSupport.prepare(chargeItems, parentProductByItemId);
        // Pre：冲销源收费项一次装载，供 2204 init_feedetl_sn
        Map<Long, ChargeItem> refundSourceById =
            ChsFeeRefundInitFeedetlSupport.loadRefundSources(chargeItemQueryService, chargeItems);
        // Pre：先展开全部 slice，收集 yb_no 后一次加载局文 overlay
        List<OpFeeSliceWork> sliceWorks = new ArrayList<>();
        LinkedHashSet<String> hilistCodes = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            ProductDefinitionCatalogQueryModel parentProduct = parentProductByItemId.get(item.getId());
            ChargeItemDefinition definitionForGate = null;
            if (item.getDefinitionId() != null) {
                definitionForGate = definitionMap.get(item.getDefinitionId());
            }
            String parentDisplayName = null;
            if (definitionForGate != null && isNotEmpty(definitionForGate.getChargeName())) {
                parentDisplayName = definitionForGate.getChargeName().trim();
            }
            // Pre：诊疗套餐有子项时只展开子项行（父项通常无 yb_no）
            List<ChsFeeUploadSlice> slices = activityPackageFeeExpandSupport.expandToUploadSlices(
                item, parentProduct, parentDisplayName, packageBatch);
            for (ChsFeeUploadSlice slice : slices) {
                ProductDefinitionCatalogQueryModel product = slice.getProduct();
                String itemDisplayName = slice.getDisplayName();
                if (isEmpty(itemDisplayName)) {
                    itemDisplayName = parentDisplayName;
                }
                // Gate：无国家目录编码禁止上传 2204
                if (product == null || isEmpty(product.getYbNo())) {
                    throw new BusinessException(
                        "INS-011",
                        buildMissingYbNoMessage(item, itemDisplayName, product));
                }
                hilistCodes.add(product.getYbNo().trim());
                sliceWorks.add(new OpFeeSliceWork(item, slice, product, itemDisplayName, definitionForGate));
            }
        }
        Map<String, YbNationalCatalogNoticeOverlay> overlayByHilist =
            catalogNoticeComplianceAssertSupport.preloadOverlays(
                TenantUtils.getCurrentTenantId(), contract.getId(), hilistCodes);
        // Pre：同处方号 ELEP hi_rxno 请求级去重
        Map<String, String> hiRxnoByPrescriptionNo = new HashMap<>();
        List<OpSettleChargeLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int drugTracCnt = 0;
        int mcsTracCnt = 0;
        Map<Long, MedicationDefinition> medicationById = new HashMap<>();
        for (OpFeeSliceWork work : sliceWorks) {
            ChargeItem item = work.item();
            ChsFeeUploadSlice slice = work.slice();
            ProductDefinitionCatalogQueryModel product = work.product();
            String itemDisplayName = work.itemDisplayName();
            ChargeItemDefinition definitionForGate = work.definitionForGate();
            catalogNoticeComplianceAssertSupport.assertAllowed(
                TenantUtils.getCurrentTenantId(),
                contract.getId(),
                product.getYbNo().trim(),
                product.getProductBusNo(),
                slice.getPric(),
                itemDisplayName,
                product.getProductDefinitionId(),
                product.getProductTableName(),
                overlayByHilist);
            MedicationDefinition medForFee = null;
            if (isNotEmpty(product.getProductTableName())) {
                // DD §4.3：药品两分支 floor；比例=收费行对应 req/发药批号→主表优先，否则药典
                if (SettlementTraceCountSupport.isMedication(product.getProductTableName())) {
                    // 有药品定义才加载药典贴码单位/比例
                    if (product.getProductDefinitionId() != null) {
                        medForFee = medicationById.get(product.getProductDefinitionId());
                        // 本批未缓存：读药典实体
                        if (medForFee == null) {
                            medForFee = medicationDefinitionQueryService
                                .findOptionalMedicationDefinitionEntityById(product.getProductDefinitionId())
                                .orElse(null);
                            // 读到药典实体：写入本批缓存防 N+1
                            if (medForFee != null) {
                                medicationById.put(product.getProductDefinitionId(), medForFee);
                            }
                        }
                    }
                    String chargeUnit = item.getQuantityUnit();
                    String catalogUnit = null;
                    BigDecimal catalogRatio = null;
                    // 药典命中：作为主表缺失时的回落
                    if (medForFee != null) {
                        catalogUnit = medForFee.getTraceUnitCode();
                        catalogRatio = medForFee.getTraceUnitRatio();
                    }
                    ChsSettlementDrugTraceRatioResolver.ResolvedDrugTraceUnit resolved =
                        settlementDrugTraceRatioResolver.resolve(
                            item,
                            product.getProductDefinitionId(),
                            catalogUnit,
                            catalogRatio,
                            drugTraceContext);
                    // 追溯码计数仍按收费单位数量（勿用折片后 cnt）
                    drugTracCnt += SettlementTraceCountSupport.countDrugTracUnits(
                        product.getProductTableName(),
                        slice.getCnt(),
                        chargeUnit,
                        resolved.traceUnitCode(),
                        resolved.traceUnitRatio());
                } else {
                    // 耗材：仍按 mcs_trac_cnt 原口径 floor
                    mcsTracCnt += SettlementTraceCountSupport.countMcsTracUnits(
                        product.getProductTableName(), slice.getCnt());
                }
            }
            // Pre：药品按盒收费时 cnt/pric 折成目录计价单位，对齐医保限价口径
            String fullYbNo = product.getYbNo().trim();
            NationalCatalogPackHint packHint = resolvePackHint(packHintByYbNo, fullYbNo, contract.getId());
            SettlementFeeUnitConvertSupport.FeeUploadQtyPrice uploadQtyPrice =
                SettlementFeeUnitConvertSupport.toSplitUnit(
                    medForFee, item.getQuantityUnit(), slice.getCnt(), slice.getPric(), packHint);
            OpSettleChargeLine line = new OpSettleChargeLine();
            line.setChargeItemId(slice.getChargeItemId());
            line.setFeedetlSn(slice.getFeedetlSn());
            // Pre：退单行写原费用流水（refund_id → 正数行 feedetl_sn）
            String initFeedetlSn = ChsFeeRefundInitFeedetlSupport.resolveInitFeedetlSn(
                item, slice.getFeedetlSn(), refundSourceById);
            if (isNotEmpty(initFeedetlSn)) {
                line.setInitFeedetlSn(initFeedetlSn);
            }
            // 2204 耗材口径：med_list_codg=医保编码截前15；medins_list_codg=完整医保编码
            line.setMedListCodg(SettlementTraceCountSupport.toPlatformMedListCodg(
                product.getProductTableName(), fullYbNo));
            line.setMedinsListCodg(resolveMedinsListCodg(product, fullYbNo));
            // Pre：折片后金额须与 pric×cnt 一致（长春 2204 费用分割校验；6 位单价平摊）
            line.setDetItemFeeSumamt(uploadQtyPrice.sumamtOr(slice.getDetItemFeeSumamt()));
            line.setCnt(uploadQtyPrice.cnt());
            line.setPric(uploadQtyPrice.pric());
            // Gate：负数 cnt 必须已带 init_feedetl_sn
            ChsFeeRefundInitFeedetlSupport.assertNegativeCntHasInit(
                line.getCnt(), line.getInitFeedetlSn(), line.getFeedetlSn());
            line.setFeeOcurTime(item.getOccurrenceTime());
            line.setRxno(this.resolve2204Rxno(item.getPrescriptionNo(), hiRxnoByPrescriptionNo));
            // Pre：3101 医嘱树必填名称/目录类/规格（与 §2.6 Snapshot 同源，禁止空串出站）
            line.setItemName(resolveChargeItemDisplayName(itemDisplayName, product, definitionForGate));
            line.setHilistType(resolveHilistTypeByProductTable(product.getProductTableName()));
            line.setChrgType(ChsDetailAuditConstants.DEFAULT_CHRG_TYPE);
            line.setHilistLv(ChsDetailAuditConstants.DEFAULT_HILIST_LV);
            line.setSpec(ChsDetailAuditConstants.DEFAULT_SPEC);
            String qtyUnit = uploadQtyPrice.unitCodeOrNull();
            if (isEmpty(qtyUnit)) {
                qtyUnit = item.getQuantityUnit();
            }
            if (isNotEmpty(qtyUnit)) {
                line.setSpecUnt(qtyUnit.trim());
            } else {
                line.setSpecUnt(ChsDetailAuditConstants.DEFAULT_SPEC_UNT);
            }
            // 2204 dise_codg 可空：仅慢病组有病种码时传；普通门诊不回填主诊码
            if (isNotEmpty(groupDiseCodg)) {
                line.setDiseCodg(groupDiseCodg);
            }
            if (organization != null) {
                line.setBilgDeptCodg(resolveDeptPlatformCodg(organization));
                line.setBilgDeptName(organization.getName());
            }
            if (doctor != null) {
                line.setBilgDrCodg(doctor.getYbNo());
                line.setBilgDrName(doctor.getName());
            }
            lines.add(line);
            // Pre：medfee_sumamt 与上传明细合计一致（6 位单价平摊后通常=院内合计）
            total = total.add(MonetaryAmountSupport.nullToZero(line.getDetItemFeeSumamt()));
        }

        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");

        OpPreSettleCommand ctx = new OpPreSettleCommand();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(partial.getEncounterId());
        ctx.setEncounterBusNo(encounter.getEncounterBusNo());
        ctx.setPatientId(encounter.getPatientId());
        ctx.setPatientName(encounter.getPatientName());
        ctx.setPatientCertNo(encounter.getIdCard());
        ctx.setPatientGender(encounter.getGenderEnum());
        // Pre：3101 brdy — Encounter 患者出生日期
        if (encounter.getBirthDate() != null) {
            ctx.setBrdy(encounter.getBirthDate().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        ctx.setEncounterStartTime(encounter.getStartTime());
        ctx.setOrganizationId(encounter.getOrganizationId());
        if (organization != null) {
            ctx.setDeptCode(resolveDeptPlatformCodg(organization));
            ctx.setDeptName(organization.getName());
            ctx.setCaty(resolveDeptPlatformCodg(organization));
        }
        if (doctor != null) {
            ctx.setAtddrNo(doctor.getYbNo());
            ctx.setDrName(doctor.getName());
        }
        ctx.setMdtrtCertType(partial.getMdtrtCertType());
        ctx.setMdtrtCertNo(partial.getMdtrtCertNo());
        ctx.setPsnNo(partial.getPsnNo());
        ctx.setInsuplcAdmdvs(partial.getInsuplcAdmdvs());
        ctx.setInsutype(partial.getInsutype());
        ctx.setMedType(medType);
        ctx.setRegisterFeePath(registerFeePath);
        ctx.setMainDiagCode(mainDiagCode);
        ctx.setMainDiagName(mainDiagName);
        ctx.setClinicDiagnoses(clinicDiagnoses);
        ctx.setChargeLines(lines);
        ctx.setMedfeeSumamt(total);
        // Pre：零售/上传分差落上下文，供预结算/正式结算快照与日结勾稽
        SettlementRetailUploadDiffSupport.RetailUploadDiffAmounts retailUploadDiff =
            SettlementRetailUploadDiffSupport.of(sumChargeItemRetailTotal(chargeItems), total);
        ctx.setRetailMedfeeSumamt(retailUploadDiff.retailMedfeeSumamt());
        ctx.setUploadMedfeeSumamt(retailUploadDiff.uploadMedfeeSumamt());
        ctx.setRetailUploadDiffAmt(retailUploadDiff.retailUploadDiffAmt());
        ctx.setPaymentDraftId(partial.getPaymentDraftId());
        ctx.setOperatorId(operatorId);
        ctx.setTenantId(TenantUtils.getCurrentTenantId());
        if (isNotEmpty(partial.getAcctUsedFlag())) {
            ctx.setAcctUsedFlag(partial.getAcctUsedFlag().trim());
        } else {
            ctx.setAcctUsedFlag("1");
        }
        ctx.setPsnSetlway("01");
        ctx.setMinpacuntDrugTracCnt(drugTracCnt);
        ctx.setMcsTracCnt(mcsTracCnt);
        Long accountId = null;
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                accountId = item.getAccountId();
                break;
            }
        }
        ctx.setAccountId(accountId);
        // Gate：签发/预结算可能触发 2201，医师编码必填（禁 "-"）
        this.requireDoctorFor2201(doctor);
        return ctx;
    }

    /**
     * 按医疗类别与账单绑定诊断解析 2203 诊断包。
     *
     * <p>诊疗结算：优先按本组 {@code ChargeItem.encounter_diagnosis_ids} 有序并集上传多行 diseinfo；
     * 首位仅打 maindiag_flag（统计/展示），不截断为单诊。挂号费可默认单行。</p>
     *
     * @param encounterId     就诊 id
     * @param chargeItems     本组收费项；可空（挂号登记路径）
     * @param registerFeePath 是否挂号费批次（默认诊断策略）
     * @param contractBusNo   契约 bus_no
     * @param groupDiseCodg   慢病组病种码（可空；仅写入 2204 费用行，不回填主诊；不替代 diseinfo 全量）
     * @param groupDiseName   慢病组病种名（可空；仅覆盖主诊展示名）
     * @return 主诊展示字段 + diseinfo 全量行
     */
    private DiagnosisBundle resolveDiagnosisBundle(
        Long encounterId,
        List<ChargeItem> chargeItems,
        boolean registerFeePath,
        String contractBusNo,
        String groupDiseCodg,
        String groupDiseName) {
        List<Long> orderedBillDxIds = this.collectOrderedEncounterDiagnosisIdsFromChargeItems(chargeItems);
        List<OpClinicDiagnosisLine> clinicDiagnoses;
        // Pre：账单已绑诊断 → 按 ids 串 有序并集上传多诊（结算真源）
        if (!orderedBillDxIds.isEmpty()) {
            clinicDiagnoses = InsuranceDiagnosisCodeAssembler.resolveClinicDiagnosesByOrderedIds(
                orderedBillDxIds,
                encounterQueryService,
                conditionDefinitionQueryService,
                !registerFeePath);
        } else if (registerFeePath) {
            clinicDiagnoses = List.of();
        } else {
            // 兼容：账单尚未落 ids 串 时回落本诊次全量诊断
            clinicDiagnoses = InsuranceDiagnosisCodeAssembler.resolveClinicDiagnoses(
                encounterId,
                encounterQueryService,
                conditionDefinitionQueryService,
                true);
        }
        if (registerFeePath && clinicDiagnoses.isEmpty()) {
            // Cmd：挂号费尚无临床诊断 → 配置/领域默认码单行
            DiagnosisHeader registerFeeDiag = this.resolveRegisterFeeDefaultDiagnosis(contractBusNo);
            OpClinicDiagnosisLine defaultLine = new OpClinicDiagnosisLine()
                .setDiagSrtNo(1)
                .setDiagCode(registerFeeDiag.code())
                .setDiagName(registerFeeDiag.name())
                .setDiagType(DiagType.WESTERN.getCode())
                .setMaindiagFlag(ChsOutpatientRegisterFeeConstants.DIAG_MAINDIAG_FLAG_YES);
            clinicDiagnoses = List.of(defaultLine);
        }
        String mainDiagCode = clinicDiagnoses.get(0).getDiagCode();
        String mainDiagName = clinicDiagnoses.get(0).getDiagName();
        // Pre：慢病组病种码/名仅覆盖主诊展示字段；diseinfo 仍保留账单绑定的多诊全量
        if (isNotEmpty(groupDiseCodg)) {
            mainDiagCode = groupDiseCodg.trim();
        }
        if (isNotEmpty(groupDiseName)) {
            mainDiagName = groupDiseName.trim();
        }
        return new DiagnosisBundle(mainDiagCode, mainDiagName, clinicDiagnoses);
    }

    /**
     * 是否挂号费结算批次：国标 med_type=12，或本组收费项全部为挂号登记来源（实际上报可为 11）。
     *
     * @param chargeItems 本组收费项；可空
     * @param medType     本批次医疗类别
     * @return 挂号费批次 true
     */
    private boolean isRegisterFeeBatch(List<ChargeItem> chargeItems, String medType) {
        if (ChsOutpatientRegisterFeeConstants.MED_TYPE_OUTPATIENT_REGISTRATION.equals(medType)) {
            return true;
        }
        if (chargeItems == null || chargeItems.isEmpty()) {
            return false;
        }
        String registrationSource = GenerateSource.REGISTRATION_REGISTER.getCode();
        for (ChargeItem item : chargeItems) {
            if (item == null || !registrationSource.equals(item.getGenerateSourceEnum())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从本组收费项 ids 串收集有序就诊诊断 id（先出现者优先，保留各行 ids 串内顺序）。
     *
     * @param chargeItems 收费项；可空
     * @return 有序去重 id 列表
     */
    private List<Long> collectOrderedEncounterDiagnosisIdsFromChargeItems(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item == null) {
                continue;
            }
            List<Long> ids = EncounterDiagnosisIdsSupport.parseOrderedIds(
                item.getEncounterDiagnosisIds(), true);
            for (Long id : ids) {
                if (id != null) {
                    ordered.add(id);
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 挂号费默认诊断：先读契约配置，缺省用 {@link ChsOutpatientRegisterFeeConstants}。
     *
     * @param contractBusNo 费用性质 bus_no
     * @return 非空编码与名称
     */
    private DiagnosisHeader resolveRegisterFeeDefaultDiagnosis(String contractBusNo) {
        String code = ChsOutpatientRegisterFeeConstants.DEFAULT_DIAG_CODE;
        String name = ChsOutpatientRegisterFeeConstants.DEFAULT_DIAG_NAME;
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        // Pre：院点 chs-contract 可覆盖默认诊断码/名以对齐 1307 显示名
        if (config != null) {
            if (isNotEmpty(config.getRegisterFeeDefaultDiagCode())) {
                code = config.getRegisterFeeDefaultDiagCode().trim();
            }
            if (isNotEmpty(config.getRegisterFeeDefaultDiagName())) {
                name = config.getRegisterFeeDefaultDiagName().trim();
            }
        }
        return new DiagnosisHeader(code, name);
    }

    /** 主诊断编码与名称的内部载体。 */
    private record DiagnosisHeader(String code, String name) {
    }

    /** 2203 诊断包：主诊字段 + diseinfo 全量行。 */
    private record DiagnosisBundle(
        String mainDiagCode,
        String mainDiagName,
        List<OpClinicDiagnosisLine> clinicDiagnoses) {
    }

    /**
     * 2204.rxno：仅传 ELEP 已上传成功时{@code hi_rxno}；无电子处方时留空，禁止用院内处方号伪造。
     *
     * @param prescriptionNo           院内处方号（关联 {@code med_medication_request}）
     * @param hiRxnoByPrescriptionNo   本请求缓存（命中则不再打 ELEP）；value 空串表示已查无 hi_rxno
     * @return 平台 hi_rxno，或 {@code null} 表示无电子处方明细
     */
    private String resolve2204Rxno(String prescriptionNo, Map<String, String> hiRxnoByPrescriptionNo) {
        if (isEmpty(prescriptionNo)) {
            return null;
        }
        String key = prescriptionNo.trim();
        // 本批已查过：空串哨兵表示无 hi_rxno
        if (hiRxnoByPrescriptionNo != null && hiRxnoByPrescriptionNo.containsKey(key)) {
            String cached = hiRxnoByPrescriptionNo.get(key);
            if (isEmpty(cached)) {
                return null;
            }
            return cached;
        }
        ElepPrescriptionExtension elepExtension = elepPrescriptionExtensionProvider.getIfAvailable();
        // ELEP 未装配或未启用：2204 不得携带 rxno，避免医保按电子处方核验
        if (elepExtension == null || !elepExtension.isEnabled()) {
            if (hiRxnoByPrescriptionNo != null) {
                hiRxnoByPrescriptionNo.put(key, "");
            }
            return null;
        }
        Optional<String> hiRxno = elepExtension.findRxNoByPrescriptionNo(
                        new ElepRxNoByPrescriptionNoExtensionIn().setPrescriptionNo(key))
                .map(ElepRxNoQueryExtensionOut::getHiRxno)
                .filter(StringUtils::isNotEmpty);
        // 未找到已上传 hi_rxno：留空，禁止回退院内处方
        if (hiRxno.isEmpty()) {
            if (hiRxnoByPrescriptionNo != null) {
                hiRxnoByPrescriptionNo.put(key, "");
            }
            return null;
        }
        String resolved = hiRxno.get();
        if (hiRxnoByPrescriptionNo != null) {
            hiRxnoByPrescriptionNo.put(key, resolved);
        }
        return resolved;
    }

    /**
     * 展开后待门禁/组行的工作单元（先批 overlay 再逐行 assert）。
     *
     * @param item              院内收费项
     * @param slice             上传逻辑行
     * @param product           产品投影（已含 yb_no）
     * @param itemDisplayName   展示名
     * @param definitionForGate 定价定义（可空）
     */
    private record OpFeeSliceWork(
        ChargeItem item,
        ChsFeeUploadSlice slice,
        ProductDefinitionCatalogQueryModel product,
        String itemDisplayName,
        ChargeItemDefinition definitionForGate) {
    }

    /**
     * 解析 3101/2204 科室平台码：优先 {@code yb_no}，否则 {@code bus_no}；超长截断。
     *
     * @param organization 开单/就诊科室
     * @return 非空科室码；均空则 null（由门禁本地校验拦截）
     */
    private String resolveDeptPlatformCodg(Organization organization) {
        if (organization == null) {
            return null;
        }
        String codg = organization.getYbNo();
        if (isEmpty(codg)) {
            codg = organization.getBusNo();
        }
        if (isEmpty(codg)) {
            return null;
        }
        String trimmed = codg.trim();
        if (trimmed.length() > ChsDetailAuditConstants.DEPT_CODG_MAX_LEN) {
            return trimmed.substring(0, ChsDetailAuditConstants.DEPT_CODG_MAX_LEN);
        }
        return trimmed;
    }

    /**
     * 产品表名 → 3101 {@code hilist_type}（目录类别）。
     *
     * @param productTableName 产品来源表
     * @return 101 西药 / 201 诊疗 / 301 材料
     */
    private String resolveHilistTypeByProductTable(String productTableName) {
        if (SettlementTraceCountSupport.isMedication(productTableName)) {
            return ChsDetailAuditConstants.HILIST_TYPE_WESTERN_DRUG;
        }
        if (SettlementTraceCountSupport.isDevice(productTableName)) {
            return ChsDetailAuditConstants.HILIST_TYPE_MATERIAL;
        }
        return ChsDetailAuditConstants.HILIST_TYPE_ACTIVITY;
    }

    /**
     * 3101 目录/医院名称：切片展示名 → 产品名 → 收费项定义名。
     *
     * @param sliceDisplayName 套餐展开切片名
     * @param product          目录产品
     * @param definition       收费项定义
     * @return 非空名称；均空则 "-"
     */
    private String resolveChargeItemDisplayName(
        String sliceDisplayName,
        ProductDefinitionCatalogQueryModel product,
        ChargeItemDefinition definition) {
        if (isNotEmpty(sliceDisplayName)) {
            return sliceDisplayName.trim();
        }
        if (product != null && isNotEmpty(product.getProductName())) {
            return product.getProductName().trim();
        }
        if (definition != null && isNotEmpty(definition.getChargeName())) {
            return definition.getChargeName().trim();
        }
        return ChsDetailAuditConstants.DEFAULT_SPEC;
    }

    /**
     * 汇总本组收费项院内零售合计（父项 total_price；与账单一致）。
     *
     * @param chargeItems 收费项
     * @return 行金额加总（未两位化；分差支持内会 scaleSettle）
     */
    private static BigDecimal sumChargeItemRetailTotal(List<ChargeItem> chargeItems) {
        BigDecimal retail = BigDecimal.ZERO;
        if (chargeItems == null || chargeItems.isEmpty()) {
            return retail;
        }
        for (ChargeItem item : chargeItems) {
            if (item == null) {
                continue;
            }
            retail = retail.add(MonetaryAmountSupport.nullToZero(item.getTotalPrice()));
        }
        return retail;
    }

    /** 从已加载产品投影收集国家码（供批量读目录包装）。 */
    private static LinkedHashSet<String> collectYbNos(
        Map<String, ProductDefinitionCatalogQueryModel> productMap) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (productMap == null || productMap.isEmpty()) {
            return codes;
        }
        for (ProductDefinitionCatalogQueryModel product : productMap.values()) {
            if (product != null && isNotEmpty(product.getYbNo())) {
                codes.add(product.getYbNo().trim());
            }
        }
        return codes;
    }

    /**
     * 取目录包装 hint；套餐子项码未预载时补查一次写入缓存。
     */
    private NationalCatalogPackHint resolvePackHint(
        Map<String, NationalCatalogPackHint> packHintByYbNo,
        String ybNo,
        Long contractId) {
        if (isEmpty(ybNo) || packHintByYbNo == null) {
            return null;
        }
        String code = ybNo.trim();
        if (packHintByYbNo.containsKey(code)) {
            return packHintByYbNo.get(code);
        }
        Map<String, NationalCatalogPackHint> loaded = nationalCatalogItemLookup.loadPackHintsByHilistCodes(
            contractId, TenantUtils.getCurrentTenantId(), List.of(code));
        NationalCatalogPackHint hint = loaded.get(code);
        packHintByYbNo.put(code, hint);
        return hint;
    }

    /**
     * 批量加载收费项关联的国家目录产品定义。
     *
     * @param chargeItems 收费项列表
     * @param definitionMap chargeItemDefinitionId → 定义
     * @return 复合键 → 目录查询模型；无关联产品时返回空 Map
     */
    private Map<String, ProductDefinitionCatalogQueryModel> loadProducts(
        List<ChargeItem> chargeItems,
        Map<Long, ChargeItemDefinition> definitionMap) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem item : chargeItems) {
            ChargeItemDefinition definition = null;
            if (item.getDefinitionId() != null) {
                definition = definitionMap.get(item.getDefinitionId());
            }
            ProductDefinitionBatchLookup lookup = resolveCatalogLookup(item, definition);
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return productDefinitionQueryService.batchLoadAsMap(lookups);
    }

    /**
     * 解析收费项对应的国家目录查询键：优先 chargeItem 上的 product，否则回退 definition.instance。
     *
     * @param row 收费项
     * @param definition 收费项定义（可为 null）
     * @return 批量查询键；无法解析时返回 null
     */
    private ProductDefinitionBatchLookup resolveCatalogLookup(ChargeItem row, ChargeItemDefinition definition) {
        if (row.getProductId() != null && isNotEmpty(row.getProductTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(row.getProductId());
            lookup.setProductTableName(row.getProductTable().trim());
            return lookup;
        }
        if (definition != null && definition.getInstanceId() != null && isNotEmpty(definition.getInstanceTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(definition.getInstanceId());
            lookup.setProductTableName(definition.getInstanceTable().trim());
            return lookup;
        }
        return null;
    }

    /**
     * 收集本批收费项涉及的 chargeItemDefinitionId 并批量查询。
     *
     * @param chargeItems 收费项列表
     * @return definitionId → 收费项定义
     */
    private Map<Long, ChargeItemDefinition> buildDefinitionMap(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getDefinitionId() != null) {
                definitionIds.add(item.getDefinitionId());
            }
        }
        return chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    /**
     * 从已加载的目录 Map 中取出单条收费项对应的国家目录产品。
     *
     * @param item 收费项
     * @param productMap loadProducts 返回的目录 Map
     * @param definitionMap buildDefinitionMap 返回的定义 Map
     * @return 国家目录查询模型；无关联键时返回 null
     */
    private ProductDefinitionCatalogQueryModel resolveProduct(
        ChargeItem item,
        Map<String, ProductDefinitionCatalogQueryModel> productMap,
        Map<Long, ChargeItemDefinition> definitionMap) {
        ChargeItemDefinition definition = null;
        if (item.getDefinitionId() != null) {
            definition = definitionMap.get(item.getDefinitionId());
        }
        ProductDefinitionBatchLookup lookup = resolveCatalogLookup(item, definition);
        if (lookup == null) {
            return null;
        }
        return productMap.get(ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(), lookup.getProductDefinitionId()));
    }

    /**
     * 2204 {@code medins_list_codg}（医药机构目录编码）。
     *
     * <p>耗材：传完整医保编码 {@code yb_no}（与短码 {@code med_list_codg} 成对）；
     * 院内已废弃 {@code yb_org_no}，禁止回落短业务码 {@code bus_no}。</p>
     * <p>药品/诊疗：仍优先 {@code yb_org_no} → {@code bus_no} → 定义主键。</p>
     *
     * @param product  产品目录投影
     * @param fullYbNo 完整医保编码（未截断）
     * @return 机构目录编码
     */
    private static String resolveMedinsListCodg(
        ProductDefinitionCatalogQueryModel product,
        String fullYbNo) {
        if (product == null) {
            return "";
        }
        // 耗材：只用完整医保编码，与 med_list_codg 短码成对
        if (SettlementTraceCountSupport.isDevice(product.getProductTableName())) {
            if (isNotEmpty(fullYbNo)) {
                return fullYbNo.trim();
            }
            return "";
        }
        if (isNotEmpty(product.getYbOrgNo())) {
            return product.getYbOrgNo().trim();
        }
        if (isNotEmpty(product.getProductBusNo())) {
            return product.getProductBusNo().trim();
        }
        if (product.getProductDefinitionId() != null) {
            return String.valueOf(product.getProductDefinitionId());
        }
        return "";
    }

    /**
     * 组装收费项缺国家医保码提示（含项目名/单据号，便于收费员定位）。
     *
     * @param item            收费项
     * @param chargeItemName  收费项定义名称（可空）
     * @param product         产品目录投影（可空）
     * @return 非空提示文案
     */
    private static String buildMissingYbNoMessage(
        ChargeItem item,
        String chargeItemName,
        ProductDefinitionCatalogQueryModel product) {
        StringBuilder sb = new StringBuilder("收费项缺少国家医保编码(yb_no)");
        String displayName = null;
        if (isNotEmpty(chargeItemName)) {
            displayName = chargeItemName.trim();
        } else if (product != null && isNotEmpty(product.getProductName())) {
            displayName = product.getProductName().trim();
        }
        if (isNotEmpty(displayName)) {
            sb.append("：").append(displayName);
        }
        if (item != null && isNotEmpty(item.getBusNo())) {
            sb.append("，单据号=").append(item.getBusNo().trim());
        }
        if (product != null && isNotEmpty(product.getProductBusNo())) {
            sb.append("，院内产品编码=").append(product.getProductBusNo().trim());
        }
        if (item != null && item.getId() != null) {
            sb.append("，chargeItemId=").append(item.getId());
        }
        return sb.toString();
    }
}
