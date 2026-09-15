import { Table, TableCell, TableView } from '@tiptap/extension-table';
import type { Node as ProseMirrorNode } from '@tiptap/pm/model';

/** 表格/单元格 data-name、data-id，供按名称取值与 PDF 定位 */
const emrNameAttr = {
  default: null as null | string,
  parseHTML: (element: HTMLElement) => element.getAttribute('data-name'),
  renderHTML: (attributes: { name?: null | string }) => {
    if (!attributes.name) {
      return {};
    }
    return { 'data-name': attributes.name };
  },
};

const emrIdAttr = {
  default: null as null | string,
  parseHTML: (element: HTMLElement) => element.getAttribute('data-id'),
  renderHTML: (attributes: { id?: null | string }) => {
    if (!attributes.id) {
      return {};
    }
    return { 'data-id': attributes.id };
  },
};

/** TableView 自己建 table 节点，renderHTML 的 data-name 不会落到 DOM，须在 View 里回写 */
function syncEmrTableDomAttrs(table: HTMLTableElement, node: ProseMirrorNode) {
  const name = typeof node.attrs.name === 'string' ? node.attrs.name : '';
  const id = typeof node.attrs.id === 'string' ? node.attrs.id : '';
  if (name) {
    table.setAttribute('data-name', name);
  } else {
    table.removeAttribute('data-name');
  }
  if (id) {
    table.setAttribute('data-id', id);
  } else {
    table.removeAttribute('data-id');
  }
}

class EmrTableView extends TableView {
  constructor(node: ProseMirrorNode, cellMinWidth: number) {
    super(node, cellMinWidth);
    syncEmrTableDomAttrs(this.table, node);
  }

  update(node: ProseMirrorNode) {
    const ok = super.update(node);
    if (ok) {
      syncEmrTableDomAttrs(this.table, node);
    }
    return ok;
  }
}

/** 扩展 Table：支持 attrs.name / attrs.id，并写到真实 table DOM */
export const EmrTable = Table.extend({
  addOptions() {
    return {
      ...this.parent?.(),
      View: EmrTableView,
    };
  },
  addAttributes() {
    return {
      ...this.parent?.(),
      name: emrNameAttr,
      id: emrIdAttr,
    };
  },
});

/** 扩展 TableCell：支持 attrs.name（按名单元格） */
export const EmrTableCell = TableCell.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      name: emrNameAttr,
    };
  },
});
