/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfconfig.request;

import com.openhis.campus.application.campusgfconfig.response.GfFreePercentDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SaveFreePercentsRequest {

    private Long tenantId;

    @NotBlank
    private String chargeModeEnum;

    @NotEmpty
    @Valid
    private List<GfFreePercentDto> items = new ArrayList<>();
}
