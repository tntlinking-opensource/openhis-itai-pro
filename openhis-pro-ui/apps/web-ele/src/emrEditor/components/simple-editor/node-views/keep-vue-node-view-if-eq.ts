import type { Node as PMNode } from '@tiptap/pm/model';

/**
 * Vue NodeView 默认在 decoration / getPos 刷新时整棵重绘。
 * 模板里 contenteditable 是空 span，重绘会清掉槽内文字和换页垫片；
 * 入院记录 50+ 岛时插入诊断会把主线程打满。
 * 仅当节点内容（含 attrs）真变了才 updateProps。
 */
export function keepVueNodeViewIfEq(props: {
  newNode: PMNode;
  oldNode: PMNode;
  updateProps: () => void;
}): boolean {
  if (props.oldNode.type !== props.newNode.type) return false;
  if (!props.oldNode.eq(props.newNode)) {
    props.updateProps();
  }
  return true;
}
