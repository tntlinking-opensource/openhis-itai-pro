import type { Extensions } from '@tiptap/core';

import { Extension } from '@tiptap/core';

import { EmrFillableKeymap } from '../../components/simple-editor/extensions/emr-fillable-keymap';
import { EmrFillableIslandGuard } from './emr-fillable-island-guard';
import { EmrInlineFieldPmArrow } from '../../components/simple-editor/extensions/emr-inline-field-pm-arrow';
import { EmrFreeText } from '../../components/simple-editor/extensions/emr-free-text';
import { EmrMetadataChip } from '../../components/simple-editor/extensions/emr-metadata-chip';
import { EmrWordlikeOps } from '../../components/simple-editor/extensions/emr-wordlike-ops';

export { EMR_SKIP_FILLABLE_RESTORE_META, FILLABLE_KIT_META } from './meta';

export interface RegisterEmrFillableFieldKitOptions {
  /** 预留：模板页 vs 文书页差异配置 */
  surface?: 'document' | 'template';
}

/**
 * 可填域套件（方案 B：atom 节点 + NodeView 岛屿，无 PM 正文/IME 补丁）。
 */
export function registerEmrFillableFieldKit(
  _options?: RegisterEmrFillableFieldKitOptions,
): Extensions {
  return [
    EmrFreeText,
    EmrMetadataChip,
    EmrFillableIslandGuard,
    EmrFillableKeymap,
    EmrInlineFieldPmArrow,
    EmrWordlikeOps,
  ];
}

/**
 * Tiptap 单一入口扩展：atom 可填节点 + Tab 键位 + 岛屿方向键 + Word 化操作。
 */
export const EmrFillableFieldKit = Extension.create<RegisterEmrFillableFieldKitOptions>({
  name: 'emrFillableFieldKit',

  addExtensions() {
    return registerEmrFillableFieldKit();
  },
});
