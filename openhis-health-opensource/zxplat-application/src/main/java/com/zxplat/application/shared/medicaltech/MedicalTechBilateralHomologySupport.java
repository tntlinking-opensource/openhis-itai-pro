/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.medicaltech;

import com.zxplat.application.shared.nursestation.DeviceAutoInventoryDeductSettingResolver;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseListModel;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医技协同门禁：出院卡医技执行净态；护士/医技取消互不卡；签退依赖既有净剩 Procedure Gate。
 *
 * <p>口径见协同 DD 2026-09-29：出院以「执行科室=TECH 的诊疗项目是否医技侧净剩执行」替代一键确费；
 * 耗材在自动扣库关闭时须发放完成。类名保留历史 {@code BilateralHomology}。</p>
 *
 * <p>医技净态与医技站写路径对齐：院点配置了执行科室共看组（如国基 CT↔DR）时，
 * 组内任一登录科写出的 {@code Procedure.org_id} 均算已执行；未配置则仍仅认 SR 本科。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechBilateralHomologySupport {

    /** 诊疗请求只读：出院待执行 TECH 项目集合。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 执行记录只读：医技侧净剩 COMPLETED。 */
    private final ProcedureQueryService procedureQueryService;

    /** 组织只读：判定 TECH。 */
    private final OrganizationQueryService organizationQueryService;

    /** 耗材请求只读：医技划价来源。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** 耗材发放只读：非自动扣库须 COMPLETED。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 耗材是否自动扣库开关。 */
    private final DeviceAutoInventoryDeductSettingResolver deviceAutoInventoryDeductSettingResolver;

    /** 执行科室共看组：出院净态与医技站可见轴同根（国基 CT/DR 合并）。 */
    private final MedicalTechSharedExecutingOrgSupport medicalTechSharedExecutingOrgSupport;

    /**
     * 护士站取消执行前同态预检（已废止）：空操作，保证无痛切换。
     *
     * <p>护士与医技互不卡；调用点可保留以免大改编排。</p>
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
     * 出院门禁：执行科室为 TECH 的诊疗须医技侧净剩执行；非自动扣库时医技划价耗材须已发放（EX-IP-ATD-309）。
     *
     * <p>按<strong>项目</strong>校验（SR.org_id = TECH），不以登录科汇总。医生签退仍走
     * {@code InpatientAdviceRevokeGate}（净剩含 TECH Procedure 即拦）。</p>
     *
     * <p>净态认共看组：{@code Procedure.org_id} 落在 SR 执行科共看可见轴内即算已执行
     * （与医技站写 {@code loginTech}、查询共看同轴；未配置共看则仅认本科）。</p>
     *
     * @param encounterId 就诊主键
     * @throws BusinessException 存在未医技执行的 TECH 诊疗或未发放的医技划价耗材
     */
    public void assertDischargeTechPending(Long encounterId) {
        // Gate：无就诊则跳过（由调用方已校验）
        if (encounterId == null) {
            return;
        }
        // Pre：装载本就诊诊疗 / 执行 / 组织
        List<ServiceRequestListModel> serviceRequests =
            this.serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        Set<Long> candidateOrgIds = new LinkedHashSet<>();
        if (serviceRequests != null) {
            for (ServiceRequestListModel row : serviceRequests) {
                if (row != null && row.getOrgId() != null) {
                    candidateOrgIds.add(row.getOrgId());
                }
            }
        }
        Map<Long, Organization> orgById = this.organizationQueryService.mapByIds(candidateOrgIds);
        List<Procedure> procedures = this.procedureQueryService.listByEncounterIds(List.of(encounterId));
        Map<Long, List<Procedure>> proceduresByServiceRequestId =
            this.groupProceduresByServiceRequestId(procedures);
        // Pre：按 SR 执行科缓存共看可见轴，禁止逐条重复解析配置组
        Map<Long, Set<Long>> acceptedTechOrgsBySrOrgId = new HashMap<>();
        // 业务：逐条 TECH 执行科诊疗断言医技侧净剩 COMPLETED（共看组内任一科均可）
        if (serviceRequests != null) {
            for (ServiceRequestListModel row : serviceRequests) {
                if (!this.isDischargeTechExecutionCandidate(row, orgById)) {
                    continue;
                }
                Long techOrgId = row.getOrgId();
                Set<Long> acceptedTechOrgIds =
                    this.resolveAcceptedTechOrgIds(techOrgId, acceptedTechOrgsBySrOrgId);
                List<Procedure> requestProcedures = proceduresByServiceRequestId.get(row.getId());
                // Gate：该项目尚无医技侧净剩执行 → 拒绝出院
                if (!this.hasNetCompletedTechProcedure(requestProcedures, acceptedTechOrgIds)) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_ATD_309,
                        "请完成医技执行后再办理出院");
                }
            }
        }
        // 业务：自动扣库关时，医技划价耗材须发放完成
        this.assertMedicalTechPricingDevicesDispensedWhenManual(encounterId);
    }

    /**
     * 判断 Procedure 是否属护士侧（历史 org_id 空或执行科室非 TECH）。
     *
     * @param procedureOrgId Procedure.org_id
     * @param orgById        组织索引（可空则仅认 null 为护士侧）
     * @return 护士侧为 true
     */
    public boolean isNurseSideProcedureOrg(Long procedureOrgId, Map<Long, Organization> orgById) {
        // 历史错写：org_id 空视为护士侧
        if (procedureOrgId == null) {
            return true;
        }
        if (orgById == null || orgById.isEmpty()) {
            return false;
        }
        Organization org = orgById.get(procedureOrgId);
        // 未知组织保守不当护士侧，避免把医技行误并入护士净态
        if (org == null || org.getClassEnum() == null) {
            return false;
        }
        // 业务：仅非 TECH 工作科室算护士侧（含 NS）
        if (org.getClassEnum() == OrganizationClassification.TECH) {
            return false;
        }
        return true;
    }

    /**
     * 过滤出护士侧 Procedure（出院临时净态 / 护士次数须与医技侧隔离）。
     *
     * @param procedures 同请求下全部执行记录
     * @param orgById    组织索引
     * @return 护士侧子集；入参空则空列表
     */
    public List<Procedure> filterNurseSideProcedures(List<Procedure> procedures, Map<Long, Organization> orgById) {
        if (procedures == null || procedures.isEmpty()) {
            return List.of();
        }
        ArrayList<Procedure> nurseSide = new ArrayList<>();
        for (Procedure procedure : procedures) {
            if (procedure == null) {
                continue;
            }
            if (this.isNurseSideProcedureOrg(procedure.getOrgId(), orgById)) {
                nurseSide.add(procedure);
            }
        }
        return nurseSide;
    }

    /**
     * 是否为出院须卡医技执行的诊疗候选（医生开立 + 执行科 TECH + 已校对/已停嘱 + 非转科出院护理）。
     *
     * @param row     诊疗请求
     * @param orgById 组织索引
     * @return 须校验医技执行为 true
     */
    private boolean isDischargeTechExecutionCandidate(
        ServiceRequestListModel row,
        Map<Long, Organization> orgById) {
        if (row == null || row.getId() == null || row.getOrgId() == null) {
            return false;
        }
        // 业务：仅医生开立；护士划价不落 TECH 执行科（另有划价 Gate）
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum())) {
            return false;
        }
        if (row.getParentId() != null) {
            return false;
        }
        if (!this.isVerifiedOrStopped(row.getStatusEnum())) {
            return false;
        }
        if (this.isExcludedDischargeServiceCategory(row.getCategoryEnum())) {
            return false;
        }
        Organization org = orgById.get(row.getOrgId());
        // Gate：执行科室须为 TECH
        if (org == null || org.getClassEnum() != OrganizationClassification.TECH) {
            return false;
        }
        return true;
    }

    /**
     * 请求状态是否为已校对或已停嘱。
     *
     * @param statusEnum 状态 code
     * @return 候选为 true
     */
    private boolean isVerifiedOrStopped(String statusEnum) {
        if (RequestStatus.VERIFIED.getCode().equals(statusEnum)) {
            return true;
        }
        if (RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 排除转科/出院/护理类诊疗类目。
     *
     * @param categoryEnum 诊疗类目 code
     * @return 应排除为 true
     */
    private boolean isExcludedDischargeServiceCategory(String categoryEnum) {
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(categoryEnum)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(categoryEnum)) {
            return true;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING.equals(categoryEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 解析 SR 执行科对应的出院净态认可科室集合（共看组或仅本科），并按 SR 科缓存。
     *
     * @param techOrgId                   SR 执行科室
     * @param acceptedTechOrgsBySrOrgId   SR 科 → 认可集合缓存（本方法写入）
     * @return 非空认可集合（至少含 SR 本科）；techOrgId 空则空集
     */
    private Set<Long> resolveAcceptedTechOrgIds(
        Long techOrgId,
        Map<Long, Set<Long>> acceptedTechOrgsBySrOrgId) {
        // Gate：无执行科则无可认净态
        if (techOrgId == null) {
            return Set.of();
        }
        if (acceptedTechOrgsBySrOrgId != null) {
            Set<Long> cached = acceptedTechOrgsBySrOrgId.get(techOrgId);
            // 命中缓存 → 复用，避免同科多项目重复 resolve
            if (cached != null) {
                return cached;
            }
        }
        // 业务：与医技站共看可见轴同根（未配置则仅本科）
        Set<Long> resolved =
            this.medicalTechSharedExecutingOrgSupport.resolveVisibleExecutingOrgIds(techOrgId);
        if (acceptedTechOrgsBySrOrgId != null) {
            acceptedTechOrgsBySrOrgId.put(techOrgId, resolved);
        }
        return resolved;
    }

    /**
     * 是否存在医技侧净剩 COMPLETED（org_id 落在认可 TECH 轴内；CANCEL.refund_id 对冲后不算）。
     *
     * <p>认可轴含 SR 执行科及其共看对等科（如 CT↔DR）；护士侧 / 非认可科不计入。</p>
     *
     * @param procedures          同请求 Procedure
     * @param acceptedTechOrgIds  认可的医技执行科室集合（共看组或仅本科）
     * @return 有净剩为 true
     */
    private boolean hasNetCompletedTechProcedure(
        List<Procedure> procedures,
        Set<Long> acceptedTechOrgIds) {
        if (procedures == null || procedures.isEmpty()) {
            return false;
        }
        if (acceptedTechOrgIds == null || acceptedTechOrgIds.isEmpty()) {
            return false;
        }
        Set<Long> offsetCompletedIds = new HashSet<>();
        for (Procedure entity : procedures) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            // 仅共看可见 TECH 轴内执行记录参与对冲（含对等科 loginTech）
            if (!acceptedTechOrgIds.contains(entity.getOrgId())) {
                continue;
            }
            if (EventStatus.isCancelExecutionStatusCode(entity.getStatusEnum())) {
                if (entity.getRefundId() != null) {
                    offsetCompletedIds.add(entity.getRefundId());
                }
            }
        }
        for (Procedure entity : procedures) {
            if (entity == null || entity.getId() == null) {
                continue;
            }
            if (!acceptedTechOrgIds.contains(entity.getOrgId())) {
                continue;
            }
            if (!EventStatus.COMPLETED.getCode().equals(entity.getStatusEnum())) {
                continue;
            }
            // 已被 CANCEL 对冲的 COMPLETED 不算净剩
            if (offsetCompletedIds.contains(entity.getId())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 按诊疗请求主键分组 Procedure。
     *
     * @param procedures 就诊下执行记录
     * @return serviceRequestId → Procedure 列表
     */
    private Map<Long, List<Procedure>> groupProceduresByServiceRequestId(List<Procedure> procedures) {
        Map<Long, List<Procedure>> grouped = new HashMap<>();
        if (procedures == null) {
            return grouped;
        }
        String serviceTable = TableNameConstants.WKF_SERVICE_REQUEST;
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getRequestId() == null) {
                continue;
            }
            if (!serviceTable.equals(procedure.getRequestTable())) {
                continue;
            }
            List<Procedure> bucket = grouped.get(procedure.getRequestId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(procedure.getRequestId(), bucket);
            }
            bucket.add(procedure);
        }
        return grouped;
    }

    /**
     * 非自动扣库时：医技划价耗材须存在 COMPLETED 发放，否则拒绝出院。
     *
     * @param encounterId 就诊主键
     */
    private void assertMedicalTechPricingDevicesDispensedWhenManual(Long encounterId) {
        // Gate：自动扣库开 → 划完即发，不额外拦
        if (this.deviceAutoInventoryDeductSettingResolver.isEnabled()) {
            return;
        }
        List<DeviceRequestListModel> deviceRequests =
            this.deviceRequestQueryService.listDeviceRequestsByEncounter(encounterId);
        if (deviceRequests == null || deviceRequests.isEmpty()) {
            return;
        }
        String medicalTechPricing = GenerateSource.MEDICAL_TECH_PRICING.getCode();
        Set<Long> pendingRequestIds = new LinkedHashSet<>();
        for (DeviceRequestListModel row : deviceRequests) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 业务：仅医技划价耗材
            if (!medicalTechPricing.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            pendingRequestIds.add(row.getId());
        }
        if (pendingRequestIds.isEmpty()) {
            return;
        }
        List<DeviceDispenseListModel> dispenses =
            this.deviceDispenseQueryService.listDeviceDispensesByEncounter(encounterId);
        Set<Long> completedRequestIds = new HashSet<>();
        if (dispenses != null) {
            for (DeviceDispenseListModel dispense : dispenses) {
                if (dispense == null || dispense.getDeviceReqId() == null) {
                    continue;
                }
                if (!pendingRequestIds.contains(dispense.getDeviceReqId())) {
                    continue;
                }
                // 业务：主发放 COMPLETED 视为已发出
                if (DispenseStatus.COMPLETED.getCode().equals(dispense.getStatusEnum())) {
                    completedRequestIds.add(dispense.getDeviceReqId());
                }
            }
        }
        for (Long requestId : pendingRequestIds) {
            // Gate：尚无 COMPLETED 发放 → 拒绝出院
            if (!completedRequestIds.contains(requestId)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_309,
                    "请完成医技耗材发放后再办理出院");
            }
        }
    }
}
