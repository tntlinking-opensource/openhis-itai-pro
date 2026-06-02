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

package com.openhis.health.domain.emergency.knowledge.queries;

import com.openhis.health.domain.emergency.knowledge.entity.KnowledgeBaseItem;
import com.openhis.health.domain.emergency.knowledge.queries.model.KnowledgeBaseItemQueryModel;
import com.openhis.health.domain.emergency.knowledge.queries.model.KnowledgeTopNItemQueryModel;
import com.openhis.health.domain.emergency.knowledge.repository.KnowledgeBaseItemRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.openhis.health.domain.emergency.knowledge.KnowledgeConstants.STATUS_ENABLED;

/**
 * 急诊知识库读侧（DM-EMG-002 / SDD §5.2）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeQueryService {

    private final KnowledgeBaseItemRepository knowledgeBaseItemRepository;

    public Page<KnowledgeBaseItemQueryModel> listKnowledgeBaseItemsPaged(KnowledgeBaseItemPageQuery filter, Pageable pageable) {
        Specification<KnowledgeBaseItem> spec = buildSpec(filter);
        Pageable sorted =
                pageable.getSort().isSorted()
                        ? pageable
                        : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "updatedAt"));
        return knowledgeBaseItemRepository.findAll(spec, sorted).map(this::toRow);
    }

    public List<KnowledgeTopNItemQueryModel> listTopKnowledgeItemsForTriage(String type, int limit) {
        if (limit < 1) {
            return List.of();
        }
        List<KnowledgeBaseItem> rows = listEnabledItemsByTypeOrdered(type);
        int cap = Math.min(limit, rows.size());
        List<KnowledgeBaseItem> slice = rows.subList(0, cap);
        return slice.stream().map(this::toTopN).collect(Collectors.toList());
    }

    /** IF-EMG-01：按类型返回全部启用词条（sort_order 升序），供主诉/症状多选下拉。 */
    public List<KnowledgeBaseItemQueryModel> listEnabledPicklistTermsByType(String type) {
        if (StringUtils.isBlank(type)) {
            return List.of();
        }
        return listEnabledItemsByTypeOrdered(type.trim()).stream().map(this::toRow).collect(Collectors.toList());
    }

    private List<KnowledgeBaseItem> listEnabledItemsByTypeOrdered(String type) {
        if (StringUtils.isBlank(type)) {
            return List.of();
        }
        return knowledgeBaseItemRepository.findByTypeAndStatusAndIsDeletedFalseOrderBySortOrderAsc(
                type.trim(), STATUS_ENABLED);
    }

    private Specification<KnowledgeBaseItem> buildSpec(KnowledgeBaseItemPageQuery q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (q == null) {
                return cb.and(ps.toArray(new Predicate[0]));
            }
            if (StringUtils.isNotBlank(q.getNameHint())) {
                String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(q.getNameHint().trim());
                ps.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), pattern));
            }
            if (StringUtils.isNotBlank(q.getPinyinHint())) {
                String trimmed = q.getPinyinHint().trim();
                ps.add(cb.like(cb.lower(root.get("pinyinCode")), SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed)));
            }
            if (q.getTriageLevel() != null) {
                ps.add(cb.equal(root.get("triageLevel"), q.getTriageLevel()));
            }
            if (StringUtils.isNotBlank(q.getType())) {
                ps.add(cb.equal(root.get("type"), q.getType().trim()));
            }
            if (StringUtils.isNotBlank(q.getCategory())) {
                ps.add(cb.equal(root.get("category"), q.getCategory().trim()));
            }
            if (q.getStatus() != null) {
                ps.add(cb.equal(root.get("status"), q.getStatus()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private KnowledgeBaseItemQueryModel toRow(KnowledgeBaseItem e) {
        KnowledgeBaseItemQueryModel m = new KnowledgeBaseItemQueryModel();
        m.setId(e.getId());
        m.setName(e.getName());
        m.setPinyinCode(e.getPinyinCode());
        m.setTriageLevel(e.getTriageLevel());
        m.setType(e.getType());
        m.setCategory(e.getCategory());
        m.setSortOrder(e.getSortOrder());
        m.setStatus(e.getStatus());
        m.setUpdatedAt(e.getUpdatedAt());
        return m;
    }

    private KnowledgeTopNItemQueryModel toTopN(KnowledgeBaseItem e) {
        KnowledgeTopNItemQueryModel m = new KnowledgeTopNItemQueryModel();
        m.setId(e.getId());
        m.setName(e.getName());
        m.setPinyinCode(e.getPinyinCode());
        m.setTriageLevel(e.getTriageLevel());
        m.setSortOrder(e.getSortOrder());
        m.setType(e.getType());
        return m;
    }
}
