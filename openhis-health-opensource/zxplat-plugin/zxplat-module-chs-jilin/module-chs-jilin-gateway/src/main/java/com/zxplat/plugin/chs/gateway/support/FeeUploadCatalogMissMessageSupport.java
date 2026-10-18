/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsFeeDetailLine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 2204/2301 平台「未查到基础目录」时，把本批对得上的药品、耗材、诊疗写进错误文案。
 *
 * <p>平台常只回截断后的目录码（耗材前 15 位），收费员无法从编码反查项目。</p>
 */
public final class FeeUploadCatalogMissMessageSupport {

    /** 短码易误伤流水号，短于此长度不参与匹配。 */
    private static final int MIN_CODE_LEN = 8;

    /** 一次提示最多点名条数，避免整批同码把弹窗撑满。 */
    private static final int MAX_ITEMS = 8;

    private FeeUploadCatalogMissMessageSupport() {
    }

    /**
     * 目录未命中时，在平台原文前写明本批项目。
     *
     * @param errMsg 平台 err_msg，可空
     * @param lines  本批实际上传的费用行（含不上报的本地名称）
     * @return 带项目名的文案；非目录失败或对不上行时原样返回
     */
    public static String decorate(String errMsg, List<ChsFeeDetailLine> lines) {
        // 无原文或本批无明细：保持平台原句
        if (isEmpty(errMsg) || lines == null || lines.isEmpty()) {
            return errMsg;
        }
        // 其他拒付（重复流水、金额不平）不套目录文案
        if (!isCatalogMiss(errMsg)) {
            return errMsg;
        }
        String hint = buildHint(errMsg, lines);
        // 本批没有对上平台点名的编码：仍展示平台原文
        if (isEmpty(hint)) {
            return errMsg;
        }
        return hint + errMsg;
    }

    /**
     * 是否为政策中心查不到结算/基础目录。
     *
     * @param errMsg 平台错误原文
     * @return 是目录未命中时 true
     */
    private static boolean isCatalogMiss(String errMsg) {
        if (errMsg.contains("未查到基础目录")) {
            return true;
        }
        return errMsg.contains("查询结算目录信息失败");
    }

    /**
     * 拼「未对码项目」前缀。
     *
     * @param errMsg 平台错误原文
     * @param lines  本批费用行
     * @return 前缀；无匹配行时空串
     */
    private static String buildHint(String errMsg, List<ChsFeeDetailLine> lines) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> parts = new ArrayList<>();
        boolean truncated = false;
        for (ChsFeeDetailLine line : lines) {
            if (line == null || !matches(errMsg, line)) {
                continue;
            }
            String part = formatLine(line);
            // 同名同码只提示一次
            if (!seen.add(part)) {
                continue;
            }
            // 已点满：后面还有命中项时加「等」
            if (parts.size() >= MAX_ITEMS) {
                truncated = true;
                break;
            }
            parts.add(part);
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("未对码项目：");
        for (int i = 0; i < parts.size(); i++) {
            // 多项之间用顿号，方便收费员一次看完
            if (i > 0) {
                sb.append("、");
            }
            sb.append(parts.get(i));
        }
        // 超出条数上限时告知还有未列出的行
        if (truncated) {
            sb.append("等");
        }
        sb.append("。");
        return sb.toString();
    }

    /**
     * 平台原文是否点到了该行的上传码或院内医保码。
     *
     * @param errMsg 平台错误原文
     * @param line   费用行
     * @return 命中时 true
     */
    private static boolean matches(String errMsg, ChsFeeDetailLine line) {
        String medListCodg = trim(line.getMedListCodg());
        if (medListCodg.length() >= MIN_CODE_LEN && errMsg.contains(medListCodg)) {
            return true;
        }
        String medinsListCodg = trim(line.getMedinsListCodg());
        return medinsListCodg.length() >= MIN_CODE_LEN && errMsg.contains(medinsListCodg);
    }

    /**
     * 单行提示：类别、名称、上传目录码、院内医保码。
     *
     * @param line 费用行
     * @return 非空短句
     */
    private static String formatLine(ChsFeeDetailLine line) {
        String kind = trim(line.getLocalCatalogKind());
        if (isEmpty(kind)) {
            kind = "项目";
        }
        StringBuilder sb = new StringBuilder(kind);
        String name = trim(line.getLocalItemName());
        // 有名称才加书名号，避免空「」
        if (isNotEmpty(name)) {
            sb.append("「").append(name).append("」");
        }
        String medListCodg = trim(line.getMedListCodg());
        String medinsListCodg = trim(line.getMedinsListCodg());
        sb.append("（上传目录码 ").append(medListCodg);
        // 院内医保码与上传码不同（耗材截断）时一并写出
        if (isNotEmpty(medinsListCodg) && !medinsListCodg.equals(medListCodg)) {
            sb.append("，院内医保码 ").append(medinsListCodg);
        }
        sb.append("）");
        return sb.toString();
    }

    /**
     * 去空白；空则空串。
     *
     * @param value 原串
     * @return 非 null
     */
    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
