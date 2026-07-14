/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单据详情报表各域 {@link SupplyType} 白名单与默认类型（AS-INV-RPT-002 · BR-INV-DDC-001/002）。
 */
@Component
public class DocumentDetailTypeWhitelistHelper {

    /**
     * 解析请求类型：未传时使用本域默认类型。
     *
     * @param scope   业务域
     * @param typeEnum 请求类型，可为空
     * @return 非空白持久化码
     */
    public String resolveTypeEnumOrDefault(DocumentDetailDomainScope scope, String typeEnum) {
        if (StringUtils.isNotEmpty(typeEnum)) {
            return typeEnum.trim();
        }
        return this.defaultTypeEnum(scope);
    }

    /**
     * 校验类型落在本域白名单内。
     *
     * @param scope    业务域
     * @param typeEnum 持久化类型码
     * @throws BusinessException {@code INV_DDC_202} 非法类型
     */
    public void assertTypeInDomain(DocumentDetailDomainScope scope, String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "单据类型无效或不在本业务线范围内");
        }
        String trimmed = typeEnum.trim();
        List<SupplyType> whitelist = this.whitelistFor(scope);
        for (SupplyType supplyType : whitelist) {
            if (supplyType.getCode().equals(trimmed)) {
                return;
            }
        }
        // Gate：不在本域 §1.6 白名单内
        throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "单据类型无效或不在本业务线范围内");
    }

    /**
     * 构建本域 init 下拉选项。
     *
     * @param scope 业务域
     * @return 类型选项列表
     */
    public List<OptionDto> buildTypeEnumOptions(DocumentDetailDomainScope scope) {
        List<OptionDto> options = new ArrayList<>();
        List<SupplyType> whitelist = this.whitelistFor(scope);
        for (SupplyType supplyType : whitelist) {
            options.add(new OptionDto(supplyType.getSortOrder(), supplyType.getCode(), supplyType.getDisplay()));
        }
        return options;
    }

    /**
     * 本域默认单据类型持久化码。
     *
     * @param scope 业务域
     * @return 默认 code
     */
    public String defaultTypeEnum(DocumentDetailDomainScope scope) {
        if (scope == DocumentDetailDomainScope.PHARMACY) {
            return SupplyType.PHARMACY_REQUISITION.getCode();
        }
        if (scope == DocumentDetailDomainScope.DEPARTMENT) {
            return SupplyType.DEPT_REQUISITION.getCode();
        }
        return SupplyType.CABINET_INBOUND_RECEIPT.getCode();
    }

    /**
     * 按域返回 AS §1.6 白名单（不含 §八 未建包类型）。
     *
     * @param scope 业务域
     * @return 枚举列表
     */
    private List<SupplyType> whitelistFor(DocumentDetailDomainScope scope) {
        List<SupplyType> list = new ArrayList<>();
        if (scope == DocumentDetailDomainScope.PHARMACY) {
            list.add(SupplyType.PHARMACY_REQUISITION);
            list.add(SupplyType.PHARMACY_STOCK_IN);
            list.add(SupplyType.PHARMACY_RETURN_TO_WAREHOUSE);
            list.add(SupplyType.PHARMACY_GAIN_LOSS);
            list.add(SupplyType.PHARMACY_STOCKTAKE);
            list.add(SupplyType.PHARMACY_TRANSFER_IN);
            list.add(SupplyType.PHARMACY_TRANSFER_OUT);
            return list;
        }
        if (scope == DocumentDetailDomainScope.DEPARTMENT) {
            list.add(SupplyType.DEPT_REQUISITION);
            list.add(SupplyType.DEPT_STOCK_IN);
            list.add(SupplyType.DEPT_RETURN);
            list.add(SupplyType.DEPT_GAIN_LOSS);
            list.add(SupplyType.DEPT_STOCKTAKE);
            list.add(SupplyType.DEPT_TRANSFER_IN);
            list.add(SupplyType.DEPT_TRANSFER_OUT);
            list.add(SupplyType.DEPT_ISSUE);
            list.add(SupplyType.DEPT_ISSUE_RETURN);
            return list;
        }
        list.add(SupplyType.CABINET_INBOUND_RECEIPT);
        list.add(SupplyType.CABINET_PURCHASE_ORDER);
        list.add(SupplyType.CABINET_RETURN_TO_SUPPLIER);
        list.add(SupplyType.CABINET_STOCK_OUT);
        list.add(SupplyType.CABINET_RETURN_STOCK_IN);
        list.add(SupplyType.CABINET_GAIN_LOSS);
        list.add(SupplyType.CABINET_STOCKTAKE);
        list.add(SupplyType.CABINET_TRANSFER_IN);
        list.add(SupplyType.CABINET_TRANSFER_OUT);
        return list;
    }
}
