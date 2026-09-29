/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr;

import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveBatchExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveDetailExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchivePageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveWriteExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchiveDetailExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchivePageExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchiveWriteExtensionOut;

/**
 * EMR 文书归档审批写/读 SPI（AS-MRMS-APR / EMR 提交归档）。
 *
 * <p>与既有 {@link EmrDocumentExtension#lockAllByEncounter} 一键锁定<strong>并列新增</strong>，
 * 不替代、不修改旧归档病例契约。</p>
 */
public interface EmrDocumentArchiveWorkflowExtension {

    /**
     * EMR 模块是否可用。
     *
     * @return true 表示可调用写/读方法
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 单份提交归档：DRAFT → PENDING_ARCHIVE。
     *
     * @param command 文书与操作人
     * @return 写结果
     */
    default EmrDocumentArchiveWriteExtensionOut submitArchive(EmrDocumentArchiveWriteExtensionIn command) {
        return disabledWrite();
    }

    /**
     * 批量提交归档（同事务由调用方/实现保证）。
     *
     * @param command 文书 id 列表与操作人
     * @return 写结果（以最后一份或汇总为准，失败抛异常）
     */
    default EmrDocumentArchiveWriteExtensionOut submitArchiveBatch(EmrDocumentArchiveBatchExtensionIn command) {
        return disabledWrite();
    }

    /**
     * 同意归档：PENDING_ARCHIVE → ARCHIVED。
     *
     * @param command 文书与审批人
     * @return 写结果
     */
    default EmrDocumentArchiveWriteExtensionOut approveArchive(EmrDocumentArchiveWriteExtensionIn command) {
        return disabledWrite();
    }

    /**
     * 驳回归档：PENDING_ARCHIVE → DRAFT。
     *
     * @param command 须含 rejectReason
     * @return 写结果
     */
    default EmrDocumentArchiveWriteExtensionOut rejectArchive(EmrDocumentArchiveWriteExtensionIn command) {
        return disabledWrite();
    }

    /**
     * 待归档审批分页。
     *
     * @param query 租户/就诊集合/分页
     * @return 分页摘要（不含正文）
     */
    default EmrDocumentArchivePageExtensionOut pagePendingArchive(EmrDocumentArchivePageExtensionIn query) {
        EmrDocumentArchivePageExtensionOut empty = new EmrDocumentArchivePageExtensionOut();
        empty.setTotalCount(0L);
        empty.setContent(java.util.Collections.emptyList());
        return empty;
    }

    /**
     * 审批详情（含正文）。
     *
     * @param query 文书主键
     * @return 详情；未找到时 documentId 为空
     */
    default EmrDocumentArchiveDetailExtensionOut getArchiveDetail(EmrDocumentArchiveDetailExtensionIn query) {
        return new EmrDocumentArchiveDetailExtensionOut();
    }

    /**
     * EMR 未启用时的写失败信封。
     *
     * @return success=false
     */
    private static EmrDocumentArchiveWriteExtensionOut disabledWrite() {
        EmrDocumentArchiveWriteExtensionOut out = new EmrDocumentArchiveWriteExtensionOut();
        out.setSuccess(false);
        out.setMessage("电子病历未启用");
        return out;
    }
}
