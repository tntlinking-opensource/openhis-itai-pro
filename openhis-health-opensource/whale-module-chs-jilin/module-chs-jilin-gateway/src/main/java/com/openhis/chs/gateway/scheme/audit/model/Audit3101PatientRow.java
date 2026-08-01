/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/** 3101 patient_dtos 元素 */
@Data
@Accessors(chain = true)
public class Audit3101PatientRow {

    @JSONField(name = "patn_id")
    private String patnId;

    @JSONField(name = "patn_name")
    private String patnName;

    private String gend;

    private String brdy;

    private String poolarea;

    @JSONField(name = "curr_mdtrt_id")
    private String currMdtrtId;

    @JSONField(name = "fsi_encounter_dtos")
    private List<Audit3101EncounterRow> fsiEncounterDtos;

    /** HIS 扩展；联调常用 {}，以空 Map 序列化 */
    @JSONField(name = "fsi_his_data_dto")
    private Map<String, Object> fsiHisDataDto;
}
