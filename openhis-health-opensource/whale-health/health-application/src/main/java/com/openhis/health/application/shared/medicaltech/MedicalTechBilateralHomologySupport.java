/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.medicaltech;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医技协同门禁（软落地）：取消同态已废；出院仅卡医技相关费用未确费。
 *
 * <p>类名保留历史 {@code BilateralHomology}，避免大范围改注入点。口径见协同 DD 2026-09-21：
 * 护士/医技取消与签退<strong>不再</strong>互卡；{@link #assertDischargeTechPending} 改为
 * 就诊下 TECH 相关 ChargeItem 须已行级确费（{@code fee_confirmer_id}）。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechBilateralHomologySupport {

    /** 收费只读：出院确费门禁按就诊装载费用行。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 诊疗请求只读：相关判定条件①。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 耗材请求只读：相关判定条件②。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** 耗材发放只读：发放房→组织。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 位置只读：库房→组织。 */
    private final LocationQueryService locationQueryService;

    /** 组织只读：判定 TECH。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 护士站取消执行前同态预检（已废止）：空操作，保证无痛切换。
     *
     * <p>历史 BR-IP-CXL-017 / EX-IP-CXL-207 不再抛出；调用点可保留以免大改编排。</p>
     *
     * @param completedRows 待取消 COMPLETED 行（忽略）
     */
    public void assertNurseCancelHomology(Collection<ProcedureRecordQueryModel> completedRows) {
        // Gate：软落地——取消不再卡医技侧净态（入参仅保留调用方签名）
        if (completedRows == null) {
            return;
        }
        return;
    }

    /**
     * 医技站取消执行前同态预检（已废止）：空操作。
     *
     * @param completedRows 待取消本站 COMPLETED 行（忽略）
     */
    public void assertTechCancelHomology(Collection<ProcedureRecordQueryModel> completedRows) {
        // Gate：软落地——医技取消本侧不互卡护士侧（入参仅保留调用方签名）
        if (completedRows == null) {
            return;
        }
        return;
    }

    /**
     * 出院门禁：就诊下存在医技相关费用行且尚未确费时拒绝（EX-IP-ATD-309）。
     *
     * <p>相关轴与费用清单 BR-MT-FEE-002 一致，但范围是<strong>任一 TECH 科</strong>（非登录科）；
     * 另含 {@code generate_source=medical-tech-pricing}（医技划价耗材及其冲销留痕），
     * 避免仅命中诊疗执行科而漏拦医技划价耗材。</p>
     *
     * @param encounterId 就诊主键
     * @throws BusinessException 存在未确费的医技相关费用行
     */
    public void assertDischargeTechPending(Long encounterId) {
        // Gate：无就诊则跳过（由调用方已校验）
        if (encounterId == null) {
            return;
        }
        // Pre：装载本就诊全部费用
        ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
        chargeCmd.setEncounterId(encounterId);
        List<ChargeItem> allCharges = this.chargeItemQueryService.listChargeItemsByEncounterId(chargeCmd);
        // Gate：无费用行则无医技确费压力
        if (allCharges == null || allCharges.isEmpty()) {
            return;
        }
        // Pre：批量装载 SR / DR / Dispense / Location，构建相关判定索引（禁止 N+1）
        TechRelatedChargeIndex index = this.buildTechRelatedChargeIndex(encounterId, allCharges);
        // Gate：无 TECH 组织且无医技划价来源行 → 无痛跳过
        if (index.techOrgIds.isEmpty() && !this.hasMedicalTechPricingCharge(allCharges)) {
            return;
        }
        for (ChargeItem charge : allCharges) {
            if (charge == null || charge.getId() == null) {
                continue;
            }
            // 业务：非医技相关行不参与出院确费门禁
            if (!this.isRelatedToAnyTech(charge, index)) {
                continue;
            }
            // 业务：医技相关行确费人为空 → 拒绝出院
            if (charge.getFeeConfirmerId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_309,
                    "请医技确费后再办理出院");
            }
        }
    }

    /**
     * 本就诊是否存在医技划价来源费用行（含冲销继承同码）。
     *
     * @param allCharges 本就诊费用
     * @return 存在为 true
     */
    private boolean hasMedicalTechPricingCharge(List<ChargeItem> allCharges) {
        if (allCharges == null || allCharges.isEmpty()) {
            return false;
        }
        String medicalTechPricing = GenerateSource.MEDICAL_TECH_PRICING.getCode();
        for (ChargeItem charge : allCharges) {
            if (charge == null) {
                continue;
            }
            if (medicalTechPricing.equals(charge.getGenerateSourceEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按就诊预取相关判定索引（SR/DR/发放房组织 + TECH 组织集合）。
     *
     * @param encounterId 就诊主键
     * @param allCharges  本就诊已装载费用行（复用，避免二次查询）
     * @return 索引
     */
    private TechRelatedChargeIndex buildTechRelatedChargeIndex(
        Long encounterId,
        List<ChargeItem> allCharges) {
        TechRelatedChargeIndex index = new TechRelatedChargeIndex();
        List<ServiceRequestListModel> serviceRequests =
            this.serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        if (serviceRequests != null) {
            for (ServiceRequestListModel row : serviceRequests) {
                if (row != null && row.getId() != null) {
                    index.serviceById.put(row.getId(), row);
                    if (row.getOrgId() != null) {
                        index.candidateOrgIds.add(row.getOrgId());
                    }
                }
            }
        }
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounter(encounterId);
        if (deviceRequests != null) {
            for (DeviceRequestListModel row : deviceRequests) {
                if (row != null && row.getId() != null) {
                    index.deviceById.put(row.getId(), row);
                }
            }
        }
        List<DeviceDispenseListModel> deviceDispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounter(encounterId);
        index.locationOrgByDispenseId =
            this.mapDispenseIdToLocationOrganizationId(deviceRequests, deviceDispenses);
        index.locationOrgByRequestLocationId =
            this.mapRequestLocationToOrganizationId(deviceRequests);
        for (Long orgId : index.locationOrgByDispenseId.values()) {
            if (orgId != null) {
                index.candidateOrgIds.add(orgId);
            }
        }
        for (Long orgId : index.locationOrgByRequestLocationId.values()) {
            if (orgId != null) {
                index.candidateOrgIds.add(orgId);
            }
        }
        // Pre：收费行 org / requesting_org 纳入 TECH 候选（医技划价两列均落登录 TECH）
        if (allCharges != null) {
            for (ChargeItem charge : allCharges) {
                if (charge == null) {
                    continue;
                }
                if (charge.getRequestingOrgId() != null) {
                    index.candidateOrgIds.add(charge.getRequestingOrgId());
                }
                if (charge.getOrgId() != null) {
                    index.candidateOrgIds.add(charge.getOrgId());
                }
            }
        }
        Map<Long, Organization> orgById = this.organizationQueryService.mapByIds(index.candidateOrgIds);
        for (Map.Entry<Long, Organization> entry : orgById.entrySet()) {
            Organization org = entry.getValue();
            // 仅 class=TECH 进入出院确费范围
            if (org != null && org.getClassEnum() == OrganizationClassification.TECH) {
                index.techOrgIds.add(entry.getKey());
            }
        }
        return index;
    }

    /**
     * 收费行是否属任一 TECH 科相关（BR-MT-FEE-002 三条件 + 医技划价来源）。
     *
     * @param charge 收费行
     * @param index  预取索引
     * @return 相关为 true
     */
    private boolean isRelatedToAnyTech(ChargeItem charge, TechRelatedChargeIndex index) {
        // 条件④：医技划价来源（含冲销留痕）——医技划耗材主路径，不依赖库房组织 class
        if (GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(charge.getGenerateSourceEnum())) {
            return true;
        }
        // 条件①：诊疗锚点 org_id 为 TECH
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(charge.getServiceTable())
            && charge.getServiceId() != null) {
            ServiceRequestListModel sr = index.serviceById.get(charge.getServiceId());
            if (sr != null && sr.getOrgId() != null && index.techOrgIds.contains(sr.getOrgId())) {
                return true;
            }
        }
        // 条件②：耗材发放房 / 请求库房组织为 TECH
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(charge.getServiceTable())
            && charge.getServiceId() != null) {
            if (charge.getDispenseId() != null) {
                Long dispenseOrg = index.locationOrgByDispenseId.get(charge.getDispenseId());
                if (dispenseOrg != null && index.techOrgIds.contains(dispenseOrg)) {
                    return true;
                }
            }
            DeviceRequestListModel dr = index.deviceById.get(charge.getServiceId());
            if (dr != null && dr.getLocationId() != null) {
                Long requestLocOrg = index.locationOrgByRequestLocationId.get(dr.getLocationId());
                if (requestLocOrg != null && index.techOrgIds.contains(requestLocOrg)) {
                    return true;
                }
            }
            if (dr != null && dr.getPerformLocation() != null) {
                Long performOrg = index.locationOrgByRequestLocationId.get(dr.getPerformLocation());
                if (performOrg != null && index.techOrgIds.contains(performOrg)) {
                    return true;
                }
            }
        }
        // 条件③：requesting_org_id 或 org_id 为 TECH（医技划价两列同源）
        if (charge.getRequestingOrgId() != null && index.techOrgIds.contains(charge.getRequestingOrgId())) {
            return true;
        }
        if (charge.getOrgId() != null && index.techOrgIds.contains(charge.getOrgId())) {
            return true;
        }
        return false;
    }

    /**
     * 构建发放主键 → 库房上级组织映射。
     *
     * @param deviceRequests  耗材请求
     * @param deviceDispenses 发放读模型
     * @return dispenseId → organizationId
     */
    private Map<Long, Long> mapDispenseIdToLocationOrganizationId(
        List<DeviceRequestListModel> deviceRequests,
        List<DeviceDispenseListModel> deviceDispenses) {
        Map<Long, Long> result = new HashMap<>();
        Set<Long> dispenseIds = new HashSet<>();
        if (deviceDispenses != null) {
            for (DeviceDispenseListModel d : deviceDispenses) {
                if (d != null && d.getId() != null) {
                    dispenseIds.add(d.getId());
                }
            }
        }
        List<DeviceDispense> entities = this.deviceDispenseQueryService.listDeviceDispensesByIds(dispenseIds);
        Set<Long> locationIds = new HashSet<>();
        Map<Long, Long> locationByDispenseId = new HashMap<>();
        if (entities != null) {
            for (DeviceDispense entity : entities) {
                if (entity == null || entity.getId() == null || entity.getLocationId() == null) {
                    continue;
                }
                locationByDispenseId.put(entity.getId(), entity.getLocationId());
                locationIds.add(entity.getLocationId());
            }
        }
        if (deviceRequests != null) {
            for (DeviceRequestListModel req : deviceRequests) {
                if (req == null) {
                    continue;
                }
                if (req.getLocationId() != null) {
                    locationIds.add(req.getLocationId());
                }
                if (req.getPerformLocation() != null) {
                    locationIds.add(req.getPerformLocation());
                }
            }
        }
        Map<Long, Location> locationById = this.locationQueryService.loadLocationEntityMapByIds(locationIds);
        for (Map.Entry<Long, Long> entry : locationByDispenseId.entrySet()) {
            Location location = locationById.get(entry.getValue());
            if (location != null && location.getOrganizationId() != null) {
                result.put(entry.getKey(), location.getOrganizationId());
            }
        }
        return result;
    }

    /**
     * 构建请求库房主键 → 组织映射（含 performLocation）。
     *
     * @param deviceRequests 耗材请求
     * @return locationId → organizationId
     */
    private Map<Long, Long> mapRequestLocationToOrganizationId(List<DeviceRequestListModel> deviceRequests) {
        Map<Long, Long> result = new HashMap<>();
        if (deviceRequests == null || deviceRequests.isEmpty()) {
            return result;
        }
        Set<Long> locationIds = new HashSet<>();
        for (DeviceRequestListModel req : deviceRequests) {
            if (req == null) {
                continue;
            }
            if (req.getLocationId() != null) {
                locationIds.add(req.getLocationId());
            }
            if (req.getPerformLocation() != null) {
                locationIds.add(req.getPerformLocation());
            }
        }
        Map<Long, Location> locationById = this.locationQueryService.loadLocationEntityMapByIds(locationIds);
        for (Map.Entry<Long, Location> entry : locationById.entrySet()) {
            Location location = entry.getValue();
            if (location != null && location.getOrganizationId() != null) {
                result.put(entry.getKey(), location.getOrganizationId());
            }
        }
        return result;
    }

    /**
     * 出院确费相关判定预取索引。
     */
    private static final class TechRelatedChargeIndex {

        /** 诊疗请求索引。 */
        private final Map<Long, ServiceRequestListModel> serviceById = new HashMap<>();

        /** 耗材请求索引。 */
        private final Map<Long, DeviceRequestListModel> deviceById = new HashMap<>();

        /** 发放 → 库房组织。 */
        private Map<Long, Long> locationOrgByDispenseId = new HashMap<>();

        /** 请求库房 → 组织。 */
        private Map<Long, Long> locationOrgByRequestLocationId = new HashMap<>();

        /** 候选组织（未滤 TECH）。 */
        private final Set<Long> candidateOrgIds = new LinkedHashSet<>();

        /** class=TECH 的组织主键。 */
        private final Set<Long> techOrgIds = new HashSet<>();
    }
}
