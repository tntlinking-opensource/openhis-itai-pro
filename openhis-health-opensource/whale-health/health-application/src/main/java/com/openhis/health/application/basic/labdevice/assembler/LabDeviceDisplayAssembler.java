/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.labdevice.assembler;

import com.openhis.health.application.basic.labdevice.dto.LabDeviceDto;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检验设备列表/详情展示 enrich（B.6.4：跨聚合读 Organization/Supplier 仅用于展示名）。
 */
@Component
@RequiredArgsConstructor
public class LabDeviceDisplayAssembler {

    private final OrganizationQueryService organizationQueryService;
    private final SupplierQueryService supplierQueryService;

    /**
     * 批量 enrich 科室与厂家名称。
     *
     * @param rows DTO 列表
     */
    public void enrichRows(List<LabDeviceDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> orgIds = new HashSet<>();
        Set<Long> supplierIds = new HashSet<>();
        for (LabDeviceDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getOrgId() != null) {
                orgIds.add(row.getOrgId());
            }
            if (row.getManufacturerId() != null) {
                supplierIds.add(row.getManufacturerId());
            }
        }
        Map<Long, String> orgNameById = organizationQueryService.mapNamesByIds(orgIds);
        Map<Long, String> supplierNameById = supplierQueryService.mapNamesByIds(supplierIds);
        for (LabDeviceDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getOrgId() != null) {
                row.setOrgName(orgNameById.get(row.getOrgId()));
            }
            if (row.getManufacturerId() != null) {
                row.setManufacturerName(supplierNameById.get(row.getManufacturerId()));
            }
        }
    }
}
