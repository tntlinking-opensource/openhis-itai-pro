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

package com.openhis.health.domain.print.printtemplate.constants;

/**
 * 画布打印模板状态码（存库 VARCHAR）。
 */
public final class PrintTemplateStatus {

    public static final String DRAFT = "draft";
    public static final String PUBLISHED = "published";
    public static final String ARCHIVED = "archived";

    private PrintTemplateStatus() {
    }
}
