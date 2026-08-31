/**
 * EmrFillableFieldKit — 可填域（元数据/自由文本）唯一对外入口。
 *
 * 外部模块（分页、页眉、SimpleEditor、content-lock、paste）只允许 import 本文件。
 * 禁止直接 import fillable-field/core/* 或 emr-wordlike-ops-helpers。
 */

import type { JSONContent } from '@tiptap/core';

import {
  flattenFillableNodesToMarks,
  flattenFillableMarksToNodes,
  normalizeFillableDocForEdit,
  promoteDataElementValuesToContent,
  serializeFillableMarksToNodes,
} from './core/model';

// --- 模型 / 序列化 ---
export {
  EMR_FILLABLE_MARK,
  FILLABLE_EMPTY_CARET,
  FILLABLE_ZWSP,
  buildFillableInsertJson,
  buildFillableNodeInsertJson,
  flattenFillableNodesToMarks,
  flattenFillableMarksToNodes,
  normalizeFillableDocForEdit,
  plainTextToFillableMarkJson,
  promoteDataElementValuesToContent,
  serializeFillableMarksToNodes,
  stripFillableZwsp,
  type EmrFillableKind,
  type EmrFillableMarkAttrs,
} from './core/model';

// --- 只读查询（content-lock / paste / 校验可用） ---
export {
  collectAllFillableHits,
  collectEditableFillableContentRanges,
  collectFillableMarkRanges,
  findNearestEditableFillable,
  isEditableFillableNode,
  isFillableHitEmpty,
  liftPosOutOfFillableField,
  replaceFillableHitPlainText,
  resolveFillableAt,
  resolveFillableAtExclusiveEnd,
  selectionTouchesNonFillableText,
  snapCaretInFillable,
  type EmrFillableHit,
} from './core/resolve';

// --- 光标策略 ---
export {
  applyFillableCaretPolicy,
  fillableCaretAfterInsertCommand,
  shouldSnapClickToEmptyFillable,
  type FillableCaretPolicy,
} from './runtime/caret-policy';

// --- 插入命令（工具栏 / 拖拽） ---
export {
  buildFillableInsertJsonFromDrop,
  fillableCaretAfterDropCommand,
  type InsertFillableFromDropAttrs,
} from './commands/insert-from-drop';
export { buildInsertEmrFreeTextChainSteps } from './commands/insert-free-text';
export {
  resolveInsertEmrMetadataChip,
  type InsertEmrMetadataChipAttrs,
} from './commands/insert-metadata';

// --- 集成窄接口 ---
export {
  collectEditableFillableRangesInSelection,
  doesSelectionTouchLockedPlainText,
  isRangeInsideEditableFillable,
  isSelectionInsideEditableFillableContent,
} from './integrations/content-lock-port';
export { resolvePasteTargetFillable, snapPasteCaretInFillable } from './integrations/paste-port';

// --- IME（中文输入法 + 可填域） ---
export {
  getFillableImeStorage,
  isFillableImeActive,
  isImeSideEffectBlocked,
  type EmrImeControllerStorage,
  type EmrImePhase,
} from './runtime/ime-controller';
export { settleFillableImeAfterDocChange } from './runtime/ime-settle';
export {
  EMR_SKIP_FILLABLE_RESTORE_META,
  EmrFillableFieldKit,
  FILLABLE_KIT_META,
  registerEmrFillableFieldKit,
  type RegisterEmrFillableFieldKitOptions,
} from './kit/register-fillable-kit';

/** @deprecated 请改用 applyFillableCaretPolicy / fillableCaretAfterInsertCommand */
export { selectInsideFillableSlot, selectOutsideFillableSlot } from './core/resolve';

/** 打开文档：节点 + 兼容 mark 迁移 */
export function normalizeEmrDocForEdit(doc: unknown) {
  return normalizeFillableDocForEdit(
    promoteDataElementValuesToContent(doc as import('@tiptap/core').JSONContent),
  );
}

/** 存盘：mark 写回节点 */
export function serializeEmrDocForSave(doc: JSONContent) {
  return serializeFillableMarksToNodes(doc);
}
