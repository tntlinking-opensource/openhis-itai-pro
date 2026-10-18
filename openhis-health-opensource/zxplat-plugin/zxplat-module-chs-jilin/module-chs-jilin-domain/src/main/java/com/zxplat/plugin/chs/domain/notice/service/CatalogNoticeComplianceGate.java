/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.notice.command.ComplianceGateCommand;
import com.zxplat.plugin.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.zxplat.plugin.chs.domain.notice.entity.YbServiceProjectMap;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceGateResult;
import com.zxplat.plugin.chs.domain.notice.port.CatalogNoticeHisProductPort;
import com.zxplat.plugin.chs.domain.notice.queryservice.NationalCatalogOverlayQueryService;
import com.zxplat.plugin.chs.domain.notice.repository.YbServiceProjectMapRepository;
import com.zxplat.plugin.chs.domain.notice.support.CatalogNoticeOverlayRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 医保文件运行时硬阻断门禁（BR-05/09）。
 * <p>聚合边界：仅读本聚合 overlay / project_map，经 {@link CatalogNoticeHisProductPort} 回查院内等级。
 * 码：C-STOP-ACTIVE / C-PAY-CLASS / C-PRICE-OVER / C-PROJECT-UNMAPPED 阻断；
 * C-STOP-LAG 仅下载侧告警（本类 evaluate 不产出）；C-PRICE-UNKNOWN 放行。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeComplianceGate {

    public static final String C_STOP_ACTIVE = "C-STOP-ACTIVE";
    /** 下载侧告警：通知已停用但镜像仍可用（不硬阻断下载）。 */
    public static final String C_STOP_LAG = "C-STOP-LAG";
    public static final String C_PAY_CLASS = "C-PAY-CLASS";
    public static final String C_PRICE_OVER = "C-PRICE-OVER";
    public static final String C_PRICE_UNKNOWN = "C-PRICE-UNKNOWN";
    public static final String C_PROJECT_UNMAPPED = "C-PROJECT-UNMAPPED";

    private static final String MAP_PENDING = "PENDING";

    private final NationalCatalogOverlayQueryService overlayQueryService;
    private final YbServiceProjectMapRepository projectMapRepository;
    private final CatalogNoticeHisProductPort hisProductPort;

    /**
     * 评估单行医保结算是否允许继续开单/上传。
     *
     * @param command 行级入参
     * @return allowed=false 时携带门禁码
     */
    public ComplianceGateResult evaluate(ComplianceGateCommand command) {
        // Pre：无预加载 Map 时逐条读 overlay（兼容未批查调用方）
        return this.evaluate(command, null);
    }

    /**
     * 评估单行是否允许开单/上传；可传入本批已批量加载的 overlay，避免按行 N+1。
     *
     * <p>当 {@code overlayByHilist != null} 且 hilist 非空：Map 无键视为「无 overlay / 放行」，
     * 与 {@link NationalCatalogOverlayQueryService#getOverlay} 空结果同义，<b>不再</b>回落单条查询
     *（调用方须保证本批 yb_no 已纳入 {@code mapOverlaysByHilistCodes}）。</p>
     *
     * @param command         行级入参
     * @param overlayByHilist 本批预加载（key=hilistCode）；null 表示走单条 getOverlay
     * @return allowed=false 时携带门禁码
     */
    public ComplianceGateResult evaluate(
            ComplianceGateCommand command,
            Map<String, YbNationalCatalogNoticeOverlay> overlayByHilist) {
        // 无租户上下文：结算链不拦截（由上游保证租户）
        if (command == null || command.getTenantId() == null) {
            return ComplianceGateResult.allow();
        }
        String hilist = trimToNull(command.getHilistCode());
        String project = trimToNull(command.getProjectCode());
        LocalDate asOf = command.getAsOfDate();
        if (asOf == null) {
            asOf = LocalDate.now();
        }

        // 前置：未映射国家码则硬阻断医保开单
        ComplianceGateResult unmapped = checkProjectUnmapped(command);
        if (!unmapped.isAllowed()) {
            return unmapped;
        }

        // Pre：优先用本批 Map；未预加载时仍单条查询（语义与门禁规则不变）
        Optional<YbNationalCatalogNoticeOverlay> overlayOpt = this.resolveOverlay(command, hilist, overlayByHilist);
        // 无 overlay：通知未触达该编码，放行
        if (overlayOpt.isEmpty()) {
            return ComplianceGateResult.allow();
        }
        YbNationalCatalogNoticeOverlay overlay = overlayOpt.get();

        // 停用已生效：禁止开单/上传
        ComplianceGateResult stop = checkStopActive(overlay, hilist, project, asOf);
        if (!stop.isAllowed()) {
            return stop;
        }

        // 甲乙类不一致：禁止开单/上传
        ComplianceGateResult pay = checkPayClass(command, overlay, hilist, project);
        if (!pay.isAllowed()) {
            return pay;
        }

        // 限价：超限阻断；无限价仅标注 UNKNOWN 放行
        return checkPrice(command.getUnitPrice(), overlay, hilist, project);
    }

    /**
     * 解析本行 overlay：预加载 Map 优先；未预加载则单条读库。
     *
     * @param command         行级入参（取 tenant/contract/listType）
     * @param hilist          已 trim 的国家码；可空
     * @param overlayByHilist 本批预加载；null 表示未批查
     * @return overlay；无则 empty
     */
    private Optional<YbNationalCatalogNoticeOverlay> resolveOverlay(
            ComplianceGateCommand command,
            String hilist,
            Map<String, YbNationalCatalogNoticeOverlay> overlayByHilist) {
        // 无国家码：无 overlay 可评
        if (hilist == null) {
            return Optional.empty();
        }
        // 本批已批量加载：缺键 = 无通知叠加（与 getOverlay 空同义）
        if (overlayByHilist != null) {
            YbNationalCatalogNoticeOverlay preloaded = overlayByHilist.get(hilist);
            if (preloaded == null) {
                return Optional.empty();
            }
            return Optional.of(preloaded);
        }
        return overlayQueryService.getOverlay(
            command.getTenantId(), command.getContractId(), command.getListType(), hilist);
    }

    /**
     * 仅项目码、无国家码时硬阻断。
     *
     * <p>注意：国家医疗服务价格项目编码本身常以 {@code 01} 开头（如 {@code 012302030030000}），
     * <strong>不能</strong>仅凭 {@code yb_no/hilist} 前缀 {@code 01*} 判定为未映射物价码。
     * 仅当入参只有 projectCode、没有 hilist，且映射表未落成时才阻断。</p>
     */
    private ComplianceGateResult checkProjectUnmapped(ComplianceGateCommand command) {
        Long tenantId = command.getTenantId();
        String hilist = trimToNull(command.getHilistCode());
        String project = trimToNull(command.getProjectCode());
        String itemName = trimToNull(command.getItemDisplayName());
        String itemLabel;
        if (itemName == null) {
            itemLabel = "该收费项目";
        } else {
            itemLabel = "「" + itemName + "」";
        }
        // 已有 hilist/yb_no：视为可上传目录编码（含合法的 01* 医疗服务价格国码），不再前缀误杀
        if (hilist != null) {
            return ComplianceGateResult.allow();
        }
        if (project != null && looksLikeProjectCode(project)) {
            Optional<YbServiceProjectMap> mapOpt =
                projectMapRepository.findByTenantIdAndProjectCodeAndIsDeletedFalse(tenantId, project);
            if (mapOpt.isEmpty() || MAP_PENDING.equals(mapOpt.get().getStatus())
                || trimToNull(mapOpt.get().getHilistCode()) == null) {
                return ComplianceGateResult.block(
                    C_PROJECT_UNMAPPED,
                    itemLabel + "仅有物价项目码「" + project + "」，尚未映射国家医保目录编码，无法医保收费。"
                        + "请到基础目录打开该项目，选用正确的国家医保编码后保存，再回到收费页重试。",
                    hilist,
                    project,
                    itemName,
                    command.getProductDefinitionId(),
                    command.getProductTableName());
            }
        }
        return ComplianceGateResult.allow();
    }

    private ComplianceGateResult checkStopActive(
            YbNationalCatalogNoticeOverlay overlay,
            String hilist,
            String project,
            LocalDate asOf) {
        if (!CatalogNoticeOverlayRules.isNoticeStopEffective(overlay, asOf)) {
            return ComplianceGateResult.allow();
        }
        String msg = "目录已停用，禁止医保开单/上传：编码=" + hilist;
        LocalDate end = overlay.getNoticeEndDate();
        if (end != null) {
            msg = msg + "，停用日=" + end;
        }
        return ComplianceGateResult.block(C_STOP_ACTIVE, msg, hilist, project);
    }

    private ComplianceGateResult checkPayClass(
            ComplianceGateCommand command,
            YbNationalCatalogNoticeOverlay overlay,
            String hilist,
            String project) {
        String expected = CatalogNoticeOverlayRules.readPatchString(
            overlay.getNoticePatchJson(), "chrgitm_lv");
        if (expected == null) {
            return ComplianceGateResult.allow();
        }
        String actual = trimToNull(command.getProductChrgitmLv());
        if (actual == null) {
            actual = hisProductPort.findProductChrgitmLv(
                command.getTenantId(), hilist, project);
        }
        if (actual == null) {
            return ComplianceGateResult.allow();
        }
        // 通知补丁为国标 1/2/3（甲乙丙）；院内 chrgitm_lv 与 yb_type 一致为 2/3/4
        if (nationalToHospitalYbTypeLv(expected).equals(normalizeLv(actual))) {
            return ComplianceGateResult.allow();
        }
        return ComplianceGateResult.block(
            C_PAY_CLASS,
            "支付类别不一致，禁止医保开单/上传：编码=" + hilist
                + "，通知等级=" + expected + "，院内等级=" + actual,
            hilist,
            project);
    }

    private ComplianceGateResult checkPrice(
            BigDecimal unitPrice,
            YbNationalCatalogNoticeOverlay overlay,
            String hilist,
            String project) {
        BigDecimal limit = CatalogNoticeOverlayRules.readPatchDecimal(
            overlay.getNoticePatchJson(), "hilist_pric_uplmt_amt");
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return ComplianceGateResult.allowUnknown(
                C_PRICE_UNKNOWN,
                "无限价，允许开单/上传：" + hilist);
        }
        if (unitPrice == null) {
            return ComplianceGateResult.allow();
        }
        if (unitPrice.compareTo(limit) > 0) {
            return ComplianceGateResult.block(
                C_PRICE_OVER,
                "单价超过医保限价，禁止开单/上传：编码=" + hilist
                    + "，单价=" + unitPrice.toPlainString()
                    + "，限价=" + limit.toPlainString(),
                hilist,
                project);
        }
        return ComplianceGateResult.allow();
    }

    private static boolean looksLikeProjectCode(String code) {
        return code != null && code.startsWith("01");
    }

    private static String normalizeLv(String lv) {
        String t = trimToNull(lv);
        if (t == null) {
            return "";
        }
        if (t.endsWith(".0")) {
            return t.substring(0, t.length() - 2);
        }
        return t;
    }

    /**
     * 国标收费等级 → 院内 yb_type/chrgitm_lv（1自费/2甲/3乙/4丙）。
     * <p>1/01→2，2/02→3，3/03→4；无法解析时回退原文。</p>
     */
    private static String nationalToHospitalYbTypeLv(String lv) {
        String t = normalizeLv(lv);
        if (t.isEmpty()) {
            return "";
        }
        try {
            int raw = Integer.parseInt(t);
            if (raw >= 1 && raw <= 3) {
                return String.valueOf(raw + 1);
            }
            return String.valueOf(raw);
        } catch (NumberFormatException ex) {
            return t;
        }
    }
}
