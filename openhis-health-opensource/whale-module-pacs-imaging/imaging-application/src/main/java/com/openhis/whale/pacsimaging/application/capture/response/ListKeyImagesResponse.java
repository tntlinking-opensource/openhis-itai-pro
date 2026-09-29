/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.response;

import com.openhis.whale.pacsimaging.application.capture.dto.KeyImageRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键图列表响应。
 */
@Getter
@Setter
public class ListKeyImagesResponse {

    /** 关键图行。 */
    private List<KeyImageRowDto> records = new ArrayList<>();
}
