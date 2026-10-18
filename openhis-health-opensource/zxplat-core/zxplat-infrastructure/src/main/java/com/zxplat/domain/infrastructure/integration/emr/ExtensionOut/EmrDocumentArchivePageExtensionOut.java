/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.emr.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文书待归档审批分页出参。
 */
@Getter
@Setter
public class EmrDocumentArchivePageExtensionOut {

    /** 总条数。 */
    private Long totalCount;

    /** 行列表。 */
    private List<EmrDocumentArchiveItemExtensionOut> content = new ArrayList<>();

    /**
     * 待审列表行。
     */
    @Getter
    @Setter
    public static class EmrDocumentArchiveItemExtensionOut {

        /** 文书主键。 */
        private Long documentId;

        /** 患者标识。 */
        private String patientId;

        /** 就诊标识。 */
        private String encounterId;

        /** 标题。 */
        private String title;

        /** 文书类型。 */
        private String documentTypeCode;

        /** 状态。 */
        private String statusCode;

        /** 书写科室。 */
        private Long organizationId;

        /** 业务版本。 */
        private Integer versionNo;

        /** 提交归档时间。 */
        private LocalDateTime archiveSubmittedAt;

        /** 提交人。 */
        private Long archiveSubmittedBy;
    }
}
