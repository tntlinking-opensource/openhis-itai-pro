/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 患者历史中药处方按方聚合读模型（IF-DOR-39 读侧真源，排除当前就诊）。
 */
@Getter
@Setter
public class TcmHerbalPrescriptionHistoryGroupQueryModel {
    /** 处方号。 */
    private String prescriptionNo;
    /** 来源就诊标识。 */
    private Long encounterId;
    /** 方级开立时间（组内最早 req_opened_time，空行回落 req_authored_time）。 */
    private LocalDateTime reqOpenedTime;
    /** 开立医师主键（med_medication_request.requester_id，取方内最早开立行；应用层 enrich 姓名）。 */
    private Long requesterId;
    /** 方内行数。 */
    private Integer lineCount;
    /** 方内饮片定义主键集合（应用层补药名展示）。 */
    private List<Long> medicationIds = new ArrayList<>();
}
