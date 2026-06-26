/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.encounter;

import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.module.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.EncounterSessionScope;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextPageQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.encounterhistory.entity.EncounterHistory;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院就诊上下文读侧组装（由 {@link EncounterContextFacade} 委托，替代 {@code InpatientInfoRMQueryService}）。
 *
 * <p>分页/排序/机构收窄见 B.10.11；病区床位范围经 {@link LocationQueryService} 与就诊位置子表 EXISTS 收敛。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientEncounterContextLoader {

    private final EncounterContextPageQueryService pageQueryService;
    private final EncounterQueryService encounterQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PatientQueryService patientQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    private final EncounterHistoryQueryService encounterHistoryQueryService;

    /** 当前会话机构收窄（B.10.11，禁止经 PageQuery 传 organizationId）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /**
     * 住院登记队列分页：默认 listStatus 为在院登记列表口径。
     *
     * @param query 分页与地理/关键字筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageRegQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        if (StringUtils.isBlank(effective.getRegistrationListStatus())) {
            effective.setRegistrationListStatus(InpatientRegistrationConstants.DEFAULT_LIST_STATUS);
        }
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院预交金患者队列：清床离院（{@code DISCHARGED}）前均在列，含已结算待清床（{@code COMPLETED}）。
     *
     * <p>收退门禁由 {@code InpatientPrepayAppService} 另行校验，与本读侧可见范围分离。</p>
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pagePrepayQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setPrepayEligibleOnly(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院护士站在院队列分页（含任务筛选、地理收窄等护士站专用谓词）。
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageNurseStationQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setNurseStationQueue(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院医生站在院队列分页。
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageDoctorStationQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setDoctorStationQueue(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院结算队列：待结算页签按「有待结算费用 ∪ 就诊 PENDING」收窄；已结算页签按 COMPLETED。
     *
     * @param query 分页、页签与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageSettleQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setSettleQueue(Boolean.TRUE);
        if (StringUtils.isBlank(effective.getSettleQueueTab())) {
            effective.setSettleQueueTab(InpatientSettleQueueTab.PENDING_SETTLE);
        }
        if (InpatientSettleQueueTab.PENDING_SETTLE.equals(effective.getSettleQueueTab())) {
            List<Long> encounterIds = chargeItemQueryService.listEncounterIdsWithPendingSettlementCharges();
            effective.setEncounterIdsIn(encounterIds);
        }
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 通用住院就诊上下文分页（机构由 {@link CurrentManagedOrganizationIdsResolver} 收窄）。
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientEncounterContexts(InpatientEncounterPageQuery query) {
        Page<Encounter> page = pageQueryService.pageInpatientEncounterEntities(query, this.buildSessionScope());
        List<InpatientEncounterContextQueryModel> content = this.toContextModels(page.getContent());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    /**
     * 住院就诊上下文列表（无分页，按创建时间倒序）。
     *
     * @param query 筛选条件
     * @return 上下文行列表
     */
    public List<InpatientEncounterContextQueryModel> listInpatientEncounterContexts(InpatientEncounterPageQuery query) {
        List<Encounter> rows = pageQueryService.listInpatientEncounterEntities(query, this.buildSessionScope());
        return this.toContextModels(rows);
    }

    private EncounterSessionScope buildSessionScope() {
        return new EncounterSessionScope(
            managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery(),
            managedOrganizationIdsResolver.isOrganizationScopeEnforcementActive(),
            this.extractWardLocationIds(managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty()));
    }

    /**
     * 按就诊主键取单条住院上下文（非 IMP 类别不返回）。
     *
     * @param encounterId 就诊主键
     * @return 上下文；不存在或非住院时 empty
     */
    public Optional<InpatientEncounterContextQueryModel> getInpatientContextByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterQueryService.findInpatientEncounterEntityById(encounterId)
            .map(encounter -> this.toContextModels(List.of(encounter)).get(0));
    }

    /**
     * 按调用方给定顺序批量装载住院上下文（缺失 id 跳过，不抛错）。
     *
     * @param encounterIdsOrdered 就诊主键有序列表
     * @return 与输入顺序一致的上下文列表
     */
    public List<InpatientEncounterContextQueryModel> listInpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        if (encounterIdsOrdered == null || encounterIdsOrdered.isEmpty()) {
            return new ArrayList<>();
        }
        List<Encounter> loaded =
            encounterQueryService.listInpatientEncounterEntitiesByIds(encounterIdsOrdered);
        Map<Long, InpatientEncounterContextQueryModel> byEncounterId = new HashMap<>(loaded.size() * 2);
        for (InpatientEncounterContextQueryModel model : this.toContextModels(loaded)) {
            if (model.getEncounterId() != null) {
                byEncounterId.put(model.getEncounterId(), model);
            }
        }
        List<InpatientEncounterContextQueryModel> ordered = new ArrayList<>(encounterIdsOrdered.size());
        for (Long encounterId : encounterIdsOrdered) {
            InpatientEncounterContextQueryModel hit = byEncounterId.get(encounterId);
            if (hit != null) {
                ordered.add(hit);
            }
        }
        return ordered;
    }

    /**
     * 按就诊主键强制取得住院上下文，不存在时抛 {@link ErrorCodeConstants#ENCOUNTER_NOT_FOUND}。
     *
     * @param encounterId 就诊主键
     * @return 上下文
     */
    public InpatientEncounterContextQueryModel mustGetInpatientContextByEncounterId(Long encounterId) {
        return this.getInpatientContextByEncounterId(encounterId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND,
                "住院就诊上下文不存在"));
    }

    /** 由病区位置实体列表提取 id，与护士站树同一回落口径。 */
    private List<Long> extractWardLocationIds(List<Location> wards) {
        if (wards == null || wards.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> wardIds = new LinkedHashSet<>();
        for (Location ward : wards) {
            if (ward != null && ward.getId() != null) {
                wardIds.add(ward.getId());
            }
        }
        return new ArrayList<>(wardIds);
    }

    private List<InpatientEncounterContextQueryModel> toContextModels(List<Encounter> encounters) {
        if (encounters == null || encounters.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter.getId() != null) {
                encounterIds.add(encounter.getId());
            }
            if (encounter.getPatientId() != null) {
                patientIds.add(encounter.getPatientId());
            }
            if (encounter.getOrganizationId() != null) {
                organizationIds.add(encounter.getOrganizationId());
            }
        }
        Map<Long, Patient> patientById = patientQueryService.loadPatientEntityMapByIds(patientIds);
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationIds);

        Map<Long, List<EncounterLocation>> locationsByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            for (EncounterLocation row :
                encounterQueryService.listActiveEncounterLocationEntitiesByEncounterIds(encounterIds)) {
                if (row.getEncounterId() == null) {
                    continue;
                }
                locationsByEncounterId
                    .computeIfAbsent(row.getEncounterId(), ignored -> new ArrayList<>())
                    .add(row);
            }
        }

        Set<Long> locationIds = new LinkedHashSet<>();
        for (List<EncounterLocation> rows : locationsByEncounterId.values()) {
            for (EncounterLocation row : rows) {
                if (row.getLocationId() != null) {
                    locationIds.add(row.getLocationId());
                }
            }
        }
        Map<Long, Location> locationById = new HashMap<>(locationQueryService.loadLocationEntityMapByIds(locationIds));
        Set<Long> wardParentIds = new LinkedHashSet<>();
        for (Location location : locationById.values()) {
            if (location.getParentId() != null
                && LocationForm.BED.getCode().equals(location.getFormEnum())) {
                wardParentIds.add(location.getParentId());
            }
        }
        if (!wardParentIds.isEmpty()) {
            locationById.putAll(locationQueryService.loadLocationEntityMapByIds(wardParentIds));
        }

        Map<Long, List<EncounterParticipant>> participantsByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            for (EncounterParticipant participant :
                encounterQueryService.listActiveParticipantEntitiesByEncounterIds(encounterIds)) {
                if (participant.getEncounterId() == null) {
                    continue;
                }
                participantsByEncounterId
                    .computeIfAbsent(participant.getEncounterId(), ignored -> new ArrayList<>())
                    .add(participant);
            }
        }
        Set<Long> practitionerIds = new LinkedHashSet<>();
        for (List<EncounterParticipant> rows : participantsByEncounterId.values()) {
            for (EncounterParticipant row : rows) {
                if (row.getPractitionerId() != null) {
                    practitionerIds.add(row.getPractitionerId());
                }
            }
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
                if (practitioner.getId() != null && StringUtils.isNotBlank(practitioner.getName())) {
                    practitionerNameById.put(practitioner.getId(), practitioner.getName());
                }
            }
        }

        List<InpatientEncounterContextQueryModel> models = new ArrayList<>(encounters.size());
        for (Encounter encounter : encounters) {
            InpatientEncounterContextQueryModel model = new InpatientEncounterContextQueryModel();
            model.setEncounterId(encounter.getId());
            model.setEncounterBusNo(encounter.getBusNo());
            model.setStatusEnum(encounter.getStatusEnum());
            model.setPatientId(encounter.getPatientId());
            model.setOrganizationId(encounter.getOrganizationId());
            model.setStartTime(encounter.getStartTime());
            model.setEndTime(encounter.getEndTime());
            model.setInWayCode(encounter.getInWayCode());
            model.setOutWayCode(encounter.getOutWayCode());
            if (encounter.getOrganizationId() != null) {
                model.setOrganizationName(organizationNameById.get(encounter.getOrganizationId()));
            }
            Patient patient = patientById.get(encounter.getPatientId());
            if (patient != null) {
                model.setPatientName(patient.getName());
                model.setGenderEnum(patient.getGenderEnum());
                if (patient.getBirthDate() != null) {
                    model.setBirthDate(patient.getBirthDate().atStartOfDay());
                    Date birthDate = Date.from(
                        patient.getBirthDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                    String ageText = AgeCalculatorUtil.getAge(birthDate);
                    if (StringUtils.isNotBlank(ageText)) {
                        model.setAgeText(ageText);
                    }
                }
            }
            this.applyActiveLocation(model, locationsByEncounterId.get(encounter.getId()), locationById);
            this.applyActiveParticipants(
                model,
                participantsByEncounterId.get(encounter.getId()),
                practitionerNameById);
            EncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot diagnosisSnapshot =
                this.encounterDiagnosisSnapshotAssembler.buildSnapshot(encounter.getId());
            model.setDiagnosisJson(diagnosisSnapshot.diagnosisJson());
            models.add(model);
        }
        // 已清床离院无活动位置，按末次已完成 WARD 回填病区展示（AS-IP-NUR-001 BR-IP-NUR-012）。
        this.applyLastCompletedWardForDischarged(models);
        // ON_HOLD 待转科/待出院分流列（替代 InpatientInfo 投影的 transfer_org_id、discharge_type_enum）。
        this.applyInpatientHoldRoutingFields(models, encounters);
        for (InpatientEncounterContextQueryModel model : models) {
            model.setStatusEnumText(InpatientEncounterStatusDisplayUtil.resolveDisplayText(
                model.getStatusEnum(),
                model.getTransferOrgId(),
                model.getDischargeTypeEnum()));
        }
        return models;
    }

    /**
     * 回填 {@code ON_HOLD} 转科目标与出院类型（DM-ADM-002 §2.3.2；替代废止的 InpatientInfo 读模型列）。
     *
     * @param models     已装配的住院上下文行
     * @param encounters 同源就诊主档
     */
    private void applyInpatientHoldRoutingFields(
        List<InpatientEncounterContextQueryModel> models,
        List<Encounter> encounters) {
        if (models == null || models.isEmpty() || encounters == null || encounters.isEmpty()) {
            return;
        }
        Map<Long, Encounter> encounterById = new HashMap<>(encounters.size() * 2);
        LinkedHashSet<Long> onHoldEncounterIds = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            encounterById.put(encounter.getId(), encounter);
            if (EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                onHoldEncounterIds.add(encounter.getId());
            }
        }
        if (onHoldEncounterIds.isEmpty()) {
            return;
        }
        Map<Long, List<EncounterHistory>> historiesByEncounterId = new HashMap<>();
        for (EncounterHistory row : encounterHistoryQueryService.listEncounterHistoryEntitiesByEncounterIds(
            onHoldEncounterIds)) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            historiesByEncounterId
                .computeIfAbsent(row.getEncounterId(), ignored -> new ArrayList<>())
                .add(row);
        }
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null) {
                continue;
            }
            if (!EncounterStatus.ON_HOLD.getCode().equals(model.getStatusEnum())) {
                continue;
            }
            Long encounterId = model.getEncounterId();
            List<EncounterHistory> histories = historiesByEncounterId.get(encounterId);
            Long transferTargetOrgId = this.resolveLatestTransferTargetOrganizationId(histories);
            if (transferTargetOrgId != null) {
                model.setTransferOrgId(transferTargetOrgId);
                continue;
            }
            Encounter encounter = encounterById.get(encounterId);
            if (encounter != null && StringUtils.isNotBlank(encounter.getOutWayCode())) {
                model.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
                continue;
            }
            if (this.hasDischargeMilestone(histories)) {
                model.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
            }
        }
    }

    /**
     * 取最近一次转科里程碑上的目标科室（待转科分流）。
     *
     * @param histories 该就诊里程碑列表
     * @return 目标科室主键；无转科里程碑时为 null
     */
    private Long resolveLatestTransferTargetOrganizationId(List<EncounterHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return null;
        }
        List<EncounterHistory> sorted = new ArrayList<>(histories);
        sorted.sort(Comparator.comparing(
            EncounterHistory::getCreatedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        for (EncounterHistory row : sorted) {
            if (row == null) {
                continue;
            }
            if (!CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equals(row.getName())) {
                continue;
            }
            if (row.getTargetOrganizationId() != null) {
                return row.getTargetOrganizationId();
            }
        }
        return null;
    }

    /**
     * 是否存在出院类里程碑（医生站出院医嘱等路径）。
     *
     * @param histories 该就诊里程碑列表
     * @return true 表示已记录出院里程碑
     */
    private boolean hasDischargeMilestone(List<EncounterHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return false;
        }
        for (EncounterHistory row : histories) {
            if (row == null) {
                continue;
            }
            if (CustomConstants.EncounterHistoryMilestoneName.DISCHARGE.equals(row.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 已清床离院就诊无活动位置时，按末次已完成 WARD 轨迹回填病区展示字段。
     *
     * @param models 住院上下文行
     */
    private void applyLastCompletedWardForDischarged(List<InpatientEncounterContextQueryModel> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> dischargedEncounterIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null || model.getWardId() != null) {
                continue;
            }
            if (!EncounterStatus.DISCHARGED.getCode().equals(model.getStatusEnum())) {
                continue;
            }
            dischargedEncounterIds.add(model.getEncounterId());
        }
        if (dischargedEncounterIds.isEmpty()) {
            return;
        }
        Map<Long, Long> wardIdByEncounterId =
            encounterQueryService.mapLastCompletedWardLocationIdByEncounterIds(dischargedEncounterIds);
        if (wardIdByEncounterId.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> wardLocationIds = new LinkedHashSet<>(wardIdByEncounterId.values());
        Map<Long, Location> wardById = locationQueryService.loadLocationEntityMapByIds(wardLocationIds);
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null || model.getWardId() != null) {
                continue;
            }
            Long wardId = wardIdByEncounterId.get(model.getEncounterId());
            if (wardId == null) {
                continue;
            }
            model.setWardId(wardId);
            Location ward = wardById.get(wardId);
            if (ward != null && StringUtils.isNotBlank(ward.getName())) {
                model.setWardName(ward.getName());
            }
        }
    }

    /** 回填病区/床位/房间展示字段（活动轨迹解析）。 */
    private void applyActiveLocation(
        InpatientEncounterContextQueryModel model,
        List<EncounterLocation> activeLocations,
        Map<Long, Location> locationById) {
        if (activeLocations == null || activeLocations.isEmpty()) {
            return;
        }
        Long bedId = null;
        String bedName = null;
        Long wardId = null;
        String wardName = null;
        for (EncounterLocation row : activeLocations) {
            if (row == null || row.getLocationId() == null) {
                continue;
            }
            Location location = locationById.get(row.getLocationId());
            if (location == null) {
                continue;
            }
            if (LocationForm.BED.getCode().equals(row.getFormEnum())
                || LocationForm.BED.getCode().equals(location.getFormEnum())) {
                bedId = location.getId();
                bedName = location.getName();
                if (location.getParentId() != null) {
                    Location parent = locationById.get(location.getParentId());
                    if (parent != null && LocationForm.WARD.getCode().equals(parent.getFormEnum())) {
                        wardId = parent.getId();
                        wardName = parent.getName();
                    } else if (parent != null && LocationForm.HOUSE.getCode().equals(parent.getFormEnum())
                        && parent.getParentId() != null) {
                        Location ward = locationById.get(parent.getParentId());
                        if (ward != null) {
                            wardId = ward.getId();
                            wardName = ward.getName();
                        }
                    }
                }
            } else if (LocationForm.WARD.getCode().equals(row.getFormEnum())
                || LocationForm.WARD.getCode().equals(location.getFormEnum())) {
                wardId = location.getId();
                wardName = location.getName();
            }
        }
        model.setBedId(bedId);
        model.setBedName(bedName);
        model.setWardId(wardId);
        model.setWardName(wardName);
    }

    /** 回填主治/责任护士等参与者展示字段。 */
    private void applyActiveParticipants(
        InpatientEncounterContextQueryModel model,
        List<EncounterParticipant> participants,
        Map<Long, String> practitionerNameById) {
        if (participants == null) {
            return;
        }
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getPractitionerId() == null) {
                continue;
            }
            String roleCode = participant.getRoleCode();
            Long practitionerId = participant.getPractitionerId();
            String name = practitionerNameById.get(practitionerId);
            if (EncounterParticipantRole.ATTENDING_DOCTOR.getCode().equals(roleCode)) {
                model.setAttendingDoctorId(practitionerId);
                model.setAttendingDoctorName(name);
            } else if (EncounterParticipantRole.PRIMARY_NURSE.getCode().equals(roleCode)) {
                model.setPrimaryNurseId(practitionerId);
                model.setPrimaryNurseName(name);
            }
        }
    }
}
