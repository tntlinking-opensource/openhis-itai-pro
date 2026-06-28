/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.emr.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * EMR 文书摘要（卷宗目录 / 质控读侧）。
 */
@Getter
@Setter
public class EmrDocumentSnapshot {

    private Long documentId;
    private String title;
    private String documentTypeCode;
    private String documentTypeName;
    private String statusCode;
    private LocalDateTime createdAt;
    private boolean printable;
}
