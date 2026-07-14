/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;

import java.util.Optional;

/**
 * 产品定义「来源表名 + 定义主键」复合业务键工具（与 {@code ProductDefinitionRMQueryService#buildProductCompositeKey} 行为一致）。
 *
 * <p>分隔符见 {@link GeneralSharedConstants#PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR}（ASCII SOH）。</p>
 */
public final class ProductDefinitionKeyUtil {

    private ProductDefinitionKeyUtil() {
    }

    /**
     * 构造与产品读模型批量 Map 一致的复合键。
     *
     * @param productTableName    来源表名
     * @param productDefinitionId 定义主键
     * @return 拼接键；参数缺失时 {@code null}
     */
    public static String buildCompositeKey(String productTableName, Long productDefinitionId) {
        if (productTableName == null || productDefinitionId == null) {
            return null;
        }
        return productTableName.trim()
            + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR
            + productDefinitionId;
    }

    /**
     * 从复合键解析来源表名（分隔符左侧）。
     *
     * @param compositeKey {@link #buildCompositeKey} 产物
     * @return 表名；无法解析时 empty
     */
    public static Optional<String> parseTableName(String compositeKey) {
        return parse(compositeKey).map(ParsedProductDefinitionKey::productTableName);
    }

    /**
     * 从复合键解析定义主键（分隔符右侧）。
     *
     * @param compositeKey {@link #buildCompositeKey} 产物
     * @return 主键；无法解析时 empty
     */
    public static Optional<Long> parseDefinitionId(String compositeKey) {
        return parse(compositeKey).map(ParsedProductDefinitionKey::productDefinitionId);
    }

    /**
     * 解析复合键为表名 + 主键。
     */
    public static Optional<ParsedProductDefinitionKey> parse(String compositeKey) {
        if (compositeKey == null || compositeKey.isEmpty()) {
            return Optional.empty();
        }
        char sep = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        int idx = compositeKey.indexOf(sep);
        if (idx <= 0 || idx >= compositeKey.length() - 1) {
            return Optional.empty();
        }
        String table = compositeKey.substring(0, idx).trim();
        if (table.isEmpty()) {
            return Optional.empty();
        }
        String idPart = compositeKey.substring(idx + 1);
        try {
            long id = Long.parseLong(idPart);
            return Optional.of(new ParsedProductDefinitionKey(table, id));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * 复合键解析结果。
     *
     * @param productTableName    来源表名
     * @param productDefinitionId 定义主键
     */
    public record ParsedProductDefinitionKey(String productTableName, long productDefinitionId) {
    }
}
