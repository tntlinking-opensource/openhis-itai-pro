/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

/**
 * 单 infno 目录 TXT 列映射（0-based TAB 分隔，与 OpenHIS C# ClassMap Index 对齐）。
 *
 * @param infno           交易编号
 * @param listType        写入 {@code yb_national_catalog_item.list_type}
 * @param sourceInfno     写入 {@code source_infno}
 * @param chainMode       版本链推进方式
 * @param idxHilistCode   主键列 → hilist_code
 * @param idxHilistName   名称列 → hilist_name
 * @param idxDosform      剂型（可 -1）
 * @param idxSpec         规格（可 -1）
 * @param idxPinyin       拼音（可 -1）
 * @param idxAprvno       批准文号（可 -1）
 * @param idxProdentpName 生产企业名称（可 -1）
 * @param idxValiFlag     有效标志（可 -1）
 * @param idxChrgitmLv    收费等级（可 -1）
 * @param idxBegndate     开始日期（可 -1）
 * @param idxEnddate      结束日期（可 -1）
 * @param idxUpdtTime     更新时间（可 -1）
 * @param idxVersionNo    行级版本号列（可 -1）
 * @param idxVersionName  版本名称列（可 -1）；{@link CatalogVersionChainMode#VERSION_FIELD} 时表示 version 列
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
