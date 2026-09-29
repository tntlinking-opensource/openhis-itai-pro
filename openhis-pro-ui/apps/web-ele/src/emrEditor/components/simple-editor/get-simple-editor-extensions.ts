import type { Extensions } from '@tiptap/core';

import Color from '@tiptap/extension-color';
import Highlight from '@tiptap/extension-highlight';
import Link from '@tiptap/extension-link';
import Placeholder from '@tiptap/extension-placeholder';
import Subscript from '@tiptap/extension-subscript';
import Superscript from '@tiptap/extension-superscript';
import { TableRow } from '@tiptap/extension-table';
import Underline from '@tiptap/extension-underline';
import TaskItem from '@tiptap/extension-task-item';
import TaskList from '@tiptap/extension-task-list';
import TextAlign from '@tiptap/extension-text-align';
import { TextStyle } from '@tiptap/extension-text-style';
import { FontFamily } from '@tiptap/extension-text-style/font-family';
import { FontSize } from '@tiptap/extension-text-style/font-size';
import StarterKit from '@tiptap/starter-kit';

import { EmrBlockLayout } from './extensions/emr-block-layout';
import { EmrCheckboxField } from './extensions/emr-checkbox-field';
import { EmrDataElement } from './extensions/emr-data-element';
import { EmrFindReplace } from './extensions/emr-find-replace';
import { EmrFixedText } from './extensions/emr-fixed-text';
import { EmrHorizontalDashed } from './extensions/emr-horizontal-dashed';
import { EmrImage } from './extensions/emr-image';
import { EmrPageBreak } from './extensions/emr-page-break';
import { EmrPagination } from './extensions/emr-pagination';
import { EmrSelectField } from './extensions/emr-select-field';
import { EmrStyledHorizontalRule } from './extensions/emr-styled-horizontal-rule';
import { EmrTableNavigation } from './extensions/emr-table-navigation';
import { EmrTextField } from './extensions/emr-text-field';
import { EmrTable, EmrTableCell, EmrTableHeader } from './extensions/emr-table';
import { EmrToc } from './extensions/emr-toc';
import { EmrWordlikePaste } from './extensions/emr-wordlike-paste';
import { EmrFillableFieldKit } from '../../fillable-field';

/**
 * 与官方 Simple Editor 模板对齐的基础扩展集（MIT 开源扩展）。
 * @see https://tiptap.dev/docs/ui-components/templates/simple-editor
 */
export function getSimpleEditorExtensions(options?: { placeholder?: string }): Extensions {
  return [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      horizontalRule: false,
    }),
    TaskList,
    TaskItem.configure({ nested: true }),
    Link.configure({ openOnClick: false, autolink: true }),
    Underline,
    // inline + 拖拽缩放；院徽可与医院名称同一段落紧挨显示
    EmrImage,
    Placeholder.configure({
      /**
       * 仅整篇为空时显示（空白 Tab 引导文案）。
       * 已打开模板/病历中的空段落不得再显示「请选择左侧模板…」。
       */
      placeholder: ({ editor }) => {
        if (!editor.isEmpty) return '';
        return options?.placeholder ?? '开始输入…';
      },
      showOnlyWhenEditable: true,
    }),
    TextStyle,
    FontFamily.configure({ types: ['textStyle'] }),
    FontSize.configure({ types: ['textStyle'] }),
    EmrTable.configure({
      resizable: true,
      // 末列可拖；表宽由 CSS/协议锁 100%，不会把纸撑破
      lastColumnResizable: true,
    }),
    TableRow,
    EmrTableHeader,
    EmrTableCell,
    EmrBlockLayout,
    EmrFindReplace,
    Color,
    Highlight.configure({ multicolor: true }),
    Superscript,
    Subscript,
    TextAlign.configure({
      types: ['heading', 'paragraph'],
      alignments: ['left', 'center', 'right', 'justify'],
      defaultAlignment: 'left',
    }),
    EmrDataElement,
    EmrFillableFieldKit,
    EmrWordlikePaste,
    EmrTableNavigation,
    EmrFixedText,
    EmrPageBreak,
    EmrPagination,
    EmrHorizontalDashed,
    EmrStyledHorizontalRule,
    EmrToc,
    EmrTextField,
    EmrCheckboxField,
    EmrSelectField,
  ];
}
