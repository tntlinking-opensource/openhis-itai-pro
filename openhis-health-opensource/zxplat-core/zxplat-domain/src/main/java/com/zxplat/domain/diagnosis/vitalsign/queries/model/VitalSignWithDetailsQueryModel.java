/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 主表 + 明细组合读模型（getWithDetailsById）。
 */
@Getter
@Setter
public class VitalSignWithDetailsQueryModel {

    /** 主表读模型。 */
    private VitalSignHeaderQueryModel header;

    /** 明细读模型列表。 */
    private List<VitalSignDetailRowQueryModel> details = new ArrayList<>();
}
