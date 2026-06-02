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

package com.openhis.health.application.integration.winning.catalog.mapper;

import com.openhis.health.application.integration.winning.catalog.dto.SyncWinningCatalogRequest;
import com.openhis.health.application.integration.winning.catalog.dto.WinningActivityCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogPullRequest;
import com.openhis.health.application.integration.winning.catalog.dto.WinningDeviceCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningMedicationCatalogItemDto;
import com.openhis.health.domain.administration.devicedefinition.command.ImportDeviceDefinitionCatalogCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.ImportActivityCatalogCommand;
import com.openhis.health.domain.share.enums.PublicationStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 卫宁标准化 DTO → 领域导入/写命令装配。
 */
@Component
public class WinningCatalogSyncMapper {

    public WinningCatalogPullRequest toPullRequest(SyncWinningCatalogRequest request) {
        WinningCatalogPullRequest pull = new WinningCatalogPullRequest();
        pull.setOrganizationId(request.getOrganizationId());
        pull.setKeyword(request.getKeyword());
        pull.setPageNo(request.getPageNo());
        pull.setPageSize(request.getPageSize());
        pull.setUpdatedSince(request.getUpdatedSince());
        return pull;
    }

    public String resolveBusNo(String busNo, String externalItemCode) {
        if (StringUtils.isNotBlank(busNo)) {
            return busNo.trim();
        }
        if (StringUtils.isNotBlank(externalItemCode)) {
            return externalItemCode.trim();
        }
        return null;
    }

    public ImportDeviceDefinitionCatalogCommand toDeviceImportCommand(
        List<WinningDeviceCatalogItemDto> items,
        Long operatorId,
        boolean overwrite) {
        ImportDeviceDefinitionCatalogCommand cmd = new ImportDeviceDefinitionCatalogCommand();
        cmd.setOperatorId(operatorId);
        cmd.setOverwrite(overwrite);
        List<ImportDeviceDefinitionCatalogCommand.Item> rows = new ArrayList<>();
        for (WinningDeviceCatalogItemDto src : items) {
            String busNo = resolveBusNo(src.getBusNo(), src.getExternalItemCode());
            if (busNo == null || StringUtils.isBlank(src.getName())) {
                continue;
            }
            ImportDeviceDefinitionCatalogCommand.Item row = new ImportDeviceDefinitionCatalogCommand.Item();
            row.setBusNo(busNo);
            row.setName(src.getName().trim());
            row.setCategoryCode(src.getCategoryCode());
            row.setTypeCode(src.getTypeCode());
            row.setUnitCode(StringUtils.firstNonBlank(src.getUnitCode(), "U"));
            row.setTotalVolume(src.getTotalVolume());
            row.setStockUnitCode(src.getStockUnitCode());
            row.setOrgId(src.getOrganizationId());
            row.setLocationId(src.getLocationId());
            row.setYbFlag(src.getYbFlag());
            row.setYbNo(src.getYbNo());
            rows.add(row);
        }
        cmd.setItems(rows);
        return cmd;
    }

    public ImportActivityCatalogCommand toActivityImportCommand(
        List<WinningActivityCatalogItemDto> items,
        Long operatorId) {
        ImportActivityCatalogCommand cmd = new ImportActivityCatalogCommand();
        cmd.setOperatorId(operatorId);
        ImportActivityCatalogCommand.ImportActivityCatalogItemCommand batch =
            new ImportActivityCatalogCommand.ImportActivityCatalogItemCommand();
        batch.setMode("UPSERT");
        List<ImportActivityCatalogCommand.ActivityCatalogRowCommand> rows = new ArrayList<>();
        for (WinningActivityCatalogItemDto src : items) {
            String busNo = resolveBusNo(src.getBusNo(), src.getExternalItemCode());
            if (busNo == null || StringUtils.isBlank(src.getName())) {
                continue;
            }
            ImportActivityCatalogCommand.ActivityCatalogRowCommand row =
                new ImportActivityCatalogCommand.ActivityCatalogRowCommand();
            row.setBusNo(busNo);
            row.setName(src.getName().trim());
            row.setCategoryCode(src.getCategoryCode());
            row.setPackageUnitCode(StringUtils.firstNonBlank(src.getPackageUnitCode(), "次"));
            row.setOrgId(src.getOrganizationId());
            row.setLocationId(src.getLocationId());
            row.setTypeEnum(src.getTypeEnum());
            row.setStatusEnum(StringUtils.firstNonBlank(src.getStatusEnum(), PublicationStatus.ACTIVE.getCode()));
            rows.add(row);
        }
        batch.setRows(rows);
        cmd.setItem(batch);
        return cmd;
    }
}
