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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 费用性质（合同/结算口径）主数据读侧查询服务。
 *
 * <p>职责：为收费、医保与报表提供费用性质详情与列表；只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class ContractQueryService {
    private final ContractRepository contractRepository;


    /**
     * 按主键取费用性质详情；不存在返回 null，由调用方决定是否报错。
     *
     * @param id 主键
     * @return 详情模型或 null
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
            .map(item -> {
                ContractListItemQueryModel model = new ContractListItemQueryModel();
                model.setId(item.getId());
                model.setBusNo(item.getBusNo());
                model.setName(item.getName());
                model.setCategoryEnum(item.getCategoryEnum());
                model.setActiveFlag(item.getActiveFlag());
                model.setDisplayOrder(item.getDisplayOrder());
                return model;
            });
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
            ContractListItemQueryModel model = new ContractListItemQueryModel();
            model.setId(item.getId());
            model.setBusNo(item.getBusNo());
            model.setName(item.getName());
            model.setCategoryEnum(item.getCategoryEnum());
            model.setActiveFlag(item.getActiveFlag());
            model.setDisplayOrder(item.getDisplayOrder());
            out.add(model);
        }
        return out;
    }

    /**
     * 全量费用性质简表（不分页，适用于下拉或缓存预热；数据量由主数据规模约束）。
     *
     * @return 全部列表项，按展示序与主键排序
     */
    public List<ContractListItemQueryModel> listAllContracts() {
        return contractRepository.findAll(Sort.by(Sort.Direction.ASC, FieldNameConstants.DISPLAY_ORDER, FieldNameConstants.ID))
            .stream()
            .map(item -> {
                ContractListItemQueryModel model = new ContractListItemQueryModel();
                model.setId(item.getId());
                model.setBusNo(item.getBusNo());
                model.setName(item.getName());
                model.setCategoryEnum(item.getCategoryEnum());
                model.setActiveFlag(item.getActiveFlag());
                model.setDisplayOrder(item.getDisplayOrder());
                return model;
            })
            .toList();
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
            if (StringUtils.isNotBlank(busNo)) {
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
}
