/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.service;

import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.query.OrganizationManagementListQuery;
import com.openhis.health.domain.administration.organization.repository.OrganizationRepository;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.utils.IdInQueryChunkSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 机构读侧查询服务。
 *
 * <p>职责：按业务场景（门诊挂号、入院、药房、仓储等）筛选启用机构，并提供主键加载、业务单号解析与废弃前置校验。</p>
 *
 * <p>边界：只读；树形展示当前多为平铺启用列表由前端组装；废弃校验仅检查是否存在启用子节点。</p>
 * <p>聚合边界：仅本聚合 {@link OrganizationRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class OrganizationQueryService {

    private final OrganizationRepository organizationRepository;

    /**
     * 门诊挂号可选机构：启用且分类为门诊（诊所）。
     *
     * @return 机构列表
     */
    public List<Organization> listOrganizationsForOutpatientRegistration() {
        return scopeToCurrentTenant(organizationRepository.findAllByStatusEnumAndClassEnumOrderByDisplayOrderAscIdAsc(
            PublicationStatus.ACTIVE.getCode(), OrganizationClassification.CLINIC));
    }

    /**
     * 门诊挂号机构树数据入口（当前与 {@link #listOrganizationsForOutpatientRegistration()} 同源）。
     *
     * @return 机构列表
     */
    public List<Organization> loadOrganizationTreeForOutpatientRegistration() {
        return this.listOrganizationsForOutpatientRegistration();
    }

    /**
     * 住院入院可选机构：启用且分类为住院。
     *
     * @return 机构列表
     */
    public List<Organization> listOrganizationsForInpatientAdmission() {
        return scopeToCurrentTenant(organizationRepository.findAllByStatusEnumAndClassEnumOrderByDisplayOrderAscIdAsc(
            PublicationStatus.ACTIVE.getCode(), OrganizationClassification.INPATIENT));
    }

    /**
     * 护士站科室下拉：启用且分类为护士站（{@link OrganizationClassification#NS}）。
     *
     * <p>含门诊注射室、采血室、疗区护士站等；<strong>禁止</strong>用挂号科/入院科冒充。</p>
     *
     * @return 护士站机构列表
     */
    public List<Organization> listOrganizationsForNurseStation() {
        return scopeToCurrentTenant(organizationRepository.findAllByStatusEnumAndClassEnumOrderByDisplayOrderAscIdAsc(
            PublicationStatus.ACTIVE.getCode(), OrganizationClassification.NS));
    }

    /**
     * 工作科室下拉：启用的护士站（{@code NS}）与医技科室（{@code TECH}）。
     *
     * <p>护士工作量按岗位 {@code adm_pr_assign.org_id} 统计时使用；与人员主档默认登录科室无关。</p>
     *
     * @return 工作科室列表（按展示顺序）
     */
    public List<Organization> listOrganizationsForWorkDepartment() {
        List<Organization> merged = new ArrayList<>();
        merged.addAll(this.listOrganizationsForNurseStation());
        merged.addAll(scopeToCurrentTenant(organizationRepository.findAllByStatusEnumAndClassEnumOrderByDisplayOrderAscIdAsc(
            PublicationStatus.ACTIVE.getCode(), OrganizationClassification.TECH)));
        return this.sortByDisplayOrder(merged);
    }

    /**
     * 按主键加载机构用于展示（不存在则 {@code null}）。
     *
     * @param organizationId 机构主键
     * @return 机构实体或 {@code null}
     */
    public Organization loadOrganizationByIdForDisplay(Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId).orElse(null);
        if (belongsToCurrentTenant(organization)) {
            return organization;
        }
        return null;
    }

    /**
     * 按主键批量加载机构实体（登录 OU 桥接等避免逐条 {@link #loadOrganizationByIdForDisplay}）。
     *
     * <p>仅本聚合 {@link OrganizationRepository}；结果已按当前租户过滤。</p>
     *
     * @param organizationIds 机构主键集合；{@code null} 或空时返回空映射
     * @return 机构主键 → 实体；未命中或越租户时不写入该键
     */
    public Map<Long, Organization> mapByIds(Collection<Long> organizationIds) {
        Map<Long, Organization> organizationById = new HashMap<>();
        if (organizationIds == null || organizationIds.isEmpty()) {
            return organizationById;
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long organizationId : organizationIds) {
            if (organizationId != null) {
                distinctIds.add(organizationId);
            }
        }
        if (distinctIds.isEmpty()) {
            return organizationById;
        }
        // Pre：机构主键 IN 分批
        List<Organization> organizations = IdInQueryChunkSupport.loadByIdInChunks(
            distinctIds,
            chunkIds -> scopeToCurrentTenant(organizationRepository.findAllByIdIn(new LinkedHashSet<>(chunkIds))));
        for (Organization organization : organizations) {
            if (organization != null && organization.getId() != null) {
                organizationById.put(organization.getId(), organization);
            }
        }
        return organizationById;
    }

    /**
     * 批量解析机构名称（列表/发药单等场景避免 N+1，对齐 A.9.1）。
     *
     * @param organizationIds 机构主键集合；{@code null} 或空时返回空映射
     * @return 机构主键 → 名称；未命中或无名称时不写入该键
     */
    public Map<Long, String> mapNamesByIds(Collection<Long> organizationIds) {
        Map<Long, String> nameByOrganizationId = new HashMap<>();
        if (organizationIds == null || organizationIds.isEmpty()) {
            return nameByOrganizationId;
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long organizationId : organizationIds) {
            if (organizationId != null) {
                distinctIds.add(organizationId);
            }
        }
        if (distinctIds.isEmpty()) {
            return nameByOrganizationId;
        }
        // Pre：机构主键 IN 分批
        List<Organization> organizations = IdInQueryChunkSupport.loadByIdInChunks(
            distinctIds,
            chunkIds -> scopeToCurrentTenant(organizationRepository.findAllByIdIn(new LinkedHashSet<>(chunkIds))));
        for (Organization organization : organizations) {
            if (organization.getId() != null && organization.getName() != null) {
                nameByOrganizationId.put(organization.getId(), organization.getName());
            }
        }
        return nameByOrganizationId;
    }

    /**
     * 当前租户是否已存在未废弃的医院（编辑时可排除自身）。
     *
     * <p>管理态口径：含草稿/启用/停用，不含已废弃（DISCARDED）记录。</p>
     *
     * @param excludeOrganizationId 编辑场景下排除的机构主键；新建时传 {@code null}
     * @return {@code true} 表示除排除项外仍存在医院
     */
    public boolean existsHospitalExcluding(Long excludeOrganizationId) {
        List<Organization> hospitals = scopeToCurrentTenant(organizationRepository.findAllByTypeEnumAndStatusEnumIn(
            OrganizationType.HOSPITAL.getCode(),
            List.of(
                PublicationStatus.DRAFT.getCode(),
                PublicationStatus.ACTIVE.getCode(),
                PublicationStatus.INACTIVE.getCode())));
        for (Organization hospital : hospitals) {
            if (hospital == null || hospital.getId() == null) {
                continue;
            }
            if (excludeOrganizationId != null && excludeOrganizationId.equals(hospital.getId())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 当前租户启用态医院列表（读 {@code medins_type} 等医院级属性）。
     *
     * <p>仅本聚合 Repository；含租户裁剪。病案首页中医信息等按医院节点机构类型判定。</p>
     *
     * @return 启用医院；无则空列表
     */
    public List<Organization> listActiveHospitals() {
        return scopeToCurrentTenant(organizationRepository.findAllByTypeEnumAndStatusEnumIn(
            OrganizationType.HOSPITAL.getCode(),
            List.of(PublicationStatus.ACTIVE.getCode())));
    }

    /**
     * 判断机构是否允许作废：若仍存在启用子机构则不可作废，防止断树或遗留悬挂数据。
     *
     * @param organizationId 待判断机构主键
     * @return {@code true} 表示当前无启用子节点，可作废
     */
    public boolean checkOrganizationAbolishable(Long organizationId) {
        return !organizationRepository.existsByParentIdAndStatusEnum(
            organizationId, PublicationStatus.ACTIVE.getCode());
    }

    /**
     * 全部启用机构列表（平铺），供通用树/字典组装。
     *
     * @return 启用机构列表
     */
    public List<Organization> getOrganizationTree() {
        return scopeToCurrentTenant(
            organizationRepository.findAllByStatusEnumOrderByDisplayOrderAscIdAsc(
                PublicationStatus.ACTIVE.getCode()));
    }

    /**
     * 科室管理列表：含 DRAFT/ACTIVE/INACTIVE，不含 DISCARDED 已废弃机构。
     *
     * @return 机构平铺列表
     */
    public List<Organization> listOrganizationsForManagement() {
        return listOrganizationsForManagement(new OrganizationManagementListQuery());
    }

    /**
     * 科室管理列表（带筛选）：在管理态机构范围内按条件过滤，并保留命中节点的上级以维持树形展示。
     *
     * @param query 查询条件；{@code null} 时等价于无筛选
     * @return 平铺机构列表
     */
    public List<Organization> listOrganizationsForManagement(OrganizationManagementListQuery query) {
        List<Organization> allManagement = scopeToCurrentTenant(
            organizationRepository.findAllByStatusEnumInOrderByDisplayOrderAscIdAsc(
                List.of(
                    PublicationStatus.DRAFT.getCode(),
                    PublicationStatus.ACTIVE.getCode(),
                    PublicationStatus.INACTIVE.getCode())));
        OrganizationManagementListQuery effective;
        if (query == null) {
            effective = new OrganizationManagementListQuery();
        } else {
            effective = query;
        }
        if (!effective.hasAnyFilter()) {
            return allManagement;
        }

        Map<Long, Organization> byId = new HashMap<>();
        for (Organization organization : allManagement) {
            if (organization.getId() != null) {
                byId.put(organization.getId(), organization);
            }
        }

        Set<Long> visibleIds = new LinkedHashSet<>();
        for (Organization organization : allManagement) {
            if (!matchesManagementFilter(organization, effective)) {
                continue;
            }
            // 命中节点需连同上级一并返回，否则树表无法挂载。
            Long currentId = organization.getId();
            while (currentId != null) {
                if (!visibleIds.add(currentId)) {
                    break;
                }
                Organization current = byId.get(currentId);
                if (current == null) {
                    currentId = null;
                } else {
                    currentId = current.getParentId();
                }
            }
        }

        List<Organization> filtered = new ArrayList<>();
        for (Organization organization : allManagement) {
            if (organization.getId() != null && visibleIds.contains(organization.getId())) {
                filtered.add(organization);
            }
        }
        return filtered;
    }

    /**
     * 机构字典树数据（与 {@link #getOrganizationTree()} 同源）。
     *
     * @return 启用机构列表
     */
    public List<Organization> getOrganizationDictTree() {
        return this.getOrganizationTree();
    }

    /**
     * 责任科室 / 默认登录科室 / 出诊科室 / 执行科室下拉：仅启用「科室」节点且已维护分类。
     *
     * <p>排除医院根、分类为空的脏数据；名称与类型均以 {@code adm_organization} 为准，
     * 不得把医院或占位节点当成可选科室。根节点（{@code parentId == null}）即使
     * {@code typeEnum} 误标为科室，也按医院根排除，避免「执行科室」等下拉里出现院名。</p>
     *
     * @return 可作责任/登录/出诊/执行科室的机构子集
     */
    public List<Organization> getOrganizationTreeForResponsibilityDept() {
        List<Organization> all = this.getOrganizationTree();
        List<Organization> result = new ArrayList<>();
        // Loop：只要科室层级且分类已维护的节点
        for (Organization organization : all) {
            if (organization == null) {
                continue;
            }
            // 根节点为医院（选院步骤），不得进入科室下拉；兼容 type 误标为科室的脏数据
            if (organization.getParentId() == null) {
                continue;
            }
            // 医院层级（含 code / sortOrder 数字串）属选院步骤，不得进入科室下拉
            OrganizationType type = OrganizationType.resolveRaw(organization.getTypeEnum());
            if (type != OrganizationType.DEPARTMENT) {
                continue;
            }
            // 分类为空多为导入脏节点，不可选
            if (organization.getClassEnum() == null) {
                continue;
            }
            result.add(organization);
        }
        return this.sortByDisplayOrder(result);
    }

    /**
     * 机构按 displayOrder、id 升序排序（返回新列表；null 排序号置后，对齐 DM §9.1）。
     *
     * @param organizations 原始列表
     * @return 排序副本；空输入返回不可变空列表
     */
    private List<Organization> sortByDisplayOrder(List<Organization> organizations) {
        if (organizations == null || organizations.isEmpty()) {
            return List.of();
        }
        List<Organization> sorted = new ArrayList<>(organizations);
        sorted.sort(Comparator
            .comparing(Organization::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Organization::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    /**
     * 按主键批量解析机构名称（工作台列表等轻量展示）。
     *
     * @param organizationIds 机构主键集合
     * @return 机构主键 → 名称
     */
    public Map<Long, String> findOrganizationNamesByIds(Collection<Long> organizationIds) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : organizationIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>(idSet.size());
        // Pre：机构主键 IN 分批
        List<Organization> organizations = IdInQueryChunkSupport.loadByIdInChunks(
            idSet,
            chunkIds -> scopeToCurrentTenant(organizationRepository.findAllByIdIn(new LinkedHashSet<>(chunkIds))));
        for (Organization o : organizations) {
            out.put(o.getId(), StringUtils.defaultString(o.getName()));
        }
        return out;
    }

    /**
     * 按业务单号解析机构（首条匹配）。
     *
     * @param busNo 机构业务单号
     * @return 机构实体；无匹配时 {@code null}
     */
    public Organization findFirstByBusNo(String busNo) {
        Organization organization = organizationRepository.findFirstByBusNo(busNo).orElse(null);
        if (belongsToCurrentTenant(organization)) {
            return organization;
        }
        return null;
    }

    /**
     * 按业务单号批量解析机构主键（登录 OU code=busNo 反查，避免 N+1）。
     *
     * @param busNos 业务单号集合；{@code null} 或空时返回空映射
     * @return busNo → 机构主键；未命中不写入；同一 busNo 多条时保留先出现者
     */
    public Map<String, Long> mapIdsByBusNos(Collection<String> busNos) {
        Map<String, Long> idByBusNo = new HashMap<>();
        if (busNos == null || busNos.isEmpty()) {
            return idByBusNo;
        }
        Set<String> distinctBusNos = new LinkedHashSet<>();
        for (String busNo : busNos) {
            if (busNo == null || busNo.isBlank()) {
                continue;
            }
            distinctBusNos.add(busNo.trim());
        }
        if (distinctBusNos.isEmpty()) {
            return idByBusNo;
        }
        for (Organization organization : scopeToCurrentTenant(organizationRepository.findByBusNoIn(distinctBusNos))) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            if (organization.getBusNo() == null || organization.getBusNo().isBlank()) {
                continue;
            }
            String key = organization.getBusNo().trim();
            if (!idByBusNo.containsKey(key)) {
                idByBusNo.put(key, organization.getId());
            }
        }
        return idByBusNo;
    }

    /**
     * 解析卫统 N041 入/出院科别码（机构维护 {@code caty}，对齐国基 inHosDeptype/outDeptype）。
     *
     * <p>首页 {@code adm_caty_code}/{@code dscg_caty_code} 常存医保 {@code yb_no}（如 A03），
     * 直报要的是卫统科别数字码（如 04）。禁止把医保码原样写入 N041。</p>
     *
     * <p>解析顺序：按 yb_no 命中取 caty → 按 bus_no 命中取 caty → 入参已是纯数字卫统码则原样；
     * 否则空串（宁可缺报，勿导错码表）。</p>
     *
     * @param rawHomepageCaty 首页科别码（yb_no / bus_no / 已是卫统数字码）
     * @return 卫统科别码；无法可靠解析则空串
     */
    public String resolveWeistatCatyCode(String rawHomepageCaty) {
        String raw = StringUtils.trimToNull(rawHomepageCaty);
        if (raw == null) {
            return "";
        }
        // Pre：先按医保码反查科室 caty（首页自动带入多为 yb_no）
        List<Organization> byYb = scopeToCurrentTenant(organizationRepository.findByYbNoIn(List.of(raw)));
        for (Organization organization : byYb) {
            String caty = StringUtils.trimToNull(organization.getCaty());
            if (caty != null) {
                return caty;
            }
        }
        // Pre：再按院内 bus_no 反查（误存层级码时）
        List<Organization> byBus = scopeToCurrentTenant(organizationRepository.findByBusNoIn(List.of(raw)));
        for (Organization organization : byBus) {
            String caty = StringUtils.trimToNull(organization.getCaty());
            if (caty != null) {
                return caty;
            }
        }
        // 已是卫统数字科别（国基样例 04）
        if (looksLikeWeistatDeptCaty(raw)) {
            return raw;
        }
        // 医保码等无法映射时不回落原文，避免 N041 写入 A03 这类错体系码
        return "";
    }

    /**
     * 是否像卫统科别数字码（1～6 位纯数字，如 {@code 04}）。
     *
     * @param raw 候选
     * @return true 表示可作 N041 RYKB/CYKB 原文
     */
    private boolean looksLikeWeistatDeptCaty(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        if (raw.length() > 6) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 按院内 {@code bus_no} 批量取科室医保编码 {@code yb_no}（病案 4401/4101A 科别出站）。
     *
     * <p>仅本聚合 Repository；无 {@code yb_no} 的机构不写入映射。</p>
     *
     * @param busNos 院内业务号集合；空则返回空 Map
     * @return busNo → yb_no；未命中或不含医保编码不写入
     */
    public Map<String, String> mapYbNoByBusNos(Collection<String> busNos) {
        Map<String, String> ybNoByBusNo = new HashMap<>();
        if (busNos == null || busNos.isEmpty()) {
            return ybNoByBusNo;
        }
        Set<String> distinctBusNos = new LinkedHashSet<>();
        for (String busNo : busNos) {
            if (busNo == null || busNo.isBlank()) {
                continue;
            }
            distinctBusNos.add(busNo.trim());
        }
        if (distinctBusNos.isEmpty()) {
            return ybNoByBusNo;
        }
        for (Organization organization : scopeToCurrentTenant(organizationRepository.findByBusNoIn(distinctBusNos))) {
            if (organization == null) {
                continue;
            }
            if (organization.getBusNo() == null || organization.getBusNo().isBlank()) {
                continue;
            }
            if (organization.getYbNo() == null || organization.getYbNo().isBlank()) {
                continue;
            }
            String key = organization.getBusNo().trim();
            if (!ybNoByBusNo.containsKey(key)) {
                ybNoByBusNo.put(key, organization.getYbNo().trim());
            }
        }
        return ybNoByBusNo;
    }

    /**
     * 租户隔离：组织须属于当前租户；无租户上下文时不拦截。
     *
     * @param organization 待判定组织
     * @return true 表示可见
     */
    private boolean belongsToCurrentTenant(Organization organization) {
        if (organization == null) {
            return false;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return true;
        }
        return tenantId.equals(organization.getTenantId());
    }

    /**
     * 批量组织结果按当前租户过滤；无租户上下文时原样返回。
     *
     * @param organizations 原始列表
     * @return 租户内组织列表
     */
    private List<Organization> scopeToCurrentTenant(List<Organization> organizations) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null || organizations == null || organizations.isEmpty()) {
            if (organizations == null) {
                return List.of();
            }
            return organizations;
        }
        List<Organization> scoped = new ArrayList<>(organizations.size());
        for (Organization organization : organizations) {
            if (belongsToCurrentTenant(organization)) {
                scoped.add(organization);
            }
        }
        return scoped;
    }

    /**
     * 判断机构是否满足科室管理页筛选条件。
     *
     * @param organization 候选机构
     * @param query 查询条件
     * @return 满足时为 true
     */
    private boolean matchesManagementFilter(Organization organization, OrganizationManagementListQuery query) {
        if (organization == null || query == null) {
            return false;
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())
            && !query.getStatusEnum().trim().equals(organization.getStatusEnum())) {
            return false;
        }
        OrganizationType typeFilter = OrganizationType.resolveRaw(query.getTypeEnum());
        if (typeFilter != null && !typeFilter.getCode().equals(organization.getTypeEnum())) {
            return false;
        }
        OrganizationClassification classFilter = resolveOrganizationClassification(query.getClassEnum());
        if (classFilter != null && organization.getClassEnum() != classFilter) {
            return false;
        }
        if (StringUtils.isEmpty(query.getKeyword())) {
            return true;
        }
        return matchesKeyword(organization, query.getKeyword().trim());
    }

    /**
     * 关键字模糊匹配：名称、医保字段、拼音/五笔、默认医师、科别等（不区分大小写）。
     *
     * @param organization 机构
     * @param keyword 已 trim 的关键字
     * @return 命中时为 true
     */
    private boolean matchesKeyword(Organization organization, String keyword) {
        String core = keyword.toLowerCase(java.util.Locale.ROOT);
        return containsIgnoreCase(organization.getName(), core)
            || containsIgnoreCase(organization.getYbNo(), core)
            || containsIgnoreCase(organization.getYbName(), core)
            || containsIgnoreCase(organization.getMedinsId(), core)
            || containsIgnoreCase(organization.getMedinsAdmdvs(), core)
            || containsIgnoreCase(organization.getMedinsType(), core)
            || containsIgnoreCase(organization.getMedinsLv(), core)
            || containsIgnoreCase(organization.getPyStr(), core)
            || containsIgnoreCase(organization.getWbStr(), core)
            || containsIgnoreCase(organization.getCaty(), core)
            || containsIgnoreCase(organization.getBusNo(), core)
            || (organization.getDefDoctorId() != null
                && String.valueOf(organization.getDefDoctorId()).toLowerCase().contains(core));
    }

    /**
     * 不区分大小写的子串包含判断。
     *
     * @param source 源字符串
     * @param keywordCore 已小写化的关键字（不含 % 通配符）
     * @return 包含时为 true
     */
    private boolean containsIgnoreCase(String source, String keywordCore) {
        if (StringUtils.isEmpty(source)) {
            return false;
        }
        return source.toLowerCase().contains(keywordCore);
    }

    /**
     * 解析机构业务分类：支持领域 code 或 sortOrder 数字字符串。
     *
     * @param classEnumRaw 前端原始值
     * @return 分类枚举；无法解析时返回 null
     */
    private OrganizationClassification resolveOrganizationClassification(String classEnumRaw) {
        if (StringUtils.isEmpty(classEnumRaw)) {
            return null;
        }
        String normalized = classEnumRaw.trim();
        for (OrganizationClassification item : OrganizationClassification.values()) {
            if (item != null && item.getCode().equals(normalized)) {
                return item;
            }
        }
        try {
            long sortOrder = Long.parseLong(normalized);
            for (OrganizationClassification item : OrganizationClassification.values()) {
                if (item != null && item.getSortOrder() != null && item.getSortOrder().longValue() == sortOrder) {
                    return item;
                }
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }
}
