/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.constants;

/**
 * 与 {@link com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService} 配合的序号位数等数值常量。
 */
public interface BusinessSequenceConstants {

    /** busNo 去前缀后的序号位数（与按日生成序号的默认后缀位数一致）。 */
    /**
     * BUS_NO_SEQ_LENGTH。
     */
    int BUS_NO_SEQ_LENGTH = 4;
}
