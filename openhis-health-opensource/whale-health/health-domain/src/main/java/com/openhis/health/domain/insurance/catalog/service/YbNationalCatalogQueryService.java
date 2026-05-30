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

package com.openhis.health.domain.insurance.catalog.service;

import com.openhis.health.domain.insurance.catalog.entity.YbNationalCatalogItem;
import com.openhis.health.domain.insurance.catalog.queries.NationalCatalogPageQuery;
import com.openhis.health.domain.insurance.catalog.repository.YbNationalCatalogItemRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 国家医保目录镜像只读查询。
 */
@Service
@RequiredArgsConstructor
public class YbNationalCatalogQueryService {

    private final YbNationalCatalogItemRepository repository;

    public Page<YbNationalCatalogItem> page(NationalCatalogPageQuery query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = query.getPageSize() <= 0 ? 20 : query.getPageSize();
        PageRequest pageable = PageRequest.of(pageNo - 1, pageSize,
            Sort.by(Sort.Direction.ASC, "hilistCode"));
        return repository.findAll(buildSpec(query), pageable);
    }

    /** 本地镜像最大 updt_time，供 1312 增量游标；无数据时返回 empty。 */
    public Optional<LocalDate> findMaxUpdtTime(Long contractId, Long tenantId) {
        if (contractId == null) {
            return Optional.empty();
        }
        NationalCatalogPageQuery query = new NationalCatalogPageQuery();
        query.setContractId(contractId);
        query.setTenantId(tenantId);
        return repository.findAll(buildSpec(query),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "updtTime")))
            .stream()
            .map(YbNationalCatalogItem::getUpdtTime)
            .filter(t -> t != null)
            .map(t -> t.toLocalDate())
            .findFirst();
    }

    private Specification<YbNationalCatalogItem> buildSpec(NationalCatalogPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query.getTenantId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
            }
            if (query.getContractId() != null) {
                predicates.add(cb.equal(root.get("contractId"), query.getContractId()));
            }
            if (StringUtils.isNotBlank(query.getListType())) {
                predicates.add(cb.equal(root.get("listType"), query.getListType().trim()));
            }
            if (StringUtils.isNotBlank(query.getHilistCode())) {
                predicates.add(cb.like(root.get("hilistCode"), query.getHilistCode().trim() + "%"));
            }
            if (StringUtils.isNotBlank(query.getKeyword())) {
                String kw = "%" + query.getKeyword().trim() + "%";
                predicates.add(cb.or(
                    cb.like(root.get("hilistName"), kw),
                    cb.like(root.get("pinyin"), kw),
                    cb.like(root.get("hilistCode"), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
