/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.support;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeLineCommand;

import java.time.LocalDate;

/**
 * 标准落地包语义（扫描 / 应用共用）。
 * <p>列名对齐不够：须区分物价码与目录码、文件级业务日、已停不复活。
 * 不自动补前导 0、不改写码形；仅允许把误入 {@code hilist_code} 且对列为空的物价码挪到 {@code project_code}。</p>
 */
public final class CatalogNoticeCanonicalSemantics {

    /** 通知可用标志：停用。 */
    public static final String FLAG_STOP = "0";

    private CatalogNoticeCanonicalSemantics() {
    }

    /**
     * 文件级是否具备可排序业务日（通知日、生效日至少一个）。
     *
     * @param noticeDate    通知日
     * @param effectiveDate 生效日
     * @return 可应用排序则为 true
     */
    public static boolean hasBusinessDate(LocalDate noticeDate, LocalDate effectiveDate) {
        if (noticeDate != null) {
            return true;
        }
        return effectiveDate != null;
    }

    /**
     * 编码是否为脏值（中文占位、未赋码等），行级应跳过而非整包失败。
     *
     * @param code 待检编码
     * @return 脏码返回 true
     */
    public static boolean isDirtyCode(String code) {
        String trimmed = trimToNull(code);
        if (trimmed == null) {
            return false;
        }
        if (trimmed.contains("未赋码")) {
            return true;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为物价项目码：{@code 01*}（可带 T 等后缀）或 {@code 37*}（颗粒剂项目码）。
     * <p>不要求 15 位，避免把历史缺位码与带后缀码判死。</p>
     *
     * @param code 待检编码
     * @return 物价码返回 true
     */
    public static boolean isPriceItemCode(String code) {
        String trimmed = trimToNull(code);
        if (trimmed == null) {
            return false;
        }
        if (isDirtyCode(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("01")) {
            return true;
        }
        return trimmed.startsWith("37");
    }

    /**
     * 后文 ADD 是否应保持已停（不得把 notice_avali_flag 改回可用）。
     *
     * @param currentFlag 叠加行当前可用标志
     * @return 已停则 true
     */
    public static boolean shouldKeepStopped(String currentFlag) {
        return FLAG_STOP.equals(trimToNull(currentFlag));
    }

    /**
     * 清洗单行：物价码误入 hilist 且 project 为空时挪列；不补 0。
     *
     * @param line 待清洗行，可空
     */
    public static void sanitizeLine(PersistNoticeLineCommand line) {
        if (line == null) {
            return;
        }
        String hilist = trimToNull(line.getHilistCode());
        String project = trimToNull(line.getProjectCode());
        line.setHilistCode(hilist);
        line.setProjectCode(project);
        if (hilist == null) {
            return;
        }
        if (!isPriceItemCode(hilist)) {
            return;
        }
        if (project == null) {
            // 对列为空：纠正列位，不改码形
            line.setProjectCode(hilist);
            line.setHilistCode(null);
            appendLineWarning(line, "物价码已从 hilist_code 挪到 project_code");
            return;
        }
        if (!hilist.equals(project)) {
            appendLineWarning(line, "hilist_code 疑为物价码且 project_code 已有值，未自动改写");
        }
    }

    /**
     * 行级是否应跳过入库：脏码或两种键都空。
     *
     * @param line 已 sanitize 的行
     * @return 应跳过返回 true
     */
    public static boolean shouldSkipLine(PersistNoticeLineCommand line) {
        if (line == null) {
            return true;
        }
        String hilist = trimToNull(line.getHilistCode());
        String project = trimToNull(line.getProjectCode());
        if (isDirtyCode(hilist) || isDirtyCode(project)) {
            return true;
        }
        if (hilist == null && project == null) {
            return true;
        }
        return false;
    }

    /**
     * 在行警告上追加一句（截断避免超长）。
     *
     * @param line    明细命令
     * @param message 警告
     */
    public static void appendLineWarning(PersistNoticeLineCommand line, String message) {
        if (line == null) {
            return;
        }
        String msg = trimToNull(message);
        if (msg == null) {
            return;
        }
        String old = trimToNull(line.getWarningMessage());
        if (old == null) {
            line.setWarningMessage(truncate(msg, 1024));
            return;
        }
        line.setWarningMessage(truncate(old + "；" + msg, 1024));
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
