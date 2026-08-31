/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.contract.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.repository.ContractRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 费用性质（合同/结算口径）主数据读侧查询服务。
 *
 * <p>职责：为收费、医保与报表提供费用性质详情与列表；只读不写库。</p>
 * <p>聚合边界：仅本聚合 {@link ContractRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ContractQueryService {
    private final ContractRepository contractRepository;


    /**
     * 按业务号加载费用性质实体（bridge/编排读路径）；不存在或已软删时 empty。
     *
     * @param busNo 费用性质业务号
     * @return 费用性质实体；无效入参或不存在时 empty
     */
    public Optional<Contract> findOptionalContractEntityByBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            return Optional.empty();
        }
        return contractRepository.findByBusNo(busNo.trim())
                .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 按主键加载费用性质实体（bridge/编排读路径）；不存在或已软删时 empty。
     *
     * @param id 费用性质主键
     * @return 费用性质实体；主键为空或不存在时 empty
     */
    public Optional<Contract> findOptionalContractEntityById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return contractRepository.findById(id)
                .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 按主键取费用性质详情；不存在返回 null，由调用方决定是否报错。
     *
     * @param id 费用性质主键
     * @return 详情读模型；不存在时 {@code null}
     */
    public ContractDetailQueryModel findContractById(Long id) {
        Contract contract = contractRepository.findById(id).orElse(null);
        if (contract == null) {
            return null;
        }
        ContractDetailQueryModel model = new ContractDetailQueryModel();
        model.setId(contract.getId());
        model.setBusNo(contract.getBusNo());
        model.setName(contract.getName());
        model.setCategoryEnum(contract.getCategoryEnum());
        model.setStatusEnum(contract.getStatusEnum());
        model.setActiveFlag(contract.getActiveFlag());
        model.setDisplayOrder(contract.getDisplayOrder());
        model.setYbFlag(contract.getYbFlag());
        return model;
    }

    /**
     * 按机构分页列出费用性质简项，展示顺序按 displayOrder 升序。
     *
     * @param orgId 机构标识
     * @param pageable 分页参数
     * @return 分页列表项
     */
    public Page<ContractListItemQueryModel> listContracts(Long orgId, Pageable pageable) {
        return contractRepository.findAllByOrgIdOrderByDisplayOrderAsc(orgId, pageable)
            .map(this::toListItem);
    }

    /**
     * 按机构列出全部费用性质简项（不分页；体量由主数据配置约束，供医生站就诊上下文下拉等场景）。
     *
     * @param orgId 机构标识
     * @return 列表项，按展示序与主键排序；无配置时返回空列表
     */
    public List<ContractListItemQueryModel> listAllContractsByOrgId(Long orgId) {
        List<ContractListItemQueryModel> out = new ArrayList<>();
        // Loop：遍历机构下全部费用性质记录，装配列表项模型
        for (Contract item : contractRepository.findAllByOrgIdOrderByDisplayOrderAscIdAsc(orgId)) {
            if (item == null) {
                continue;
            }
            out.add(this.toListItem(item));
        }
        return out;
    }

    /**
     * 全量费用性质简表（不分页，适用于下拉或缓存预热；数据量由主数据规模约束）。
     *
     * <p>排除已软删记录，与 {@link #listAllContractsByOrgId(Long)} 口径一致。</p>
     *
     * @return 有效列表项，按展示序与主键排序
     */
    public List<ContractListItemQueryModel> listAllContracts() {
        List<ContractListItemQueryModel> out = new ArrayList<>();
        // Loop：遍历租户内未删费用性质，装配下拉/缓存项
        for (Contract item : contractRepository.findAll(
            Sort.by(Sort.Direction.ASC, FieldNameConstants.DISPLAY_ORDER, FieldNameConstants.ID))) {
            if (item == null || Boolean.TRUE.equals(item.getIsDeleted())) {
                // 软删合同不参与下拉与展示判定
                continue;
            }
            out.add(this.toListItem(item));
        }
        return out;
    }

    /**
     * 按主键批量加载费用性质简表（页内装配，避免 listAllContracts 全表）。
     *
     * @param ids 合同主键集合
     * @return 未软删列表项；空入参为空列表
     */
    public List<ContractListItemQueryModel> listContractsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        // Loop：去重非空合同主键
        for (Long id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        List<ContractListItemQueryModel> out = new ArrayList<>();
        // Loop：页内合同主键 → 简表（跳过软删）
        for (Contract item : contractRepository.findAllById(distinct)) {
            if (item == null || Boolean.TRUE.equals(item.getIsDeleted())) {
                continue;
            }
            out.add(this.toListItem(item));
        }
        return out;
    }

    /**
     * 实体 → 列表项：含通道区划与医保标记，供挂号 init 一次装配，避免按 id 回表。
     *
     * @param item 费用性质实体
     * @return 列表读模型
     */
    private ContractListItemQueryModel toListItem(Contract item) {
        ContractListItemQueryModel model = new ContractListItemQueryModel();
        model.setId(item.getId());
        model.setBusNo(item.getBusNo());
        model.setName(item.getName());
        model.setCategoryEnum(item.getCategoryEnum());
        model.setActiveFlag(item.getActiveFlag());
        model.setDisplayOrder(item.getDisplayOrder());
        model.setAdmVs(item.getAdmVs());
        model.setYbFlag(item.getYbFlag());
        return model;
    }

    /**
     * 按费用性质业务号批量解析展示名称（合同名称）。
     *
     * @param busNos 合同业务号集合，允许含空白，将被忽略
     * @return 业务号 → 名称；无命中时该业务号不在映射中
     */
    public Map<String, String> mapContractNameByBusNoIn(Collection<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        // Loop：遍历入参集合，提取有效业务号
        for (String busNo : busNos) {
            if (StringUtils.isNotEmpty(busNo)) {
                unique.add(busNo.trim());
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Contract> list = contractRepository.findAllByBusNoIn(unique);
        Map<String, String> out = new HashMap<>(list.size() * 2);
        // Loop：遍历查询结果，构建业务号到名称的映射
        for (Contract contract : list) {
            if (contract == null || contract.getBusNo() == null) {
                continue;
            }
            out.put(contract.getBusNo(), StringUtils.defaultString(contract.getName()));
        }
        return out;
    }

    /**
     * 按业务号批量加载费用性质实体（交账/报表 enrichment 用）。
     *
     * @param busNos 合同业务号集合
     * @return 未软删的合同实体列表；无命中时空列表
     */
    public List<Contract> findAllContractEntitiesByBusNoIn(Collection<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String busNo : busNos) {
            if (StringUtils.isNotEmpty(busNo)) {
                unique.add(busNo.trim());
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyList();
        }
        List<Contract> list = contractRepository.findAllByBusNoIn(unique);
        List<Contract> out = new ArrayList<>(list.size());
        for (Contract contract : list) {
            if (contract == null || Boolean.TRUE.equals(contract.getIsDeleted())) {
                continue;
            }
            out.add(contract);
        }
        return out;
    }

    /**
     * 按合同业务号批量解析合同主键。
     *
     * @param busNos 合同业务号集合
     * @return 业务号 → 合同主键
     */
    public Map<String, Long> mapContractIdByBusNoIn(Collection<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String busNo : busNos) {
            if (StringUtils.isNotEmpty(busNo)) {
                unique.add(busNo.trim());
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Contract> list = contractRepository.findAllByBusNoIn(unique);
        Map<String, Long> out = new HashMap<>(list.size() * 2);
        for (Contract contract : list) {
            if (contract == null || contract.getBusNo() == null || contract.getId() == null) {
                continue;
            }
            out.put(contract.getBusNo(), contract.getId());
        }
        return out;
    }

    /**
     * 按费用性质分类码解析合同主键集合（AS-OP-PHA-005 费用性质筛预解析，B.6.4）。
     *
     * @param categoryEnum {@link com.openhis.health.domain.share.enums.ContractCategory} 持久化 code；空则返回空列表
     * @return 未软删且分类命中的合同主键列表
     */
    public List<Long> listIdsByCategoryEnum(String categoryEnum) {
        String normalized = StringUtils.trimToNull(categoryEnum);
        if (normalized == null) {
            return Collections.emptyList();
        }
        List<Contract> all = contractRepository.findAll();
        List<Long> ids = new ArrayList<>();
        // Loop：全表未删合同中筛分类码；规模可控的主数据
        for (Contract contract : all) {
            if (contract == null || Boolean.TRUE.equals(contract.getIsDeleted())) {
                continue;
            }
            if (normalized.equals(contract.getCategoryEnum()) && contract.getId() != null) {
                ids.add(contract.getId());
            }
        }
        return ids;
    }
}
