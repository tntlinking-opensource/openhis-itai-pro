/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.support;

import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.advice.SkinTestCompanionDisplaySupport;
import com.zxplat.application.shared.advice.SkinTestOutcomeByMedicationRequestLoader;
import com.zxplat.application.shared.advice.SkinTestOutcomeSnapshot;
import com.zxplat.application.shared.advice.InpatientEntrustOrderGateSupport;
import com.zxplat.application.shared.advice.OrderCatalogDisplayAssembler;
import com.zxplat.application.shared.advice.OrderRequestCatalogWarmAssembler;
import com.zxplat.application.shared.product.MedicationDailyDoseDisplayAssembler;
import com.zxplat.application.shared.product.MedicationDosageDisplayAssembler;
import com.zxplat.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.advice.OrderRequestCatalogWarmContext;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayContext;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayCore;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewListDetailIndex;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.zxplat.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.share.constants.ChargeItemDmConstants;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱查看：列表行展示内核（明细索引、批量预热、药/诊行字段组装）。
 *
 * <p>供医嘱校对、医嘱执行、护士划价父医嘱等模块复用；读模型骨架查询仍由各业务
 * {@code OrderInfoRMQueryService} 场景方法承担，本类不改变 IF 契约与过滤规则。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationOrderViewListAssembler {

    /** 术语展示名批量解析。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 频次展示码表：cus_frequency 主数据。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 离院里程碑：出院方式 code（死亡出院展示名）。 */
    private final EncounterHistoryQueryService encounterHistoryQueryService;

    /** 药/诊定义预热。 */
    private final OrderRequestCatalogWarmAssembler orderRequestCatalogWarmAssembler;

    /** 国临手术定义：术式行展示名。 */
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;

    /** 产品读模型批量查询。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 库位名称解析。 */
    private final LocationQueryService locationQueryService;

    /** 医务人员姓名解析。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 校对人岗位 → 病区上级科室展示名解析。 */
    private final PractitionerCheckOrgDisplayResolver practitionerCheckOrgDisplayResolver;

    /** 嘱托医嘱展示名解析（BR-IP-ORD-028④；与住院医生站列表同源）。 */
    private final InpatientEntrustOrderGateSupport inpatientEntrustOrderGateSupport;

    /** 皮试药 → 最新登记结果（companion SR → AllergyIntolerance）。 */
    private final SkinTestOutcomeByMedicationRequestLoader skinTestOutcomeByMedicationRequestLoader;

    /**
     * 第一遍扫描：建立明细 id 索引并收集批量预热 id 集合。
     *
     * @param skeleton       OrderInfo 骨架行（可为空）
     * @param medByEncounter 就诊 → 药品明细
     * @param svcByEncounter 就诊 → 诊疗明细
     * @return 明细索引与 id 集合
     */
    public NurseStationOrderViewListDetailIndex indexEncounterDetails(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter) {
        NurseStationOrderViewListDetailIndex index = new NurseStationOrderViewListDetailIndex();
        if (medByEncounter != null) {
            for (List<MedicationRequestListQueryModel> list : medByEncounter.values()) {
                for (MedicationRequestListQueryModel row : list) {
                    if (row == null) {
                        continue;
                    }
                    if (row.getId() != null) {
                        index.getMedDetailById().put(row.getId(), row);
                    }
                    if (row.getMedicationId() != null) {
                        index.getMedDefIds().add(row.getMedicationId());
                    }
                    this.collectPractitionerAndLocation(row.getRequesterId(), row.getRecorderId(),
                        row.getPerformLocation(), index.getPractitionerIds(), index.getLocationIds());
                    if (row.getPerformerCheckId() != null) {
                        index.getPractitionerIds().add(row.getPerformerCheckId());
                    }
                    // 停嘱人：停嘱时间后跟人名（BR-IP-ORD-031）。
                    if (row.getStopperId() != null) {
                        index.getPractitionerIds().add(row.getStopperId());
                    }
                    // 停嘱校对人：与开嘱校对人同解析链
                    if (row.getStopPerformerCheckId() != null) {
                        index.getPractitionerIds().add(row.getStopPerformerCheckId());
                    }
                }
            }
        }
        if (svcByEncounter != null) {
            for (List<ServiceRequestListModel> list : svcByEncounter.values()) {
                for (ServiceRequestListModel row : list) {
                    if (row == null) {
                        continue;
                    }
                    if (row.getId() != null) {
                        index.getSvcDetailById().put(row.getId(), row);
                    }
                    if (this.shouldWarmActivityDefinition(row.getCategoryEnum(), row.getActivityId())) {
                        index.getActivityDefIds().add(row.getActivityId());
                    }
                    // 术式行：收集国临 PD 供列表展示名
                    if (row.getProcedureDefinitionId() != null) {
                        index.getProcedureDefinitionIds().add(row.getProcedureDefinitionId());
                    }
                    this.collectPractitionerAndLocation(row.getRequesterId(), null, row.getLocationId(),
                        index.getPractitionerIds(), index.getLocationIds());
                    if (row.getPerformerCheckId() != null) {
                        index.getPractitionerIds().add(row.getPerformerCheckId());
                    }
                    if (row.getStopperId() != null) {
                        index.getPractitionerIds().add(row.getStopperId());
                    }
                    if (row.getStopPerformerCheckId() != null) {
                        index.getPractitionerIds().add(row.getStopPerformerCheckId());
                    }
                }
            }
        }
        if (skeleton != null) {
            for (InpatientOrderListSkeleton order : skeleton) {
                if (order == null) {
                    continue;
                }
                if (order.getMedRequesterId() != null) {
                    index.getPractitionerIds().add(order.getMedRequesterId());
                }
                if (order.getRequesterId() != null) {
                    index.getPractitionerIds().add(order.getRequesterId());
                }
                if (order.getStopperId() != null) {
                    index.getPractitionerIds().add(order.getStopperId());
                }
                if (order.getStopPerformerCheckId() != null) {
                    index.getPractitionerIds().add(order.getStopPerformerCheckId());
                }
                if (order.getMedicationId() != null) {
                    index.getMedDefIds().add(order.getMedicationId());
                }
                if (this.shouldWarmActivityDefinition(order.getCategoryEnum(), order.getActivityId())) {
                    index.getActivityDefIds().add(order.getActivityId());
                }
                if (order.getLocationId() != null) {
                    index.getLocationIds().add(order.getLocationId());
                }
            }
        }
        return index;
    }

    /**
     * 在骨架遍历前批量预热目录、术语、人员与库位名称。
     *
     * @param index 由 {@link #indexEncounterDetails} 产出
     * @return 展示上下文
     */
    public NurseStationOrderViewDisplayContext prepareDisplayContext(NurseStationOrderViewListDetailIndex index) {
        NurseStationOrderViewDisplayContext ctx = new NurseStationOrderViewDisplayContext();
        OrderRequestCatalogWarmContext catalogWarm = orderRequestCatalogWarmAssembler.prepareByDefinitionIds(
            index.getActivityDefIds(), index.getMedDefIds(), Set.of());
        ctx.setMedicationDefinitionById(catalogWarm.getMedicationDefinitionById());
        ctx.setActivityDefinitionById(catalogWarm.getActivityDefinitionById());
        ctx.setProcedureDefinitionNameById(this.mapProcedureDefinitionNames(index.getProcedureDefinitionIds()));
        ctx.setProductByKey(this.batchLoadProducts(index.getMedDefIds(), index.getActivityDefIds()));
        ctx.setUnitLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE));
        ctx.setMethodLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE));
        ctx.setRateLabelByCode(frequencyOptionLoader.buildRateLabelMap());
        ctx.setTimesPerDayByRateCode(frequencyOptionLoader.buildTimesPerDayMap());
        // Pre：输液滴速术语（与医生站列表 speedName 同源）
        ctx.setSpeedLabelByCode(
            terminologyOptionLoader.buildLabelMap(DoctorStationAdviceConstants.TERMINOLOGY_SPEED_CODE_SYSTEM_NAME));
        ctx.setDoseUnitLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT));
        ctx.setPractitionerNameById(this.loadPractitionerNames(index.getPractitionerIds()));
        ctx.setLocationNameById(locationQueryService.mapNamesByIds(index.getLocationIds()));
        // Pre：校对人绑定病区 → 上级科室展示名（医嘱执行「执行科室」等消费方使用）
        ctx.setPerformerCheckOrgDisplayByKey(practitionerCheckOrgDisplayResolver.buildPerformerCheckOrgDisplayByKey(
            index.getMedDetailById().values(),
            index.getSvcDetailById().values()));
        // Pre：皮试检查 companion 关联药名（requestId → 药品定义/产品名）
        ctx.setMedicationAdviceNameByRequestId(this.buildMedicationAdviceNameByRequestId(
            index.getMedDetailById(),
            ctx.getMedicationDefinitionById(),
            ctx.getProductByKey()));
        // Pre：出院方式 code（死亡出院 →「死亡出院」展示名，与医生站列表/打印同源）
        ctx.setDischargeOutWayCodeByServiceRequestId(
            this.indexDischargeOutWayCodes(index.getSvcDetailById()));
        return ctx;
    }

    /**
     * 批量预热皮试药最新登记结果，写入展示上下文供药嘱行回填 {@code skinTestOutcome*}。
     *
     * @param ctx            已由 {@link #prepareDisplayContext} 产出的上下文
     * @param medByEncounter 就诊 → 药品明细
     */
    public void enrichSkinTestOutcomes(
        NurseStationOrderViewDisplayContext ctx,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter) {
        if (ctx == null) {
            return;
        }
        ctx.setSkinTestOutcomeByMedicationRequestId(
            skinTestOutcomeByMedicationRequestLoader.loadByEncounters(medByEncounter));
    }

    /**
     * 药品申请主键 → 关联展示标签（药名 + 规格），供皮试检查追加「（关联：…）」。
     *
     * @param medDetailById            药品明细
     * @param medicationDefinitionById 药品定义
     * @param productByKey             产品读模型
     * @return requestId → 标签
     */
    private Map<Long, String> buildMedicationAdviceNameByRequestId(
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        Map<Long, MedicationDefinition> medicationDefinitionById,
        Map<String, ProductDefinitionDisplayDto> productByKey
    ) {
        Map<Long, String> nameByRequestId = new HashMap<>();
        if (medDetailById == null || medDetailById.isEmpty()) {
            return nameByRequestId;
        }
        for (MedicationRequestListQueryModel row : medDetailById.values()) {
            if (row == null || row.getId() == null) {
                continue;
            }
            String name = null;
            String spec = null;
            Long medDefId = row.getMedicationId();
            MedicationDefinition def = null;
            if (medDefId != null && medicationDefinitionById != null) {
                def = medicationDefinitionById.get(medDefId);
            }
            if (def != null) {
                name = StringUtils.trimToEmpty(def.getName());
                // 规格与医嘱列表 specificationText 同源
                spec = OrderCatalogDisplayAssembler.resolveMedicationSpecText(def);
            }
            ProductDefinitionDisplayDto product = null;
            if (medDefId != null && productByKey != null) {
                product = productByKey.get(
                    ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, medDefId));
            }
            if (StringUtils.isEmpty(name) && product != null) {
                name = StringUtils.trimToEmpty(product.getProductName());
            }
            if (StringUtils.isEmpty(spec) && product != null) {
                spec = StringUtils.trimToEmpty(product.getTotalVolume());
            }
            String label = SkinTestCompanionDisplaySupport.formatLinkedMedicationLabel(name, spec);
            if (StringUtils.isNotEmpty(label)) {
                nameByRequestId.put(row.getId(), label);
            }
        }
        return nameByRequestId;
    }

    /**
     * 从 OrderInfo 解析域 Request 主键（药嘱 medReqId，诊嘱 orderId）。
     *
     * @param order 读模型行
     * @return 申请主键；无法识别时 null
     */
    public Long resolveRequestId(InpatientOrderListSkeleton order) {
        if (order == null) {
            return null;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            return order.getMedReqId();
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            return order.getOrderId();
        }
        return null;
    }

    /**
     * OrderInfo.orderType → Procedure/绑定查询用的 Request 表名。
     *
     * @param order 读模型行
     * @return 表名字面量；无法识别时 null
     */
    public String resolveRequestTable(InpatientOrderListSkeleton order) {
        if (order == null) {
            return null;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            return TableNameConstants.MED_MEDICATION_REQUEST;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            return TableNameConstants.WKF_SERVICE_REQUEST;
        }
        return null;
    }

    /**
     * 稳定行键：requestId + U+0001 + orderType。
     *
     * @param requestId 申请主键
     * @param orderType 医嘱类型
     * @return 行键
     */
    public String buildListRowKey(Long requestId, String orderType) {
        if (requestId == null) {
            return null;
        }
        return requestId + String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR) + orderType;
    }

    /**
     * 药品 OrderInfo 行 → 展示内核。
     *
     * @param order     骨架
     * @param medDetail 药品明细；可 null
     * @param ctx       预热上下文
     * @return 展示内核；非药嘱时 null
     */
    public NurseStationOrderViewDisplayCore buildMedicationDisplayCore(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        NurseStationOrderViewDisplayContext ctx) {
        if (order == null || !ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            return null;
        }
        Long requestId = order.getMedReqId();
        NurseStationOrderViewDisplayCore core = new NurseStationOrderViewDisplayCore();
        core.setListRowKey(this.buildListRowKey(requestId, ReadModelOrderTypeConstants.MED_REQUEST));
        core.setRequestId(requestId);
        core.setOrderType(ReadModelOrderTypeConstants.MED_REQUEST);
        core.setEncounterId(order.getEncounterId());
        core.setPatientId(order.getPatientId());
        String statusCode = this.resolveRequestStatusCode(order, medDetail, null);
        core.setStatusEnum(statusCode);
        core.setStatusDisplay(this.resolveRequestStatusDisplay(statusCode));
        String therapyCode = order.getTherapyEnum();
        if (medDetail != null && StringUtils.isNotEmpty(medDetail.getTherapyEnum())) {
            therapyCode = medDetail.getTherapyEnum();
        }
        core.setTherapyEnum(therapyCode);
        core.setTherapyDisplay(this.resolveTherapyDisplay(therapyCode));
        core.setOrderKindDisplay(InpatientNurseStationConstants.ORDER_KIND_DISPLAY_MEDICATION);
        Long medDefId = order.getMedicationId();
        if (medDetail != null && medDetail.getMedicationId() != null) {
            medDefId = medDetail.getMedicationId();
        }
        MedicationDefinition def = ctx.getMedicationDefinitionById().get(medDefId);
        if (def != null) {
            core.setAdviceDisplayName(def.getName());
            core.setSpecificationText(OrderCatalogDisplayAssembler.resolveMedicationSpecText(def));
        } else {
            ProductDefinitionDisplayDto product = ctx.getProductByKey().get(
                ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.MED_MEDICATION_DEFINITION, medDefId));
            if (product != null) {
                core.setAdviceDisplayName(product.getProductName());
            }
        }
        ProductDefinitionDisplayDto medProduct = ctx.getProductByKey().get(
            ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, medDefId));
        // 包装装量后缀：1片x100（前端与规格拼成 5mg1片x100）；包装单位展示供摆药总量
        if (medProduct != null) {
            core.setPartPercent(medProduct.getPackageUnitRatio());
            core.setPackageSplitRatioText(
                MedicationPackageSplitQuantityDisplayAssembler.buildPackageSplitRatioText(medProduct));
            String packageUnitDisplay = StringUtils.firstNonBlank(
                medProduct.getPackageUnitCodeDictText(),
                ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, medProduct.getPackageUnitCode(), medProduct));
            core.setPackageUnitDisplay(packageUnitDisplay);
        }
        if (medDetail != null) {
            core.setDosageDisplay(this.formatDosageDisplay(medDetail.getDose(), medDetail.getDoseUnitCode(),
                ctx.getDoseUnitLabelByCode(), medDetail.getQuantity(), medDetail.getUnitCode(),
                ctx.getUnitLabelByCode(), medProduct));
            // 分次 schedule：有值时剂量列走序列（如 2mg,1mg），禁止再用单值×次数估日总量（BR-IP-EXE-040）
            core.setDoseSchedule(medDetail.getDoseSchedule());
            core.setQuantitySchedule(medDetail.getQuantitySchedule());
            if (StringUtils.isNotEmpty(medDetail.getDoseSchedule())) {
                String scheduleUnitName = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                    ctx.getDoseUnitLabelByCode(), medDetail.getDoseUnitCode(), medProduct);
                core.setDosageDisplay(this.buildDoseScheduleDisplay(
                    medDetail.getDoseSchedule(), scheduleUnitName));
            }
            // 临时/出院带药：quantity 为开立总量，供摆药/用药单总量列；长期不填（避免单次用量被误当总量）
            core.setQuantityDisplay(this.buildOrderTotalQuantityDisplay(
                therapyCode,
                medDetail.getQuantity(),
                medDetail.getUnitCode(),
                ctx.getUnitLabelByCode(),
                medProduct));
            // 长期药日剂量：有 schedule 时各段求和；否则单次×每日次数（BR-IP-EXE-040）
            core.setDailyDoseDisplay(this.buildDailyDoseDisplay(
                medDetail.getDose(),
                medDetail.getDoseUnitCode(),
                medDetail.getDoseSchedule(),
                medDetail.getQuantitySchedule(),
                medDetail.getUnitCode(),
                medDetail.getRateCode(),
                therapyCode,
                ctx.getDoseUnitLabelByCode(),
                ctx.getUnitLabelByCode(),
                ctx.getTimesPerDayByRateCode(),
                medProduct));
            // 首日用量回显：仅长期有意义；单位展示优先剂量术语，回落开立单位术语/目录字典（BR-IP-ORD-030）
            core.setFirstDose(medDetail.getFirstDose());
            core.setFirstDoseUnitCode(medDetail.getFirstDoseUnitCode());
            core.setFirstDoseUnitName(this.resolveFirstDoseUnitDisplayName(
                medDetail.getFirstDoseUnitCode(),
                ctx.getDoseUnitLabelByCode(),
                ctx.getUnitLabelByCode(),
                medProduct));
            core.setUsageDisplay(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                ctx.getMethodLabelByCode(), medDetail.getMethodCode()));
            core.setFrequencyDisplay(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                ctx.getRateLabelByCode(), medDetail.getRateCode()));
            // 输液滴速：医生站开立写入 speed_code，护士校对/执行扩展行回显
            core.setSpeedCode(medDetail.getSpeedCode());
            core.setSpeedDisplay(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                ctx.getSpeedLabelByCode(), medDetail.getSpeedCode()));
            // 开立/签发分列同名字段；开立空时展示回落签发（存量兼容）。
            LocalDateTime medOpened = medDetail.getReqOpenedTime();
            if (medOpened == null) {
                medOpened = medDetail.getReqAuthoredTime();
            }
            core.setReqOpenedTime(medOpened);
            core.setReqAuthoredTime(medDetail.getReqAuthoredTime());
            core.setStopTime(medDetail.getStopTime());
            Long authorId = medDetail.getRequesterId();
            if (authorId == null) {
                authorId = medDetail.getRecorderId();
            }
            core.setRequesterName(ctx.getPractitionerNameById().get(authorId));
            core.setStopperId(medDetail.getStopperId());
            core.setStopperName(ctx.getPractitionerNameById().get(medDetail.getStopperId()));
            core.setPerformerCheckName(ctx.getPractitionerNameById().get(medDetail.getPerformerCheckId()));
            // 停嘱校对痕迹：与开嘱校对人同姓名解析链
            core.setStopPerformerCheckId(medDetail.getStopPerformerCheckId());
            core.setStopCheckTime(medDetail.getStopCheckTime());
            core.setStopCheckerName(ctx.getPractitionerNameById().get(medDetail.getStopPerformerCheckId()));
            Long medEncounterId = medDetail.getEncounterId();
            if (medEncounterId == null) {
                medEncounterId = order.getEncounterId();
            }
            core.setPerformerCheckOrgDisplay(practitionerCheckOrgDisplayResolver.resolvePerformerCheckOrgDisplay(
                ctx.getPerformerCheckOrgDisplayByKey(),
                medDetail.getPerformerCheckId(),
                medEncounterId));
            if (medDetail.getPerformLocation() != null) {
                core.setPerformerLocationDisplay(ctx.getLocationNameById().get(medDetail.getPerformLocation()));
            }
            core.setGroupId(medDetail.getGroupId());
            if (medDetail.getGroupId() != null && medDetail.getGroupId() <= Integer.MAX_VALUE) {
                core.setGroupNo(medDetail.getGroupId().intValue());
            }
            core.setSortNumber(medDetail.getSortNumber());
            // 皮试药标记：校对列表提示护士需登记皮试（AS-IP-NUR-011）。
            core.setSkinTestFlag(medDetail.getSkinTestFlag());
            // 皮试结果回填（BR-IP-ORD-016）：已登记时供列表跟在药名后展示阴/阳性
            this.applySkinTestOutcomeToCore(core, requestId, ctx);
            // 医嘱备注（过滤套餐覆盖 JSON 误入）
            core.setRemark(this.resolveOrderRemarkForDisplay(medDetail.getRemark()));
        } else {
            // 明细缺失时：开立可用骨架解析（含存量回落）；签发须直读列，禁止把开立回填进签发。
            core.setReqOpenedTime(order.resolveOrderOpenedTime());
            core.setReqAuthoredTime(order.getReqAuthoredTime());
            core.setStopTime(order.getStopTime());
            core.setRequesterName(ctx.getPractitionerNameById().get(order.getMedRequesterId()));
            core.setStopperId(order.getStopperId());
            core.setStopperName(ctx.getPractitionerNameById().get(order.getStopperId()));
            core.setStopPerformerCheckId(order.getStopPerformerCheckId());
            core.setStopCheckTime(order.getStopCheckTime());
            core.setStopCheckerName(ctx.getPractitionerNameById().get(order.getStopPerformerCheckId()));
        }
        // Asm：停嘱待校标记（STOPPED + 长期 + 未写停嘱校对痕迹）
        core.setStopAuditPending(this.resolveStopAuditPending(
            core.getStatusEnum(), core.getTherapyEnum(), core.getStopPerformerCheckId()));
        return core;
    }

    /**
     * 诊疗 OrderInfo 行 → 展示内核。
     *
     * @param order     骨架
     * @param svcDetail 诊疗明细；可 null
     * @param ctx       预热上下文
     * @return 展示内核；非诊嘱时 null
     */
    public NurseStationOrderViewDisplayCore buildServiceDisplayCore(
        InpatientOrderListSkeleton order,
        ServiceRequestListModel svcDetail,
        NurseStationOrderViewDisplayContext ctx) {
        if (order == null || !ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            return null;
        }
        Long requestId = order.getOrderId();
        NurseStationOrderViewDisplayCore core = new NurseStationOrderViewDisplayCore();
        core.setListRowKey(this.buildListRowKey(requestId, ReadModelOrderTypeConstants.SERVICE_REQUEST));
        core.setRequestId(requestId);
        core.setOrderType(ReadModelOrderTypeConstants.SERVICE_REQUEST);
        core.setEncounterId(order.getEncounterId());
        core.setPatientId(order.getPatientId());
        String statusCode = this.resolveRequestStatusCode(order, null, svcDetail);
        core.setStatusEnum(statusCode);
        core.setStatusDisplay(this.resolveRequestStatusDisplay(statusCode));
        String serviceCategory = this.resolveServiceCategoryEnum(order, svcDetail);
        core.setCategoryEnum(serviceCategory);
        Long procedureDefinitionId = null;
        // Pre：明细携带术式 PD 时供类型列/展示名特殊识别
        if (svcDetail != null) {
            procedureDefinitionId = svcDetail.getProcedureDefinitionId();
        }
        // 类型列：目录类目（检验/检查/治疗/手术/麻醉…）；术式挂靠 PD 时强制「手术」
        core.setOrderKindDisplay(
            this.resolveOrderKindDisplay(
                ReadModelOrderTypeConstants.SERVICE_REQUEST,
                serviceCategory,
                procedureDefinitionId));
        String therapyCode = order.getTherapyEnum();
        if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getTherapyEnum())) {
            therapyCode = svcDetail.getTherapyEnum();
        }
        core.setTherapyEnum(therapyCode);
        core.setTherapyDisplay(this.resolveTherapyDisplay(therapyCode));
        Long activityId = this.resolveCatalogActivityId(order.getActivityId(), svcDetail, serviceCategory);
        if (this.isSpecialServiceCategory(serviceCategory)) {
            // 出院/转科不依赖活动定义 catalog，展示名与住院医生站列表口径一致（BR-IP-ORD-017）。
            String outWayCode = null;
            if (ctx.getDischargeOutWayCodeByServiceRequestId() != null && requestId != null) {
                outWayCode = ctx.getDischargeOutWayCodeByServiceRequestId().get(requestId);
            }
            core.setAdviceDisplayName(
                this.resolveSpecialServiceAdviceDisplayName(serviceCategory, outWayCode));
        } else if (procedureDefinitionId != null
            && ctx.getProcedureDefinitionNameById() != null) {
            // 手术术式行：展示国临术式名（非 AD）
            String procedureName = ctx.getProcedureDefinitionNameById().get(procedureDefinitionId);
            if (StringUtils.isNotEmpty(procedureName)) {
                core.setAdviceDisplayName(procedureName);
            }
        } else if (activityId != null) {
            ActivityDefinition activityDef = ctx.getActivityDefinitionById().get(activityId);
            if (activityDef != null) {
                core.setAdviceDisplayName(activityDef.getName());
            } else {
                ProductDefinitionDisplayDto product = ctx.getProductByKey().get(
                    ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId));
                if (product != null) {
                    core.setAdviceDisplayName(product.getProductName());
                }
            }
        }
        ProductDefinitionDisplayDto svcProduct = null;
        if (activityId != null) {
            svcProduct = ctx.getProductByKey().get(
                ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId));
        }
        if (svcDetail != null) {
            core.setDosageDisplay(this.formatQuantityUnitDisplay(svcDetail.getQuantity(), svcDetail.getUnitCode(),
                ctx.getUnitLabelByCode(), svcProduct));
            core.setFrequencyDisplay(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                ctx.getRateLabelByCode(), svcDetail.getRateCode()));
            // 开立/签发分列同名字段；开立空时展示回落签发（存量兼容）。
            LocalDateTime svcOpened = svcDetail.getReqOpenedTime();
            if (svcOpened == null) {
                svcOpened = svcDetail.getReqAuthoredTime();
            }
            core.setReqOpenedTime(svcOpened);
            core.setReqAuthoredTime(svcDetail.getReqAuthoredTime());
            core.setStopTime(svcDetail.getStopTime());
            core.setRequesterName(ctx.getPractitionerNameById().get(svcDetail.getRequesterId()));
            core.setStopperId(svcDetail.getStopperId());
            core.setStopperName(ctx.getPractitionerNameById().get(svcDetail.getStopperId()));
            core.setPerformerCheckName(ctx.getPractitionerNameById().get(svcDetail.getPerformerCheckId()));
            // 停嘱校对痕迹：与开嘱校对人同姓名解析链
            core.setStopPerformerCheckId(svcDetail.getStopPerformerCheckId());
            core.setStopCheckTime(svcDetail.getStopCheckTime());
            core.setStopCheckerName(ctx.getPractitionerNameById().get(svcDetail.getStopPerformerCheckId()));
            Long svcEncounterId = svcDetail.getEncounterId();
            if (svcEncounterId == null) {
                svcEncounterId = order.getEncounterId();
            }
            core.setPerformerCheckOrgDisplay(practitionerCheckOrgDisplayResolver.resolvePerformerCheckOrgDisplay(
                ctx.getPerformerCheckOrgDisplayByKey(),
                svcDetail.getPerformerCheckId(),
                svcEncounterId));
            if (svcDetail.getLocationId() != null) {
                core.setPerformerLocationDisplay(ctx.getLocationNameById().get(svcDetail.getLocationId()));
            }
            core.setSortNumber(svcDetail.getSortNumber());
            // 医嘱备注（过滤套餐覆盖 JSON 误入）
            core.setRemark(this.resolveOrderRemarkForDisplay(svcDetail.getRemark()));
            // 出院/转科专用字段：计划出院时间与原因（BR-IP-ORD-017 / BR-IP-AUD-006）
            this.fillSpecialServiceDischargeFields(core, serviceCategory, svcDetail);
        } else {
            // 明细缺失时：开立可用骨架解析（含存量回落）；签发须直读列，禁止把开立回填进签发。
            core.setReqOpenedTime(order.resolveOrderOpenedTime());
            core.setReqAuthoredTime(order.getReqAuthoredTime());
            core.setStopTime(order.getStopTime());
            core.setRequesterName(ctx.getPractitionerNameById().get(order.getRequesterId()));
            core.setStopperId(order.getStopperId());
            core.setStopperName(ctx.getPractitionerNameById().get(order.getStopperId()));
            core.setStopPerformerCheckId(order.getStopPerformerCheckId());
            core.setStopCheckTime(order.getStopCheckTime());
            core.setStopCheckerName(ctx.getPractitionerNameById().get(order.getStopPerformerCheckId()));
        }
        // Asm：停嘱待校标记（STOPPED + 长期 + 未写停嘱校对痕迹）
        core.setStopAuditPending(this.resolveStopAuditPending(
            core.getStatusEnum(), core.getTherapyEnum(), core.getStopPerformerCheckId()));
        // Asm：嘱托行展示名优先正文，禁止只显示目录名「嘱托」（BR-IP-ORD-028④）
        String entrustContent = null;
        if (svcDetail != null) {
            entrustContent = svcDetail.getEntrustContent();
        }
        core.setAdviceDisplayName(inpatientEntrustOrderGateSupport.resolveServiceAdviceDisplayName(
            serviceCategory, core.getAdviceDisplayName(), entrustContent));
        // Asm：皮试检查 companion 挂 based_on 皮试药，展示名追加「（关联：药名 规格）」
        if (svcDetail != null) {
            Long basedOnMedReqId = SkinTestCompanionDisplaySupport.resolveBasedOnMedicationRequestId(
                svcDetail.getBasedOnTable(), svcDetail.getBasedOnId());
            if (basedOnMedReqId != null) {
                core.setBasedOnMedicationRequestId(basedOnMedReqId);
                String linkedName = null;
                if (ctx.getMedicationAdviceNameByRequestId() != null) {
                    linkedName = ctx.getMedicationAdviceNameByRequestId().get(basedOnMedReqId);
                }
                core.setSkinTestLinkedMedicationName(linkedName);
                core.setAdviceDisplayName(SkinTestCompanionDisplaySupport.appendLinkedMedicationHint(
                    core.getAdviceDisplayName(), linkedName));
            }
        }
        return core;
    }

    /**
     * 解析诊疗类目持久化码（category_enum）；明细优先于 OrderInfo 骨架。
     *
     * @param order     读模型骨架行
     * @param svcDetail 诊疗明细；可 null
     * @return 类目 code；无值时 null
     */
    public String resolveServiceCategoryEnum(
        InpatientOrderListSkeleton order,
        ServiceRequestListModel svcDetail) {
        if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getCategoryEnum())) {
            return svcDetail.getCategoryEnum().trim();
        }
        if (order != null && StringUtils.isNotEmpty(order.getCategoryEnum())) {
            return order.getCategoryEnum().trim();
        }
        return null;
    }

    /**
     * 是否为出院/转科等特殊诊疗类目（不依赖活动定义 catalog）。
     *
     * @param serviceCategory {@link InpatientNurseStationConstants} 类目 code
     * @return 出院或转科时为 true
     */
    public boolean isSpecialServiceCategory(String serviceCategory) {
        return InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(serviceCategory)
            || InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(serviceCategory);
    }

    /**
     * 护士站医嘱执行列表是否排除该诊疗类目（与入出转 ATD-03 待执行列表口径一致）。
     *
     * <p>转科/出院/护理类不经护士站「执行」Tab 走 Procedure 槽位，校对后由专用流程处理。</p>
     *
     * @param serviceCategory 诊疗类目 code
     * @return true 表示不进入 IF-IP-EXE-01 列表
     */
    public boolean isExcludedFromNurseStationExecuteList(String serviceCategory) {
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(serviceCategory)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(serviceCategory)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING.equals(serviceCategory)) {
            return true;
        }
        return false;
    }

    /**
     * 出院/转科类医嘱固定展示名（与 {@link DoctorStationAdviceConstants} 住院医生站列表一致）。
     *
     * @param serviceCategory 诊疗类目 code
     * @param outWayCode      出院方式 code；死亡时展示「死亡出院」
     * @return 展示名；非特殊类目时 null
     */
    public String resolveSpecialServiceAdviceDisplayName(String serviceCategory, String outWayCode) {
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(serviceCategory)) {
            if (DoctorStationAdviceConstants.OUT_WAY_CODE_DEATH.equals(StringUtils.trimToEmpty(outWayCode))) {
                return DoctorStationAdviceConstants.ADVICE_LIST_DEATH_DISCHARGE_DISPLAY_NAME_INPATIENT;
            }
            return DoctorStationAdviceConstants.ADVICE_LIST_DISCHARGE_DISPLAY_NAME_INPATIENT;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(serviceCategory)) {
            return DoctorStationAdviceConstants.ADVICE_LIST_TRANSFER_DISPLAY_NAME_INPATIENT;
        }
        return null;
    }

    /**
     * 按离院里程碑索引：出院 ServiceRequest → out_way_code。
     *
     * @param svcDetailById 诊疗明细索引
     * @return requestId → outWayCode
     */
    private Map<Long, String> indexDischargeOutWayCodes(Map<Long, ServiceRequestListModel> svcDetailById) {
        Map<Long, String> map = new HashMap<>();
        if (svcDetailById == null || svcDetailById.isEmpty()) {
            return map;
        }
        Set<Long> encounterIds = new HashSet<>();
        Set<Long> dischargeRequestIds = new HashSet<>();
        // 仅收集出院类 SR，避免对非出院就诊扫里程碑
        for (ServiceRequestListModel row : svcDetailById.values()) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(
                StringUtils.trimToEmpty(row.getCategoryEnum()))) {
                continue;
            }
            dischargeRequestIds.add(row.getId());
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty() || dischargeRequestIds.isEmpty()) {
            return map;
        }
        for (Long encounterId : encounterIds) {
            List<EncounterHistoryMilestoneQueryModel> milestones =
                encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
            if (milestones == null || milestones.isEmpty()) {
                continue;
            }
            for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
                if (milestone == null || milestone.getRequestId() == null) {
                    continue;
                }
                if (!CustomConstants.EncounterHistoryMilestoneName.DISCHARGE.equals(milestone.getName())) {
                    continue;
                }
                if (!dischargeRequestIds.contains(milestone.getRequestId())) {
                    continue;
                }
                if (StringUtils.isNotEmpty(milestone.getOutWayCode())) {
                    map.put(milestone.getRequestId(), milestone.getOutWayCode().trim());
                }
            }
        }
        return map;
    }

    /**
     * 出院/转科专用展示字段：计划出院时间与原因（BR-IP-ORD-017 / BR-IP-AUD-006）。
     *
     * <p>计划出院时间取 {@code occurrence_start_time}（医生开立时填写）；原因取
     * {@code reason_text}。普通诊疗类目不写本字段。出院方式 out_way 仍由就诊里程碑解析，
     * 校对写路径使用，列表本期不下发出院方式名。</p>
     *
     * @param core            展示内核；非空
     * @param serviceCategory 已解析诊疗类目
     * @param svcDetail       诊疗明细；可 null
     */
    private void fillSpecialServiceDischargeFields(
        NurseStationOrderViewDisplayCore core,
        String serviceCategory,
        ServiceRequestListModel svcDetail) {
        if (!this.isSpecialServiceCategory(serviceCategory)) {
            return;
        }
        if (svcDetail == null) {
            return;
        }
        // 计划出院/转科时间：医生填写的 occurrence_start_time
        if (svcDetail.getOccurrenceStartTime() != null) {
            core.setPlannedDischargeTime(svcDetail.getOccurrenceStartTime());
        }
        // 出院/转科原因正文
        if (StringUtils.isNotEmpty(svcDetail.getReasonText())) {
            core.setReasonText(svcDetail.getReasonText());
        }
    }

    /**
     * 特殊类目或无效 activityId 不参与活动定义预热，避免占位 id 污染批量查询。
     *
     * @param categoryEnum 诊疗类目
     * @param activityId   活动定义主键
     * @return true 表示可加入 catalog 预热集合
     */
    private boolean shouldWarmActivityDefinition(String categoryEnum, Long activityId) {
        if (activityId == null || activityId <= 0L) {
            return false;
        }
        if (this.isSpecialServiceCategory(categoryEnum)) {
            return false;
        }
        return true;
    }

    /**
     * 批量解析国临术式名称（护士列表术式行）。
     *
     * @param procedureDefinitionIds PD 主键集合
     * @return id → name
     */
    private Map<Long, String> mapProcedureDefinitionNames(Set<Long> procedureDefinitionIds) {
        Map<Long, String> result = new HashMap<>();
        if (procedureDefinitionIds == null || procedureDefinitionIds.isEmpty()) {
            return result;
        }
        Map<Long, ProcedureDefinitionSearchItemQueryModel> items =
            procedureDefinitionQueryService.mapSearchItemsByIds(procedureDefinitionIds);
        for (Map.Entry<Long, ProcedureDefinitionSearchItemQueryModel> entry : items.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(entry.getValue().getName())) {
                result.put(entry.getKey(), entry.getValue().getName());
            }
        }
        return result;
    }

    /**
     * 出院/转科等占位 activity_id 不参与活动定义 catalog 回落。
     *
     * @param orderActivityId 骨架 activityId
     * @param svcDetail       诊疗明细
     * @param serviceCategory 已解析类目
     * @return 可查询 catalog 的 activityId；特殊类目时为 null
     */
    private Long resolveCatalogActivityId(
        Long orderActivityId,
        ServiceRequestListModel svcDetail,
        String serviceCategory) {
        if (this.isSpecialServiceCategory(serviceCategory)) {
            return null;
        }
        Long candidate = orderActivityId;
        if (svcDetail != null && svcDetail.getActivityId() != null) {
            candidate = svcDetail.getActivityId();
        }
        if (candidate == null || candidate <= 0L) {
            return null;
        }
        return candidate;
    }

    /**
     * 收集开立人/录入人/执行库位 id，供批量姓名/库位名解析。
     */
    private void collectPractitionerAndLocation(
        Long requesterId,
        Long recorderId,
        Long locationId,
        Set<Long> practitionerIds,
        Set<Long> locationIds) {
        if (requesterId != null) {
            practitionerIds.add(requesterId);
        }
        if (recorderId != null) {
            practitionerIds.add(recorderId);
        }
        if (locationId != null) {
            locationIds.add(locationId);
        }
    }

    /**
     * 批量加载药品/活动产品读模型（展示名回落）。
     */
    private Map<String, ProductDefinitionDisplayDto> batchLoadProducts(Set<Long> medDefIds, Set<Long> activityDefIds) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long id : medDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        for (Long id : activityDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 批量解析医务人员姓名。
     */
    private Map<Long, String> loadPractitionerNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<Practitioner> list = practitionerQueryService.findAllByIds(ids);
        for (Practitioner p : list) {
            if (p != null && p.getId() != null) {
                map.put(p.getId(), p.getName());
            }
        }
        return map;
    }

    /**
     * 请求状态：域明细优先于 OrderInfo 骨架（停嘱后投影未追上时仍以写表为准）。
     */
    private String resolveRequestStatusCode(
        InpatientOrderListSkeleton order,
        MedicationRequestListQueryModel medDetail,
        ServiceRequestListModel svcDetail) {
        if (order == null) {
            return null;
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            if (medDetail != null && StringUtils.isNotEmpty(medDetail.getStatusEnum())) {
                return medDetail.getStatusEnum();
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getStatusEnum())) {
                return svcDetail.getStatusEnum();
            }
        }
        return order.getStatusEnum();
    }

    /**
     * RequestStatus code → 展示文案。
     */
    private String resolveRequestStatusDisplay(String statusCode) {
        if (StringUtils.isEmpty(statusCode)) {
            return null;
        }
        RequestStatus status = EnumUtil.fromCode(RequestStatus.class, statusCode);
        if (status != null) {
            return status.getDisplay();
        }
        return null;
    }

    /**
     * 护士站列表「类型」列展示：按 orderType + 目录类目解析，与中西药 Tab 解耦。
     *
     * <p>检验/检查/治疗/手术/麻醉等须展示对应目录类别，禁止一律回落「诊疗」。
     * 术式挂靠 {@code procedureDefinitionId} 时强制「手术」（activity_id=0 亦可识别）。
     * 嘱托（{@code category_enum=52}）须展示「嘱托」（BR-IP-ORD-028 / BR-IP-AUD-006）。</p>
     *
     * @param orderType              MED_REQUEST / SERVICE_REQUEST
     * @param categoryEnum           诊疗类目 code；药嘱可 null
     * @param procedureDefinitionId  术式 PD 主键；非术式可 null
     * @return 类型列文案
     */
    public String resolveOrderKindDisplay(
        String orderType, String categoryEnum, Long procedureDefinitionId) {
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(orderType)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_MEDICATION;
        }
        if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(orderType)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_SERVICE;
        }
        // 术式：挂靠 PD 即手术，不依赖类目码是否已写 24
        if (procedureDefinitionId != null) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_PROCEDURE;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_DISCHARGE;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_TRANSFER;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_NURSING;
        }
        // 嘱托：类型列独立展示，不得归入「诊疗」（BR-IP-ORD-028）
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_ENTRUST.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_ENTRUST;
        }
        // 检验
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_PROOF;
        }
        // 检查
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_TEST.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_TEST;
        }
        // 治疗
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_TREATMENT.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_TREATMENT;
        }
        // 手术（含非术式手术类 SR）
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_PROCEDURE;
        }
        // 麻醉
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryEnum)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_ANESTHESIA;
        }
        // 未识别类目仍回落「诊疗」，避免空白
        return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_SERVICE;
    }

    /**
     * 治疗类型 code → 展示文案；非法码 null。
     *
     * @param therapyCode 持久化治疗类型码
     * @return 展示文案
     */
    private String resolveTherapyDisplay(String therapyCode) {
        if (StringUtils.isEmpty(therapyCode)) {
            return null;
        }
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, therapyCode);
        if (therapyType != null) {
            return therapyType.getDisplay();
        }
        return null;
    }

    /**
     * 是否停嘱待校：STOPPED 且长期且尚未写停嘱校对痕迹。
     *
     * @param statusEnum           请求状态 code
     * @param therapyEnum          长临 code
     * @param stopPerformerCheckId 停嘱校对人；空表示尚未停嘱校对
     * @return true 表示停嘱待校；否则 false
     */
    private Boolean resolveStopAuditPending(
        String statusEnum,
        String therapyEnum,
        Long stopPerformerCheckId) {
        if (!RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return Boolean.FALSE;
        }
        if (!TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            return Boolean.FALSE;
        }
        if (stopPerformerCheckId != null) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    /**
     * 药嘱剂量展示：委托 {@link MedicationDosageDisplayAssembler}
     * （最小单位为主；输液/注射目录主显计量单位）；无 dose 时回落 quantity+unit。
     *
     * @param dose                单次计量剂量
     * @param doseUnitCode        剂量单位码
     * @param doseUnitLabelByCode 剂量单位术语
     * @param quantity            开立数量（回落）
     * @param unitCode            开立单位码
     * @param unitLabelByCode     开立单位术语
     * @param productRow          药品目录（含 injectFlag / 换算比）
     * @return 剂量展示串；无法装配时可为 null
     */
    private String formatDosageDisplay(
        BigDecimal dose,
        String doseUnitCode,
        Map<String, String> doseUnitLabelByCode,
        BigDecimal quantity,
        String unitCode,
        Map<String, String> unitLabelByCode,
        ProductDefinitionDisplayDto productRow) {
        // 有单次剂量：走统一「计量（最小单位）」文案；单位相同时不拼括号
        if (dose != null) {
            return MedicationDosageDisplayAssembler.formatDosageDisplay(
                dose, doseUnitCode, doseUnitLabelByCode, productRow);
        }
        return this.formatQuantityUnitDisplay(quantity, unitCode, unitLabelByCode, productRow);
    }

    /**
     * 解析首日用量单位展示名：剂量术语优先，未命中再走开立单位术语/目录字典（支/瓶等常落 UNIT_CODE）。
     *
     * @param firstDoseUnitCode   首日用量单位编码；可空
     * @param doseUnitLabelByCode 剂量单位术语映射
     * @param unitLabelByCode     开立单位术语映射
     * @param productRow          药品目录读模型；可空
     * @return 展示名；无法解析时为 null
     */
    private String resolveFirstDoseUnitDisplayName(
        String firstDoseUnitCode,
        Map<String, String> doseUnitLabelByCode,
        Map<String, String> unitLabelByCode,
        ProductDefinitionDisplayDto productRow) {
        if (StringUtils.isEmpty(firstDoseUnitCode)) {
            return null;
        }
        // Pre：先按剂量单位术语解析（与医生站列表 firstDoseUnitName 同入口）
        String doseUnitName = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
            doseUnitLabelByCode, firstDoseUnitCode, productRow);
        if (StringUtils.isNotEmpty(doseUnitName)) {
            return doseUnitName.trim();
        }
        // 回落：开立/包装/拆零单位术语 + 产品字典（首日量单位常为支/瓶等非剂量码）
        return ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            unitLabelByCode, firstDoseUnitCode, productRow);
    }

    /**
     * 开立总量展示：仅临时/出院带药（BR-MRQ-009 总量语义）；长期返回 null。
     *
     * @param therapyCode         长临 code
     * @param quantity            申请 quantity
     * @param unitCode            申请 unit_code
     * @param unitLabelByCode     单位术语
     * @param productRow          药品目录（单位回落）
     * @return 如 {@code 1盒}；非总量 therapy 或数量空时 null
     */
    private String buildOrderTotalQuantityDisplay(
        String therapyCode,
        BigDecimal quantity,
        String unitCode,
        Map<String, String> unitLabelByCode,
        ProductDefinitionDisplayDto productRow) {
        String therapy = StringUtils.trimToEmpty(therapyCode);
        if (!TherapyType.TEMPORARY.getCode().equals(therapy)
            && !TherapyType.DISCHARGE_MEDICATION.getCode().equals(therapy)) {
            return null;
        }
        return this.formatQuantityUnitDisplay(quantity, unitCode, unitLabelByCode, productRow);
    }

    /**
     * 长期药日剂量友好文案（每日三片）；非长期或无法解析每日次数时返回 null。
     *
     * <p>有分次序列时各段求和（禁止单值×次数；BR-IP-EXE-040）。
     * 输液/注射目录与 {@code dosageDisplay} 同走计量单位（每日500ml，禁止每日2瓶）。</p>
     *
     * @param dose                    单次计量剂量
     * @param doseUnitCode            剂量单位码
     * @param doseSchedule            分次计量串；可空
     * @param quantitySchedule        分次拆零串；可空
     * @param unitCode                开立单位码
     * @param rateCode                频次码
     * @param therapyCode             长临 code
     * @param doseUnitLabelByCode     剂量单位术语
     * @param unitLabelByCode         开立单位术语
     * @param timesPerDayByRateCode   频次→每日次数
     * @param productRow              药品目录（injectFlag / 换算比）
     * @return 如「每日三片」；无法装配时 null
     */
    private String buildDailyDoseDisplay(
        BigDecimal dose,
        String doseUnitCode,
        String doseSchedule,
        String quantitySchedule,
        String unitCode,
        String rateCode,
        String therapyCode,
        Map<String, String> doseUnitLabelByCode,
        Map<String, String> unitLabelByCode,
        Map<String, Integer> timesPerDayByRateCode,
        ProductDefinitionDisplayDto productRow
    ) {
        if (!TherapyType.LONG_TERM.getCode().equals(StringUtils.trimToEmpty(therapyCode))) {
            return null;
        }
        String doseUnitName = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
            doseUnitLabelByCode, doseUnitCode, productRow);
        String quantityUnitName = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            unitLabelByCode, unitCode, productRow);
        Integer timesPerDay = null;
        if (timesPerDayByRateCode != null && StringUtils.isNotEmpty(rateCode)) {
            timesPerDay = timesPerDayByRateCode.get(rateCode);
        }
        MedicationDosageDisplayAssembler.MedicationDosageDisplayResult dosageResult =
            MedicationDosageDisplayAssembler.build(dose, doseUnitCode, doseUnitLabelByCode, productRow);
        // 输液/注射：与 dosageDisplay 同走计量单位（每日500ml，禁止每日2瓶）
        BigDecimal perDoseQty = dosageResult.resolveFriendlyPerDoseQty(dose);
        String perDoseUnit = dosageResult.resolveFriendlyPerDoseUnitName(doseUnitName);
        // 输液计量主显：跳过拆零 schedule，避免「每日2瓶」盖住计量日剂量
        String quantityScheduleForDaily = quantitySchedule;
        String quantityUnitForDaily = quantityUnitName;
        if (dosageResult.isPreferDoseUnitDisplay()) {
            quantityScheduleForDaily = null;
            quantityUnitForDaily = null;
        }
        return MedicationDailyDoseDisplayAssembler.buildDailyDoseDisplayPreferringSchedule(
            quantityScheduleForDaily,
            quantityUnitForDaily,
            doseSchedule,
            doseUnitName,
            perDoseQty,
            perDoseUnit,
            timesPerDay);
    }

    /**
     * 分次剂量序列展示：各段拼单位，如 {@code 2mg,1mg}（BR-IP-EXE-040）。
     *
     * @param doseSchedule 计量逗号串
     * @param unitName     单位展示名；可空
     * @return 序列文案
     */
    private String buildDoseScheduleDisplay(String doseSchedule, String unitName) {
        if (StringUtils.isEmpty(doseSchedule)) {
            return null;
        }
        String[] parts = doseSchedule.split(",");
        StringBuilder sb = new StringBuilder();
        String unit = StringUtils.trimToEmpty(unitName);
        for (int i = 0; i < parts.length; i++) {
            String seg = StringUtils.trimToEmpty(parts[i]);
            if (seg.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(seg);
            if (!unit.isEmpty()) {
                sb.append(unit);
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 数量+单位拼接展示。
     */
    private String formatQuantityUnitDisplay(
        BigDecimal quantity,
        String unitCode,
        Map<String, String> unitLabelByCode,
        ProductDefinitionDisplayDto productRow) {
        if (quantity == null) {
            return null;
        }
        String qtyText = quantity.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        String unit = ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            unitLabelByCode, unitCode, productRow);
        if (StringUtils.isEmpty(unit) && "EA".equalsIgnoreCase(StringUtils.trimToEmpty(unitCode))) {
            unit = ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT;
        }
        if (StringUtils.isNotEmpty(unit)) {
            return qtyText + unit;
        }
        return qtyText;
    }

    /**
     * 皮试药行回填最新登记结果（仅 {@code skin_test_flag=true} 且 Allergy 已存在时写值）。
     *
     * @param core      展示内核
     * @param requestId 药品申请主键
     * @param ctx       预热上下文（含皮试 outcome 索引）
     */
    private void applySkinTestOutcomeToCore(
        NurseStationOrderViewDisplayCore core,
        Long requestId,
        NurseStationOrderViewDisplayContext ctx) {
        if (core == null || requestId == null || ctx == null) {
            return;
        }
        if (!Boolean.TRUE.equals(core.getSkinTestFlag())) {
            return;
        }
        Map<Long, SkinTestOutcomeSnapshot> outcomeByMedReqId = ctx.getSkinTestOutcomeByMedicationRequestId();
        if (outcomeByMedReqId == null || outcomeByMedReqId.isEmpty()) {
            return;
        }
        SkinTestOutcomeSnapshot outcome = outcomeByMedReqId.get(requestId);
        if (outcome == null || StringUtils.isEmpty(outcome.getOutcomeCode())) {
            return;
        }
        core.setSkinTestOutcomeCode(outcome.getOutcomeCode());
        core.setSkinTestOutcomeText(outcome.getOutcomeText());
    }

    /**
     * 护士站展示用医嘱备注：过滤套餐子项覆盖 JSON（历史曾写入 remark 列）。
     *
     * @param remark 原始 remark
     * @return 可展示备注；空或 JSON 覆盖时 null
     */
    private String resolveOrderRemarkForDisplay(String remark) {
        String text = trimToNull(remark);
        if (text == null) {
            return null;
        }
        char first = text.charAt(0);
        // 套餐子项覆盖 JSON 不以医嘱备注展示
        if (first == '{' || first == '[') {
            return null;
        }
        return text;
    }
}
