/**
 * 国家医保目录选码 → 院内目录表单字段匹配辅助。
 * 供药品/耗材/诊疗维护页共用：名称精确→模糊、厂家归一化匹配、限价解析。
 */

export interface CatalogLabeledOption {
  children?: CatalogLabeledOption[];
  label: string;
  value: string;
}

/** 解析目录限价上限为表单数字 */
export function parseCatalogPriceUplmt(value: string | undefined | null): number | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  const num = Number(String(value).trim());
  return Number.isFinite(num) && num >= 0 ? num : undefined;
}

/** 展平树形下拉 */
export function flattenLabeledOptions(
  options: CatalogLabeledOption[],
): Array<{ label: string; value: string }> {
  const result: Array<{ label: string; value: string }> = [];
  const walk = (nodes: CatalogLabeledOption[]) => {
    for (const node of nodes) {
      result.push({ label: node.label, value: node.value });
      if (node.children?.length) {
        walk(node.children);
      }
    }
  };
  walk(options);
  return result;
}

/**
 * 按下拉 label 匹配选项 value：精确优先，其次互为包含；多命中取最短 label。
 */
export function resolveOptionValueByLabel(
  options: CatalogLabeledOption[],
  rawLabel: string | undefined | null,
): string | undefined {
  const text = String(rawLabel ?? '').trim();
  const flat = flattenLabeledOptions(options);
  if (!text || flat.length === 0) {
    return undefined;
  }
  const normalized = text.toLowerCase();
  const exact = flat.find((item) => item.label.trim().toLowerCase() === normalized);
  if (exact) {
    return exact.value;
  }
  const fuzzy = flat.filter((item) => {
    const label = item.label.trim().toLowerCase();
    return label.includes(normalized) || normalized.includes(label);
  });
  if (fuzzy.length === 0) {
    return undefined;
  }
  if (fuzzy.length === 1) {
    return fuzzy[0]!.value;
  }
  fuzzy.sort((a, b) => a.label.length - b.label.length);
  return fuzzy[0]!.value;
}

/** 按多个候选 label 依次匹配 */
export function resolveOptionValueByLabels(
  options: CatalogLabeledOption[],
  labels: string[],
): string | undefined {
  for (const label of labels) {
    const matched = resolveOptionValueByLabel(options, label);
    if (matched) {
      return matched;
    }
  }
  return undefined;
}

/** 厂家名归一化：去空白/括号段/公司后缀 */
export function normalizeManufacturerName(raw: string): string {
  return String(raw ?? '')
    .trim()
    .toLowerCase()
    .replaceAll(/[（(][^）)]*[）)]/g, '')
    .replaceAll(/有限责任公司|股份有限公司|有限公司|集团公司|集团|制药厂|药业|制药|公司/g, '')
    .replaceAll(/[\s·・.。，,、]/g, '');
}

/**
 * 按目录生产企业匹配院内厂家下拉：精确 → 归一化精确 → 包含/核心名。
 */
export function resolveManufacturerOptionValue(
  options: Array<{ label: string; value: string }>,
  rawName: string | undefined | null,
): string | undefined {
  const text = String(rawName ?? '').trim();
  if (!text || options.length === 0) {
    return undefined;
  }
  const lower = text.toLowerCase();
  const exact = options.find((item) => item.label.trim().toLowerCase() === lower);
  if (exact) {
    return exact.value;
  }

  const normalizedCatalog = normalizeManufacturerName(text);
  if (normalizedCatalog) {
    const normExact = options.filter(
      (item) => normalizeManufacturerName(item.label) === normalizedCatalog,
    );
    if (normExact.length === 1) {
      return normExact[0]!.value;
    }
    if (normExact.length > 1) {
      normExact.sort((a, b) => a.label.length - b.label.length);
      return normExact[0]!.value;
    }
  }

  const core = text.split(/[（(]/)[0]?.trim() ?? text;
  const coreNorm = normalizeManufacturerName(core);
  const candidates = options.filter((item) => {
    const label = item.label.trim().toLowerCase();
    const labelNorm = normalizeManufacturerName(item.label);
    if (label.includes(lower) || lower.includes(label)) {
      return true;
    }
    if (normalizedCatalog && labelNorm) {
      if (labelNorm.includes(normalizedCatalog) || normalizedCatalog.includes(labelNorm)) {
        return true;
      }
    }
    if (coreNorm && labelNorm && (labelNorm.includes(coreNorm) || coreNorm.includes(labelNorm))) {
      return true;
    }
    return false;
  });
  if (candidates.length === 0) {
    return undefined;
  }
  if (candidates.length === 1) {
    return candidates[0]!.value;
  }
  candidates.sort((a, b) => a.label.length - b.label.length);
  return candidates[0]!.value;
}

/** 目录包装数量解析为正数倍数 */
export function parseCatalogPackCount(value: string | undefined | null): number | undefined {
  const raw = String(value ?? '').trim();
  if (!raw) {
    return undefined;
  }
  const num = Number(raw);
  if (!Number.isFinite(num) || num <= 0) {
    return undefined;
  }
  return num;
}
