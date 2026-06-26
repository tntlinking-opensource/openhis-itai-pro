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

package com.openhis.health.domain.diagnosis.bodystructure.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.diagnosis.bodystructure.entity.BodyStructure;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureInfoQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureListItemQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureTreeQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.repository.BodyStructureRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 身体部位（解剖部位）主数据读侧查询服务。
 *
 * <p>职责：提供树形检索、简项分页与单条详情，供诊断录入与编码辅助使用；只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class BodyStructureQueryService {
    private final BodyStructureRepository repository;


    /**
     * 构建部位树：先索引全部节点，再按 parentId 挂载子节点；孤儿节点挂到根集合以免丢失展示。
     *
     * @return 森林根节点列表（无父或父缺失的节点均可能出现在根层）
     */
    public List<BodyStructureTreeQueryModel> getBodyStructureTree() {
        List<BodyStructure> bodyStructures = repository.findAllByOrderByBusNoAsc();
        Map<Long, BodyStructureTreeQueryModel> map = new HashMap<>();
        List<BodyStructureTreeQueryModel> roots = new ArrayList<>();
        // 第一遍：为每个未删部位建立树节点并放入 id→节点 索引
        for (BodyStructure bodyStructure : bodyStructures) {
            if (Boolean.TRUE.equals(bodyStructure.getIsDeleted())) {
                continue;
            }
            BodyStructureTreeQueryModel model = new BodyStructureTreeQueryModel();
            model.setId(bodyStructure.getId());
            model.setBusNo(bodyStructure.getBusNo());
            model.setName(bodyStructure.getName());
            model.setStatusEnum(bodyStructure.getStatusEnum());
            map.put(model.getId(), model);
        }
        for (BodyStructure bodyStructure : bodyStructures) {
            if (Boolean.TRUE.equals(bodyStructure.getIsDeleted())) {
                continue;
            }
            BodyStructureTreeQueryModel current = map.get(bodyStructure.getId());
            // 无父节点则为顶层部位（如全身、系统级）
            if (bodyStructure.getParentId() == null) {
                roots.add(current);
            } else {
                BodyStructureTreeQueryModel parent = map.get(bodyStructure.getParentId());
                // 父存在则挂到子列表；父缺失时降级为根，避免界面丢节点
                if (parent != null) {
                    parent.getChildren().add(current);
                } else {
                    roots.add(current);
                }
            }
        }
        return roots;
    }

    /**
     * 按主键取单条部位简讯（含拼音五笔检索字段）；不存在返回 null，由调用方决定是否提示。
     *
     * @param id 部位主键
     * @return 信息模型或 null
     */
    public BodyStructureInfoQueryModel getBodyStructureInfo(Long id) {
        BodyStructure bodyStructure = repository.findById(id).orElse(null);
        if (bodyStructure == null) {
            return null;
        }
        BodyStructureInfoQueryModel model = new BodyStructureInfoQueryModel();
        model.setId(bodyStructure.getId());
        model.setBusNo(bodyStructure.getBusNo());
        model.setName(bodyStructure.getName());
        model.setParentId(bodyStructure.getParentId());
        model.setStatusEnum(bodyStructure.getStatusEnum());
        model.setPyStr(bodyStructure.getPyStr());
        model.setWbStr(bodyStructure.getWbStr());
        return model;
    }

    /**
     * 关键字检索部位简表：可按「仅启用」收窄，名称/拼音/五笔任一匹配即命中；分页由仓储完成。
     *
     * @param keyword 关键字，null 当作空串
     * @param onlyActive true 时仅返回启用发布态部位
     * @param pageable 分页参数
     * @return 分页列表项
     */
    public Page<BodyStructureListItemQueryModel> getList(String keyword, boolean onlyActive, Pageable pageable) {
        String keywordForTrim = "";
        if (keyword != null) {
            keywordForTrim = keyword;
        }
        String qTrim = trimToEmpty(keywordForTrim);
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(qTrim);
        Specification<BodyStructure> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (onlyActive) {
                preds.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            }
            preds.add(cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
            ));
            return cb.and(preds.toArray(Predicate[]::new));
        };
        Page<BodyStructure> page = repository.findAll(spec, applyDefaultListSort(pageable));
        return page.map(item -> {
            BodyStructureListItemQueryModel model = new BodyStructureListItemQueryModel();
            model.setId(item.getId());
            model.setBusNo(item.getBusNo());
            model.setName(item.getName());
            model.setParentId(item.getParentId());
            model.setStatusEnum(item.getStatusEnum());
            return model;
        });
    }

    /**
     * 按主键批量解析部位展示名（申请单明细、列表回显等场景避免 N+1）。
     *
     * @param bodyStructureIds 部位主键集合；{@code null} 或空时返回空映射
     * @return id → 名称；未命中、已删或无名称时为 {@code null} 值
     */
    public Map<Long, String> mapNamesByIds(Collection<Long> bodyStructureIds) {
        Map<Long, String> result = new HashMap<>();
        if (bodyStructureIds == null || bodyStructureIds.isEmpty()) {
            return result;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : bodyStructureIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<BodyStructure> rows = repository.findByIdIn(distinct);
        for (BodyStructure row : rows) {
            if (row == null || row.getId() == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            result.put(row.getId(), row.getName());
        }
        return result;
    }

    /** 列表未指定排序时默认按主键升序，供医生站 init 下拉稳定次序。 */
    private static Pageable applyDefaultListSort(Pageable pageable) {
        Sort defaultSort = Sort.by(Sort.Direction.ASC, FieldNameConstants.ID);
        if (pageable == null) {
            return PageRequest.of(0, Integer.MAX_VALUE, defaultSort);
        }
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        if (!pageable.isPaged()) {
            return PageRequest.of(0, Integer.MAX_VALUE, defaultSort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }
}
