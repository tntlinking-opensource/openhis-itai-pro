/** 国家药品追溯码标准长度 */
export const TRACE_CODE_LENGTH = 20;

/** 非标追溯码（如 GS1）单码长度上限（医保采集字段建议 100） */
export const TRACE_CODE_NON_STANDARD_MAX_LENGTH = 100;

/**
 * 非标追溯码药名关键字：名称包含任一关键字则不强制 20 位，绑码按名称匹配。
 * 当前：依洛尤单抗注射液（进口/GS1 码，位数非 20）。
 */
export const NON_STANDARD_TRACE_DRUG_NAME_KEYWORDS = ['依洛尤单抗'] as const;

/** 单次批量反查上限（与后端 BR-TRC-015 一致） */
export const TRACE_BATCH_RESOLVE_MAX = 50;

/** 追溯码是否视为未绑定 */
export function isTraceSlotEmpty(value: string | undefined): boolean {
  const t = (value ?? '').trim();
  return t === '' || t === '—';
}

/** 药品名称是否属于非标追溯码品（按关键字包含匹配） */
export function isNonStandardTraceDrugName(itemName: string | undefined): boolean {
  const name = String(itemName ?? '').trim();
  if (!name) return false;
  return NON_STANDARD_TRACE_DRUG_NAME_KEYWORDS.some((kw) => name.includes(kw));
}

/** 是否为标准 20 位追溯码 */
export function isStandardTraceCode(code: string | undefined): boolean {
  const t = String(code ?? '').trim();
  return t.length === TRACE_CODE_LENGTH;
}

/**
 * 是否允许作为非标长度追溯码（非 20 位、非空、不超过上限）。
 * 纯数字且长度为 20 的倍数仍走标准切分，不落此分支。
 */
export function isNonStandardLengthTraceCode(code: string | undefined): boolean {
  const t = String(code ?? '').trim();
  if (!t) return false;
  if (t.length === TRACE_CODE_LENGTH) return false;
  if (t.length > TRACE_CODE_NON_STANDARD_MAX_LENGTH) return false;
  // 连续粘贴的多段 20 位纯数字仍按标准码切分，不当非标整码
  if (t.length > TRACE_CODE_LENGTH && t.length % TRACE_CODE_LENGTH === 0 && /^\d+$/.test(t)) {
    return false;
  }
  return true;
}

export type ExtractTraceCodesOptions = {
  /**
   * 为 true 时：允许非 20 位整段入 ok（GS1 等）。
   * 仅当明细中存在非标药名行时由扫码弹窗打开。
   */
  allowNonStandardLength?: boolean;
};

/** 将输入按换行拆分，并支持无换行时连续 20 位一段 */
export function extractTraceCodes(
  raw: string,
  options?: ExtractTraceCodesOptions,
): { bad: string[]; ok: string[] } {
  const allowNonStandard = options?.allowNonStandardLength === true;
  const normalized = raw.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
  const segments = normalized.split('\n');
  const ok: string[] = [];
  const bad: string[] = [];
  for (const seg of segments) {
    const t = seg.trim();
    if (!t) continue;
    if (t.length === TRACE_CODE_LENGTH) {
      ok.push(t);
      continue;
    }
    // 纯数字且为 20 倍数：按标准国家码连续粘贴切分
    if (t.length > TRACE_CODE_LENGTH && t.length % TRACE_CODE_LENGTH === 0 && /^\d+$/.test(t)) {
      for (let i = 0; i < t.length; i += TRACE_CODE_LENGTH) {
        ok.push(t.slice(i, i + TRACE_CODE_LENGTH));
      }
      continue;
    }
    if (allowNonStandard && isNonStandardLengthTraceCode(t)) {
      ok.push(t);
      continue;
    }
    bad.push(t.length > 40 ? `${t.slice(0, 40)}…` : t);
  }
  return { ok, bad };
}

/** 追溯码列表去重（保持首次出现顺序） */
export function dedupeTraceCodes(codes: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const code of codes) {
    const trimmed = code.trim();
    if (!trimmed || seen.has(trimmed)) continue;
    seen.add(trimmed);
    result.push(trimmed);
  }
  return result;
}

/** 将码列表按上限分批 */
export function chunkTraceCodes(codes: string[], size = TRACE_BATCH_RESOLVE_MAX): string[][] {
  const chunks: string[][] = [];
  for (let i = 0; i < codes.length; i += size) {
    chunks.push(codes.slice(i, i + size));
  }
  return chunks;
}

/** 生成一条随机 20 位数字追溯码（开发模拟扫码用） */
export function generateRandomTraceCode(): string {
  const digits: string[] = [];
  // 首位 1–9，避免全零
  digits.push(String(Math.floor(Math.random() * 9) + 1));
  for (let i = 1; i < TRACE_CODE_LENGTH; i++) {
    digits.push(String(Math.floor(Math.random() * 10)));
  }
  return digits.join('');
}

/** 批量生成不重复的随机追溯码 */
export function generateRandomTraceCodes(count: number): string[] {
  const limit = Math.min(Math.max(1, Math.floor(count)), TRACE_BATCH_RESOLVE_MAX);
  const result: string[] = [];
  const seen = new Set<string>();
  while (result.length < limit) {
    const code = generateRandomTraceCode();
    if (seen.has(code)) continue;
    seen.add(code);
    result.push(code);
  }
  return result;
}
