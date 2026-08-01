/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.support;

import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.SkinTestCompanionDisplaySupport;
import com.openhis.health.application.shared.advice.InpatientEntrustOrderGateSupport;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.advice.OrderRequestCatalogWarmAssembler;
import com.openhis.health.application.shared.product.MedicationDosageDisplayAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.advice.OrderRequestCatalogWarmContext;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayContext;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayCore;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewListDetailIndex;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** 药/诊定义预热。 */
    private final OrderRequestCatalogWarmAssembler orderRequestCatalogWarmAssembler;

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
                    this.collectPractitionerAndLocation(row.getRequesterId(), null, row.getLocationId(),
                        index.getPractitionerIds(), index.getLocationIds());
                    if (row.getPerformerCheckId() != null) {
                        index.getPractitionerIds().add(row.getPerformerCheckId());
                    }
                    if (row.getStopperId() != null) {
                        index.getPractitionerIds().add(row.getStopperId());
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
        ctx.setProductByKey(this.batchLoadProducts(index.getMedDefIds(), index.getActivityDefIds()));
        ctx.setUnitLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE));
        ctx.setMethodLabelByCode(
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE));
        ctx.setRateLabelByCode(frequencyOptionLoader.buildRateLabelMap());
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
        return ctx;
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
        if (medDetail != null) {
            core.setDosageDisplay(this.formatDosageDisplay(medDetail.getDose(), medDetail.getDoseUnitCode(),
                ctx.getDoseUnitLabelByCode(), medDetail.getQuantity(), medDetail.getUnitCode(),
                ctx.getUnitLabelByCode(), medProduct));
            core.setUsageDisplay(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                ctx.getMethodLabelByCode(), medDetail.getMethodCode()));
            core.setFrequencyDisplay(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                ctx.getRateLabelByCode(), medDetail.getRateCode()));
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
        }
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
        core.setOrderKindDisplay(
            this.resolveOrderKindDisplay(ReadModelOrderTypeConstants.SERVICE_REQUEST, serviceCategory));
        String therapyCode = order.getTherapyEnum();
        if (svcDetail != null && StringUtils.isNotEmpty(svcDetail.getTherapyEnum())) {
            therapyCode = svcDetail.getTherapyEnum();
        }
        core.setTherapyEnum(therapyCode);
        core.setTherapyDisplay(this.resolveTherapyDisplay(therapyCode));
        Long activityId = this.resolveCatalogActivityId(order.getActivityId(), svcDetail, serviceCategory);
        if (this.isSpecialServiceCategory(serviceCategory)) {
            // 出院/转科不依赖活动定义 catalog，展示名与住院医生站列表口径一致（BR-IP-ORD-017）。
            core.setAdviceDisplayName(this.resolveSpecialServiceAdviceDisplayName(serviceCategory));
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
        } else {
            // 明细缺失时：开立可用骨架解析（含存量回落）；签发须直读列，禁止把开立回填进签发。
            core.setReqOpenedTime(order.resolveOrderOpenedTime());
            core.setReqAuthoredTime(order.getReqAuthoredTime());
            core.setStopTime(order.getStopTime());
            core.setRequesterName(ctx.getPractitionerNameById().get(order.getRequesterId()));
            core.setStopperId(order.getStopperId());
            core.setStopperName(ctx.getPractitionerNameById().get(order.getStopperId()));
        }
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
     * @return 展示名；非特殊类目时 null
     */
    public String resolveSpecialServiceAdviceDisplayName(String serviceCategory) {
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(serviceCategory)) {
            return DoctorStationAdviceConstants.ADVICE_LIST_DISCHARGE_DISPLAY_NAME_INPATIENT;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(serviceCategory)) {
            return DoctorStationAdviceConstants.ADVICE_LIST_TRANSFER_DISPLAY_NAME_INPATIENT;
        }
        return null;
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
     * TherapyType code → 展示文案。
     */
    /**
     * 护士站列表「类型」列展示：按 orderType + 诊疗类目解析，与中西药 Tab 解耦。
     *
     * <p>嘱托（{@code category_enum=52}）须展示「嘱托」，禁止回落「诊疗」（BR-IP-ORD-028 / BR-IP-AUD-006）。</p>
     *
     * @param orderType      MED_REQUEST / SERVICE_REQUEST
     * @param categoryEnum   诊疗类目 code；药嘱可 null
     * @return 类型列文案
     */
    public String resolveOrderKindDisplay(String orderType, String categoryEnum) {
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(orderType)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_MEDICATION;
        }
        if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(orderType)) {
            return InpatientNurseStationConstants.ORDER_KIND_DISPLAY_SERVICE;
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
     * 药嘱剂量展示：优先 dose+计量单位（必要时括号附最小单位），回落 quantity+unit。
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
     * 护士站展示用医嘱备注：过滤套餐子项覆盖 JSON（历史曾写入 remark 列）。
     *
     * @param remark 原始 remark
     * @return 可展示备注；空或 JSON 覆盖时 null
     */
    private String resolveOrderRemarkForDisplay(String remark) {
        String text = StringUtils.trimToNull(remark);
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
