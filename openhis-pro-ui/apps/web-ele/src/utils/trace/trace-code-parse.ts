/** 国家药品追溯码标准长度 */
export const TRACE_CODE_LENGTH = 20;

/** 单次批量反查上限（与后端 BR-TRC-015 一致） */
export const TRACE_BATCH_RESOLVE_MAX = 50;

/** 追溯码是否视为未绑定 */
export function isTraceSlotEmpty(value: string | undefined): boolean {
  const t = (value ?? '').trim();
  return t === '' || t === '—';
}

/** 将输入按换行拆分，并支持无换行时连续 20 位一段 */
export function extractTraceCodes(raw: string): { bad: string[]; ok: string[] } {
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
    if (t.length > TRACE_CODE_LENGTH && t.length % TRACE_CODE_LENGTH === 0) {
      for (let i = 0; i < t.length; i += TRACE_CODE_LENGTH) {
        ok.push(t.slice(i, i + TRACE_CODE_LENGTH));
      }
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
