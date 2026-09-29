import type { EditorState } from '@tiptap/pm/state';
import type { Mark as PMMark, Node as PMNode, Schema } from '@tiptap/pm/model';
import type { Transaction } from '@tiptap/pm/state';

import { Fragment } from '@tiptap/pm/model';
import { Mapping, ReplaceStep } from '@tiptap/pm/transform';
import { NodeSelection, TextSelection } from '@tiptap/pm/state';

import {
  EMR_PM_CONTENT_FIELD_TYPES,
  isAtomFillableNode,
  isTextLikeFillableNode,
  readEmrInlineFieldPlainText,
} from '../../components/simple-editor/emr-data-element-text';
import {
  EMR_FILLABLE_MARK,
  FILLABLE_EMPTY_CARET,
  isFillableSlotEmptyText,
} from '../../components/simple-editor/emr-fillable-json';
import { EMR_FILLABLE_FOCUS_ISLAND_META } from '../kit/meta';

/** 可填域命中：优先 mark 区间，其次遗留节点 / 控件岛 */
export interface EmrFillableHit {
  editable: boolean;
  from: number;
  innerFrom: number;
  innerTo: number;
  kind: 'mark' | 'widget';
  mark?: PMMark;
  node?: PMNode;
  pos: number;
  to: number;
}

function isEditableMark(mark: PMMark): boolean {
  return mark.attrs.editable !== false && mark.attrs.editable !== '0';
}

export function isEditableFillableNode(node: PMNode | null | undefined): boolean {
  if (!node || !EMR_PM_CONTENT_FIELD_TYPES.has(node.type.name)) {
    return false;
  }
  if (node.type.name === 'emrDataElement' && node.attrs.editable === false) {
    return false;
  }
  if (node.type.name === 'emrMetadataChip' && node.attrs.editable === false) {
    return false;
  }
  return true;
}

export function isAnyFillableNode(node: PMNode | null | undefined): boolean {
  return Boolean(node && EMR_PM_CONTENT_FIELD_TYPES.has(node.type.name));
}

function findFillableMark(marks: readonly PMMark[]): PMMark | null {
  return marks.find((m) => m.type.name === EMR_FILLABLE_MARK) ?? null;
}

/**
 * 合并相邻（含 hardBreak）且 slotId 相同的 fillable mark 区间。
 */
export function collectFillableMarkRanges(doc: PMNode): EmrFillableHit[] {
  const hits: EmrFillableHit[] = [];
  let pendingHardBreak = false;

  doc.descendants((node, pos) => {
    if (node.type.name === 'hardBreak') {
      pendingHardBreak = true;
      return;
    }
    if (!node.isText || !node.text) {
      pendingHardBreak = false;
      return;
    }
    const mark = findFillableMark(node.marks);
    if (!mark) {
      pendingHardBreak = false;
      return;
    }
    const from = pos;
    const to = pos + node.nodeSize;
    const last = hits[hits.length - 1];
    const sameSlot = last && String(last.mark?.attrs.slotId) === String(mark.attrs.slotId);
    if (last && sameSlot && (last.to === from || (pendingHardBreak && last.to + 1 === from))) {
      last.to = to;
      last.innerTo = to;
      pendingHardBreak = false;
      return;
    }
    hits.push({
      kind: 'mark',
      from,
      to,
      pos: from,
      innerFrom: from,
      innerTo: to,
      mark,
      editable: isEditableMark(mark),
    });
    pendingHardBreak = false;
  });
  return hits;
}

function buildWidgetHit(node: PMNode, pos: number): EmrFillableHit {
  const atom = isAtomFillableNode(node);
  return {
    kind: 'widget',
    from: pos,
    to: pos + node.nodeSize,
    pos,
    innerFrom: atom ? pos : pos + 1,
    innerTo: atom ? pos + node.nodeSize : pos + node.nodeSize - 1,
    node,
    editable: isEditableFillableNode(node),
  };
}

function collectWidgetHits(doc: PMNode): EmrFillableHit[] {
  const hits: EmrFillableHit[] = [];
  doc.descendants((node, pos) => {
    if (!isAnyFillableNode(node)) return true;
    hits.push(buildWidgetHit(node, pos));
    return true;
  });
  return hits;
}

export function collectAllFillableHits(doc: PMNode): EmrFillableHit[] {
  return [...collectFillableMarkRanges(doc), ...collectWidgetHits(doc)].sort(
    (a, b) => a.from - b.from,
  );
}

/** 解析 pos 所在的可填域（含只读；半开区间 [from, to)，槽尾不算域内） */
export function resolveFillableAt(doc: PMNode, pos: number): EmrFillableHit | null {
  const safe = Math.min(Math.max(0, pos), doc.content.size);
  const marks = collectFillableMarkRanges(doc);
  for (const hit of marks) {
    if (safe >= hit.from && safe < hit.to) {
      return hit;
    }
  }
  const $pos = doc.resolve(safe);
  for (let d = $pos.depth; d > 0; d--) {
    const n = $pos.node(d);
    if (!EMR_PM_CONTENT_FIELD_TYPES.has(n.type.name)) {
      continue;
    }
    const nodePos = $pos.before(d);
    return buildWidgetHit(n, nodePos);
  }
  return null;
}

export function textblockBoundsAt(doc: PMNode, pos: number): { end: number; start: number } | null {
  const safe = Math.min(Math.max(0, pos), doc.content.size);
  const $pos = doc.resolve(safe);
  for (let d = $pos.depth; d > 0; d--) {
    if ($pos.node(d).isTextblock) {
      return { start: $pos.start(d), end: $pos.end(d) };
    }
  }
  return null;
}

export function findNearestEditableFillable(
  doc: PMNode,
  pos: number,
  options?: { sameTextblockOnly?: boolean },
): EmrFillableHit | null {
  const inside = resolveFillableAt(doc, pos);
  if (inside?.editable) {
    return inside;
  }

  const all = collectAllFillableHits(doc).filter((h) => h.editable);
  const block = textblockBoundsAt(doc, pos);
  if (block) {
    const after = all.find((h) => h.from >= pos && h.from < block.end);
    if (after) return after;
    let before: EmrFillableHit | null = null;
    for (const h of all) {
      if (h.to <= pos && h.from >= block.start) {
        before = h;
      }
    }
    if (before) return before;
    /** 同行仅有槽时：点在标签上也吸附（如「主诉:」） */
    const inBlock = all.filter((h) => h.from >= block.start && h.from < block.end);
    if (inBlock.length === 1) {
      return inBlock[0]!;
    }
    if (inBlock.length > 1) {
      let best: EmrFillableHit | null = null;
      let bestDist = Number.POSITIVE_INFINITY;
      for (const h of inBlock) {
        const mid = (h.from + h.to) / 2;
        const dist = Math.abs(mid - pos);
        if (dist < bestDist) {
          bestDist = dist;
          best = h;
        }
      }
      if (best) return best;
    }
  }

  if (options?.sameTextblockOnly) {
    return null;
  }

  let best: EmrFillableHit | null = null;
  let bestDist = Number.POSITIVE_INFINITY;
  for (const h of all) {
    const mid = (h.from + h.to) / 2;
    const dist = Math.abs(mid - pos);
    if (dist < bestDist) {
      bestDist = dist;
      best = h;
    }
  }
  return best;
}

/** 光标是否在可填域内容首（有内容时 Backspace 应删域外一字） */
export function isCaretAtFillableContentStart(doc: PMNode, pos: number): EmrFillableHit | null {
  const hit = resolveFillableAt(doc, pos);
  if (!hit?.editable) return null;
  if (pos !== hit.innerFrom) return null;
  if (isFillableHitEmpty(doc, hit)) return null;
  return hit;
}

/**
 * 光标是否正好落在可填域半开区间的 to（域尾外侧位）。
 * 常带 storedMarks 续写；← 回到域内，Delete 删域后一字。
 */
export function resolveFillableAtExclusiveEnd(doc: PMNode, pos: number): EmrFillableHit | null {
  if (pos <= 0) return null;
  const hit = resolveFillableAt(doc, pos - 1);
  if (!hit?.editable || hit.to !== pos) return null;
  return hit;
}

/**
 * 组字开始时锁定哪个槽（仅装饰 / 上屏补 mark 用）。
 * 硬约束：槽尾 exclusive end + storedMarks==null/[] = 已出域（] 外），返回 null。
 * 允许锁定：槽内 [from,to)；空槽/有内容槽尾且 storedMarks 仍带本槽（续写）。
 * 禁止：空槽 to + null/[] 仍锁（否则 IME 上屏会把 ] 外字吸进元数据）。
 */
export function resolveImeCompositionSlotId(
  doc: PMNode,
  head: number,
  storedMarks: readonly PMMark[] | null,
): string | null {
  if (isExplicitlyOutsideFillable(storedMarks)) return null;

  const inside = resolveFillableAt(doc, head);
  if (inside?.editable && inside.mark) {
    return String(inside.mark.attrs.slotId ?? '') || null;
  }

  // 空槽 to：仅续写态可锁；null/[] 视觉已在 ] 外，不得锁
  const emptyTrap = isEmptyFillableExclusiveEndTrap(doc, head);
  if (
    emptyTrap?.editable &&
    emptyTrap.mark &&
    hasFillableMarkInList(storedMarks) &&
    storedMarks!.some(
      (m) =>
        m.type.name === EMR_FILLABLE_MARK &&
        String(m.attrs.slotId ?? '') === String(emptyTrap.mark!.attrs.slotId ?? ''),
    )
  ) {
    return String(emptyTrap.mark.attrs.slotId ?? '') || null;
  }

  const exclusive = resolveFillableAtExclusiveEnd(doc, head);
  if (exclusive?.editable && exclusive.mark && hasFillableMarkInList(storedMarks)) {
    const slotId = String(exclusive.mark.attrs.slotId ?? '');
    if (
      slotId &&
      storedMarks!.some(
        (m) => m.type.name === EMR_FILLABLE_MARK && String(m.attrs.slotId ?? '') === slotId,
      )
    ) {
      return slotId;
    }
  }

  // 槽外仅凭 storedMarks 不锁定：否则元数据前组字会被误判为槽内续写
  return null;
}

/**
 * IME 上屏后（非组字中）：把「仍在续写/组字锁定」的槽尾外侧字补进 mark。
 * 硬约束：storedMarks==null 且无 lockedSlotId ≠ 续写，禁止吸字。
 * mark 以 slotId 在 newDoc 上查找（组字结束后 head 已在槽外，不能再靠 old exclusive）。
 */
export function extendFillableMarkOverImeComposition(
  oldState: { doc: PMNode; selection: { head: number }; storedMarks: readonly PMMark[] | null },
  newState: {
    doc: PMNode;
    selection: { head: number };
    storedMarks: readonly PMMark[] | null;
    tr: Transaction;
  },
  options?: { slotId?: string | null },
): Transaction | null {
  const lockedSlotId =
    options?.slotId != null && String(options.slotId) !== '' ? String(options.slotId) : '';
  const imeLocked = lockedSlotId !== '';

  // IME 组字锁槽时 storedMarks 常被浏览器清成 []，仍以 lockedSlotId 为准吸回槽尾拼音
  if (!imeLocked) {
    if (isExplicitlyOutsideFillable(oldState.storedMarks)) return null;
    if (isExplicitlyOutsideFillable(newState.storedMarks)) return null;
  }

  const storedMark =
    oldState.storedMarks?.find((m) => m.type.name === EMR_FILLABLE_MARK) ??
    newState.storedMarks?.find((m) => m.type.name === EMR_FILLABLE_MARK) ??
    null;

  if (!imeLocked && !storedMark) return null;

  const slotId = lockedSlotId || String(storedMark?.attrs.slotId ?? '');
  if (!slotId) return null;

  const hit =
    collectFillableMarkRanges(newState.doc).find(
      (h) => h.editable && String(h.mark?.attrs.slotId ?? '') === slotId,
    ) ?? null;
  if (!hit?.mark) return null;

  const head = newState.selection.head;
  if (head <= hit.to) return null;
  const ghost = newState.doc.textBetween(hit.to, head, '', '');
  const pinyinGhost = /^[a-zA-Z']+$/.test(ghost);
  // 锁槽时 CJK 一次上屏很短；4096 会把槽后整段体检吸进出生地（B22）
  const maxSpan = imeLocked && !pinyinGhost ? 12 : 64;
  if (head - hit.to > maxSpan) return null;
  if (!isPlainTextBetween(newState.doc, hit.to, head)) return null;

  const tr = newState.tr.addMark(hit.to, head, hit.mark);
  tr.setStoredMarks([hit.mark]);
  tr.setMeta('addToHistory', false);
  return tr;
}

/**
 * 组字中：槽尾到光标之间是未进 mark 的拼音临时段（仅装饰把 ] 画到拼音后，不改 doc）。
 * 必须已锁定本槽或 storedMarks 续写本槽；禁止「光标在 ] 外」时仍把 ] 拖出去。
 */
export function isImeCompositionGhostAfterFillable(
  doc: PMNode,
  hit: EmrFillableHit,
  head: number,
  storedMarks: readonly PMMark[] | null,
  composing: boolean,
  imeSlotId?: string | null,
): boolean {
  if (!composing || !hit.editable || !hit.mark) return false;
  const slotId = String(hit.mark.attrs.slotId ?? '');
  if (!slotId) return false;
  const locked = imeSlotId != null && imeSlotId !== '' && imeSlotId === slotId;
  const sticky = Boolean(
    storedMarks?.some(
      (m) => m.type.name === EMR_FILLABLE_MARK && String(m.attrs.slotId ?? '') === slotId,
    ),
  );
  if (!locked && !sticky) return false;
  // 组字锁槽时 storedMarks 可能已是 []，仍要把 ] 画到拼音后
  if (!locked && isExplicitlyOutsideFillable(storedMarks)) return false;
  if (head <= hit.to || head - hit.to > 64) return false;
  return isPlainTextBetween(doc, hit.to, head);
}

export function isPlainTextBetween(doc: PMNode, from: number, to: number): boolean {
  if (to <= from) return false;
  let plain = true;
  doc.nodesBetween(from, to, (node) => {
    if (!plain) return false;
    if (node.isText) {
      if (node.marks.some((m) => m.type.name === EMR_FILLABLE_MARK)) {
        plain = false;
      }
      return;
    }
    if (node.type.name === 'hardBreak') {
      plain = false;
    }
  });
  return plain;
}

function hasFillableMarkInList(storedMarks: readonly PMMark[] | null): boolean {
  return Boolean(storedMarks?.some((m) => m.type.name === EMR_FILLABLE_MARK));
}

export function collectEditableFillableContentRanges(
  doc: PMNode,
  from: number,
  to: number,
): { from: number; to: number }[] {
  if (to <= from) return [];
  const ranges: { from: number; to: number }[] = [];
  for (const hit of collectAllFillableHits(doc)) {
    if (!hit.editable) continue;
    const a = Math.max(from, hit.innerFrom);
    const b = Math.min(to, hit.innerTo);
    if (b > a) {
      ranges.push({ from: a, to: b });
    }
  }
  return ranges.sort((a, b) => a.from - b.from);
}

export function selectionTouchesNonFillableText(doc: PMNode, from: number, to: number): boolean {
  if (to <= from) return false;
  let touched = false;
  doc.nodesBetween(from, to, (node, pos) => {
    if (touched) return false;
    // 固定文本等 atom：不进 isText 分支，旧逻辑漏检导致多选删被 contentLock/fixedText 挡死
    if (node.type.name === 'emrFixedText') {
      touched = true;
      return false;
    }
    if (!node.isText || !node.text) return true;
    const hit = resolveFillableAt(doc, pos);
    if (!hit?.editable) {
      touched = true;
      return false;
    }
    return true;
  });
  return touched;
}

export function isSelectingEntireFillableContent(
  hit: EmrFillableHit,
  from: number,
  to: number,
): boolean {
  return from <= hit.innerFrom && to >= hit.innerTo;
}

export function snapCaretInFillable(hit: EmrFillableHit, pos: number): number {
  if (pos <= hit.from) return hit.innerFrom;
  if (pos >= hit.to) return hit.innerTo;
  if (pos < hit.innerFrom) return hit.innerFrom;
  if (pos > hit.innerTo) return hit.innerTo;
  return pos;
}

export function isTypingKey(event: KeyboardEvent): boolean {
  if (event.isComposing) return false;
  if (event.ctrlKey || event.metaKey || event.altKey) return false;
  if (event.key === 'Process') return false;
  if (event.key.length === 1) return true;
  return false;
}

export function isFillableHitEmpty(doc: PMNode, hit: EmrFillableHit): boolean {
  if (hit.node && isAtomFillableNode(hit.node)) {
    return isFillableSlotEmptyText(readEmrInlineFieldPlainText(hit.node));
  }
  const text = doc.textBetween(hit.innerFrom, hit.innerTo, '', '');
  return isFillableSlotEmptyText(text) || text.length === 0;
}

/**
 * 空槽半开尾陷阱：光标在 to 时，Chrome 仍画在占位框里，但 mark 已算出域，
 * 一打字就跑到框外。空槽编辑中只允许停在 innerFrom。
 */
export function isEmptyFillableExclusiveEndTrap(doc: PMNode, pos: number): EmrFillableHit | null {
  const hit = resolveFillableAtExclusiveEnd(doc, pos);
  if (!hit?.editable || !hit.mark) return null;
  if (!isFillableHitEmpty(doc, hit)) return null;
  return hit;
}

/**
 * 把空槽陷阱光标拉回可写点（innerFrom + storedMarks）。
 * 仅当 storedMarks 仍带本槽（续写）时拉回；null/[] = 已在 ] 外，不拉回。
 */
export function pullEmptyFillableCaretInside(
  tr: Transaction,
  storedMarks?: readonly PMMark[] | null,
): boolean {
  if (!tr.selection.empty) return false;
  const hit = isEmptyFillableExclusiveEndTrap(tr.doc, tr.selection.head);
  if (!hit?.mark) return false;
  const marks = storedMarks !== undefined ? storedMarks : tr.storedMarks;
  if (!hasFillableMarkInList(marks)) return false;
  const slotId = String(hit.mark.attrs.slotId ?? '');
  if (
    !marks!.some(
      (m) => m.type.name === EMR_FILLABLE_MARK && String(m.attrs.slotId ?? '') === slotId,
    )
  ) {
    return false;
  }
  tr.setSelection(TextSelection.create(tr.doc, hit.innerFrom));
  tr.setStoredMarks([hit.mark]);
  return true;
}

/**
 * 插入槽位后把光标放进域内（节点 content 内）。
 */
export function selectInsideFillableSlot(tr: Transaction, pos?: number): boolean {
  const head = pos ?? tr.selection.head;
  const hit =
    resolveFillableAt(tr.doc, head) ??
    resolveFillableAt(tr.doc, Math.max(0, head - 1)) ??
    (() => {
      if (pos == null) return null;
      const node = tr.doc.nodeAt(pos);
      if (!node || !isAnyFillableNode(node)) return null;
      return buildWidgetHit(node, pos);
    })();
  if (!hit) return false;
  if (hit.node && isAtomFillableNode(hit.node)) {
    tr.setSelection(NodeSelection.create(tr.doc, hit.from));
    tr.setMeta(EMR_FILLABLE_FOCUS_ISLAND_META, {
      pos: hit.from,
      caret: isFillableHitEmpty(tr.doc, hit) ? 'start' : 'end',
    });
    return true;
  }
  const caret = isFillableHitEmpty(tr.doc, hit)
    ? hit.innerFrom
    : Math.max(hit.innerFrom, hit.innerTo);
  tr.setSelection(TextSelection.create(tr.doc, caret));
  return true;
}

/**
 * 插入后：光标停在域外（节点之后）。
 */
export function selectOutsideFillableSlot(tr: Transaction, pos?: number): boolean {
  const head = pos ?? tr.selection.head;
  const hit =
    resolveFillableAt(tr.doc, head) ??
    resolveFillableAt(tr.doc, Math.max(0, head - 1)) ??
    (() => {
      if (pos == null) return null;
      const node = tr.doc.nodeAt(pos);
      if (!node || !isAnyFillableNode(node)) return null;
      return buildWidgetHit(node, pos);
    })();
  if (!hit) return false;
  tr.setSelection(TextSelection.create(tr.doc, hit.to));
  return true;
}

/** storedMarks=[] 表示已离开槽尾；null 表示跟 inclusive 默认（仍在槽内） */
export function isExplicitlyOutsideFillable(storedMarks: readonly PMMark[] | null): boolean {
  if (storedMarks == null) return false;
  return !storedMarks.some((m) => m.type.name === EMR_FILLABLE_MARK);
}

/**
 * 若 pos 落在可填域内部，抬到该域之后，避免元数据嵌套。
 */
export function liftPosOutOfFillableField(doc: PMNode, pos: number): number {
  const hit = resolveFillableAt(doc, pos);
  if (!hit) {
    const safe = Math.min(Math.max(0, pos), doc.content.size);
    return safe;
  }
  return hit.to;
}

function slotDeletedWithOutside(
  trs: readonly Transaction[],
  hitFrom: number,
  hitTo: number,
): boolean {
  for (const tr of trs) {
    for (const step of tr.steps) {
      if (!(step instanceof ReplaceStep)) continue;
      if (step.from < hitFrom || step.to > hitTo) {
        return true;
      }
    }
  }
  return false;
}

/** recover：仅当 replace 碰到槽起点之前才放弃（槽尾外扩属 IME 正常路径） */
function slotReplaceTouchedBeforeSlot(trs: readonly Transaction[], hitFrom: number): boolean {
  for (const tr of trs) {
    for (const step of tr.steps) {
      if (!(step instanceof ReplaceStep)) continue;
      if (step.from < hitFrom) return true;
    }
  }
  return false;
}

function slotTouched(trs: readonly Transaction[], hitFrom: number, hitTo: number): boolean {
  for (const tr of trs) {
    for (const step of tr.steps) {
      if (!(step instanceof ReplaceStep)) continue;
      if (step.from < hitTo && step.to > hitFrom) {
        return true;
      }
    }
  }
  return false;
}

/**
 * IME/输入导致 mark 整段丢失时：把槽位处的无 mark 正文重新包回元数据 mark，或回填空槽。
 * 仅 metadata / dataElement；自由文本删光即消失。
 */
export function recoverLostFillableMarkSlot(
  trs: readonly Transaction[],
  oldState: EditorState,
  newState: EditorState,
): Transaction | null {
  const markType = newState.schema.marks[EMR_FILLABLE_MARK];
  if (!markType) return null;

  const oldHits = collectFillableMarkRanges(oldState.doc);
  const newHits = collectFillableMarkRanges(newState.doc);
  const alive = new Set(newHits.map((h) => String(h.mark?.attrs.slotId ?? '')));

  const mapping = new Mapping();
  for (const stepTr of trs) {
    mapping.appendMapping(stepTr.mapping);
  }

  let tr = newState.tr;
  let changed = false;

  for (const old of oldHits) {
    if (!old.mark) continue;
    const kind = String(old.mark.attrs.kind ?? 'freeText');
    if (kind === 'freeText') continue;

    const slotId = String(old.mark.attrs.slotId ?? '');
    if (!slotId || alive.has(slotId)) continue;
    const touched = trs.length ? slotTouched(trs, old.from, old.to) : true;
    if (!touched) continue;
    // IME 上屏常把拼音落在槽尾外再一次性 replace，允许 to 超出旧槽尾
    if (slotReplaceTouchedBeforeSlot(trs, old.from)) continue;

    const at = Math.min(Math.max(0, mapping.map(old.from, 1)), tr.doc.content.size);
    let textEnd = Math.min(Math.max(at, mapping.map(old.to, -1)), tr.doc.content.size);
    // IME 上屏可能略超出旧槽尾，向后吞无 mark 的连续字（上限 64）
    while (textEnd < tr.doc.content.size && textEnd - at < 64) {
      if (resolveFillableAt(tr.doc, textEnd)?.mark) break;
      const ch = tr.doc.textBetween(textEnd, textEnd + 1, '', '');
      if (!ch) break;
      textEnd++;
    }

    const segment = tr.doc.textBetween(at, textEnd, '', '');
    if (!segment || isFillableSlotEmptyText(segment)) {
      const $pos = tr.doc.resolve(at);
      if (!$pos.parent.inlineContent) continue;
      const mark = markType.create({ ...old.mark.attrs });
      tr = tr.insert(at, newState.schema.text(FILLABLE_EMPTY_CARET, [mark]));
      changed = true;
      continue;
    }

    if (isPlainTextBetween(tr.doc, at, textEnd)) {
      const mark = markType.create({ ...old.mark.attrs });
      tr = tr.addMark(at, textEnd, mark);
      changed = true;
    }
  }

  if (!changed) return null;
  tr.setMeta('addToHistory', false);
  return tr;
}

/** 回填患者信息：改 mark 正文，控件岛只改 attrs.value */
export function replaceFillableHitPlainText(
  tr: Transaction,
  schema: Schema,
  hit: EmrFillableHit,
  text: string,
): Transaction {
  if (hit.kind === 'widget' && hit.node) {
    return tr.setNodeAttribute(hit.pos, 'value', text);
  }
  const mark = hit.mark;
  if (!mark) return tr;
  const body = text.length > 0 ? text : FILLABLE_EMPTY_CARET;
  const parts = body.split('\n');
  const nodes: PMNode[] = [];
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i] ?? '';
    if (part) {
      nodes.push(schema.text(part, [mark]));
    } else if (parts.length === 1) {
      nodes.push(schema.text(FILLABLE_EMPTY_CARET, [mark]));
    }
    if (i < parts.length - 1 && schema.nodes.hardBreak) {
      nodes.push(schema.nodes.hardBreak.create());
    }
  }
  if (!nodes.length) {
    nodes.push(schema.text(FILLABLE_EMPTY_CARET, [mark]));
  }
  return tr.replaceWith(hit.from, hit.to, Fragment.from(nodes));
}
