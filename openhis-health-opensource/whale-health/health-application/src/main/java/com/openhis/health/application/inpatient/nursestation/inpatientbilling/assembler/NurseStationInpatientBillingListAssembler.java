/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricedOrderRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationSupplementParentOrderRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.openhis.health.application.inpatient.nursestation.orderaudit.support.NurseStationOrderViewListAssembler;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayContext;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewDisplayCore;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderViewListDetailIndex;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-IP-PRC-03/05 列表装配（委托 {@link NurseStationOrderViewListAssembler}）。
 *
 * <p>已划价：ProductDefinition 批量展示名 + Charge/Dispense 状态 + {@code based_on_*}；补费父医嘱：与医嘱视图同构展示核心。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationInpatientBillingListAssembler {

    private final NurseStationOrderViewListAssembler orderViewListAssembler;
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 已划价列表行展示事实（产品名、数量、单位、患者；OrderInfo 缺字段时回源写表）。
     */
    @Getter
    @Setter
    public static class PricedOrderRowFacts {
        private Long patientId;
        private BigDecimal quantity;
        private String unitCode;
        private String unitDisplay;
        private String productDisplayName;
        /** 上级医嘱来源表（补费；OrderInfo 缺省时回源写表）。 */
        private String basedOnTable;
        /** 上级医嘱来源主键（补费）。 */
        private Long basedOnId;
    }

    /**
     * 已划价列表：批量补齐展示字段（OrderInfo + 域查询 + ProductDefinition）。
     *
     * @param skeleton OrderInfo 护士划价骨架
     * @return orderId → 展示事实
     */
    public Map<Long, PricedOrderRowFacts> buildPricedRowFactsByOrderId(List<InpatientOrderListSkeleton> skeleton) {
        Map<Long, PricedOrderRowFacts> factsByOrderId = new HashMap<>();
        if (skeleton == null || skeleton.isEmpty()) {
            return factsByOrderId;
        }
        Set<Long> serviceIds = new LinkedHashSet<>();
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (InpatientOrderListSkeleton row : skeleton) {
            if (row == null || row.getOrderId() == null) {
                continue;
            }
            if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                serviceIds.add(row.getOrderId());
            } else if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                deviceIds.add(row.getOrderId());
            }
        }
        Map<Long, ServiceRequestListModel> serviceById = serviceRequestQueryService.mapListModelsByIds(serviceIds);
        Map<Long, DeviceRequestListModel> deviceById = deviceRequestQueryService.mapListModelsByIds(deviceIds);
        Set<Long> activityIds = new LinkedHashSet<>();
        Set<Long> deviceDefIds = new LinkedHashSet<>();
        for (InpatientOrderListSkeleton row : skeleton) {
            if (row == null || row.getOrderId() == null) {
                continue;
            }
            PricedOrderRowFacts facts = new PricedOrderRowFacts();
            if (row.getPatientId() != null) {
                facts.setPatientId(row.getPatientId());
            }
            if (row.getQuantity() != null) {
                facts.setQuantity(row.getQuantity());
            }
            if (StringUtils.isNotEmpty(row.getUnitCode())) {
                facts.setUnitCode(row.getUnitCode());
            }
            if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                ServiceRequestListModel svc = serviceById.get(row.getOrderId());
                if (StringUtils.isNotEmpty(row.getBasedOnTable())) {
                    facts.setBasedOnTable(row.getBasedOnTable());
                }
                if (row.getBasedOnId() != null) {
                    facts.setBasedOnId(row.getBasedOnId());
                }
                if (svc != null) {
                    if (facts.getPatientId() == null) {
                        facts.setPatientId(svc.getPatientId());
                    }
                    if (facts.getQuantity() == null) {
                        facts.setQuantity(svc.getQuantity());
                    }
                    if (StringUtils.isEmpty(facts.getUnitCode())) {
                        facts.setUnitCode(svc.getUnitCode());
                    }
                    if (StringUtils.isEmpty(facts.getBasedOnTable()) && StringUtils.isNotEmpty(svc.getBasedOnTable())) {
                        facts.setBasedOnTable(svc.getBasedOnTable());
                    }
                    if (facts.getBasedOnId() == null && svc.getBasedOnId() != null) {
                        facts.setBasedOnId(svc.getBasedOnId());
                    }
                    Long activityId = row.getActivityId();
                    if (activityId == null) {
                        activityId = svc.getActivityId();
                    }
                    if (activityId != null) {
                        activityIds.add(activityId);
                    }
                } else if (row.getActivityId() != null) {
                    activityIds.add(row.getActivityId());
                }
            } else if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                DeviceRequestListModel dev = deviceById.get(row.getOrderId());
                if (StringUtils.isNotEmpty(row.getDevBasedOnTable())) {
                    facts.setBasedOnTable(row.getDevBasedOnTable());
                } else if (StringUtils.isNotEmpty(row.getBasedOnTable())) {
                    facts.setBasedOnTable(row.getBasedOnTable());
                }
                if (row.getDevBasedOnId() != null) {
                    facts.setBasedOnId(row.getDevBasedOnId());
                } else if (row.getBasedOnId() != null) {
                    facts.setBasedOnId(row.getBasedOnId());
                }
                if (dev != null) {
                    if (facts.getPatientId() == null) {
                        facts.setPatientId(dev.getPatientId());
                    }
                    if (facts.getQuantity() == null) {
                        facts.setQuantity(dev.getQuantity());
                    }
                    if (StringUtils.isEmpty(facts.getUnitCode())) {
                        facts.setUnitCode(dev.getUnitCode());
                    }
                    if (StringUtils.isEmpty(facts.getBasedOnTable()) && StringUtils.isNotEmpty(dev.getBasedOnTable())) {
                        facts.setBasedOnTable(dev.getBasedOnTable());
                    }
                    if (facts.getBasedOnId() == null && dev.getBasedOnId() != null) {
                        facts.setBasedOnId(dev.getBasedOnId());
                    }
                    Long deviceDefId = row.getDeviceDefId();
                    if (deviceDefId == null) {
                        deviceDefId = dev.getDeviceDefId();
                    }
                    if (deviceDefId != null) {
                        deviceDefIds.add(deviceDefId);
                    }
                } else if (row.getDeviceDefId() != null) {
                    deviceDefIds.add(row.getDeviceDefId());
                }
            }
            factsByOrderId.put(row.getOrderId(), facts);
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long id : activityIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        for (Long id : deviceDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products;
        if (lookups.isEmpty()) {
            products = Map.of();
        } else {
            products = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        }
        for (InpatientOrderListSkeleton row : skeleton) {
            if (row == null || row.getOrderId() == null) {
                continue;
            }
            PricedOrderRowFacts facts = factsByOrderId.get(row.getOrderId());
            if (facts == null) {
                continue;
            }
            String table;
            Long defId;
            if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                table = TableNameConstants.WKF_ACTIVITY_DEFINITION;
                defId = row.getActivityId();
                if (defId == null) {
                    ServiceRequestListModel svc = serviceById.get(row.getOrderId());
                    if (svc != null) {
                        defId = svc.getActivityId();
                    }
                }
            } else if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                table = TableNameConstants.ADM_DEVICE_DEFINITION;
                defId = row.getDeviceDefId();
                if (defId == null) {
                    DeviceRequestListModel dev = deviceById.get(row.getOrderId());
                    if (dev != null) {
                        defId = dev.getDeviceDefId();
                    }
                }
            } else {
                continue;
            }
            if (defId == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(table, defId);
            ProductDefinitionDisplayDto product = products.get(key);
            if (product != null) {
                facts.setProductDisplayName(product.getProductName());
                facts.setUnitDisplay(this.resolveUnitDisplay(product, facts.getUnitCode()));
            }
        }
        return factsByOrderId;
    }

    /**
     * 按产品读模型与单位 code 解析单位展示名。
     *
     * @param product  产品定义读模型
     * @param unitCode 单位 code
     * @return 展示名；无匹配时返回 null
     */
    private String resolveUnitDisplay(ProductDefinitionDisplayDto product, String unitCode) {
        if (product == null || StringUtils.isEmpty(unitCode)) {
            return null;
        }
        if (unitCode.equals(product.getPackageUnitCode())) {
            return product.getPackageUnitCodeDictText();
        }
        if (unitCode.equals(product.getSplitUnitCode())) {
            return product.getSplitUnitCodeDictText();
        }
        if (unitCode.equals(product.getPurchaseUnitCode())) {
            return product.getPurchaseUnitCodeDictText();
        }
        return null;
    }

    /**
     * 补费父医嘱分组列表（按 encounter 分组，组内按开立时间倒序）。
     *
     * @param skeleton       OrderInfo 父医嘱骨架
     * @param medByEncounter 就诊 → 药品明细
     * @param svcByEncounter 就诊 → 诊疗明细
     * @return 按患者/就诊分组的父医嘱行
     */
    public List<NurseStationPricingPatientGroupDto> assembleSupplementParentGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter,
        Map<Long, List<ServiceRequestListModel>> svcByEncounter) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        // 与医嘱视图共用索引与展示上下文，保证补费列表与校对/执行口径一致
        NurseStationOrderViewListDetailIndex index =
            orderViewListAssembler.indexEncounterDetails(skeleton, medByEncounter, svcByEncounter);
        NurseStationOrderViewDisplayContext ctx = orderViewListAssembler.prepareDisplayContext(index);
        Map<Long, List<NurseStationSupplementParentOrderRowDto>> byEncounter = new LinkedHashMap<>();
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            // 转科/出院/护理类不经医嘱计费 Tab 补费（与执行/ATD 同源；护理费由自动滚费 BR-IP-ARG-009 驱动）
            if (this.shouldExcludeSupplementParentOrder(order, index)) {
                continue;
            }
            NurseStationSupplementParentOrderRowDto row = this.mapSupplementRow(order, index, ctx);
            if (row == null) {
                continue;
            }
            byEncounter.computeIfAbsent(order.getEncounterId(), k -> new ArrayList<>()).add(row);
        }
        List<NurseStationPricingPatientGroupDto> groups = new ArrayList<>();
        for (Map.Entry<Long, List<NurseStationSupplementParentOrderRowDto>> entry : byEncounter.entrySet()) {
            NurseStationPricingPatientGroupDto group = new NurseStationPricingPatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(this.sortSupplementRows(entry.getValue()));
            groups.add(group);
        }
        return groups;
    }

    /**
     * 已划价列表分组（Charge 金额、划价模式、耗材发放状态）。
     *
     * @param skeleton               OrderInfo 已划价骨架
     * @param chargeByServiceId      服务 id → Charge 列表
     * @param dispenseIdsByDeviceReqId 耗材请求 id → 发放 id 列表
     * @param primaryDispenseStatusByDeviceReqId 耗材请求 id → 主发放状态码
     * @param pricedRowFactsByOrderId orderId → 展示事实
     * @return 按 encounter 分组的已划价行
     */
    public List<NurseStationPricingPatientGroupDto> assemblePricedOrderGroups(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, List<ChargeItem>> chargeByServiceId,
        Map<Long, List<Long>> dispenseIdsByDeviceReqId,
        Map<Long, String> primaryDispenseStatusByDeviceReqId,
        Map<Long, PricedOrderRowFacts> pricedRowFactsByOrderId) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<NurseStationPricedOrderRowDto>> byEncounter = new LinkedHashMap<>();
        for (InpatientOrderListSkeleton order : skeleton) {
            if (order == null || order.getEncounterId() == null) {
                continue;
            }
            NurseStationPricedOrderRowDto row = this.mapPricedRow(
                order, chargeByServiceId, dispenseIdsByDeviceReqId, primaryDispenseStatusByDeviceReqId,
                pricedRowFactsByOrderId);
            if (row == null) {
                continue;
            }
            byEncounter.computeIfAbsent(order.getEncounterId(), k -> new ArrayList<>()).add(row);
        }
        List<NurseStationPricingPatientGroupDto> groups = new ArrayList<>();
        for (Map.Entry<Long, List<NurseStationPricedOrderRowDto>> entry : byEncounter.entrySet()) {
            NurseStationPricingPatientGroupDto group = new NurseStationPricingPatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setPricedOrders(this.sortPricedRows(entry.getValue()));
            groups.add(group);
        }
        return groups;
    }

    /**
     * 补费父医嘱是否排除（转科/出院/护理类与 IF-IP-EXE-01、ATD-03 同源）。
     *
     * @param order 列表骨架
     * @param index 医嘱明细索引
     * @return true 表示不进入 IF-IP-PRC-03 列表
     */
    private boolean shouldExcludeSupplementParentOrder(
        InpatientOrderListSkeleton order,
        NurseStationOrderViewListDetailIndex index) {
        if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            return false;
        }
        Long requestId = orderViewListAssembler.resolveRequestId(order);
        ServiceRequestListModel svcDetail = index.getSvcDetailById().get(requestId);
        String serviceCategory = orderViewListAssembler.resolveServiceCategoryEnum(order, svcDetail);
        return orderViewListAssembler.isExcludedFromNurseStationExecuteList(serviceCategory);
    }

    /**
     * 单条父医嘱骨架 → 补费列表行（委托 OrderView 展示核心）。
     *
     * @param order   OrderInfo 行
     * @param index   就诊明细索引
     * @param ctx     展示上下文
     * @return 补费父医嘱行；非药/诊类型返回 null
     */
    private NurseStationSupplementParentOrderRowDto mapSupplementRow(
        InpatientOrderListSkeleton order,
        NurseStationOrderViewListDetailIndex index,
        NurseStationOrderViewDisplayContext ctx) {
        Long requestId = orderViewListAssembler.resolveRequestId(order);
        NurseStationOrderViewDisplayCore core;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildMedicationDisplayCore(
                order, index.getMedDetailById().get(requestId), ctx);
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
            core = orderViewListAssembler.buildServiceDisplayCore(
                order, index.getSvcDetailById().get(requestId), ctx);
        } else {
            return null;
        }
        if (core == null) {
            return null;
        }
        NurseStationSupplementParentOrderRowDto row = new NurseStationSupplementParentOrderRowDto();
        row.setListRowKey(core.getListRowKey());
        row.setRequestId(core.getRequestId());
        row.setOrderType(core.getOrderType());
        row.setEncounterId(core.getEncounterId());
        row.setPatientId(core.getPatientId());
        row.setStatusEnum(core.getStatusEnum());
        row.setStatusDisplay(core.getStatusDisplay());
        row.setTherapyEnum(core.getTherapyEnum());
        row.setTherapyDisplay(core.getTherapyDisplay());
        row.setAdviceDisplayName(core.getAdviceDisplayName());
        row.setSpecificationText(core.getSpecificationText());
        row.setDosageDisplay(core.getDosageDisplay());
        row.setUsageDisplay(core.getUsageDisplay());
        row.setFrequencyDisplay(core.getFrequencyDisplay());
        row.setReqOpenedTime(core.getReqOpenedTime());
        row.setReqAuthoredTime(core.getReqAuthoredTime());
        row.setStopTime(core.getStopTime());
        row.setRequesterName(core.getRequesterName());
        row.setStopperId(core.getStopperId());
        row.setStopperName(core.getStopperName());
        row.setPerformerLocationDisplay(core.getPerformerLocationDisplay());
        row.setCategoryEnum(core.getCategoryEnum());
        row.setGroupId(core.getGroupId());
        row.setGroupNo(core.getGroupNo());
        row.setSortNumber(core.getSortNumber());
        return row;
    }

    /**
     * 单条已划价骨架 → 列表行（Charge、模式、父医嘱摘要、耗材发放）。
     *
     * @param order                    OrderInfo 行
     * @param chargeByServiceId        服务 id → Charge
     * @param dispenseIdsByDeviceReqId 耗材发放索引
     * @param primaryDispenseStatusByDeviceReqId 主发放状态码
     * @param pricedRowFactsByOrderId   展示事实
     * @return 已划价行
     */
    private NurseStationPricedOrderRowDto mapPricedRow(
        InpatientOrderListSkeleton order,
        Map<Long, List<ChargeItem>> chargeByServiceId,
        Map<Long, List<Long>> dispenseIdsByDeviceReqId,
        Map<Long, String> primaryDispenseStatusByDeviceReqId,
        Map<Long, PricedOrderRowFacts> pricedRowFactsByOrderId) {
        Long serviceId = order.getOrderId();
        if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(order.getOrderType())) {
            serviceId = order.getOrderId();
        }
        // Charge 与 service_request / device_request 同 id 关联；读侧已 excludeSupersededSettledLines，此处仅按状态择优
        List<ChargeItem> charges = chargeByServiceId.get(serviceId);
        // 净额已冲销为 0 的划价行不进已划价列表（BR-IP-PRC-024 冲销后剔除）
        if (this.isNursePricingFullyOffset(charges)) {
            return null;
        }
        ChargeItem charge = this.resolvePrimaryLinkedCharge(charges);
        NurseStationPricedOrderRowDto row = new NurseStationPricedOrderRowDto();
        row.setListRowKey(String.valueOf(order.getOrderId()) + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR
            + order.getOrderType());
        row.setOrderId(order.getOrderId());
        row.setOrderType(order.getOrderType());
        row.setEncounterId(order.getEncounterId());
        PricedOrderRowFacts facts = null;
        if (pricedRowFactsByOrderId != null) {
            facts = pricedRowFactsByOrderId.get(order.getOrderId());
        }
        if (facts != null && facts.getPatientId() != null) {
            row.setPatientId(facts.getPatientId());
        } else {
            row.setPatientId(order.getPatientId());
        }
        if (facts != null && StringUtils.isNotEmpty(facts.getProductDisplayName())) {
            row.setProductDisplayName(facts.getProductDisplayName());
        } else {
            row.setProductDisplayName(order.getOrderType());
        }
        if (facts != null && facts.getQuantity() != null) {
            row.setQuantity(facts.getQuantity());
        } else {
            row.setQuantity(order.getQuantity());
        }
        if (facts != null && StringUtils.isNotEmpty(facts.getUnitCode())) {
            row.setUnitCode(facts.getUnitCode());
        } else {
            row.setUnitCode(order.getUnitCode());
        }
        if (facts != null && StringUtils.isNotEmpty(facts.getUnitDisplay())) {
            row.setUnitDisplay(facts.getUnitDisplay());
        }
        row.setPricedTime(this.resolvePricedTime(order, charge));
        if (charge != null) {
            row.setUnitPrice(charge.getUnitPrice());
            row.setTotalPrice(charge.getTotalPrice());
            row.setChargeStatusEnum(charge.getStatusEnum());
            ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, charge.getStatusEnum());
            if (status != null) {
                row.setChargeStatusDisplay(status.getDisplay());
            }
        }
        // 无 based_on 为直接划价，否则为医嘱补费并挂父医嘱展示名
        String modeCode = nurseStationInpatientBillingMapper.resolvePricingMode(order);
        row.setPricingModeCode(modeCode);
        if (InpatientNurseStationConstants.PRICING_MODE_DIRECT.equals(modeCode)) {
            row.setPricingModeDisplay("直接划价");
        } else {
            row.setPricingModeDisplay("医嘱补费");
            String basedOnTable = null;
            Long basedOnId = null;
            if (facts != null && StringUtils.isNotEmpty(facts.getBasedOnTable())) {
                basedOnTable = facts.getBasedOnTable();
            }
            if (facts != null && facts.getBasedOnId() != null) {
                basedOnId = facts.getBasedOnId();
            }
            if (StringUtils.isEmpty(basedOnTable)) {
                if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(order.getOrderType())
                    && StringUtils.isNotEmpty(order.getDevBasedOnTable())) {
                    basedOnTable = order.getDevBasedOnTable();
                } else {
                    basedOnTable = order.getBasedOnTable();
                }
            }
            if (basedOnId == null) {
                if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(order.getOrderType())
                    && order.getDevBasedOnId() != null) {
                    basedOnId = order.getDevBasedOnId();
                } else {
                    basedOnId = order.getBasedOnId();
                }
            }
            row.setBasedOnTable(basedOnTable);
            row.setBasedOnId(basedOnId);
        }
        if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(order.getOrderType())) {
            List<Long> dispenseIds = dispenseIdsByDeviceReqId.get(serviceId);
            if (dispenseIds != null && !dispenseIds.isEmpty()) {
                String statusCode = null;
                if (primaryDispenseStatusByDeviceReqId != null) {
                    statusCode = primaryDispenseStatusByDeviceReqId.get(serviceId);
                }
                if (statusCode == null) {
                    statusCode = DispenseStatus.PREPARATION.getCode();
                }
                row.setDispenseStatusEnum(statusCode);
                DispenseStatus status = EnumUtil.fromCode(DispenseStatus.class, statusCode);
                if (status != null) {
                    row.setDispenseStatusDisplay(status.getDisplay());
                }
            }
        }
        return row;
    }

    /**
     * 同锚点多条 Charge 取列表展示行：状态不同则优先 {@code PENDING_SETTLEMENT}（反结再生），不比 id。
     *
     * <p>正常路径下 AppService 已 {@code excludeSupersededSettledLines=true}，同锚点应只剩一条有效行；
     * 若过滤未完全生效，仍按状态兜底避免绑定源 {@code SETTLED}。</p>
     *
     * @param charges 同 service_id 关联费用
     * @return 展示用费用行；无有效行时 null
     */
    /**
     * 择优关联费用：优先未冲销的正向待结算护士划价行，其次任意待结算行。
     *
     * @param charges 同锚点费用列表
     * @return 主展示费用；无则 null
     */
    private ChargeItem resolvePrimaryLinkedCharge(List<ChargeItem> charges) {
        if (charges == null || charges.isEmpty()) {
            return null;
        }
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        String nursePricing = GenerateSource.NURSE_PRICING.getCode();
        Set<Long> offsetSourceIds = new HashSet<>();
        for (ChargeItem row : charges) {
            if (row == null || row.getRefundId() == null) {
                continue;
            }
            if (!pendingCode.equals(row.getStatusEnum())) {
                continue;
            }
            offsetSourceIds.add(row.getRefundId());
        }
        for (ChargeItem row : charges) {
            if (row == null || !pendingCode.equals(row.getStatusEnum())) {
                continue;
            }
            // 优先正向未冲销 nurse-pricing 行，避免冲销负行抢占展示
            if (nursePricing.equals(row.getGenerateSourceEnum())
                && row.getRefundId() == null
                && row.getQuantityValue() != null
                && row.getQuantityValue().signum() > 0
                && !offsetSourceIds.contains(row.getId())) {
                return row;
            }
        }
        for (ChargeItem row : charges) {
            if (row != null && pendingCode.equals(row.getStatusEnum())) {
                return row;
            }
        }
        for (ChargeItem row : charges) {
            if (row != null) {
                return row;
            }
        }
        return null;
    }

    /**
     * 判定护士划价锚点费用是否已整行冲销（待结算 nurse-pricing 净额为 0）。
     *
     * @param charges 同锚点费用列表
     * @return true 表示已完全冲销，应从已划价列表剔除
     */
    private boolean isNursePricingFullyOffset(List<ChargeItem> charges) {
        if (charges == null || charges.isEmpty()) {
            return false;
        }
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        String nursePricing = GenerateSource.NURSE_PRICING.getCode();
        BigDecimal netTotal = BigDecimal.ZERO;
        boolean hasNursePricingPending = false;
        for (ChargeItem row : charges) {
            if (row == null) {
                continue;
            }
            if (!pendingCode.equals(row.getStatusEnum())) {
                continue;
            }
            if (row.getGenerateSourceEnum() == null || !nursePricing.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            hasNursePricingPending = true;
            if (row.getTotalPrice() != null) {
                netTotal = netTotal.add(row.getTotalPrice());
            }
        }
        // 无 nurse-pricing 待结算行则不按冲销剔除（可能仅有其他来源）
        if (!hasNursePricingPending) {
            return false;
        }
        return netTotal.signum() == 0;
    }

    /**
     * 划价时间：骨架 createdAt → Charge 发生时间 → 开立时间（ListModel 路径无 createdAt）。
     */
    private LocalDateTime resolvePricedTime(InpatientOrderListSkeleton order, ChargeItem charge) {
        if (order == null) {
            return null;
        }
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt();
        }
        if (charge != null && charge.getOccurrenceTime() != null) {
            return charge.getOccurrenceTime();
        }
        return order.resolveOrderOpenedTime();
    }

    /**
     * 补费父医嘱组内排序：开立时间（{@code reqOpenedTime}，空回落签发）倒序。
     */
    private List<NurseStationSupplementParentOrderRowDto> sortSupplementRows(List<NurseStationSupplementParentOrderRowDto> rows) {
        List<NurseStationSupplementParentOrderRowDto> copy = new ArrayList<>(rows);
        copy.sort(Comparator
            .comparing(this::resolveSupplementSortTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(NurseStationSupplementParentOrderRowDto::getRequestId, Comparator.nullsLast(Comparator.naturalOrder())));
        return copy;
    }

    /**
     * 补费父医嘱排序时刻：优先开立，回落签发。
     *
     * @param row 父医嘱行
     * @return 排序时刻；均空时 null
     */
    private LocalDateTime resolveSupplementSortTime(NurseStationSupplementParentOrderRowDto row) {
        if (row == null) {
            return null;
        }
        if (row.getReqOpenedTime() != null) {
            return row.getReqOpenedTime();
        }
        return row.getReqAuthoredTime();
    }

    /**
     * 已划价组内排序：划价时间降序。
     */
    private List<NurseStationPricedOrderRowDto> sortPricedRows(List<NurseStationPricedOrderRowDto> rows) {
        List<NurseStationPricedOrderRowDto> copy = new ArrayList<>(rows);
        copy.sort(Comparator.comparing(
            NurseStationPricedOrderRowDto::getPricedTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return copy;
    }
}
