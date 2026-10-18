/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.catalog.result.Catalog3301UploadResult;
import com.zxplat.plugin.chs.domain.catalog.result.Catalog3301UploadRowResult;
import com.zxplat.plugin.chs.domain.catalog.result.Catalog3302RevokeResult;
import com.zxplat.plugin.chs.domain.catalog.result.Catalog3302RevokeRowResult;
import com.zxplat.framework.core.application.exception.BusinessException;

/** 3301/3302 行组装与结果解析工具。 */
public final class CatalogMappingRowSupport {

    private CatalogMappingRowSupport() {
    }

    /** 解析 list_type，空则取默认值。 */
    public static String listType(String requested, String defaultListType) {
        return isNotEmpty(requested) ? requested.trim() : defaultListType;
    }

    /** 院内目录编码：优先 bus_no，否则回退 id 字符串。 */
    public static String fixmedinsHilistId(String busNo, Long id) {
        return isNotEmpty(busNo) ? busNo.trim() : String.valueOf(id);
    }

    /** 取 3301 结果首行（单条上传场景）。 */
    public static Catalog3301UploadRowResult firstUploadRow(Catalog3301UploadResult result) {
        if (result.getRows() == null || result.getRows().isEmpty()) {
            return null;
        }
        return result.getRows().get(0);
    }

    /** 取 3302 结果首行（单条撤销场景）。 */
    public static Catalog3302RevokeRowResult firstRevokeRow(Catalog3302RevokeResult result) {
        if (result.getRows() == null || result.getRows().isEmpty()) {
            return null;
        }
        return result.getRows().get(0);
    }

    /** 构造 3301 失败业务异常（错误码 CATALOG_3301_FAILED）。 */
    public static BusinessException uploadFailed(Catalog3301UploadRowResult rowResult, Catalog3301UploadResult result) {
        String err = rowResult != null && isNotEmpty(rowResult.getErrorMsg())
            ? rowResult.getErrorMsg() : result.getSummary();
        return new BusinessException("CATALOG_3301_FAILED", "3301 上传失败：" + err);
    }

    /** 构造 3302 失败业务异常（错误码 CATALOG_3302_FAILED）。 */
    public static BusinessException revokeFailed(Catalog3302RevokeRowResult rowResult, Catalog3302RevokeResult result) {
        String err = rowResult != null && isNotEmpty(rowResult.getErrorMsg())
            ? rowResult.getErrorMsg() : result.getSummary();
        return new BusinessException("CATALOG_3302_FAILED", "3302 撤销失败：" + err);
    }
}
