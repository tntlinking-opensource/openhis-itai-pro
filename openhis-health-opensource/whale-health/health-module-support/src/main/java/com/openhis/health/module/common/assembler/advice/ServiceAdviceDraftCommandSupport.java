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

package com.openhis.health.module.common.assembler.advice;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.querysupport.advice.AdviceExtensionJsonSupport;
import com.openhis.health.module.querysupport.advice.dto.ServiceChildOverrideHints;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

/**
 * 诊疗医嘱草稿行 → 领域命令时的数量/单位/执行次数解析。
 *
 * <p><strong>quantity/unitCode</strong>：账单计价乘数；<strong>executionCount</strong>：护士站可执行次数上限（临嘱/门诊）。
 * 长嘱按频次执行，{@code executionCount} 可空。</p>
 */
public final class ServiceAdviceDraftCommandSupport {

    private ServiceAdviceDraftCommandSupport() {
    }

    /**
     * 解析开立计费数量：仅认显式 {@code quantity}；缺失时抛错（禁止再用执行次数冒充数量）。
     */
    public static BigDecimal requireQuantity(BigDecimal payloadQuantity, String childOverridesJson) {
        if (payloadQuantity != null && payloadQuantity.compareTo(BigDecimal.ZERO) > 0) {
            return payloadQuantity;
        }
        throw new BusinessException(
            ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_SERVICE_QUANTITY_REQUIRED);
    }

    /**
     * 解析临嘱计划执行次数：显式 {@code executionCount} → 子 JSON；长嘱允许返回 null。
     *
     * @param payloadExecutionCount 载荷执行次数
     * @param childOverridesJson      套餐子项覆盖 JSON（存量兼容）
     * @param therapyEnum             长临 code
     * @return 计划次数；长嘱可为 null
     */
    public static Integer requireExecutionCount(
        Integer payloadExecutionCount,
        String childOverridesJson,
        String therapyEnum
    ) {
        Integer resolved = resolveExecutionCount(payloadExecutionCount, childOverridesJson);
        if (TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            return resolved;
        }
        if (resolved == null || resolved <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_SERVICE_EXECUTION_COUNT_REQUIRED);
        }
        return resolved;
    }

    /**
     * 护士站：从诊疗列表行解析计划可执行次数（仅认 {@code executionCount}，禁止回落 quantity）。
     *
     * @param listRow 列表行；允许 null
     * @return 计划次数；临嘱缺失 {@code executionCount} 时为 0
     */
    public static int resolvePlannedServicePerformCount(ServiceRequestListModel listRow) {
        if (listRow == null) {
            return 0;
        }
        if (TherapyType.LONG_TERM.getCode().equals(listRow.getTherapyEnum())) {
            return 1;
        }
        if (listRow.getExecutionCount() != null && listRow.getExecutionCount() > 0) {
            return listRow.getExecutionCount();
        }
        return 0;
    }

    /**
     * 解析开立单位：显式 {@code unitCode} &gt; 子 JSON &gt; 目录包装单位；均缺失时抛错。
     */
    public static String requireUnitCode(
        String payloadUnitCode,
        String childOverridesJson,
        ProductDefinitionDisplayDto productRm
    ) {
        String unitCode = StringUtils.trimToNull(payloadUnitCode);
        if (unitCode != null) {
            return unitCode;
        }
        ServiceChildOverrideHints hints = AdviceExtensionJsonSupport.parseServiceChildOverrides(childOverridesJson);
        if (StringUtils.isNotBlank(hints.getUnitCode())) {
            return hints.getUnitCode().trim();
        }
        if (productRm != null) {
            unitCode = StringUtils.trimToNull(productRm.getPackageUnitCode());
        }
        if (unitCode == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_SERVICE_UNIT_CODE_REQUIRED);
        }
        return unitCode;
    }

    /**
     * 修改草稿时解析单位（无目录回落时仅使用载荷与子 JSON，不覆盖存量）。
     */
    public static String resolveUnitCodeForModify(String payloadUnitCode, String childOverridesJson) {
        String unitCode = StringUtils.trimToNull(payloadUnitCode);
        if (unitCode != null) {
            return unitCode;
        }
        ServiceChildOverrideHints hints = AdviceExtensionJsonSupport.parseServiceChildOverrides(childOverridesJson);
        return StringUtils.trimToNull(hints.getUnitCode());
    }

    /**
     * 修改草稿时解析计费数量；未传时不覆盖存量。
     */
    public static BigDecimal resolveQuantityForModify(BigDecimal payloadQuantity, String childOverridesJson) {
        if (payloadQuantity != null && payloadQuantity.compareTo(BigDecimal.ZERO) > 0) {
            return payloadQuantity;
        }
        return null;
    }

    /**
     * 修改草稿时解析计划执行次数；未传时不覆盖存量。
     */
    public static Integer resolveExecutionCountForModify(Integer payloadExecutionCount, String childOverridesJson) {
        Integer resolved = resolveExecutionCount(payloadExecutionCount, childOverridesJson);
        if (resolved != null && resolved > 0) {
            return resolved;
        }
        return null;
    }

    /**
     * 合并显式载荷与子 JSON 中的执行次数（子 JSON 仅存量兼容）。
     */
    private static Integer resolveExecutionCount(Integer payloadExecutionCount, String childOverridesJson) {
        if (payloadExecutionCount != null && payloadExecutionCount > 0) {
            return payloadExecutionCount;
        }
        ServiceChildOverrideHints hints = AdviceExtensionJsonSupport.parseServiceChildOverrides(childOverridesJson);
        if (hints.getExecutionCount() != null && hints.getExecutionCount() > 0) {
            return hints.getExecutionCount();
        }
        return null;
    }
}
