/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.document.inventoryitemsnapshot.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 备份批次下拉读模型：单号 + 抓拍时刻（DM-DOC-001 · IF-11 / 库房期初期末明细 IF-01）。
 */
@Getter
@Setter
public class DocInventoryItemSnapshotBusNoOptionQueryModel {

    /** 备份批次单号（{@code bus_no}）。 */
    private String busNo;

    /** 批次抓拍时刻（同批次各行一致）。 */
    private LocalDateTime snapshotTime;
}
