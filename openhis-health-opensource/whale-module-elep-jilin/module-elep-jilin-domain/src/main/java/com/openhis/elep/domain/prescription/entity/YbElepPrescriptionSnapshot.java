/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 电子处方快照：再开真源。 */
@Getter
@Setter
@Entity
@Table(name = "yb_elep_prescription_snapshot")
@AttributeOverride(name = "id", column = @Column(name = "prescription_id", nullable = false))
public class YbElepPrescriptionSnapshot extends BaseAuditedEntity {

    @Column(name = "upload_request_json", columnDefinition = "TEXT")
    private String uploadRequestJson;

    @Column(name = "upload_response_json", columnDefinition = "TEXT")
    private String uploadResponseJson;

    @Column(name = "sign_response_json", columnDefinition = "TEXT")
    private String signResponseJson;

    @Column(name = "upload_confirm_json", columnDefinition = "TEXT")
    private String uploadConfirmJson;

    @Column(name = "pdf_file_path", length = 512)
    private String pdfFilePath;

    @Column(name = "pdf_file_hash", length = 128)
    private String pdfFileHash;
}
