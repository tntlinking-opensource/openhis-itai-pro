/** 与 ProseMirror Mark.toJSON() 结构一致，用于固定字节点内嵌样式 */
export interface EmrFixedTextMarkJSON {
  attrs?: Record<string, unknown>;
  type: string;
}

/** 一段连续相同 marks 的文本 */
export interface EmrFixedTextSegmentJSON {
  marks: EmrFixedTextMarkJSON[];
  text: string;
}
