/**
 * 可填域数据模型与序列化边界（存盘 / 打开文档）。
 * 实现仍在 simple-editor/emr-fillable-json，此处为套件对外模型出口。
 */
export {
  EMR_FILLABLE_MARK,
  FILLABLE_EMPTY_CARET,
  FILLABLE_EMPTY_CARET_LEGACY,
  FILLABLE_ZWSP,
  buildFillableInsertJson,
  buildFillableNodeInsertJson,
  createFillableSlotId,
  flattenFillableNodesToMarks,
  flattenFillableMarksToNodes,
  hoistFillableAtomContent,
  normalizeFillableDocForEdit,
  isFillableSlotEmptyText,
  normalizeFillableMarkAttrs,
  plainTextToFillableMarkJson,
  serializeFillableMarksToNodes,
  stripFillablePlaceholders,
  stripFillableZwsp,
  type EmrFillableKind,
  type EmrFillableMarkAttrs,
} from '../../components/simple-editor/emr-fillable-json';

export { promoteDataElementValuesToContent } from '../../components/simple-editor/emr-data-element-text';
