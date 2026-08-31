import type { Node as PMNode } from '@tiptap/pm/model';

import { Extension } from '@tiptap/core';
import { Plugin, PluginKey, TextSelection } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';

import { resolveFillableAt } from '../../../fillable-field';

export const EMR_FIND_REPLACE_PLUGIN_KEY = new PluginKey<EmrFindReplacePluginState>(
  'emrFindReplace',
);

export interface EmrFindMatch {
  from: number;
  to: number;
}

export interface EmrFindReplacePluginState {
  searchTerm: string;
  caseSensitive: boolean;
  matches: EmrFindMatch[];
  currentIndex: number;
}

const defaultPluginState = (): EmrFindReplacePluginState => ({
  searchTerm: '',
  caseSensitive: false,
  matches: [],
  currentIndex: -1,
});

/** 匹配区间是否完全落在可填域正文内（固定模板下仅这些可替换） */
function matchInsideFillable(doc: PMNode, from: number, to: number): boolean {
  const hit = resolveFillableAt(doc, from) ?? resolveFillableAt(doc, Math.max(0, to - 1));
  if (!hit?.editable) return false;
  return from >= hit.innerFrom && to <= hit.innerTo;
}

/** 在 ProseMirror 文档中收集文本匹配区间（仅 text 节点） */
export function collectEmrFindMatches(
  doc: PMNode,
  searchTerm: string,
  caseSensitive: boolean,
): EmrFindMatch[] {
  const term = searchTerm.trim();
  if (!term) return [];
  const needle = caseSensitive ? term : term.toLowerCase();
  const matches: EmrFindMatch[] = [];
  doc.descendants((node, pos) => {
    if (!node.isText || !node.text) return;
    const haystack = caseSensitive ? node.text : node.text.toLowerCase();
    let start = 0;
    let index = haystack.indexOf(needle, start);
    while (index !== -1) {
      matches.push({ from: pos + index, to: pos + index + term.length });
      start = index + Math.max(term.length, 1);
      index = haystack.indexOf(needle, start);
    }
  });
  return matches;
}

function syncFindReplaceStorage(
  editor: { storage: { emrFindReplace?: EmrFindReplaceStorage } },
  state: EmrFindReplacePluginState,
) {
  const bucket = editor.storage.emrFindReplace;
  if (!bucket) return;
  bucket.searchTerm = state.searchTerm;
  bucket.caseSensitive = state.caseSensitive;
  bucket.matches = state.matches;
  bucket.currentIndex = state.currentIndex;
  bucket.matchCount = state.matches.length;
}

export interface EmrFindReplaceStorage {
  searchTerm: string;
  caseSensitive: boolean;
  matches: EmrFindMatch[];
  currentIndex: number;
  matchCount: number;
}

function selectCurrentMatch(
  editor: {
    chain: () => {
      focus: () => {
        setTextSelection: (range: { from: number; to: number }) => {
          scrollIntoView: () => { run: () => boolean };
        };
      };
    };
  },
  state: EmrFindReplacePluginState,
) {
  const m = state.matches[state.currentIndex];
  if (state.currentIndex < 0 || !m) return;
  editor.chain().focus().setTextSelection({ from: m.from, to: m.to }).scrollIntoView().run();
}

function buildFindState(
  doc: PMNode,
  searchTerm: string,
  caseSensitive: boolean,
  currentIndex?: number,
): EmrFindReplacePluginState {
  const matches = collectEmrFindMatches(doc, searchTerm, caseSensitive);
  let idx = currentIndex ?? (matches.length > 0 ? 0 : -1);
  if (matches.length === 0) {
    idx = -1;
  } else if (idx < 0) {
    idx = 0;
  } else if (idx >= matches.length) {
    idx = matches.length - 1;
  }
  return { searchTerm, caseSensitive, matches, currentIndex: idx };
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrFindReplace: {
      emrFind: (searchTerm: string, options?: { caseSensitive?: boolean }) => ReturnType;
      emrFindNext: () => ReturnType;
      emrFindPrevious: () => ReturnType;
      emrReplaceCurrent: (replaceWith: string) => ReturnType;
      emrReplaceAll: (replaceWith: string) => ReturnType;
      emrClearFind: () => ReturnType;
    };
  }
  interface Storage {
    emrFindReplace?: EmrFindReplaceStorage;
  }
}

export const EmrFindReplace = Extension.create({
  name: 'emrFindReplace',

  addStorage() {
    return {
      searchTerm: '',
      caseSensitive: false,
      matches: [] as EmrFindMatch[],
      currentIndex: -1,
      matchCount: 0,
    } satisfies EmrFindReplaceStorage;
  },

  addCommands() {
    return {
      emrFind:
        (searchTerm, options) =>
        ({ editor, dispatch }) => {
          const prev = EMR_FIND_REPLACE_PLUGIN_KEY.getState(editor.state);
          const caseSensitive = options?.caseSensitive ?? prev?.caseSensitive ?? false;
          const next = buildFindState(editor.state.doc, searchTerm, caseSensitive);
          if (dispatch) {
            const tr = editor.state.tr.setMeta(EMR_FIND_REPLACE_PLUGIN_KEY, next);
            editor.view.dispatch(tr);
            selectCurrentMatch(editor, next);
          }
          syncFindReplaceStorage(editor, next);
          return true;
        },

      emrFindNext:
        () =>
        ({ editor, dispatch }) => {
          const prev = EMR_FIND_REPLACE_PLUGIN_KEY.getState(editor.state) ?? defaultPluginState();
          if (!prev.matches.length) return false;
          const currentIndex = (prev.currentIndex + 1) % prev.matches.length;
          const next = { ...prev, currentIndex };
          if (dispatch) {
            const tr = editor.state.tr.setMeta(EMR_FIND_REPLACE_PLUGIN_KEY, next);
            editor.view.dispatch(tr);
            selectCurrentMatch(editor, next);
          }
          syncFindReplaceStorage(editor, next);
          return true;
        },

      emrFindPrevious:
        () =>
        ({ editor, dispatch }) => {
          const prev = EMR_FIND_REPLACE_PLUGIN_KEY.getState(editor.state) ?? defaultPluginState();
          if (!prev.matches.length) return false;
          const currentIndex = (prev.currentIndex - 1 + prev.matches.length) % prev.matches.length;
          const next = { ...prev, currentIndex };
          if (dispatch) {
            const tr = editor.state.tr.setMeta(EMR_FIND_REPLACE_PLUGIN_KEY, next);
            editor.view.dispatch(tr);
            selectCurrentMatch(editor, next);
          }
          syncFindReplaceStorage(editor, next);
          return true;
        },

      emrReplaceCurrent:
        (replaceWith) =>
        ({ editor, dispatch }) => {
          if (!editor.isEditable) return false;
          const prev = EMR_FIND_REPLACE_PLUGIN_KEY.getState(editor.state) ?? defaultPluginState();
          const m = prev.matches[prev.currentIndex];
          if (prev.currentIndex < 0 || !m) return false;
          const locked = Boolean(editor.storage.emrContentLock?.enabled);
          if (locked && !matchInsideFillable(editor.state.doc, m.from, m.to)) {
            return false;
          }
          if (dispatch) {
            let tr = editor.state.tr.insertText(replaceWith, m.from, m.to);
            const next = buildFindState(
              tr.doc,
              prev.searchTerm,
              prev.caseSensitive,
              prev.currentIndex,
            );
            tr = tr.setMeta(EMR_FIND_REPLACE_PLUGIN_KEY, next);
            editor.view.dispatch(tr);
            selectCurrentMatch(editor, next);
            syncFindReplaceStorage(editor, next);
          }
          return true;
        },

      emrReplaceAll:
        (replaceWith) =>
        ({ editor, dispatch }) => {
          if (!editor.isEditable) return false;
          const prev = EMR_FIND_REPLACE_PLUGIN_KEY.getState(editor.state) ?? defaultPluginState();
          if (!prev.matches.length) return false;
          const locked = Boolean(editor.storage.emrContentLock?.enabled);
          const targets = locked
            ? prev.matches.filter((m) => matchInsideFillable(editor.state.doc, m.from, m.to))
            : prev.matches;
          if (!targets.length) return false;
          if (dispatch) {
            let tr = editor.state.tr;
            const sorted = [...targets].sort((a, b) => b.from - a.from);
            for (const m of sorted) {
              tr = tr.insertText(replaceWith, m.from, m.to);
            }
            const next = buildFindState(tr.doc, prev.searchTerm, prev.caseSensitive, -1);
            tr = tr.setMeta(EMR_FIND_REPLACE_PLUGIN_KEY, next);
            editor.view.dispatch(tr);
            syncFindReplaceStorage(editor, next);
          }
          return true;
        },

      emrClearFind:
        () =>
        ({ editor, dispatch }) => {
          const next = defaultPluginState();
          if (dispatch) {
            const tr = editor.state.tr.setMeta(EMR_FIND_REPLACE_PLUGIN_KEY, next);
            editor.view.dispatch(tr);
          }
          syncFindReplaceStorage(editor, next);
          return true;
        },
    };
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: EMR_FIND_REPLACE_PLUGIN_KEY,
        state: {
          init: () => defaultPluginState(),
          apply(tr, value) {
            const meta = tr.getMeta(EMR_FIND_REPLACE_PLUGIN_KEY) as
              | EmrFindReplacePluginState
              | undefined;
            if (meta !== undefined) {
              return meta;
            }
            if (value.searchTerm && tr.docChanged) {
              const matches = collectEmrFindMatches(tr.doc, value.searchTerm, value.caseSensitive);
              let currentIndex = value.currentIndex;
              if (!matches.length) {
                currentIndex = -1;
              } else if (currentIndex < 0) {
                currentIndex = 0;
              } else if (currentIndex >= matches.length) {
                currentIndex = matches.length - 1;
              } else {
                const sel = tr.selection;
                if (sel instanceof TextSelection && !sel.empty) {
                  const hit = matches.findIndex((m) => m.from === sel.from && m.to === sel.to);
                  if (hit >= 0) currentIndex = hit;
                }
              }
              return { ...value, matches, currentIndex };
            }
            return value;
          },
        },
        props: {
          decorations(state) {
            const pluginState = EMR_FIND_REPLACE_PLUGIN_KEY.getState(state);
            if (!pluginState?.matches.length) {
              return DecorationSet.empty;
            }
            const decorations = pluginState.matches.map((m, index) =>
              Decoration.inline(m.from, m.to, {
                class:
                  index === pluginState.currentIndex
                    ? 'emr-find-result emr-find-result--current'
                    : 'emr-find-result',
              }),
            );
            return DecorationSet.create(state.doc, decorations);
          },
        },
      }),
    ];
  },
});
