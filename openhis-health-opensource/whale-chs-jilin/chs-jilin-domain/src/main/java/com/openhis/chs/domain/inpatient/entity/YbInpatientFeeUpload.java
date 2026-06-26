/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.entity;

import com.openhis.chs.domain.settlement.PreSettleSnapshot;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import com.openhis.chs.domain.settlement.YbPreSettleStatus;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 2301 住院费用上传及平台分割结果。 */
@Getter
@Setter
@Entity
@Table(name = "yb_inpatient_fee_upload")
public class YbInpatientFeeUpload extends BaseAuditedEntity {

    /** @deprecated 使用 {@link YbFeeUploadStatus#UPLOADED} */
    @Deprecated
    public static final String STATUS_UPLOADED = YbFeeUploadStatus.UPLOADED;
    /** @deprecated 使用 {@link YbFeeUploadStatus#REVOKED} */
    @Deprecated
    public static final String STATUS_FAILED = "FAILED";
    /** @deprecated 使用 {@link YbFeeUploadStatus#REVOKED} */
    @Deprecated
    public static final String STATUS_REVOKED = YbFeeUploadStatus.REVOKED;
    /** @deprecated 使用 {@link YbFeeUploadStatus#SETTLED} */
    @Deprecated
    public static final String STATUS_SETTLED = YbFeeUploadStatus.SETTLED;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "charge_item_id", nullable = false)
    private Long chargeItemId;

    @Column(name = "feedetl_sn", nullable = false, length = 30)
    private String feedetlSn;

    @Column(name = "mdtrt_id", nullable = false, length = 30)
    private String mdtrtId;

    @Column(name = "upload_status", nullable = false, length = 16)
    private String uploadStatus;

    @Column(name = "det_item_fee_sumamt")
    private BigDecimal detItemFeeSumamt;

    @Column(name = "chrgitm_lv", length = 3)
    private String chrgitmLv;

    @Column(name = "med_chrgitm_type", length = 6)
    private String medChrgitmType;

    @Column(name = "selfpay_prop")
    private BigDecimal selfpayProp;

    @Column(name = "fulamt_ownpay_amt")
    private BigDecimal fulamtOwnpayAmt;

    @Column(name = "overlmt_amt")
    private BigDecimal overlmtAmt;

    @Column(name = "preselfpay_amt")
    private BigDecimal preselfpayAmt;

    @Column(name = "inscp_scp_amt")
    private BigDecimal inscpScpAmt;

    @Column(name = "lmt_used_flag", length = 3)
    private String lmtUsedFlag;

    @Column(name = "platform_memo", length = 500)
    private String platformMemo;

    @Column(name = "platform_result_json", columnDefinition = "TEXT")
    private String platformResultJson;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
}
