/**
 * 上游药品「用过即记住」：本地记忆上次选的院内品与计量带。
 * 键优先 drugEntBaseInfoId；无则退化为规范化药名。
 */
export type MsfxDrugPickMemoryItem = {
  productDefinitionId: string;
  productName?: string;
  productTableName?: string;
  /** PURCHASE / PACKAGE / SPLIT 等 */
  qtyBandKind?: string;
  updatedAt: number;
};

const STORAGE_KEY = 'msfx.drugPickMemory.v1';

function readAll(): Record<string, MsfxDrugPickMemoryItem> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, MsfxDrugPickMemoryItem>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeAll(map: Record<string, MsfxDrugPickMemoryItem>) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    // quota / private mode：忽略
  }
}

export function normalizeMsfxDrugMemoryName(name: string): string {
  return String(name ?? '')
    .replace(/\s+/g, '')
    .trim()
    .toLowerCase();
}

/** 记忆键：平台药品 id 优先，否则 name:药名 */
export function resolveMsfxDrugMemoryKey(input: {
  drugEntBaseInfoId?: string | null;
  drugName?: string | null;
}): string {
  const id = String(input.drugEntBaseInfoId ?? '').trim();
  if (id) return `id:${id}`;
  const name = normalizeMsfxDrugMemoryName(String(input.drugName ?? ''));
  return name ? `name:${name}` : '';
}

export function getMsfxDrugPickMemory(key: string): MsfxDrugPickMemoryItem | null {
  const k = String(key ?? '').trim();
  if (!k) return null;
  const hit = readAll()[k];
  if (!hit?.productDefinitionId) return null;
  return hit;
}

export function saveMsfxDrugPickMemory(
  key: string,
  item: Omit<MsfxDrugPickMemoryItem, 'updatedAt'> & { updatedAt?: number },
) {
  const k = String(key ?? '').trim();
  const productDefinitionId = String(item.productDefinitionId ?? '').trim();
  if (!k || !productDefinitionId) return;
  const map = readAll();
  map[k] = {
    productDefinitionId,
    productName: item.productName,
    productTableName: item.productTableName,
    qtyBandKind: item.qtyBandKind,
    updatedAt: item.updatedAt ?? Date.now(),
  };
  writeAll(map);
}
