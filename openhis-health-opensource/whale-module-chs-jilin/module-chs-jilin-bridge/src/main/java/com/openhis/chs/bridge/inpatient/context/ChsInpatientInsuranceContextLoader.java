/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.context;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.domain.inpatient.command.IpAdmissionCommand;
import com.openhis.chs.domain.inpatient.command.IpEncounterCommand;
import com.openhis.chs.domain.inpatient.command.IpEncounterPlatformCommand;
import com.openhis.chs.domain.inpatient.command.IpPreSettleCommand;
import com.openhis.chs.domain.inpatient.command.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.loader.IpSettleContextLoader;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.domain.settlement.support.SettlementTraceCountSupport;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
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
import com.openhis.health.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.outpatient.assembler.InsuranceDiagnosisCodeAssembler;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * HIS 主数据组装 {@link IpSettleContext}（住院 bridge HIS→CHS 上下文加载）。
 *
 * <p>加载范围：就诊、科室/医师 yb_no、收费项、国家目录编码、诊断、追溯码数量、契约行政区划等；
 * 依赖 health-domain Encounter/ChargeItem/ProductDefinition/Condition 等查询服务；
 * 实现 {@link IpSettleContextLoader}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInpatientInsuranceContextLoader implements IpSettleContextLoader {

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
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;

    /** 单笔预结算上下文：加载收费项并组装完整 {@link IpSettleContext}。 */
    @Override
    public IpSettleContext enrichPreSettle(IpPreSettleCommand command) {
        List<ChargeItem> chargeItems = loadChargeItems(command.getChargeItemIds());
        return buildContext(command, chargeItems);
    }

    /** 入院登记上下文：不含收费明细，medfeeSumamt=0。 */
    @Override
    public IpSettleContext enrichAdmission(IpAdmissionCommand command) {
        return buildAdmissionContext(command);
    }

    /** 平台就诊撤销上下文：从 yb_encounter_platform_map 解析 mdtrt_id/psn_no。 */
    @Override
    public IpEncounterPlatformCommand enrichEncounterPlatform(IpEncounterCommand command) {
        Contract contract = contractReader.requireContractEntity(null, command.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        Long tenantId = TenantUtils.getCurrentTenantId();
        String medType = MedType.GENERAL_HOSPITALIZATION.getCode();
        YbEncounterPlatformMap mapped = encounterPlatformMapQueryService
            .findOptionalByEncounterContractMedType(
                command.getEncounterId(), command.getContractId(), medType, tenantId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "未找到医保就诊映射，无法撤销"));
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
        return new IpEncounterPlatformCommand()
            .setEncounterId(command.getEncounterId())
            .setContractId(command.getContractId())
            .setContractBusNo(contractBusNo)
            .setMdtrtId(mapped.getMdtrtId())
            .setMedType(mapped.getMedType())
            .setPsnNo(mapped.getPsnNo())
            .setOperatorId(operatorId)
            .setTenantId(tenantId);
    }

    /** 费用明细撤销上下文：复用 enrichEncounterPlatform 补全平台字段。 */
    @Override
    public IpRevokeFeeDetailCommand enrichRevokeFeeDetail(IpRevokeFeeDetailCommand command) {
        IpEncounterPlatformCommand platform = enrichEncounterPlatform(
            new IpEncounterCommand()
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId()));
        return new IpRevokeFeeDetailCommand()
            .setEncounterId(platform.getEncounterId())
            .setContractId(platform.getContractId())
            .setContractBusNo(platform.getContractBusNo())
            .setMdtrtId(platform.getMdtrtId())
            .setPsnNo(platform.getPsnNo())
            .setOperatorId(platform.getOperatorId())
            .setTenantId(platform.getTenantId())
            .setChargeItemIds(command.getChargeItemIds());
    }

    /**
     * 组装入院登记上下文：不含收费明细，medfeeSumamt=0。
     *
     * @param command 调用方传入的入院登记部分字段
     * @return 可供 2401 使用的完整入院上下文
     */
    private IpSettleContext buildAdmissionContext(IpAdmissionCommand command) {
        Contract contract = contractReader.requireContractEntity(null, command.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        InpatientEncounterContextQueryModel encounter = encounterContextQueryService
            .mustGetInpatientContextByEncounterId(command.getEncounterId());
        DiagnosisHeader diagnosis = resolveDiagnosis(command.getEncounterId());
        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        Practitioner doctor = null;
        if (encounter.getAttendingDoctorId() != null) {
            doctor = practitionerQueryService.findById(encounter.getAttendingDoctorId());
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
        String medType = MedType.GENERAL_HOSPITALIZATION.getCode();
        if (isNotEmpty(command.getMedType())) {
            medType = command.getMedType().trim();
        }
        String mdtrtareaAdmvs = contractPropertiesAccess.findAdmVs(contractBusNo).orElse(null);

        IpSettleContext ctx = new IpSettleContext();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(command.getEncounterId());
        ctx.setEncounterBusNo(encounter.getEncounterBusNo());
        ctx.setPatientId(encounter.getPatientId());
        ctx.setAdmissionTime(encounter.getStartTime());
        ctx.setDischargeTime(encounter.getEndTime());
        if (organization != null) {
            ctx.setAdmDeptCodg(organization.getYbNo());
            ctx.setAdmDeptName(organization.getName());
            ctx.setDscgDeptCodg(organization.getYbNo());
            ctx.setDscgDeptName(organization.getName());
        }
        ctx.setAdmBed(encounter.getBedName());
        ctx.setDscgBed(encounter.getBedName());
        ctx.setDscgWay(encounter.getOutWayCode());
        if (doctor == null) {
            throw new BusinessException("INS-011", "2401 医保入院须选择主治医生");
        }
        if (isEmpty(doctor.getYbNo())) {
            throw new BusinessException("INS-011", "2401 主治医生缺少医保编码(yb_no): " + doctor.getName());
        }
        ctx.setAtddrNo(doctor.getYbNo());
        ctx.setChfpdrName(doctor.getName());
        ctx.setMainDiagCode(diagnosis.code());
        ctx.setMainDiagName(diagnosis.name());
        ctx.setAdmDiagDscr(diagnosis.name());
        ctx.setMdtrtCertType(command.getMdtrtCertType());
        ctx.setMdtrtCertNo(command.getMdtrtCertNo());
        ctx.setPsnNo(command.getPsnNo());
        ctx.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        ctx.setInsutype(command.getInsutype());
        ctx.setMedType(medType);
        ctx.setMdtrtareaAdmvs(mdtrtareaAdmvs);
        ctx.setChargeLines(List.of());
        ctx.setMedfeeSumamt(BigDecimal.ZERO);
        ctx.setOperatorId(operatorId);
        ctx.setTenantId(TenantUtils.getCurrentTenantId());
        return ctx;
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
     * 核心组装：契约、就诊、收费明细行、诊断、追溯码计数、操作员等写入 {@link IpSettleContext}。
     *
     * @param command 调用方已填写的参保人/凭证等字段
     * @param chargeItems 本组收费项
     * @return 可供 2301/2303/2304 使用的完整上下文
     */
    private IpSettleContext buildContext(IpPreSettleCommand command, List<ChargeItem> chargeItems) {
        Contract contract = contractReader.requireContractEntity(null, command.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        InpatientEncounterContextQueryModel encounter = encounterContextQueryService
            .mustGetInpatientContextByEncounterId(command.getEncounterId());

        DiagnosisHeader diagnosis = resolveDiagnosis(command.getEncounterId());
        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        Practitioner doctor = null;
        if (encounter.getAttendingDoctorId() != null) {
            doctor = practitionerQueryService.findById(encounter.getAttendingDoctorId());
        }

        Map<Long, ChargeItemDefinition> definitionMap = buildDefinitionMap(chargeItems);
        Map<String, ProductDefinitionCatalogQueryModel> productMap = loadProducts(chargeItems, definitionMap);
        List<IpSettleChargeLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int drugTracCnt = 0;
        int mcsTracCnt = 0;
        for (ChargeItem item : chargeItems) {
            ProductDefinitionCatalogQueryModel product = resolveProduct(item, productMap, definitionMap);
            if (product == null || isEmpty(product.getYbNo())) {
                throw new BusinessException("INS-011", "收费项缺少国家医保编yb_no): chargeItemId=" + item.getId());
            }
            ChargeItemDefinition definition = null;
            if (item.getDefinitionId() != null) {
                definition = definitionMap.get(item.getDefinitionId());
            }
            ProductDefinitionBatchLookup catalogLookup = resolveCatalogLookup(item, definition);
            if (catalogLookup != null) {
                String productTable = catalogLookup.getProductTableName();
                drugTracCnt += SettlementTraceCountSupport.countDrugTracUnits(productTable, item.getQuantityValue());
                mcsTracCnt += SettlementTraceCountSupport.countMcsTracUnits(productTable, item.getQuantityValue());
            }
            IpSettleChargeLine line = new IpSettleChargeLine();
            line.setChargeItemId(item.getId());
            String feedetlSn;
            if (isNotEmpty(item.getBusNo())) {
                feedetlSn = item.getBusNo().trim();
            } else {
                feedetlSn = String.valueOf(item.getId());
            }
            line.setFeedetlSn(feedetlSn);
            line.setMedListCodg(product.getYbNo().trim());
            String medinsListCodg;
            if (isNotEmpty(product.getProductBusNo())) {
                medinsListCodg = product.getProductBusNo().trim();
            } else {
                medinsListCodg = String.valueOf(product.getProductDefinitionId());
            }
            line.setMedinsListCodg(medinsListCodg);
            line.setDetItemFeeSumamt(item.getTotalPrice());
            line.setCnt(item.getQuantityValue());
            line.setPric(item.getUnitPrice());
            line.setFeeOcurTime(item.getOccurrenceTime());
            if (isNotEmpty(item.getPrescriptionNo())) {
                line.setDrordNo(item.getPrescriptionNo().trim());
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
        String medType = MedType.GENERAL_HOSPITALIZATION.getCode();
        if (isNotEmpty(command.getMedType())) {
            medType = command.getMedType().trim();
        }
        String mdtrtareaAdmvs = contractPropertiesAccess.findAdmVs(contractBusNo).orElse(null);

        String admDeptCodg = null;
        String admDeptName = null;
        String dscgDeptCodg = null;
        String dscgDeptName = null;
        if (organization != null) {
            admDeptCodg = organization.getYbNo();
            admDeptName = organization.getName();
            dscgDeptCodg = organization.getYbNo();
            dscgDeptName = organization.getName();
        }
        String atddrNo = null;
        String chfpdrName = null;
        if (doctor != null) {
            atddrNo = doctor.getYbNo();
            chfpdrName = doctor.getName();
        }

        return new IpSettleContext()
            .setContractId(contract.getId())
            .setContractBusNo(contractBusNo)
            .setEncounterId(command.getEncounterId())
            .setEncounterBusNo(encounter.getEncounterBusNo())
            .setPatientId(encounter.getPatientId())
            .setAdmissionTime(encounter.getStartTime())
            .setDischargeTime(encounter.getEndTime())
            .setSettleMode(command.getSettleMode())
            .setAdmDeptCodg(admDeptCodg)
            .setAdmDeptName(admDeptName)
            .setDscgDeptCodg(dscgDeptCodg)
            .setDscgDeptName(dscgDeptName)
            .setAdmBed(encounter.getBedName())
            .setDscgBed(encounter.getBedName())
            .setDscgWay(encounter.getOutWayCode())
            .setAtddrNo(atddrNo)
            .setChfpdrName(chfpdrName)
            .setMainDiagCode(diagnosis.code())
            .setMainDiagName(diagnosis.name())
            .setAdmDiagDscr(diagnosis.name())
            .setMdtrtCertType(command.getMdtrtCertType())
            .setMdtrtCertNo(command.getMdtrtCertNo())
            .setPsnNo(command.getPsnNo())
            .setInsuplcAdmdvs(command.getInsuplcAdmdvs())
            .setInsutype(command.getInsutype())
            .setMedType(medType)
            .setMdtrtareaAdmvs(mdtrtareaAdmvs)
            .setChargeLines(lines)
            .setMedfeeSumamt(total)
            .setPaymentDraftId(command.getPaymentDraftId())
            .setOperatorId(operatorId)
            .setTenantId(TenantUtils.getCurrentTenantId())
            .setAcctUsedFlag(command.getAcctUsedFlag())
            .setPsnSetlway(command.getPsnSetlway())
            .setMinpacuntDrugTracCnt(drugTracCnt)
            .setMcsTracCnt(mcsTracCnt);
    }

    /**
     * 从就诊诊断链解析主诊断，供 2401/2303 及费用明细上传使用。
     *
     * @param encounterId 就诊 ID
     * @return 主诊断编码与名称
     */
    private DiagnosisHeader resolveDiagnosis(Long encounterId) {
        InsuranceDiagnosisCodeAssembler.MainDiagnosis diagnosis = InsuranceDiagnosisCodeAssembler.resolveMainDiagnosis(
            encounterId, encounterQueryService, conditionQueryService, conditionDefinitionQueryService);
        return new DiagnosisHeader(diagnosis.code(), diagnosis.name());
    }

    /** 主诊断编码与名称的内部载体。 */
    private record DiagnosisHeader(String code, String name) {
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
