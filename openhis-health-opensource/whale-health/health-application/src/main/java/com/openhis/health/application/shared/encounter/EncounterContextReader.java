/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.encounter;

import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.EncounterSessionScope;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterLocationScope;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextCriteriaSupport;
import com.openhis.health.domain.administration.encounter.service.EncounterContextQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 就诊上下文读侧统一入口（B.6.1.3）：L0 分页编排 + L1 hydrate 委托 {@link EncounterContextQueryService}，
 * L2 diagnosisJson 由本类叠加（B.6.2）。原 {@code EncounterContextPageQuerySupport} 已并入本类（S8-L1）。
 */
@Component
@RequiredArgsConstructor
public class EncounterContextReader {

    /** 就诊实体分页/列表查询（L0 实体层）。 */
    private final EncounterQueryService encounterQueryService;
    /** 位置层级读侧：病房→房间→床位树遍历。 */
    private final LocationQueryService locationQueryService;
    /** 就诊上下文 JPA Specification 与排序/分页解析。 */
    private final EncounterContextCriteriaSupport encounterContextCriteriaSupport;
    /** 就诊上下文 L1 hydrate（批量装配读模型）。 */
    private final EncounterContextQueryService encounterContextQueryService;
    /** 诊断快照 JSON 叠加（L2，B.6.2）。 */
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    /** 当前会话可管理组织机构收窄。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 当前会话可管理病区位置收窄。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /**
     * 住院登记队列分页（默认住院上下文筛选，含诊断快照）。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @return 住院就诊上下文分页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientRegQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院预交金队列分页（仅预交资格患者，不含诊断快照）。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @return 住院就诊上下文分页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientPrepayQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setPrepayEligibleOnly(Boolean.TRUE);
        effective.setIncludeDiagnosisSnapshot(Boolean.FALSE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院护士站队列分页。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @return 住院就诊上下文分页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientNurseStationQueue(
        InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setNurseStationQueue(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院医生站队列分页。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @return 住院就诊上下文分页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientDoctorStationQueue(
        InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setDoctorStationQueue(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院结算队列分页（默认待结算 Tab）。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @return 住院就诊上下文分页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientSettleQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setSettleQueue(Boolean.TRUE);
        if (StringUtils.isEmpty(effective.getSettleQueueTab())) {
            effective.setSettleQueueTab(InpatientSettleQueueTab.PENDING_SETTLE);
        }
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院结算队列按住院号去重计数（默认已结算 Tab）。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @return 去重后的住院号数量
     */
    public long countInpatientSettleQueueDistinctBusNo(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setSettleQueue(Boolean.TRUE);
        if (StringUtils.isEmpty(effective.getSettleQueueTab())) {
            effective.setSettleQueueTab(InpatientSettleQueueTab.SETTLED);
        }
        return this.countDistinctInpatientBusNo(effective, this.buildSessionScope());
    }

    /**
     * 住院就诊上下文分页（L0 实体分页 + L1 hydrate + 可选 L2 诊断快照）。
     *
     * @param query 住院分页查询
     * @return 住院就诊上下文分页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientEncounterContexts(
        InpatientEncounterPageQuery query) {
        Page<Encounter> page = this.pageInpatientEncounterEntities(query, this.buildSessionScope());
        List<InpatientEncounterContextQueryModel> content =
            this.toInpatientContextModels(page.getContent(), this.resolveIncludeDiagnosisSnapshot(query));
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    /**
     * 住院就诊上下文列表（无分页，按创建时间倒序）。
     *
     * @param query 住院分页查询
     * @return 住院就诊上下文列表
     */
    public List<InpatientEncounterContextQueryModel> listInpatientEncounterContexts(
        InpatientEncounterPageQuery query) {
        List<Encounter> rows = this.listInpatientEncounterEntities(query, this.buildSessionScope());
        return this.toInpatientContextModels(rows, this.resolveIncludeDiagnosisSnapshot(query));
    }

    /**
     * 按就诊主键获取住院上下文（含诊断快照）。
     *
     * @param encounterId 就诊主键
     * @return 住院上下文；不存在时 empty
     */
    public Optional<InpatientEncounterContextQueryModel> getInpatientContextByEncounterId(Long encounterId) {
        return encounterContextQueryService.getInpatientContextByEncounterId(encounterId)
            .map(model -> this.applyInpatientDiagnosisSnapshot(model, true));
    }

    /**
     * 按给定顺序批量加载住院上下文（保持输入 encounterId 顺序，含诊断快照）。
     *
     * @param encounterIdsOrdered 就诊主键有序列表
     * @return 与输入顺序对齐的住院上下文列表
     */
    public List<InpatientEncounterContextQueryModel> listInpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        List<InpatientEncounterContextQueryModel> loaded =
            encounterContextQueryService.listInpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
        return this.applyInpatientDiagnosisSnapshots(loaded, true);
    }

    /**
     * 按就诊主键强制获取住院上下文（不存在时由下游抛业务异常，含诊断快照）。
     *
     * @param encounterId 就诊主键
     * @return 住院上下文
     * @throws BusinessException 就诊不存在时
     */
    public InpatientEncounterContextQueryModel mustGetInpatientContextByEncounterId(Long encounterId) {
        return this.applyInpatientDiagnosisSnapshot(
            encounterContextQueryService.mustGetInpatientContextByEncounterId(encounterId),
            true);
    }

    /**
     * 校验就诊对当前护士站可见（在可管理病区范围内且命中护士站队列谓词）。
     *
     * @param encounterId    待校验就诊主键
     * @param managedWardIds 可管理病区位置主键列表
     * @param errorCode      不可见时业务错误码
     * @param message        不可见时透出文案
     * @throws BusinessException 就诊不可见或 encounterId 为 null 时
     */
    public void assertEncounterVisible(
        Long encounterId,
        List<Long> managedWardIds,
        String errorCode,
        String message) {
        if (encounterId == null) {
            throw new BusinessException(errorCode, message);
        }
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setManagedWardIds(managedWardIds);
        query.setEncounterIdsIn(List.of(encounterId));
        query.setNurseStationQueue(Boolean.TRUE);
        List<InpatientEncounterContextQueryModel> rows = this.listInpatientEncounterContexts(query);
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                return;
            }
        }
        throw new BusinessException(errorCode, message);
    }

    /**
     * 门诊就诊上下文分页（机构会话收窄 + 诊断快照叠加）。
     *
     * @param query 门诊分页查询
     * @return 门诊就诊上下文分页
     */
    public Page<OutpatientEncounterContextQueryModel> pageOutpatientEncounterContexts(
        OutpatientEncounterPageQuery query) {
        EncounterSessionScope scope = new EncounterSessionScope(
            managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery(),
            managedOrganizationIdsResolver.isOrganizationScopeEnforcementActive(),
            List.of());
        Page<Encounter> page = this.pageOutpatientEncounterEntities(query, scope);
        List<OutpatientEncounterContextQueryModel> content = this.toOutpatientContextModels(page.getContent());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    /**
     * 门诊护士站执行队列分页（读模型层，含诊断快照）。
     *
     * @param query 分页参数
     * @return 门诊就诊上下文分页
     */
    public Page<OutpatientEncounterContextQueryModel> pageNurseStationOutpatientQueue(AbstractPageQuery query) {
        Page<Encounter> page = this.pageNurseStationOutpatientEncounters(query);
        List<OutpatientEncounterContextQueryModel> content = this.toOutpatientContextModels(page.getContent());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    /**
     * 按就诊主键获取门诊上下文（含诊断快照）。
     *
     * @param encounterId 就诊主键
     * @return 门诊上下文；不存在时 empty
     */
    public Optional<OutpatientEncounterContextQueryModel> getOutpatientContextByEncounterId(Long encounterId) {
        return encounterContextQueryService.getOutpatientContextByEncounterId(encounterId)
            .map(this::applyOutpatientDiagnosisSnapshot);
    }

    /**
     * 按给定顺序批量加载门诊上下文（保持输入 encounterId 顺序，含诊断快照）。
     *
     * @param encounterIdsOrdered 就诊主键有序列表
     * @return 与输入顺序对齐的门诊上下文列表
     */
    public List<OutpatientEncounterContextQueryModel> listOutpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        List<OutpatientEncounterContextQueryModel> loaded =
            encounterContextQueryService.listOutpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
        return this.applyOutpatientDiagnosisSnapshots(loaded);
    }

    /**
     * 按就诊主键强制获取门诊上下文（不存在时由下游抛业务异常，含诊断快照）。
     *
     * @param encounterId 就诊主键
     * @return 门诊上下文
     * @throws BusinessException 就诊不存在时
     */
    public OutpatientEncounterContextQueryModel mustGetOutpatientContextByEncounterId(Long encounterId) {
        return this.applyOutpatientDiagnosisSnapshot(
            encounterContextQueryService.mustGetOutpatientContextByEncounterId(encounterId));
    }

    /**
     * 门诊挂号列表分页实体查询。
     *
     * @param query 门诊挂号分页查询；null 时使用空查询
     * @return 就诊实体分页
     */
    public Page<Encounter> pageClinicRegisterEncounters(ClinicRegisterPageQuery query) {
        ClinicRegisterPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new ClinicRegisterPageQuery();
        }
        // B.2.1：关键字非空时无 Sort，保留 Spec 诊断精确优先 ORDER BY
        Pageable pageable;
        if (StringUtils.isNotEmpty(effective.getKeyword())) {
            pageable = PageRequest.of(
                effective.springPageIndex(),
                effective.getBoundedPageSize());
        } else {
            pageable = PageRequest.of(
                effective.springPageIndex(),
                effective.getBoundedPageSize(),
                Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        }
        return encounterQueryService.pageEncounterEntities(
            encounterContextCriteriaSupport.clinicRegisterSpecification(effective), pageable);
    }

    /**
     * 门诊挂号列表计数（与 {@link #pageClinicRegisterEncounters} 共用筛选谓词）。
     *
     * @param query 门诊挂号分页查询；null 时使用空查询
     * @return 命中记录数
     */
    public long countClinicRegisterEncounters(ClinicRegisterPageQuery query) {
        ClinicRegisterPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new ClinicRegisterPageQuery();
        }
        return encounterQueryService.countEncounterEntities(
            encounterContextCriteriaSupport.clinicRegisterSpecification(effective));
    }

    /**
     * 挂号单分页实体查询。
     *
     * @param query 挂号单分页查询；null 时使用空查询
     * @return 就诊实体分页
     */
    public Page<Encounter> pageRegistrationSlipEncounters(RegistrationSlipPageQuery query) {
        RegistrationSlipPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new RegistrationSlipPageQuery();
        }
        Pageable pageable = PageRequest.of(
            effective.springPageIndex(),
            effective.getBoundedPageSize(),
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        return encounterQueryService.pageEncounterEntities(
            encounterContextCriteriaSupport.registrationSlipSpecification(effective), pageable);
    }

    /**
     * 门诊就诊上下文分页（AMB，机构会话收窄）。
     *
     * <p>排序见 {@code EncounterContextCriteriaSupport#resolveOutpatientListSort}：
     * 待诊开始时间升序、在诊接诊时间降序；多状态并集挂 CASE 分桶 ORDER BY。</p>
     *
     * @param query 门诊分页查询；null 时使用空查询
     * @param scope 会话收窄范围；null 时不收窄
     * @return 就诊实体分页
     */
    public Page<Encounter> pageOutpatientEncounterEntities(
        OutpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        OutpatientEncounterPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new OutpatientEncounterPageQuery();
        }
        EncounterSessionScope effectiveScope;
        if (scope != null) {
            effectiveScope = scope;
        } else {
            effectiveScope = EncounterSessionScope.empty();
        }
        Sort sort = encounterContextCriteriaSupport.resolveOutpatientListSort(effective);
        // B.2.1：关键字非空时勿带 Sort，保留 Spec/分桶 Spec 内精确优先 ORDER BY
        Pageable pageable;
        if (StringUtils.isNotEmpty(effective.getKeyword())) {
            pageable = PageRequest.of(
                effective.springPageIndex(),
                effective.getBoundedPageSize());
        } else {
            pageable = PageRequest.of(
                effective.springPageIndex(),
                effective.getBoundedPageSize(),
                sort);
        }
        // 多状态并集：Sort 为 unsorted，改挂 CASE 分桶 ORDER BY（待诊 startTime 升序 / 在诊 receptionTime 降序）
        Specification<Encounter> specification =
            encounterContextCriteriaSupport.outpatientSpecification(effective, effectiveScope);
        if (encounterContextCriteriaSupport.isMultiStatusOutpatientQuery(effective)) {
            String trimmedKeyword = null;
            if (StringUtils.isNotEmpty(effective.getKeyword())) {
                trimmedKeyword = effective.getKeyword().trim();
            }
            specification = specification.and(
                encounterContextCriteriaSupport.doctorStationUnionOrderSpecification(trimmedKeyword));
        }
        return encounterQueryService.pageEncounterEntities(specification, pageable);
    }

    /**
     * 门诊护士站执行队列分页。
     *
     * @param query 通用分页查询；null 时使用默认分页
     * @return 就诊实体分页
     */
    public Page<Encounter> pageNurseStationOutpatientEncounters(AbstractPageQuery query) {
        AbstractPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new AbstractPageQuery();
        }
        int pageIndex = effective.springPageIndex();
        int pageSize = 20;
        if (effective.getPageSize() != null && effective.getPageSize() > 0) {
            pageSize = effective.getPageSize();
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.START_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        // B.2.1：关键字非空时无 Sort，保留 Spec 精确优先 ORDER BY
        Pageable pageable;
        if (StringUtils.isNotEmpty(effective.getKeyword())) {
            pageable = PageRequest.of(pageIndex, pageSize);
        } else {
            pageable = PageRequest.of(pageIndex, pageSize, sort);
        }
        return encounterQueryService.pageEncounterEntities(
            encounterContextCriteriaSupport.nurseStationOutpatientSpecification(effective), pageable);
    }

    /**
     * 住院就诊上下文分页。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @param scope 会话收窄范围；null 时不收窄
     * @return 就诊实体分页
     */
    public Page<Encounter> pageInpatientEncounterEntities(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        InpatientEncounterPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new InpatientEncounterPageQuery();
        }
        EncounterSessionScope effectiveScope;
        if (scope != null) {
            effectiveScope = scope;
        } else {
            effectiveScope = EncounterSessionScope.empty();
        }
        Pageable pageable = encounterContextCriteriaSupport.resolveInpatientPageable(effective);
        InpatientEncounterLocationScope locationScope = this.resolveInpatientLocationScope(effective, effectiveScope);
        return encounterQueryService.pageEncounterEntities(
            encounterContextCriteriaSupport.inpatientSpecification(effective, effectiveScope, locationScope),
            pageable);
    }

    /**
     * 住院就诊上下文按住院号去重计数。
     *
     * @param query 住院分页查询；null 时使用空查询
     * @param scope 会话收窄范围；null 时不收窄
     * @return 去重后的住院号数量
     */
    public long countDistinctInpatientBusNo(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        InpatientEncounterPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new InpatientEncounterPageQuery();
        }
        EncounterSessionScope effectiveScope;
        if (scope != null) {
            effectiveScope = scope;
        } else {
            effectiveScope = EncounterSessionScope.empty();
        }
        InpatientEncounterLocationScope locationScope = this.resolveInpatientLocationScope(effective, effectiveScope);
        Specification<Encounter> specification =
            encounterContextCriteriaSupport.inpatientSpecification(effective, effectiveScope, locationScope);
        return encounterQueryService.countDistinctBusNoEncounterEntities(specification);
    }

    /**
     * 住院就诊上下文列表（无分页，按创建时间倒序）。
     *
     * @param query 住院列表查询；null 时使用空查询
     * @param scope 会话收窄范围；null 时不收窄
     * @return 就诊实体列表
     */
    public List<Encounter> listInpatientEncounterEntities(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        InpatientEncounterPageQuery effective;
        if (query != null) {
            effective = query;
        } else {
            effective = new InpatientEncounterPageQuery();
        }
        EncounterSessionScope effectiveScope;
        if (scope != null) {
            effectiveScope = scope;
        } else {
            effectiveScope = EncounterSessionScope.empty();
        }
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        InpatientEncounterLocationScope locationScope = this.resolveInpatientLocationScope(effective, effectiveScope);
        return encounterQueryService.listEncounterEntities(
            encounterContextCriteriaSupport.inpatientSpecification(effective, effectiveScope, locationScope),
            sort);
    }

    /**
     * 构建当前会话就诊查询范围（组织机构 + 可管理病区）。
     *
     * @return 会话收窄范围
     */
    private EncounterSessionScope buildSessionScope() {
        return new EncounterSessionScope(
            managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery(),
            managedOrganizationIdsResolver.isOrganizationScopeEnforcementActive(),
            this.extractWardLocationIds(managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty()));
    }

    /**
     * 从病区位置实体列表提取去重后的位置主键。
     *
     * @param wards 病区位置列表
     * @return 病区位置主键列表；空输入时返回空列表
     */
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

    /**
     * 解析住院查询是否叠加诊断快照（默认 true）。
     *
     * @param query 住院分页查询
     * @return 是否包含诊断快照
     */
    private boolean resolveIncludeDiagnosisSnapshot(InpatientEncounterPageQuery query) {
        if (query == null || query.getIncludeDiagnosisSnapshot() == null) {
            return true;
        }
        return Boolean.TRUE.equals(query.getIncludeDiagnosisSnapshot());
    }

    /**
     * 将住院就诊实体批量转换为上下文读模型（含可选诊断快照）。
     *
     * @param encounters               就诊实体列表
     * @param includeDiagnosisSnapshot 是否叠加诊断快照
     * @return 住院上下文读模型列表
     */
    private List<InpatientEncounterContextQueryModel> toInpatientContextModels(
        List<Encounter> encounters,
        boolean includeDiagnosisSnapshot) {
        return this.applyInpatientDiagnosisSnapshots(
            encounterContextQueryService.batchLoadInpatientContexts(encounters),
            includeDiagnosisSnapshot);
    }

    /**
     * 将门诊就诊实体批量转换为上下文读模型（含诊断快照）。
     *
     * @param encounters 就诊实体列表
     * @return 门诊上下文读模型列表
     */
    private List<OutpatientEncounterContextQueryModel> toOutpatientContextModels(List<Encounter> encounters) {
        return this.applyOutpatientDiagnosisSnapshots(
            encounterContextQueryService.batchLoadOutpatientContexts(encounters));
    }

    /**
     * 批量叠加住院诊断快照 JSON。
     *
     * @param models                   住院上下文列表
     * @param includeDiagnosisSnapshot 是否叠加诊断快照
     * @return 叠加后的列表（就地修改）
     */
    private List<InpatientEncounterContextQueryModel> applyInpatientDiagnosisSnapshots(
        List<InpatientEncounterContextQueryModel> models,
        boolean includeDiagnosisSnapshot) {
        if (!includeDiagnosisSnapshot || models == null || models.isEmpty()) {
            return models == null ? new ArrayList<>() : models;
        }
        for (InpatientEncounterContextQueryModel model : models) {
            this.applyInpatientDiagnosisSnapshot(model, true);
        }
        return models;
    }

    /**
     * 批量叠加门诊诊断快照 JSON。
     *
     * @param models 门诊上下文列表
     * @return 叠加后的列表（就地修改）
     */
    private List<OutpatientEncounterContextQueryModel> applyOutpatientDiagnosisSnapshots(
        List<OutpatientEncounterContextQueryModel> models) {
        if (models == null || models.isEmpty()) {
            return new ArrayList<>();
        }
        for (OutpatientEncounterContextQueryModel model : models) {
            this.applyOutpatientDiagnosisSnapshot(model);
        }
        return models;
    }

    /**
     * 为单条住院上下文叠加诊断快照 JSON。
     *
     * @param model                    住院上下文
     * @param includeDiagnosisSnapshot 是否叠加诊断快照
     * @return 叠加后的上下文（就地修改）
     */
    private InpatientEncounterContextQueryModel applyInpatientDiagnosisSnapshot(
        InpatientEncounterContextQueryModel model,
        boolean includeDiagnosisSnapshot) {
        if (!includeDiagnosisSnapshot || model == null || model.getEncounterId() == null) {
            return model;
        }
        EncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot diagnosisSnapshot =
            encounterDiagnosisSnapshotAssembler.buildSnapshot(model.getEncounterId());
        model.setDiagnosisJson(diagnosisSnapshot.diagnosisJson());
        return model;
    }

    /**
     * 为单条门诊上下文叠加诊断快照 JSON。
     *
     * @param model 门诊上下文
     * @return 叠加后的上下文（就地修改）
     */
    private OutpatientEncounterContextQueryModel applyOutpatientDiagnosisSnapshot(
        OutpatientEncounterContextQueryModel model) {
        if (model == null || model.getEncounterId() == null) {
            return model;
        }
        EncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot diagnosisSnapshot =
            encounterDiagnosisSnapshotAssembler.buildSnapshot(model.getEncounterId());
        model.setDiagnosisJson(diagnosisSnapshot.diagnosisJson());
        return model;
    }

    /**
     * 解析住院就诊位置收窄范围（房间床位 + 病区床位树）。
     *
     * @param query 住院分页查询
     * @param scope 会话收窄范围
     * @return 住院位置收窄范围
     */
    private InpatientEncounterLocationScope resolveInpatientLocationScope(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        if (query == null) {
            return InpatientEncounterLocationScope.empty();
        }
        List<Long> houseBedIds = List.of();
        if (query.getHouseId() != null) {
            houseBedIds = this.listBedIdsUnderParent(query.getHouseId());
        }
        LinkedHashSet<Long> wardIds = new LinkedHashSet<>();
        if (query.getWardId() != null) {
            wardIds.add(query.getWardId());
        }
        List<Long> managedWardIds = query.getManagedWardIds();
        if (managedWardIds == null || managedWardIds.isEmpty()) {
            if (scope != null) {
                managedWardIds = scope.managedWardLocationIds();
            }
        }
        if (managedWardIds != null) {
            for (Long wardId : managedWardIds) {
                if (wardId != null) {
                    wardIds.add(wardId);
                }
            }
        }
        Map<Long, List<Long>> bedIdsByWardId = new HashMap<>();
        for (Long wardId : wardIds) {
            bedIdsByWardId.put(wardId, this.collectBedIdsUnderWard(wardId));
        }
        return new InpatientEncounterLocationScope(houseBedIds, bedIdsByWardId);
    }

    /**
     * 列出指定父位置下的全部床位主键。
     *
     * @param parentLocationId 父位置主键（房间或病区）
     * @return 床位主键列表
     */
    private List<Long> listBedIdsUnderParent(Long parentLocationId) {
        List<Long> bedIds = new ArrayList<>();
        if (parentLocationId == null) {
            return bedIds;
        }
        for (Location bed : locationQueryService.listBedsUnderParent(parentLocationId)) {
            if (bed != null && bed.getId() != null) {
                bedIds.add(bed.getId());
            }
        }
        return bedIds;
    }

    /**
     * 收集病区下全部房间→床位的床位主键。
     *
     * @param wardId 病区位置主键
     * @return 床位主键列表
     */
    private List<Long> collectBedIdsUnderWard(Long wardId) {
        List<Long> bedIds = new ArrayList<>();
        if (wardId == null) {
            return bedIds;
        }
        for (Location room : locationQueryService.listRoomsUnderWard(wardId)) {
            if (room == null || Boolean.TRUE.equals(room.getIsDeleted()) || room.getId() == null) {
                continue;
            }
            for (Location bed : locationQueryService.listBedsUnderParent(room.getId())) {
                if (bed != null && bed.getId() != null) {
                    bedIds.add(bed.getId());
                }
            }
        }
        return bedIds;
    }
}
