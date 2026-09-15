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
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.chs.domain.share.constants.ChsDetailAuditConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.bridge.notice.CatalogNoticeComplianceAssertSupport;
import com.openhis.chs.bridge.outpatient.assembler.InsuranceDiagnosisCodeAssembler;
import com.openhis.chs.bridge.settlement.support.ChsActivityPackageFeeExpandSupport;
import com.openhis.chs.bridge.settlement.support.ChsFeeRefundInitFeedetlSupport;
import com.openhis.chs.bridge.settlement.support.ChsFeeUploadSlice;
import com.openhis.chs.bridge.settlement.support.ChsSettlementDrugTraceRatioResolver;
import com.openhis.chs.bridge.settlement.support.SettlementFeeUnitConvertSupport;
import com.openhis.chs.bridge.settlement.support.TcmdrugUsedWayResolver;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemLookup;
import com.openhis.chs.domain.catalog.support.NationalCatalogPackHint;
import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.openhis.chs.bridge.settlement.support.SettlementRetailUploadDiffSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HIS 主数据组装 {@link IpSettleContext}（住院 bridge HIS→CHS 上下文加载）。
 *
 * <p>加载范围：就诊、患者姓名/性别/出生日期（3101 patient_dtos）、科室/医师 yb_no、收费项、
 * 国家目录编码、诊断、追溯码数量、契约行政区划等；
 * 依赖 health-domain Encounter/ChargeItem/ProductDefinition/Condition 等查询服务；
 * 实现 {@link IpSettleContextLoader}。
 * 诊疗套餐有子项时经 {@link ChsActivityPackageFeeExpandSupport} 只上传子项行。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInpatientInsuranceContextLoader implements IpSettleContextLoader {

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
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final CatalogNoticeComplianceAssertSupport catalogNoticeComplianceAssertSupport;
    private final ChsActivityPackageFeeExpandSupport activityPackageFeeExpandSupport;
    private final ChsSettlementDrugTraceRatioResolver settlementDrugTraceRatioResolver;
    private final YbNationalCatalogItemLookup nationalCatalogItemLookup;
    /** 出院医嘱计划时间（业务出院真源）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

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
        // Pre：3101 patient_dtos 姓名/性别/出生日期（对齐门诊 ContextLoader）
        this.applyPatientDemographics(ctx, encounter);
        ctx.setAdmissionTime(encounter.getStartTime());
        ctx.setDischargeTime(this.resolveBusinessDischargeTime(encounter.getEncounterId(), encounter.getEndTime()));
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
     * @param chargeItemIds 收费项 ID 列表（不可空）
     * @return 与请求 ID 一一对应的收费项实体
     */
    private List<ChargeItem> loadChargeItems(List<Long> chargeItemIds) {
        // Gate：禁止对空列表调 size，避免 NPE
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项 ID 不能为空");
        }
        // Pre：快照/展开可能带重复 id；库查询为集合语义，按去重后条数校验
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(chargeItemIds));
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(distinctIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != distinctIds.size()) {
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
        // Pre：国家目录包装/计价单位（2301 限价折算优先于院内药典）
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
        // Pre：冲销源收费项一次装载，供 2301 init_feedetl_sn
        Map<Long, ChargeItem> refundSourceById =
            ChsFeeRefundInitFeedetlSupport.loadRefundSources(chargeItemQueryService, chargeItems);
        // Pre：先展开全部 slice，收集 yb_no 后一次加载局文 overlay
        List<IpFeeSliceWork> sliceWorks = new ArrayList<>();
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
                // Gate：无国家目录编码禁止上传 2301
                if (product == null || isEmpty(product.getYbNo())) {
                    throw new BusinessException(
                        "INS-011",
                        buildMissingYbNoMessage(item, itemDisplayName, product));
                }
                hilistCodes.add(product.getYbNo().trim());
                sliceWorks.add(new IpFeeSliceWork(item, slice, product, itemDisplayName, definitionForGate));
            }
        }
        Map<String, YbNationalCatalogNoticeOverlay> overlayByHilist =
            catalogNoticeComplianceAssertSupport.preloadOverlays(
                TenantUtils.getCurrentTenantId(), contract.getId(), hilistCodes);
        List<IpSettleChargeLine> lines = new ArrayList<>();
        List<TcmdrugUsedWayResolver.HerbLine> herbMetas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int drugTracCnt = 0;
        int mcsTracCnt = 0;
        Map<Long, MedicationDefinition> medicationById = new HashMap<>();
        for (IpFeeSliceWork work : sliceWorks) {
            ChargeItem item = work.item();
            ChsFeeUploadSlice slice = work.slice();
            ProductDefinitionCatalogQueryModel product = work.product();
            String itemDisplayName = work.itemDisplayName();
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
            IpSettleChargeLine line = new IpSettleChargeLine();
            line.setChargeItemId(slice.getChargeItemId());
            line.setFeedetlSn(slice.getFeedetlSn());
            // Pre：退单行写原费用流水（refund_id → 正数行 feedetl_sn）
            String initFeedetlSn = ChsFeeRefundInitFeedetlSupport.resolveInitFeedetlSn(
                item, slice.getFeedetlSn(), refundSourceById);
            if (isNotEmpty(initFeedetlSn)) {
                line.setInitFeedetlSn(initFeedetlSn);
            }
            // 2301 耗材口径：med_list_codg=医保编码截前15；medins_list_codg=完整医保编码
            line.setMedListCodg(SettlementTraceCountSupport.toPlatformMedListCodg(
                product.getProductTableName(), fullYbNo));
            line.setMedinsListCodg(resolveMedinsListCodg(product, fullYbNo));
            line.setItemName(resolveChargeItemDisplayName(
                itemDisplayName, product, work.definitionForGate()));
            // Pre：折片后金额须与 pric×cnt 一致（2204/2301 费用分割校验；6 位单价平摊）
            line.setDetItemFeeSumamt(uploadQtyPrice.sumamtOr(slice.getDetItemFeeSumamt()));
            line.setCnt(uploadQtyPrice.cnt());
            line.setPric(uploadQtyPrice.pric());
            // Gate：负数 cnt 必须已带 init_feedetl_sn，避免平台 HospFeeDtlBO_121
            ChsFeeRefundInitFeedetlSupport.assertNegativeCntHasInit(
                line.getCnt(), line.getInitFeedetlSn(), line.getFeedetlSn());
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
            herbMetas.add(TcmdrugUsedWayResolver.of(
                item.getPrescriptionNo(),
                product.getProductDefinitionId(),
                fullYbNo,
                slice.getChargeItemId(),
                TcmdrugUsedWayResolver.isHerbalMedication(medForFee)));
            // Pre：medfee_sumamt 与上传明细合计一致（6 位单价平摊后通常=院内合计）
            total = total.add(MonetaryAmountSupport.nullToZero(line.getDetItemFeeSumamt()));
        }
        // Pre：同处方中草药味数 >5 传复方，否则单方；非中草药不填
        Map<String, String> tcmdrugUsedWayByRx =
            TcmdrugUsedWayResolver.resolveCodeByPrescription(herbMetas);
        for (int i = 0; i < lines.size(); i++) {
            String usedWay = TcmdrugUsedWayResolver.codeOrNull(herbMetas.get(i), tcmdrugUsedWayByRx);
            // 非中草药：报文不传 tcmdrug_used_way
            if (isEmpty(usedWay)) {
                continue;
            }
            lines.get(i).setTcmdrugUsedWay(usedWay);
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
        // Pre：零售/上传分差落上下文，供预结算/正式结算快照与日结勾稽
        SettlementRetailUploadDiffSupport.RetailUploadDiffAmounts retailUploadDiff =
            SettlementRetailUploadDiffSupport.of(sumChargeItemRetailTotal(chargeItems), total);

        return new IpSettleContext()
            .setContractId(contract.getId())
            .setContractBusNo(contractBusNo)
            .setEncounterId(command.getEncounterId())
            .setEncounterBusNo(encounter.getEncounterBusNo())
            .setPatientId(encounter.getPatientId())
            // Pre：3101 patient_dtos 姓名/性别/出生日期（对齐门诊 ContextLoader）
            .setPatientName(encounter.getPatientName())
            .setPatientGender(encounter.getGenderEnum())
            .setBrdy(this.formatBirthDateFor3101(encounter))
            .setAdmissionTime(encounter.getStartTime())
            .setDischargeTime(this.resolveBusinessDischargeTime(encounter.getEncounterId(), encounter.getEndTime()))
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
            .setRetailMedfeeSumamt(retailUploadDiff.retailMedfeeSumamt())
            .setUploadMedfeeSumamt(retailUploadDiff.uploadMedfeeSumamt())
            .setRetailUploadDiffAmt(retailUploadDiff.retailUploadDiffAmt())
            .setPaymentDraftId(command.getPaymentDraftId())
            .setOperatorId(operatorId)
            .setTenantId(TenantUtils.getCurrentTenantId())
            .setAcctUsedFlag(command.getAcctUsedFlag())
            .setPsnSetlway(command.getPsnSetlway())
            .setMinpacuntDrugTracCnt(drugTracCnt)
            .setMcsTracCnt(mcsTracCnt);
    }

    /**
     * 业务出院时间：出院医嘱计划时间优先，回落 Encounter.endTime。
     *
     * @param encounterId      就诊主键
     * @param encounterEndTime 就诊 end_time；可空
     * @return 计划出院或 endTime；皆空则 null
     */
    private LocalDateTime resolveBusinessDischargeTime(Long encounterId, LocalDateTime encounterEndTime) {
        if (encounterId == null) {
            return encounterEndTime;
        }
        Map<Long, LocalDateTime> planned =
            serviceRequestQueryService.mapDischargePlannedTimeByEncounterIds(Set.of(encounterId));
        LocalDateTime fromAdvice = planned.get(encounterId);
        if (fromAdvice != null) {
            return fromAdvice;
        }
        return encounterEndTime;
    }

    /**
     * 将 Encounter 患者姓名/性别/出生日期写入住院结算上下文，供 3101 {@code patient_dtos}。
     *
     * @param ctx       待补齐的住院上下文
     * @param encounter 已装载的住院就诊投影（含患者人口学）
     */
    private void applyPatientDemographics(
            IpSettleContext ctx,
            InpatientEncounterContextQueryModel encounter) {
        // Gate：无就诊投影则无法补患者节点
        if (ctx == null || encounter == null) {
            return;
        }
        ctx.setPatientName(encounter.getPatientName());
        ctx.setPatientGender(encounter.getGenderEnum());
        ctx.setBrdy(this.formatBirthDateFor3101(encounter));
    }

    /**
     * Encounter 出生日期 → 3101 {@code brdy}（yyyy-MM-dd）；缺失则返回 null。
     *
     * @param encounter 住院就诊投影
     * @return 平台出生日期串；无出生日期时为 null
     */
    private String formatBirthDateFor3101(InpatientEncounterContextQueryModel encounter) {
        // Gate：无出生日期则 3101 会缺 brdy，由门禁本地总检拦截
        if (encounter == null || encounter.getBirthDate() == null) {
            return null;
        }
        return encounter.getBirthDate().toLocalDate()
            .format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
    }

    /**
     * 从就诊诊断链解析主诊断，供 2401/2303 及费用明细上传使用。
     *
     * @param encounterId 就诊 ID
     * @return 主诊断编码与名称
     */
    private DiagnosisHeader resolveDiagnosis(Long encounterId) {
        InsuranceDiagnosisCodeAssembler.MainDiagnosis diagnosis = InsuranceDiagnosisCodeAssembler.resolveMainDiagnosis(
            encounterId, encounterQueryService, conditionDefinitionQueryService);
        return new DiagnosisHeader(diagnosis.code(), diagnosis.name());
    }

    /** 主诊断编码与名称的内部载体。 */
    private record DiagnosisHeader(String code, String name) {
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
    private record IpFeeSliceWork(
        ChargeItem item,
        ChsFeeUploadSlice slice,
        ProductDefinitionCatalogQueryModel product,
        String itemDisplayName,
        ChargeItemDefinition definitionForGate) {
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
     * 2301 {@code medins_list_codg}（医药机构目录编码）。
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
     * @param item           收费项
     * @param chargeItemName 收费项定义名称（可空）
     * @param product        产品目录投影（可空）
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

    /**
     * 解析 3101 项目展示名：切片名 → 产品名 → 定价定义名 → 占位。
     */
    private static String resolveChargeItemDisplayName(
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
}
