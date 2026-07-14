/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 3202 明细对账本地记录 */
@Getter
@Setter
@Entity
@Table(name = "yb_recon_detail")
public class YbReconDetail extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "contract_bus_no", nullable = false, length = 64)
    private String contractBusNo;

    @Column(name = "setl_optins", length = 6)
    private String setlOptins;

    @Column(name = "clr_type", length = 6)
    private String clrType;

    @Column(name = "refd_setl_flag", length = 3)
    private String refdSetlFlag;

    @Column(name = "stmt_begndate")
    private java.time.LocalDate stmtBegndate;

    @Column(name = "stmt_enddate")
    private java.time.LocalDate stmtEnddate;

    @Column(name = "upload_file_qury_no", length = 64)
    private String uploadFileQuryNo;

    @Column(name = "upload_filename", length = 200)
    private String uploadFilename;

    @Column(name = "result_file_qury_no", length = 64)
    private String resultFileQuryNo;

    @Column(name = "result_filename", length = 200)
    private String resultFilename;

    @Column(name = "dld_endtime", length = 30)
    private String dldEndtime;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "mismatch_count")
    private Integer mismatchCount;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;
}
