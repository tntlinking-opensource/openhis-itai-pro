/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.queryservice;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeDoc;
import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeLine;
import com.zxplat.plugin.chs.domain.notice.entity.YbServiceProjectMap;
import com.zxplat.plugin.chs.domain.notice.model.ApplyPreviewSampleView;
import com.zxplat.plugin.chs.domain.notice.model.ApplyPreviewView;
import com.zxplat.plugin.chs.domain.notice.model.HisUnchargedFeeLine;
import com.zxplat.plugin.chs.domain.notice.model.PreviewApplyQuery;
import com.zxplat.plugin.chs.domain.notice.port.CatalogNoticeHisChargePort;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeDocRepository;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeLineRepository;
import com.zxplat.plugin.chs.domain.notice.repository.YbServiceProjectMapRepository;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 应用差异预览只读服务（R-06）；不写库。
 * <p>仅本聚合 Repository（doc/line/project_map）；未收费估算经 {@link CatalogNoticeHisChargePort}。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeApplyPreviewQueryService {

    private static final String LINE_ACTIVE = "ACTIVE";
    private static final String ACTION_STOP = "STOP";
    private static final String ACTION_ADD = "ADD";
    private static final String ACTION_MODIFY = "MODIFY";
    private static final String ACTION_PROVINCE_MODIFY = "PROVINCE_MODIFY";
    private static final String ACTION_SET_PAY_CLASS = "SET_PAY_CLASS";
    private static final int SAMPLE_LIMIT = 20;

    private final YbCatalogNoticeDocRepository docRepository;
    private final YbCatalogNoticeLineRepository lineRepository;
    private final YbServiceProjectMapRepository projectMapRepository;
    private final CatalogNoticeHisChargePort hisChargePort;

    /**
     * 估算即将写入的 overlay / 回写 / 未收费同步计数与样例。
     *
     * @param query docId + tenantId
     * @return 预览视图
     */
    @Transactional(readOnly = true)
    public ApplyPreviewView previewApply(PreviewApplyQuery query) {
        if (query == null || query.getDocId() == null || query.getTenantId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "预览缺少 docId/tenantId");
        }
        YbCatalogNoticeDoc doc = docRepository
            .findByIdAndTenantIdAndIsDeletedFalse(query.getDocId(), query.getTenantId())
            .orElseThrow(() -> new BusinessException(
                ChsErrorCodes.NOTICE_DOC_NOT_FOUND,
                "文件不存在，docId=" + query.getDocId()));

        List<YbCatalogNoticeLine> lines = lineRepository.findByDocIdAndLineStatusAndIsDeletedFalse(
            doc.getId(), LINE_ACTIVE);

        int overlayUpsert = 0;
        int pendingMap = 0;
        int skipped = 0;
        int writeback = 0;
        int unchargedSync = 0;
        List<ApplyPreviewSampleView> samples = new ArrayList<>();

        for (YbCatalogNoticeLine line : lines) {
            String action = normalizeAction(line.getAction());
            String hilist = trimToNull(line.getHilistCode());
            String project = trimToNull(line.getProjectCode());
            String effect;

            if (ACTION_STOP.equals(action)) {
                if (hilist != null) {
                    overlayUpsert++;
                    effect = "overlay 停用 notice_avali_flag=0";
                } else if (project != null) {
                    pendingMap++;
                    effect = "无 hilist：已映射则停 overlay，否则 PENDING map";
                } else {
                    skipped++;
                    effect = "缺少主键，跳过";
                }
            } else if (ACTION_ADD.equals(action)) {
                if (hilist != null) {
                    overlayUpsert++;
                    effect = "overlay 可用+patch；若该码已停则保持停用";
                } else if (project != null) {
                    pendingMap++;
                    effect = "仅物价码 → ServiceProjectMap PENDING，不造国家码";
                } else {
                    skipped++;
                    effect = "缺少主键，跳过";
                }
            } else if (ACTION_MODIFY.equals(action) || ACTION_PROVINCE_MODIFY.equals(action)) {
                if (hilist != null) {
                    overlayUpsert++;
                    effect = "merge notice_patch_json（已停不复活）";
                } else if (project != null) {
                    pendingMap++;
                    effect = "无 hilist：已映射则改 overlay，否则 PENDING map";
                } else {
                    skipped++;
                    effect = "缺少主键，跳过";
                }
            } else if (ACTION_SET_PAY_CLASS.equals(action)) {
                String resolvedHilist = resolvePreviewHilist(doc.getTenantId(), hilist, project);
                if (resolvedHilist != null) {
                    overlayUpsert++;
                } else {
                    pendingMap++;
                }
                if (project != null || hilist != null) {
                    writeback++;
                }
                List<HisUnchargedFeeLine> fees = hisChargePort.listUnchargedFeeLines(
                    doc.getTenantId(), project, resolvedHilist, List.of());
                unchargedSync = unchargedSync + fees.size();
                String lv = trimToNull(line.getChrgitmLv());
                StringBuilder sb = new StringBuilder("SET_PAY_CLASS");
                if (lv != null) {
                    sb.append(" → chrgitm_lv=").append(lv);
                }
                if (resolvedHilist != null) {
                    sb.append("，写 overlay(").append(resolvedHilist).append(")+回写院内");
                } else {
                    sb.append("，PENDING map + 尝试按 yb_no/project 回写");
                }
                sb.append("，未收费估算 ").append(fees.size()).append(" 行");
                effect = sb.toString();
            } else {
                skipped++;
                effect = "未知动作，跳过";
            }

            if (samples.size() < SAMPLE_LIMIT) {
                ApplyPreviewSampleView sample = new ApplyPreviewSampleView();
                sample.setAction(action);
                sample.setHilistCode(hilist);
                sample.setProjectCode(project);
                sample.setEffectSummary(effect);
                samples.add(sample);
            }
        }

        ApplyPreviewView view = new ApplyPreviewView();
        view.setOverlayUpsertCount(overlayUpsert);
        view.setPendingMapCount(pendingMap);
        view.setSkippedCount(skipped);
        view.setWritebackCount(writeback);
        view.setUnchargedSyncCount(unchargedSync);
        view.setSamples(samples);
        return view;
    }

    /**
     * 预览时把物价码解析成已映射的国家码；未映射则无法估算 overlay。
     *
     * @param tenantId 租户
     * @param hilist   行上目录码
     * @param project  行上物价码
     * @return 国家码或 null
     */
    private String resolvePreviewHilist(Long tenantId, String hilist, String project) {
        if (hilist != null) {
            return hilist;
        }
        if (project == null) {
            return null;
        }
        Optional<YbServiceProjectMap> map = projectMapRepository
            .findByTenantIdAndProjectCodeAndIsDeletedFalse(tenantId, project);
        if (map.isEmpty()) {
            return null;
        }
        return trimToNull(map.get().getHilistCode());
    }

    /**
     * 未知动作或无法归一时返回空串。
     *
     * @param action 原动作
     * @return 大写动作
     */
    private static String normalizeAction(String action) {
        String trimmed = trimToNull(action);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toUpperCase();
    }
}
