/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

-- listDetail.name 具体项目名（对齐 areahis：chargeName=统计大类，name=药品/项目名）
ALTER TABLE einv_boss_line
    ADD COLUMN IF NOT EXISTS item_name VARCHAR(200);

COMMENT ON COLUMN einv_boss_line.item_name IS '清单具体项目名称（listDetail.name；可空时出站回落 charge_name）';
