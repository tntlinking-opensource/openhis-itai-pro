/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.support;

import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 库房日结供应动账类型白名单（BR-INV-SET-007/010）。
 */
@Component
public class DailySettlementTypeWhitelistHelper {

    /**
     * 按库位形态返回供应 type_enum 白名单（排除壳单/申请单）。
     *
     * @param locationFormEnum 库位形态 code
     * @return 白名单持久化码列表
     */
    public List<String> resolveSupplyTypeCodes(String locationFormEnum) {
        if (StringUtils.isEmpty(locationFormEnum)) {
            return List.of();
        }
        String form = locationFormEnum.trim();
        List<String> codes = new ArrayList<>();
        // 按库位形态组装动账 type 白名单（壳单/申请单已排除）
        if (LocationForm.WAREHOUSE.getCode().equals(form)) {
            this.addWarehouseCodes(codes);
        } else if (LocationForm.PHARMACY.getCode().equals(form)) {
            this.addPharmacyCodes(codes);
        } else if (LocationForm.DEPARTMENT_PHARMACY.getCode().equals(form)) {
            this.addPharmacyCodes(codes);
            this.addDepartmentPharmacyCodes(codes);
        }
        return codes;
    }

    /**
     * 药库形态动账类型。
     *
     * @param codes 目标列表
     */
    private void addWarehouseCodes(List<String> codes) {
        codes.add(SupplyType.CABINET_INBOUND_RECEIPT.getCode());
        codes.add(SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode());
        codes.add(SupplyType.CABINET_STOCK_OUT.getCode());
        codes.add(SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode());
        codes.add(SupplyType.DEPT_RETURN.getCode());
        codes.add(SupplyType.CABINET_TRANSFER_OUT.getCode());
        codes.add(SupplyType.CABINET_STOCKTAKE.getCode());
        codes.add(SupplyType.CABINET_GAIN_LOSS.getCode());
    }

    /**
     * 药房形态动账类型。
     *
     * @param codes 目标列表
     */
    private void addPharmacyCodes(List<String> codes) {
        codes.add(SupplyType.CABINET_STOCK_OUT.getCode());
        codes.add(SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode());
        codes.add(SupplyType.PHARMACY_TRANSFER_OUT.getCode());
        codes.add(SupplyType.PHARMACY_STOCKTAKE.getCode());
        codes.add(SupplyType.PHARMACY_GAIN_LOSS.getCode());
    }

    /**
     * 科室药房额外动账类型。
     *
     * @param codes 目标列表
     */
    private void addDepartmentPharmacyCodes(List<String> codes) {
        codes.add(SupplyType.DEPT_RETURN.getCode());
        codes.add(SupplyType.DEPT_TRANSFER_OUT.getCode());
        codes.add(SupplyType.DEPT_STOCKTAKE.getCode());
        codes.add(SupplyType.DEPT_GAIN_LOSS.getCode());
        codes.add(SupplyType.DEPT_ISSUE.getCode());
        codes.add(SupplyType.DEPT_ISSUE_RETURN.getCode());
    }
}
