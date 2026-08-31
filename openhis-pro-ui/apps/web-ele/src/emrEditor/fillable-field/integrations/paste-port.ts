import type { Node as PMNode } from '@tiptap/pm/model';

import {
  findNearestEditableFillable,
  resolveFillableAt,
  snapCaretInFillable,
} from '../core/resolve';

export interface PasteFillableContext {
  doc: PMNode;
  pos: number;
  sameTextblockOnly?: boolean;
}

/** 粘贴落点是否在可编辑可填域内 */
export function resolvePasteTargetFillable(ctx: PasteFillableContext) {
  return (
    resolveFillableAt(ctx.doc, ctx.pos) ??
    findNearestEditableFillable(ctx.doc, ctx.pos, {
      sameTextblockOnly: ctx.sameTextblockOnly ?? true,
    })
  );
}

export function snapPasteCaretInFillable(
  hit: NonNullable<ReturnType<typeof resolvePasteTargetFillable>>,
  pos: number,
): number {
  return snapCaretInFillable(hit, pos);
}
