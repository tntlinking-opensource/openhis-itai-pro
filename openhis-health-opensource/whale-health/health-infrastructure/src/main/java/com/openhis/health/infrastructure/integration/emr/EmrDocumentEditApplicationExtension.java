/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr;

import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditCreateExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditIdExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditPageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditRejectExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditSaveDraftExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentEditApplicationExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentEditPageExtensionOut;

import java.util.Collections;
import java.util.List;

/**
 * EMR 文书编辑申请 SPI（DM-EMR-002；AS-MRMS-EDIT document 分支）。
 *
 * <p>独立于 {@link EmrDocumentExtension}，不改动既有 lock/unlock 行为。</p>
 */
public interface EmrDocumentEditApplicationExtension {

    /**
     * EMR 是否启用。
     *
     * @return true 可用
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 创建编辑申请。
     *
     * @param command 创建入参
     * @return 申请摘要
     */
    default EmrDocumentEditApplicationExtensionOut create(EmrDocumentEditCreateExtensionIn command) {
        return new EmrDocumentEditApplicationExtensionOut();
    }

    /**
     * 暂存工作稿。
     *
     * @param command 申请 id + JSON
     * @return 申请摘要
     */
    default EmrDocumentEditApplicationExtensionOut saveDraft(EmrDocumentEditSaveDraftExtensionIn command) {
        return new EmrDocumentEditApplicationExtensionOut();
    }

    /**
     * 交审。
     *
     * @param command 申请 id
     * @return 申请摘要
     */
    default EmrDocumentEditApplicationExtensionOut submitForReview(EmrDocumentEditIdExtensionIn command) {
        return new EmrDocumentEditApplicationExtensionOut();
    }

    /**
     * 同意并回写文书。
     *
     * @param command 申请 id + 审核人
     * @return 申请摘要
     */
    default EmrDocumentEditApplicationExtensionOut approve(EmrDocumentEditIdExtensionIn command) {
        return new EmrDocumentEditApplicationExtensionOut();
    }

    /**
     * 驳回。
     *
     * @param command 含驳回原因
     * @return 申请摘要
     */
    default EmrDocumentEditApplicationExtensionOut reject(EmrDocumentEditRejectExtensionIn command) {
        return new EmrDocumentEditApplicationExtensionOut();
    }

    /**
     * 按 id 加载（含双 JSON）。
     *
     * @param query 申请 id
     * @return 详情
     */
    default EmrDocumentEditApplicationExtensionOut getById(EmrDocumentEditIdExtensionIn query) {
        return new EmrDocumentEditApplicationExtensionOut();
    }

    /**
     * 审核列表分页。
     *
     * @param query 状态/文书/class/分页
     * @return 分页（列表不含双 JSON）
     */
    default EmrDocumentEditPageExtensionOut pageForReview(EmrDocumentEditPageExtensionIn query) {
        EmrDocumentEditPageExtensionOut empty = new EmrDocumentEditPageExtensionOut();
        empty.setTotalCount(0L);
        empty.setContent(Collections.emptyList());
        return empty;
    }

    /**
     * 文书维度历史。
     *
     * @param documentId 文书主键
     * @return 历史列表
     */
    default List<EmrDocumentEditApplicationExtensionOut> listHistoryByDocument(Long documentId) {
        return Collections.emptyList();
    }
}
