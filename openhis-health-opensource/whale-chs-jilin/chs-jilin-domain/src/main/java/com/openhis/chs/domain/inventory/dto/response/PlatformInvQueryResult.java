/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class PlatformInvQueryResult {

    private String infno;
    private List<Map<String, Object>> rows = new ArrayList<>();
}
