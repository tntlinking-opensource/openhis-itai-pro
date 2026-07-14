/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support;

/**
 * 按 infno 的目录 TXT 列映射规格（0-based TAB 分隔，与 OpenHIS C# ClassMap Index 对齐）。
 *
 * @param infno           交易编号
 * @param listType        写入 {@code yb_national_catalog_item.list_type}
 * @param sourceInfno     写入 {@code source_infno}
 * @param chainMode       版本链推进方式
 * @param idxHilistCode   主键列 → hilist_code（-1 表示无）
 * @param idxHilistName   名称列 → hilist_name（-1 表示无）
 * @param idxDosform      剂型列（-1 表示无）
 * @param idxSpec         规格列（-1 表示无）
 * @param idxPinyin       拼音列（-1 表示无）
 * @param idxAprvno       批准文号列（-1 表示无）
 * @param idxProdentpName 生产企业名称列（-1 表示无）
 * @param idxValiFlag     有效标志列（-1 表示无）
 * @param idxChrgitmLv    收费等级列（-1 表示无）
 * @param idxBegndate     开始日期列（-1 表示无）
 * @param idxEnddate      结束日期列（-1 表示无）
 * @param idxUpdtTime     更新时间列（-1 表示无）
 * @param idxVersionNo    行级版本号列（-1 表示无）
 * @param idxVersionName  版本名称列（-1 表示无）
 */
public record CatalogFileParserSpec(
    String infno,
    String listType,
    String sourceInfno,
    CatalogVersionChainMode chainMode,
    int idxHilistCode,
    int idxHilistName,
    int idxDosform,
    int idxSpec,
    int idxPinyin,
    int idxAprvno,
    int idxProdentpName,
    int idxValiFlag,
    int idxChrgitmLv,
    int idxBegndate,
    int idxEnddate,
    int idxUpdtTime,
    int idxVersionNo,
    int idxVersionName) {
}
