/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusdepartment.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DepartmentTreeNodeDto {

    private String type;
    private Long id;
    private String code;
    private String name;
    private List<DepartmentTreeNodeDto> children = new ArrayList<>();
}
