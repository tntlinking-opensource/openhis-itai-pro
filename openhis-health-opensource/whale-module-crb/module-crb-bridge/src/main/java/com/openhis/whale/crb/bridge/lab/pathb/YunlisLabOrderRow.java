/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * yunlis {@code l_laborder} 读模型（Path B emrExLab 真源）。
 */
@Getter
@Setter
public class YunlisLabOrderRow {

    private Long labOrderId;
    private String barcode;
    private String applyNo;
    private String sampleNo;
    private String type;
    private String receiveFlag;
    private String patientId;
    private String patientBh;
    private String patientName;
    private String idCard;
    private String bed;
    private String wardName;
    private String specimen;
    private String specimenName;
    private String applyDeptCode;
    private String applyDeptName;
    private String requester;
    private String requesterName;
    private LocalDateTime samplingTime;
    private LocalDateTime receiveTime;
    private LocalDateTime executeTime;
    private LocalDateTime checkTime;
    private String auditor;
    private String executor;
    private String execDeptCode;
    private String labDepartment;
    private String remark;
}
