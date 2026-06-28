/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement.support;

import com.openhis.chs.domain.outpatient.dto.OpSettleChargeLine;
import com.openhis.chs.domain.outpatient.dto.OpSettleContext;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.chs.domain.settlement.support.SettlementTraceCountSupport;
import com.openhis.health.integration.elep.ElepPrescriptionExtension;
import com.openhis.health.integration.elep.result.ElepRxNoSnapshot;
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRegistrationCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsurancePreSettleCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 HIS 主数据组装 {@link OpSettleContext}（adapter 侧 HIS→CHS 上下文桥）。
 *
 * <p>加载来源：就诊、科室/医生 yb_no、收费项、国家目录编码、诊断、追溯码数量、ELEP hi_rxno 等。
 * 缺 yb_no 时抛 INS-011，阻止无效 2204 上传。</p>
 *
 * <p>对应文档：{@code whale-doc/chs-jilin/20-场景-慢病多科室混合结算} 中 ContextLoader 职责。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class OutpatientInsuranceContextLoader {

    private final EncounterContextFacade encounterContextFacade;
    private final EncounterQueryService encounterQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final InsuranceContractResolver contractResolver;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final ObjectProvider<ElepPrescriptionExtension> elepPrescriptionExtensionProvider;

    /**
     * 单笔预结算上下文：加载 chargeItems 并组装完整 OpSettleContext。
     *
     * @param command Port 预结算命令
     */
    public OpSettleContext loadPreSettleContext(OpInsurancePreSettleCommand command) {
        List<ChargeItem> chargeItems = this.loadChargeItems(command.getChargeItemIds());
        return this.buildContext(command, chargeItems, command.getMedType(), null, null);
    }

    /**
     * 挂号/2201 登记上下文：不含收费明细，medfeeSumamt=0。
     *
     * @param command Port 登记命令
     */
    public OpSettleContext loadRegistrationContext(OpInsuranceRegistrationCommand command) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        String contractBusNo = contractResolver.requireContractBusNo(contract);
        OutpatientEncounterContextQueryModel encounter = encounterContextFacade
            .mustGetOutpatientContextByEncounterId(command.getEncounterId());

        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        Practitioner doctor = null;
        if (encounter.getDoctorId() != null) {
            doctor = practitionerQueryService.findById(encounter.getDoctorId());
        }
        DiagnosisHeader diagnosis = this.resolveFallbackDiagnosis(command.getEncounterId());
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
        String medType = YbMedType.GENERAL_OUTPATIENT.getCode();
        if (StringUtils.isNotBlank(command.getMedType())) {
            medType = command.getMedType().trim();
        }

        OpSettleContext ctx = new OpSettleContext();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(command.getEncounterId());
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
        ctx.setMdtrtCertType(command.getMdtrtCertType());
        ctx.setMdtrtCertNo(command.getMdtrtCertNo());
        ctx.setPsnNo(command.getPsnNo());
        ctx.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        ctx.setInsutype(command.getInsutype());
        ctx.setMedType(medType);
        ctx.setMainDiagCode(diagnosis.code());
        ctx.setMainDiagName(diagnosis.name());
        ctx.setChargeLines(List.of());
        ctx.setMedfeeSumamt(BigDecimal.ZERO);
        ctx.setOperatorId(operatorId);
        ctx.setTenantId(TenantUtils.getCurrentTenantId());
        // Gate：2201 必填科室/医师医保编码，禁止 blankToDash 后以 "-" 上传平台
        if (organization == null || StringUtils.isBlank(organization.getYbNo())) {
            String deptHint = organization != null ? organization.getName() : String.valueOf(encounter.getOrganizationId());
            throw new BusinessException(
                "INS-011",
                "2201 挂号科室缺少医保编码(yb_no): " + deptHint);
        }
        if (doctor == null) {
            throw new BusinessException("INS-011", "2201 医保挂号须选择接诊医师");
        }
        if (StringUtils.isBlank(doctor.getYbNo())) {
            throw new BusinessException(
                "INS-011",
                "2201 挂号医师缺少医保编码(yb_no): " + doctor.getName());
        }
        return ctx;
    }

    /**
     * 批量预结算单组上下文：使用分组后的 chargeItems 与组级 medType/diseCodg。
     *
     * @param command 组级 Port 命令
     * @param groupChargeItems 本组收费项
     * @param groupMedType 本组医疗类别
     * @param groupDiseCodg 本组病种编码（慢病）
     * @param groupDiseName 本组病种名称
     */
    public OpSettleContext loadPreSettleContextForGroup(
        OpInsurancePreSettleCommand command,
        List<ChargeItem> groupChargeItems,
        String groupMedType,
        String groupDiseCodg,
        String groupDiseName) {
        return this.buildContext(command, groupChargeItems, groupMedType, groupDiseCodg, groupDiseName);
    }

    private List<ChargeItem> loadChargeItems(List<Long> chargeItemIds) {
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != chargeItemIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不完整");
        }
        return chargeItems;
    }

    private OpSettleContext buildContext(
        OpInsurancePreSettleCommand command,
        List<ChargeItem> chargeItems,
        String groupMedType,
        String groupDiseCodg,
        String groupDiseName) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        String contractBusNo = contractResolver.requireContractBusNo(contract);
        OutpatientEncounterContextQueryModel encounter = encounterContextFacade
            .mustGetOutpatientContextByEncounterId(command.getEncounterId());

        String mainDiagCode = groupDiseCodg;
        String mainDiagName = groupDiseName;
        if (StringUtils.isBlank(mainDiagCode) || StringUtils.isBlank(mainDiagName)) {
            DiagnosisHeader fallback = this.resolveFallbackDiagnosis(command.getEncounterId());
            if (StringUtils.isBlank(mainDiagCode)) {
                mainDiagCode = fallback.code();
            }
            if (StringUtils.isBlank(mainDiagName)) {
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
        Map<String, ProductDefinitionDisplayDto> productMap = loadProducts(chargeItems, definitionMap);
        List<OpSettleChargeLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int drugTracCnt = 0;
        int mcsTracCnt = 0;
        for (ChargeItem item : chargeItems) {
            ProductDefinitionDisplayDto product = resolveProduct(item, productMap, definitionMap);
            // Gate：无国家目录编码禁止上传 2204
            if (product == null || StringUtils.isBlank(product.getYbNo())) {
                throw new BusinessException(
                    "INS-011",
                    "收费项缺少国家医保编码(yb_no): chargeItemId=" + item.getId());
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
            if (StringUtils.isNotBlank(item.getBusNo())) {
                line.setFeedetlSn(item.getBusNo().trim());
            } else {
                line.setFeedetlSn(String.valueOf(item.getId()));
            }
            line.setMedListCodg(product.getYbNo().trim());
            if (StringUtils.isNotBlank(product.getProductBusNo())) {
                line.setMedinsListCodg(product.getProductBusNo().trim());
            } else {
                line.setMedinsListCodg(String.valueOf(product.getProductDefinitionId()));
            }
            line.setDetItemFeeSumamt(item.getTotalPrice());
            line.setCnt(item.getQuantityValue());
            line.setPric(item.getUnitPrice());
            line.setFeeOcurTime(item.getOccurrenceTime());
            line.setRxno(resolve2204Rxno(item.getPrescriptionNo()));
            if (StringUtils.isNotBlank(groupDiseCodg)) {
                line.setDiseCodg(groupDiseCodg);
            } else if (StringUtils.isNotBlank(mainDiagCode)) {
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

        String medType = YbMedType.GENERAL_OUTPATIENT.getCode();
        if (StringUtils.isNotBlank(groupMedType)) {
            medType = groupMedType.trim();
        }

        OpSettleContext ctx = new OpSettleContext();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(command.getEncounterId());
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
        ctx.setMdtrtCertType(command.getMdtrtCertType());
        ctx.setMdtrtCertNo(command.getMdtrtCertNo());
        ctx.setPsnNo(command.getPsnNo());
        ctx.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        ctx.setInsutype(command.getInsutype());
        ctx.setMedType(medType);
        ctx.setMainDiagCode(mainDiagCode);
        ctx.setMainDiagName(mainDiagName);
        ctx.setChargeLines(lines);
        ctx.setMedfeeSumamt(total);
        ctx.setPaymentDraftId(command.getPaymentDraftId());
        ctx.setOperatorId(operatorId);
        ctx.setTenantId(TenantUtils.getCurrentTenantId());
        if (StringUtils.isNotBlank(command.getAcctUsedFlag())) {
            ctx.setAcctUsedFlag(command.getAcctUsedFlag().trim());
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

    private DiagnosisHeader resolveFallbackDiagnosis(Long encounterId) {
        InsuranceDiagnosisCodeSupport.MainDiagnosis diagnosis = InsuranceDiagnosisCodeSupport.resolveMainDiagnosis(
            encounterId,
            encounterQueryService,
            conditionQueryService,
            conditionDefinitionQueryService);
        return new DiagnosisHeader(diagnosis.code(), diagnosis.name());
    }

    private record DiagnosisHeader(String code, String name) {
    }

    /**
     * 2204.rxno：仅当 ELEP 已上传成功时填 {@code hi_rxno}；无电子处方时留空，禁止用院内处方号伪造。
     *
     * @param prescriptionNo 院内处方号（关联 {@code med_medication_request}）
     * @return 平台 hi_rxno，或 {@code null} 表示非电子处方明细
     */
    private String resolve2204Rxno(String prescriptionNo) {
        if (StringUtils.isBlank(prescriptionNo)) {
            return null;
        }
        ElepPrescriptionExtension elepExtension = elepPrescriptionExtensionProvider.getIfAvailable();
        // ELEP 未装配或未启用：2204 不得携带 rxno，避免医保按电子处方核验
        if (elepExtension == null || !elepExtension.isEnabled()) {
            return null;
        }
        Optional<String> hiRxno = elepExtension.findRxNoByPrescriptionNo(prescriptionNo.trim())
                .map(ElepRxNoSnapshot::getHiRxno)
                .filter(StringUtils::isNotBlank);
        // 未找到已上传的 hi_rxno：留空，禁止回退院内处方号
        if (hiRxno.isEmpty()) {
            return null;
        }
        return hiRxno.get();
    }

    private Map<String, ProductDefinitionDisplayDto> loadProducts(
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
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    private ProductDefinitionBatchLookup resolveCatalogLookup(ChargeItem row, ChargeItemDefinition definition) {
        if (row.getProductId() != null && StringUtils.isNotBlank(row.getProductTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(row.getProductId());
            lookup.setProductTableName(row.getProductTable().trim());
            return lookup;
        }
        if (definition != null && definition.getInstanceId() != null && StringUtils.isNotBlank(definition.getInstanceTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(definition.getInstanceId());
            lookup.setProductTableName(definition.getInstanceTable().trim());
            return lookup;
        }
        return null;
    }

    private Map<Long, ChargeItemDefinition> buildDefinitionMap(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getDefinitionId() != null) {
                definitionIds.add(item.getDefinitionId());
            }
        }
        return chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    private ProductDefinitionDisplayDto resolveProduct(
        ChargeItem item,
        Map<String, ProductDefinitionDisplayDto> productMap,
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
