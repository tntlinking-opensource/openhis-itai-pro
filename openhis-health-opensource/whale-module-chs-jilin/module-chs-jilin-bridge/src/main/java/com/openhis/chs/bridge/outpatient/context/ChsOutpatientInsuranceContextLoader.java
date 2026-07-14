/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.context;

import com.openhis.chs.domain.outpatient.command.OpPlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpSettleChargeLine;
import com.openhis.chs.domain.outpatient.loader.OpPreSettleContextLoader;
import com.openhis.chs.domain.settlement.support.SettlementTraceCountSupport;
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
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.infrastructure.integration.elep.ElepPrescriptionExtension;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepRxNoByPrescriptionNoExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepRxNoQueryExtensionOut;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.outpatient.assembler.InsuranceDiagnosisCodeAssembler;
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
 * <p>缺少 yb_no 时抛 INS-011，阻止无编码的 2204 上传。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsOutpatientInsuranceContextLoader implements OpPreSettleContextLoader {

    private final EncounterContextQueryService encounterContextQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionQueryService productDefinitionQueryService;
    private final ChsInsuranceContractReaderImpl contractReader;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final ObjectProvider<ElepPrescriptionExtension> elepPrescriptionExtensionProvider;

    /**
     * 单笔预结算上下文：加载 chargeItems 并组装完整 {@link OpPreSettleCommand}。
     *
     * @param partial 调用方传入的预结算部分字段（含 chargeItemIds）
     * @return 含收费明细、诊断、追溯码计数的完整预结算上下文
     */
    @Override
    public OpPreSettleCommand enrichPreSettle(OpPreSettleCommand partial) {
        List<ChargeItem> chargeItems = this.loadChargeItems(partial.getChargeItemIds());
        return this.buildContext(partial, chargeItems, partial.getMedType(), null, null);
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
        Practitioner doctor = null;
        if (encounter.getDoctorId() != null) {
            doctor = practitionerQueryService.findById(encounter.getDoctorId());
        }
        DiagnosisHeader diagnosis = this.resolveFallbackDiagnosis(partial.getEncounterId());
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
        String medType = MedType.GENERAL_OUTPATIENT.getCode();
        if (isNotEmpty(partial.getMedType())) {
            medType = partial.getMedType().trim();
        }

        OpPreSettleCommand ctx = new OpPreSettleCommand();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(partial.getEncounterId());
        ctx.setEncounterBusNo(encounter.getEncounterBusNo());
        ctx.setPatientId(encounter.getPatientId());
        ctx.setPatientName(encounter.getPatientName());
        ctx.setPatientCertNo(encounter.getIdCard());
        ctx.setPatientGender(encounter.getGenderEnum());
        ctx.setEncounterStartTime(encounter.getStartTime());
        ctx.setOrganizationId(encounter.getOrganizationId());
        if (organization != null) {
            ctx.setDeptCode(organization.getYbNo());
            ctx.setDeptName(organization.getName());
            ctx.setCaty(organization.getYbNo());
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
        ctx.setMainDiagCode(diagnosis.code());
        ctx.setMainDiagName(diagnosis.name());
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
        if (doctor == null) {
            throw new BusinessException("INS-011", "2201 医保挂号须选择接诊医师");
        }
        if (isEmpty(doctor.getYbNo())) {
            throw new BusinessException(
                "INS-011",
                "2201 挂号医师缺少医保编码(yb_no): " + doctor.getName());
        }
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
     * 按 ID 批量加载收费项，数量与请求不一致时抛错。
     *
     * @param chargeItemIds 收费项 ID 列表
     * @return 与请求 ID 一一对应的收费项实体
     */
    private List<ChargeItem> loadChargeItems(List<Long> chargeItemIds) {
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != chargeItemIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不完整");
        }
        return chargeItems;
    }

    /**
     * 核心组装：契约、就诊、收费明细行、诊断、追溯码计数、操作员等写入 {@link OpPreSettleCommand}。
     *
     * @param partial 调用方已填写的参保人/凭证等字段
     * @param chargeItems 本组收费项（单笔或批量分组）
     * @param groupMedType 医疗类别；为空时用普通门诊
     * @param groupDiseCodg 慢病病种编码；为空时回退主诊断
     * @param groupDiseName 慢病病种名称；为空时回退主诊断
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

        String mainDiagCode = groupDiseCodg;
        String mainDiagName = groupDiseName;
        if (isEmpty(mainDiagCode) || isEmpty(mainDiagName)) {
            DiagnosisHeader fallback = this.resolveFallbackDiagnosis(partial.getEncounterId());
            if (isEmpty(mainDiagCode)) {
                mainDiagCode = fallback.code();
            }
            if (isEmpty(mainDiagName)) {
                mainDiagName = fallback.name();
            }
        }

        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        Practitioner doctor = null;
        if (encounter.getDoctorId() != null) {
            doctor = practitionerQueryService.findById(encounter.getDoctorId());
        }

        Map<Long, ChargeItemDefinition> definitionMap = buildDefinitionMap(chargeItems);
        Map<String, ProductDefinitionCatalogQueryModel> productMap = loadProducts(chargeItems, definitionMap);
        List<OpSettleChargeLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int drugTracCnt = 0;
        int mcsTracCnt = 0;
        for (ChargeItem item : chargeItems) {
            ProductDefinitionCatalogQueryModel product = resolveProduct(item, productMap, definitionMap);
            // Gate：无国家目录编码禁止上传 2204
            if (product == null || isEmpty(product.getYbNo())) {
                throw new BusinessException(
                    "INS-011",
                    "收费项缺少国家医保编yb_no): chargeItemId=" + item.getId());
            }
            ChargeItemDefinition definition = null;
            if (item.getDefinitionId() != null) {
                definition = definitionMap.get(item.getDefinitionId());
            }
            ProductDefinitionBatchLookup catalogLookup = resolveCatalogLookup(item, definition);
            if (catalogLookup != null) {
                String productTable = catalogLookup.getProductTableName();
                drugTracCnt += SettlementTraceCountSupport.countDrugTracUnits(
                    productTable, item.getQuantityValue());
                mcsTracCnt += SettlementTraceCountSupport.countMcsTracUnits(
                    productTable, item.getQuantityValue());
            }
            OpSettleChargeLine line = new OpSettleChargeLine();
            line.setChargeItemId(item.getId());
            if (isNotEmpty(item.getBusNo())) {
                line.setFeedetlSn(item.getBusNo().trim());
            } else {
                line.setFeedetlSn(String.valueOf(item.getId()));
            }
            line.setMedListCodg(product.getYbNo().trim());
            if (isNotEmpty(product.getProductBusNo())) {
                line.setMedinsListCodg(product.getProductBusNo().trim());
            } else {
                line.setMedinsListCodg(String.valueOf(product.getProductDefinitionId()));
            }
            line.setDetItemFeeSumamt(item.getTotalPrice());
            line.setCnt(item.getQuantityValue());
            line.setPric(item.getUnitPrice());
            line.setFeeOcurTime(item.getOccurrenceTime());
            line.setRxno(resolve2204Rxno(item.getPrescriptionNo()));
            if (isNotEmpty(groupDiseCodg)) {
                line.setDiseCodg(groupDiseCodg);
            } else if (isNotEmpty(mainDiagCode)) {
                line.setDiseCodg(mainDiagCode);
            }
            if (organization != null) {
                line.setBilgDeptCodg(organization.getYbNo());
                line.setBilgDeptName(organization.getName());
            }
            if (doctor != null) {
                line.setBilgDrCodg(doctor.getYbNo());
                line.setBilgDrName(doctor.getName());
            }
            lines.add(line);
            total = total.add(item.getTotalPrice());
        }

        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");

        String medType = MedType.GENERAL_OUTPATIENT.getCode();
        if (isNotEmpty(groupMedType)) {
            medType = groupMedType.trim();
        }

        OpPreSettleCommand ctx = new OpPreSettleCommand();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(partial.getEncounterId());
        ctx.setEncounterBusNo(encounter.getEncounterBusNo());
        ctx.setPatientId(encounter.getPatientId());
        ctx.setPatientName(encounter.getPatientName());
        ctx.setPatientCertNo(encounter.getIdCard());
        ctx.setPatientGender(encounter.getGenderEnum());
        ctx.setEncounterStartTime(encounter.getStartTime());
        ctx.setOrganizationId(encounter.getOrganizationId());
        if (organization != null) {
            ctx.setDeptCode(organization.getYbNo());
            ctx.setDeptName(organization.getName());
            ctx.setCaty(organization.getYbNo());
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
        ctx.setMainDiagCode(mainDiagCode);
        ctx.setMainDiagName(mainDiagName);
        ctx.setChargeLines(lines);
        ctx.setMedfeeSumamt(total);
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
        return ctx;
    }

    /**
     * 从就诊诊断链解析主诊断，供 2201/2203 及无慢病编码时的 2204 使用。
     *
     * @param encounterId 就诊 ID
     * @return 主诊断编码与名称
     */
    private DiagnosisHeader resolveFallbackDiagnosis(Long encounterId) {
        InsuranceDiagnosisCodeAssembler.MainDiagnosis diagnosis = InsuranceDiagnosisCodeAssembler.resolveMainDiagnosis(
            encounterId,
            encounterQueryService,
            conditionQueryService,
            conditionDefinitionQueryService);
        return new DiagnosisHeader(diagnosis.code(), diagnosis.name());
    }

    /** 主诊断编码与名称的内部载体。 */
    private record DiagnosisHeader(String code, String name) {
    }

    /**
     * 2204.rxno：仅传 ELEP 已上传成功时{@code hi_rxno}；无电子处方时留空，禁止用院内处方号伪造。
     *
     * @param prescriptionNo 院内处方号（关联 {@code med_medication_request}）
     * @return 平台 hi_rxno，或 {@code null} 表示无电子处方明细
     */
    private String resolve2204Rxno(String prescriptionNo) {
        if (isEmpty(prescriptionNo)) {
            return null;
        }
        ElepPrescriptionExtension elepExtension = elepPrescriptionExtensionProvider.getIfAvailable();
        // ELEP 未装配或未启用：2204 不得携带 rxno，避免医保按电子处方核验
        if (elepExtension == null || !elepExtension.isEnabled()) {
            return null;
        }
        Optional<String> hiRxno = elepExtension.findRxNoByPrescriptionNo(
                        new ElepRxNoByPrescriptionNoExtensionIn().setPrescriptionNo(prescriptionNo.trim()))
                .map(ElepRxNoQueryExtensionOut::getHiRxno)
                .filter(StringUtils::isNotEmpty);
        // 未找到已上传 hi_rxno：留空，禁止回退院内处方
        if (hiRxno.isEmpty()) {
            return null;
        }
        return hiRxno.get();
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
}
