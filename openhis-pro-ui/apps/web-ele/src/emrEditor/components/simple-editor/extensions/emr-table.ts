import { Table, TableCell } from '@tiptap/extension-table';

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

/** 扩展 Table：支持 attrs.name / attrs.id */
export const EmrTable = Table.extend({
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
