/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.zxplat.plugin.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.zxplat.plugin.chs.domain.notice.model.HisMappedProduct;
import com.zxplat.plugin.chs.domain.notice.support.CatalogNoticeOverlayRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 目录检查规则：院内已启用医保项对照国家目录、已应用局文的有效期 / 启用 / 限价。
 */
public final class CatalogCheckEvaluator {

    private CatalogCheckEvaluator() {
    }

    /**
     * 评估单条院内项目。
     *
     * @param product        院内项；空则无问题
     * @param catalog        命中的国家目录行；可空
     * @param overlay        局文叠加；可空
     * @param catalogPresent 本地是否已有任意国家目录镜像（无镜像时不报「镜像无此码」）
     * @param asOf           判定日；空则当天
     * @return 问题列表；无问题为空列表
     */
    public static List<Finding> evaluate(
            HisMappedProduct product,
            YbNationalCatalogItem catalog,
            YbNationalCatalogNoticeOverlay overlay,
            boolean catalogPresent,
            LocalDate asOf) {
        List<Finding> findings = new ArrayList<>();
        if (product == null) {
            return findings;
        }
        LocalDate day = asOf == null ? LocalDate.now() : asOf;
        String ybNo = trimToNull(product.getYbNo());
        if (ybNo == null) {
            findings.add(finding(
                CatalogCheckIssueCodes.MISSING_YB_NO,
                "已启用但未维护国家医保编码，医保结算时会被拦截",
                null, null, null, product.getSalePrice()));
            return findings;
        }
        if (CatalogNoticeOverlayRules.isNoticeStopEffective(overlay, day)) {
            LocalDate stopDay = overlay.getNoticeEndDate();
            String msg = "局文已停用该编码";
            if (stopDay != null) {
                msg = msg + "（停用日 " + stopDay + "）";
            }
            findings.add(finding(
                CatalogCheckIssueCodes.NOTICE_STOP, msg, stopDay, null, null, product.getSalePrice()));
        } else {
            appendNoticeValidityFindings(findings, overlay, day, product.getSalePrice());
        }
        if (catalog == null) {
            if (catalogPresent) {
                findings.add(finding(
                    CatalogCheckIssueCodes.NOT_IN_CATALOG,
                    "本地国家目录镜像中找不到该编码，请先同步 1301/1312 或核对对码",
                    null, null, null, product.getSalePrice()));
            }
            return findings;
        }
        if (NationalCatalogSelectableComposer.REASON_PLATFORM_INVALID.equals(
            NationalCatalogSelectableComposer.platformUnselectableReason(
                catalog.getValiFlag(), null, day))) {
            findings.add(finding(
                CatalogCheckIssueCodes.DISABLED,
                "国家目录有效标志非有效（vali_flag=" + catalog.getValiFlag() + "）",
                catalog.getEnddate(), catalog.getValiFlag(), null, product.getSalePrice()));
        }
        if (NationalCatalogSelectableComposer.REASON_PLATFORM_EXPIRED.equals(
            NationalCatalogSelectableComposer.platformUnselectableReason(
                "1", catalog.getEnddate(), day))) {
            findings.add(finding(
                CatalogCheckIssueCodes.EXPIRED,
                "国家目录结束日已过（enddate=" + catalog.getEnddate() + "）",
                catalog.getEnddate(), catalog.getValiFlag(), null, product.getSalePrice()));
        }
        BigDecimal limit = CatalogNoticeOverlayRules.readPatchDecimal(
            overlay == null ? null : overlay.getNoticePatchJson(), "hilist_pric_uplmt_amt");
        BigDecimal sale = product.getSalePrice();
        if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0 && sale != null && sale.compareTo(limit) > 0) {
            findings.add(finding(
                CatalogCheckIssueCodes.PRICE_OVER,
                "院内售价 " + sale.toPlainString() + " 超过医保限价 " + limit.toPlainString(),
                catalog.getEnddate(), catalog.getValiFlag(), limit, sale));
        }
        return findings;
    }

    /**
     * 局文有效期：叠加 patch 的 begin_date / end_date（应用 ADD/MODIFY 时写入）。
     * 未到起日报未生效；超过止日报过期。已报停用的不再重复报过期。
     */
    private static void appendNoticeValidityFindings(
            List<Finding> findings,
            YbNationalCatalogNoticeOverlay overlay,
            LocalDate day,
            BigDecimal salePrice) {
        if (overlay == null) {
            return;
        }
        LocalDate begin = readPatchDate(overlay, "begin_date");
        LocalDate end = readPatchDate(overlay, "end_date");
        if (end == null) {
            end = overlay.getNoticeEndDate();
        }
        if (begin != null && day.isBefore(begin)) {
            findings.add(finding(
                CatalogCheckIssueCodes.NOTICE_NOT_EFFECTIVE,
                "局文尚未生效（起日 " + begin + (end == null ? "" : "，止日 " + end) + "）",
                end, null, null, salePrice));
        }
        if (end != null && day.isAfter(end)) {
            findings.add(finding(
                CatalogCheckIssueCodes.NOTICE_EXPIRED,
                "局文有效期已过（" + (begin == null ? "" : "起日 " + begin + "，") + "止日 " + end + "）",
                end, null, null, salePrice));
        }
    }

    private static LocalDate readPatchDate(YbNationalCatalogNoticeOverlay overlay, String key) {
        String raw = CatalogNoticeOverlayRules.readPatchString(overlay.getNoticePatchJson(), key);
        if (raw == null) {
            return null;
        }
        String text = raw.length() >= 10 ? raw.substring(0, 10) : raw;
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Finding finding(
            String code,
            String message,
            LocalDate enddate,
            String valiFlag,
            BigDecimal limitAmt,
            BigDecimal salePrice) {
        Finding finding = new Finding();
        finding.setIssueCode(code);
        finding.setIssueLabel(CatalogCheckIssueCodes.labelOf(code));
        finding.setMessage(message);
        finding.setEnddate(enddate);
        finding.setValiFlag(valiFlag);
        finding.setLimitAmt(limitAmt);
        finding.setSalePrice(salePrice);
        return finding;
    }

    /** 单条检查命中。 */
    public static final class Finding {
        private String issueCode;
        private String issueLabel;
        private String message;
        private LocalDate enddate;
        private String valiFlag;
        private BigDecimal limitAmt;
        private BigDecimal salePrice;

        public String getIssueCode() {
            return issueCode;
        }

        public void setIssueCode(String issueCode) {
            this.issueCode = issueCode;
        }

        public String getIssueLabel() {
            return issueLabel;
        }

        public void setIssueLabel(String issueLabel) {
            this.issueLabel = issueLabel;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public LocalDate getEnddate() {
            return enddate;
        }

        public void setEnddate(LocalDate enddate) {
            this.enddate = enddate;
        }

        public String getValiFlag() {
            return valiFlag;
        }

        public void setValiFlag(String valiFlag) {
            this.valiFlag = valiFlag;
        }

        public BigDecimal getLimitAmt() {
            return limitAmt;
        }

        public void setLimitAmt(BigDecimal limitAmt) {
            this.limitAmt = limitAmt;
        }

        public BigDecimal getSalePrice() {
            return salePrice;
        }

        public void setSalePrice(BigDecimal salePrice) {
            this.salePrice = salePrice;
        }
    }
}
