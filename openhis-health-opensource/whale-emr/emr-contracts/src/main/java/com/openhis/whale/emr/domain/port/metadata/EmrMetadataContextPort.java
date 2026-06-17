/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.whale.emr.domain.port.metadata;

import java.util.Map;

/**
 * 向 EMR 元数据解析上下文贡献患者/就诊等外部数据（由 HIS bridge 实现）。
 */
public interface EmrMetadataContextPort {

    void contributePatient(Map<String, String> target, String patientId);

    void contributeEncounter(Map<String, String> target, String encounterId);

    void contributePractitioner(Map<String, String> target, Long practitionerId);
}
