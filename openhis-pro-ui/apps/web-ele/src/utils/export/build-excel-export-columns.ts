/**
 * 后端 Excel 导出列清单工具：页面列 → `exportColumns` 请求体。
 *
 * <p>与 whale-core {@code ExcelExportColumnDto} / {@code AnnotatedExcelExporter} 对齐；
 * 展示字段（如 quantityDisplay）在此展开为可求和数量列 + 单位列。</p>
 */

export interface ExcelExportColumnPayload {
  /** 导出行 DTO 字段名（须落在后端 @Excel 白名单） */
  field: string;
  /** 表头；与页面列 title 一致 */
  title: string;
}

/** 页面列最小结构（与 ReportPrintColumnDef 兼容） */
export interface ExcelExportSourceColumn {
  field: string;
  title: string;
}

/**
 * 展示字段 → 导出行字段展开（数量可求和 + 单位分列）。
 * 未命中的 field 原样透传（可用 fieldAliases 改名）。
 */
const DEFAULT_FIELD_EXPAND: Record<string, Array<{ field: string; title?: string }>> = {
  quantityDisplay: [
    { field: 'quantity', title: '数量' },
    { field: 'unitText', title: '单位' },
  ],
  netQuantity: [{ field: 'netQuantity' }, { field: 'packageUnitName', title: '单位' }],
  minUnitQuantity: [{ field: 'minUnitQuantity' }, { field: 'minUnitName', title: '最小单位' }],
};

/** 页面 field → 导出行 field（字典展示名等） */
const DEFAULT_FIELD_ALIASES: Record<string, string> = {
  sourceCode_dictText: 'sourceText',
  statusOrTypeEnum_enumText: 'statusOrTypeText',
};

export interface BuildExcelExportColumnsOptions {
  /** 覆盖默认展开规则 */
  fieldExpand?: Record<string, Array<{ field: string; title?: string }>>;
  /** 覆盖默认别名 */
  fieldAliases?: Record<string, string>;
}

/**
 * 将页面/打印列目录转为后端 `exportColumns`。
 *
 * @param catalog 当前 Tab 可见列（页面有什么导出什么）
 * @param options 展开/别名覆盖
 */
export function buildExcelExportColumns(
  catalog: ExcelExportSourceColumn[],
  options?: BuildExcelExportColumnsOptions,
): ExcelExportColumnPayload[] {
  const expand = { ...DEFAULT_FIELD_EXPAND, ...(options?.fieldExpand ?? {}) };
  const aliases = { ...DEFAULT_FIELD_ALIASES, ...(options?.fieldAliases ?? {}) };
  const result: ExcelExportColumnPayload[] = [];
  // 按页面列序展开，保证导出列顺序与表格一致
  for (const col of catalog) {
    if (!col?.field) {
      continue;
    }
    const title = String(col.title ?? '').trim() || col.field;
    const expanded = expand[col.field];
    if (expanded && expanded.length > 0) {
      for (let i = 0; i < expanded.length; i += 1) {
        const item = expanded[i];
        if (!item?.field) {
          continue;
        }
        // 展开首段沿用页面 title（如「数量」），后续段用规则默认 title
        const itemTitle = i === 0 ? title : String(item.title ?? '').trim() || item.field;
        result.push({ field: item.field, title: itemTitle });
      }
      continue;
    }
    const aliased = aliases[col.field] ?? col.field;
    result.push({ field: aliased, title });
  }
  return result;
}
