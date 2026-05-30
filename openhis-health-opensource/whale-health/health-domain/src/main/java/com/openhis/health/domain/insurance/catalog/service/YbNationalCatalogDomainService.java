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

import com.openhis.health.domain.insurance.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.health.domain.insurance.catalog.entity.YbNationalCatalogItem;
import com.openhis.health.domain.insurance.catalog.repository.YbNationalCatalogItemRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 国家医保目录镜像写侧（1312/1301 upsert）。
 */
@Service
@RequiredArgsConstructor
public class YbNationalCatalogDomainService {

    private final YbNationalCatalogItemRepository repository;

    @Transactional
    public int upsertRows(Long contractId, Long tenantId, Long operatorId,
                          List<UpsertNationalCatalogRowCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (UpsertNationalCatalogRowCommand row : rows) {
            if (row == null || StringUtils.isBlank(row.getHilistCode())) {
                continue;
            }
            String listType = StringUtils.defaultIfBlank(row.getListType(), "101");
            YbNationalCatalogItem entity = repository
                .findByContractIdAndListTypeAndHilistCodeAndTenantIdAndIsDeletedFalse(
                    contractId, listType, row.getHilistCode(), tenantId)
                .orElseGet(YbNationalCatalogItem::new);
            if (entity.getId() == null) {
                entity.setContractId(contractId);
                entity.setTenantId(tenantId);
                entity.setListType(listType);
                entity.setHilistCode(row.getHilistCode());
                entity.setIsDeleted(false);
                entity.setCreatedBy(operatorId);
            }
            entity.setHilistName(row.getHilistName());
            entity.setChrgitmLv(row.getChrgitmLv());
            entity.setLmtUsedFlag(row.getLmtUsedFlag());
            entity.setBegndate(row.getBegndate());
            entity.setEnddate(row.getEnddate());
            entity.setValiFlag(row.getValiFlag());
            entity.setPinyin(row.getPinyin());
            entity.setSourceInfno(row.getSourceInfno());
            entity.setUpdtTime(row.getUpdtTime());
            entity.setRawJson(row.getRawJson());
            entity.setUpdatedBy(operatorId);
            repository.save(entity);
            count++;
        }
        return count;
    }
}
