/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement.support;

import com.openhis.chs.domain.catalog.port.ChsContractPropertiesPort;
import com.openhis.chs.domain.inpatient.dto.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.dto.IpEncounterPlatformCommand;
import com.openhis.chs.domain.inpatient.dto.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.dto.IpSettleContext;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.chs.domain.settlement.support.SettlementTraceCountSupport;
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceAdmissionCommand;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceEncounterCommand;
import com.openhis.whale.chs.jilin.contracts.command.IpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 从 HIS 主数据组装 {@link IpSettleContext}（住院 adapter 侧上下文桥）。
 *
 * <p>与门诊 {@link OutpatientInsuranceContextLoader} 对称：加载就诊、科室/医生 yb_no、
 * 收费项国家目录、诊断、追溯码、就医地 adm_vs 等。2404/2302 等撤销交易从
 * {@code yb_encounter_platform_map} 反查 mdtrt_id。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InpatientInsuranceContextLoader {

    private final EncounterContextFacade encounterContextFacade;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final InsuranceContractResolver contractResolver;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final ChsContractPropertiesPort contractPropertiesPort;
    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;

    /**
     * 住院预结算上下文：加载 chargeItems 并组装 IpSettleContext（含 settleMode）。
     *
     * @param command Port 预结算命令
     */
    public IpSettleContext loadPreSettleContext(IpInsurancePreSettleCommand command) {
        List<ChargeItem> chargeItems = this.loadChargeItems(command.getChargeItemIds());
        return this.buildContext(command, chargeItems);
    }

    /**
     * 入院/2403 变更上下文：不含收费明细，medfeeSumamt=0。
     *
     * @param command Port 入院命令
     */
    public IpSettleContext loadAdmissionContext(IpInsuranceAdmissionCommand command) {
        return this.buildAdmissionContext(command);
    }

    /**
     * 2404/2405 撤销用：从 yb_encounter_platform_map 解析 mdtrt_id、psn_no、contractBusNo。
     *
     * @param command 含 encounterId、contractId
     * @return 平台撤销命令（domain DTO）
     * @throws BusinessException 未找到医保就诊映射
     */
    public IpEncounterPlatformCommand loadEncounterPlatformCommand(IpInsuranceEncounterCommand command) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        String contractBusNo = contractResolver.requireContractBusNo(contract);
        Long tenantId = TenantUtils.getCurrentTenantId();
        // 住院默认 med_type=21（普通住院），与 2401 登记时一致
        String medType = YbMedType.GENERAL_HOSPITALIZATION.getCode();
        YbEncounterPlatformMap mapped = encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                command.getEncounterId(), command.getContractId(), medType, tenantId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "未找到医保就诊映射，无法撤销"));
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "当前会话操作人不能为空");
        IpEncounterPlatformCommand domain = new IpEncounterPlatformCommand();
        domain.setEncounterId(command.getEncounterId());
        domain.setContractId(command.getContractId());
        domain.setContractBusNo(contractBusNo);
        domain.setMdtrtId(mapped.getMdtrtId());
        domain.setMedType(mapped.getMedType());
        domain.setPsnNo(mapped.getPsnNo());
        domain.setOperatorId(operatorId);
        domain.setTenantId(tenantId);
        return domain;
    }

    /**
     * 2302 撤销费用明细：先解析平台就诊映射，再附带 chargeItemIds。
     *
     * @param command Port 撤销命令
     */
    public IpRevokeFeeDetailCommand loadRevokeFeeDetailCommand(IpInsuranceRevokeFeeDetailCommand command) {
        IpEncounterPlatformCommand platform = loadEncounterPlatformCommand(
            new IpInsuranceEncounterCommand()
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId()));
        IpRevokeFeeDetailCommand domain = new IpRevokeFeeDetailCommand();
        domain.setEncounterId(platform.getEncounterId());
        domain.setContractId(platform.getContractId());
        domain.setContractBusNo(platform.getContractBusNo());
        domain.setMdtrtId(platform.getMdtrtId());
        domain.setPsnNo(platform.getPsnNo());
        domain.setOperatorId(platform.getOperatorId());
        domain.setTenantId(platform.getTenantId());
        domain.setChargeItemIds(command.getChargeItemIds());
        return domain;
    }

    private IpSettleContext buildAdmissionContext(IpInsuranceAdmissionCommand command) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        String contractBusNo = contractResolver.requireContractBusNo(contract);
        InpatientEncounterContextQueryModel encounter = encounterContextFacade
            .mustGetInpatientContextByEncounterId(command.getEncounterId());
        DiagnosisHeader diagnosis = this.resolveDiagnosis(command.getEncounterId());
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
        String medType = YbMedType.GENERAL_HOSPITALIZATION.getCode();
        if (StringUtils.isNotBlank(command.getMedType())) {
            medType = command.getMedType().trim();
        }
        String mdtrtareaAdmvs = contractPropertiesPort.findAdmVs(contractBusNo).orElse(null);

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
        if (doctor != null) {
            ctx.setAtddrNo(doctor.getYbNo());
            ctx.setChfpdrName(doctor.getName());
        }
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

    private List<ChargeItem> loadChargeItems(List<Long> chargeItemIds) {
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != chargeItemIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不完整");
        }
        return chargeItems;
    }

    private IpSettleContext buildContext(IpInsurancePreSettleCommand command, List<ChargeItem> chargeItems) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        String contractBusNo = contractResolver.requireContractBusNo(contract);
        InpatientEncounterContextQueryModel encounter = encounterContextFacade
            .mustGetInpatientContextByEncounterId(command.getEncounterId());

        DiagnosisHeader diagnosis = this.resolveDiagnosis(command.getEncounterId());
        Organization organization = null;
        if (encounter.getOrganizationId() != null) {
            organization = organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId());
        }
        Practitioner doctor = null;
        if (encounter.getAttendingDoctorId() != null) {
            doctor = practitionerQueryService.findById(encounter.getAttendingDoctorId());
        }

        Map<Long, ChargeItemDefinition> definitionMap = buildDefinitionMap(chargeItems);
        Map<String, ProductDefinitionDisplayDto> productMap = loadProducts(chargeItems, definitionMap);
        List<IpSettleChargeLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int drugTracCnt = 0;
        int mcsTracCnt = 0;
        for (ChargeItem item : chargeItems) {
            ProductDefinitionDisplayDto product = resolveProduct(item, productMap, definitionMap);
            // Gate：无 yb_no 禁止 2301 上传
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
            IpSettleChargeLine line = new IpSettleChargeLine();
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
            if (StringUtils.isNotBlank(item.getPrescriptionNo())) {
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

        String medType = YbMedType.GENERAL_HOSPITALIZATION.getCode();
        if (StringUtils.isNotBlank(command.getMedType())) {
            medType = command.getMedType().trim();
        }

        String mdtrtareaAdmvs = contractPropertiesPort.findAdmVs(contractBusNo).orElse(null);

        IpSettleContext ctx = new IpSettleContext();
        ctx.setContractId(contract.getId());
        ctx.setContractBusNo(contractBusNo);
        ctx.setEncounterId(command.getEncounterId());
        ctx.setEncounterBusNo(encounter.getEncounterBusNo());
        ctx.setPatientId(encounter.getPatientId());
        ctx.setAdmissionTime(encounter.getStartTime());
        ctx.setDischargeTime(encounter.getEndTime());
        ctx.setSettleMode(command.getSettleMode());
        if (organization != null) {
            ctx.setAdmDeptCodg(organization.getYbNo());
            ctx.setAdmDeptName(organization.getName());
            ctx.setDscgDeptCodg(organization.getYbNo());
            ctx.setDscgDeptName(organization.getName());
        }
        ctx.setAdmBed(encounter.getBedName());
        ctx.setDscgBed(encounter.getBedName());
        ctx.setDscgWay(encounter.getOutWayCode());
        if (doctor != null) {
            ctx.setAtddrNo(doctor.getYbNo());
            ctx.setChfpdrName(doctor.getName());
        }
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
        ctx.setChargeLines(lines);
        ctx.setMedfeeSumamt(total);
        ctx.setPaymentDraftId(command.getPaymentDraftId());
        ctx.setOperatorId(operatorId);
        ctx.setTenantId(TenantUtils.getCurrentTenantId());
        ctx.setAcctUsedFlag(command.getAcctUsedFlag());
        ctx.setPsnSetlway(command.getPsnSetlway());
        ctx.setMinpacuntDrugTracCnt(drugTracCnt);
        ctx.setMcsTracCnt(mcsTracCnt);
        return ctx;
    }

    private DiagnosisHeader resolveDiagnosis(Long encounterId) {
        List<EncounterDiagnosisQueryModel> diagnoses = conditionQueryService.getEncounterDiagnosis(encounterId);
        if (diagnoses.isEmpty()) {
            return new DiagnosisHeader(null, null);
        }
        EncounterDiagnosisQueryModel first = diagnoses.get(0);
        String mainDiagCode = null;
        String mainDiagName = null;
        if (first.getDefinitionId() != null) {
            List<ConditionDefinitionBatchItemQueryModel> defs =
                conditionDefinitionQueryService.getConditionDefinitionListByIds(List.of(first.getDefinitionId()));
            if (!defs.isEmpty()) {
                mainDiagCode = defs.get(0).getConditionCode();
                mainDiagName = defs.get(0).getName();
            }
        }
        if (StringUtils.isBlank(mainDiagName)) {
            mainDiagName = first.getDescription();
        }
        return new DiagnosisHeader(mainDiagCode, mainDiagName);
    }

    private record DiagnosisHeader(String code, String name) {
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
