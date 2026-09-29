/**
 * Skill P0 页眉侧车：住院 vs 门诊分流
 *
 * 住院：院名 + {documentTitle} + float 姓名/住院号；正文 patientHeader 去掉姓名/住院号行
 * 门诊：院名 + 原 formTitle 文案（如「门诊病历」）；页眉无姓名/住院号；正文 patientHeader 保留姓名
 */
import type { JSONContent } from '@tiptap/core';

import {
  buildInpatientChromeHeaderTemplate,
  extractChromeLogoSrc,
} from '../components/simple-editor/emr-page-chrome';

export type PageChromeScope = 'inpatient' | 'outpatient';

export interface EnsurePageChromeOptions {
  /** 文书类型编码，如 OUTPATIENT_RECORD */
  documentTypeCode?: string;
  /** 显式指定；未传时由 documentTypeCode / 正文 formTitle 推断 */
  scope?: PageChromeScope;
}

const OUTPATIENT_TYPE_CODES = new Set(['OUTPATIENT_RECORD', 'OUTPATIENT_EMR']);

export function resolvePageChromeScope(
  doc: JSONContent,
  options?: EnsurePageChromeOptions,
): PageChromeScope {
  if (options?.scope) return options.scope;
  const code = String(options?.documentTypeCode ?? '').trim();
  if (OUTPATIENT_TYPE_CODES.has(code)) return 'outpatient';
  const formTitle = extractFormTitleText(Array.isArray(doc.content) ? doc.content : []);
  if (formTitle && /门诊/.test(formTitle)) return 'outpatient';
  return 'inpatient';
}

function walkParagraphText(node: JSONContent, out: string[]): void {
  if (node.type === 'text') out.push(String(node.text ?? ''));
  for (const c of node.content ?? []) walkParagraphText(c, out);
}

/** 迁移前从 formTitle 段提取固定标题文案（如「门诊病历」），勿用运行时 {documentTitle} */
export function extractFormTitleText(content: JSONContent[]): string | null {
  for (const n of content) {
    const name = String(n.attrs?.name ?? '');
    if (name !== 'formTitle' && !isLikelyFormTitle(n)) continue;
    if (n.type !== 'paragraph') continue;
    const texts: string[] = [];
    walkParagraphText(n, texts);
    const t = texts.join('').replace(/\s+/g, '').trim();
    if (t) return t;
  }
  return null;
}

function walkFindLogo(nodes: JSONContent[] | undefined): string | null {
  for (const n of nodes ?? []) {
    if (n.type === 'image' && n.attrs?.title === 'hospitalLogo') return String(n.attrs.src ?? '');
    const nested = walkFindLogo(n.content);
    if (nested) return nested;
  }
  return null;
}

function collectFieldIds(node: JSONContent, ids: string[]): void {
  if (node.type === 'emrDataElement') ids.push(String(node.attrs?.elementId ?? ''));
  if (node.type === 'emrMetadataChip') ids.push(String(node.attrs?.fieldKey ?? ''));
  for (const c of node.content ?? []) collectFieldIds(c, ids);
}

export function isLikelyHospitalTitle(n: JSONContent): boolean {
  if (n.type !== 'paragraph') return false;
  if (String(n.attrs?.name ?? '') === 'hospitalTitle') return true;
  if (String(n.attrs?.textAlign ?? '') !== 'center') return false;
  let hasLogo = false;
  let hasOrg = false;
  const walk = (node: JSONContent) => {
    if (node.type === 'image' && node.attrs?.title === 'hospitalLogo') hasLogo = true;
    if (node.type === 'emrDataElement' && node.attrs?.elementId === 'orgName') hasOrg = true;
    if (node.type === 'emrMetadataChip' && node.attrs?.fieldKey === 'orgName') hasOrg = true;
    for (const c of node.content ?? []) walk(c);
  };
  walk(n);
  return hasLogo || hasOrg;
}

export function isLikelyFormTitle(n: JSONContent): boolean {
  if (String(n.attrs?.name ?? '') === 'formTitle') return true;
  if (n.type !== 'paragraph') return false;
  if (String(n.attrs?.textAlign ?? '') !== 'center') return false;
  const texts: string[] = [];
  walkParagraphText(n, texts);
  const t = texts.join('').replace(/\s+/g, '');
  return /记录|小结|告知|知情|讨论|交接|粘贴|病历/.test(t) && t.length <= 24;
}

function buildInpatientHeaderTemplate(logoSrc: string | null, existingTpl: string): string {
  const src =
    logoSrc?.trim() ||
    extractChromeLogoSrc(existingTpl) ||
    '';
  if (!src) return existingTpl;
  return buildInpatientChromeHeaderTemplate(src);
}

function buildOutpatientHeaderTemplate(
  logoSrc: string | null,
  existingTpl: string,
  formTitleLiteral: string,
): string {
  const imgPart = logoSrc
    ? `{{emrImg w=39:${logoSrc}}}`
    : existingTpl.match(/\{\{emrImg[^}]+\}\}/)?.[0] ?? '';
  const nl = '\n';
  const title = formTitleLiteral || '门诊病历';
  return (
    `${imgPart}<span style="font-size:22pt;font-weight:700">{orgName}</span>${nl}` +
    `<span style="font-size:16pt">${title}</span>`
  );
}

export function ensurePageChromeP0(
  doc: JSONContent & Record<string, unknown>,
  options?: EnsurePageChromeOptions,
): JSONContent & Record<string, unknown> {
  const content = Array.isArray(doc.content) ? [...doc.content] : [];
  const scope = resolvePageChromeScope(doc, options);
  const formTitleLiteral = extractFormTitleText(content) ?? '';

  const logoSrc = walkFindLogo(content);

  doc.content = content.filter((n) => {
    const name = String(n.attrs?.name ?? '');
    // Skill §B：假页眉 + pageHeaderHint 一律移出正文
    if (
      name === 'hospitalTitle' ||
      name === 'formTitle' ||
      name === 'pageHeader' ||
      name === 'pageHeaderHint'
    ) {
      return false;
    }
    if (isLikelyHospitalTitle(n) || isLikelyFormTitle(n)) return false;
    return true;
  });

  doc.content = (doc.content as JSONContent[]).map((n) => {
    if (n.type !== 'table') return n;
    const tname = String(n.attrs?.name ?? '');
    if (tname !== 'patientHeader' && tname !== 'patientBar') return n;
    const rows = (n.content ?? []).map((row) => {
      if (row.type !== 'tableRow') return row;
      const cells = (row.content ?? []).filter((cell) => {
        const ids: string[] = [];
        collectFieldIds(cell, ids);
        if (scope === 'outpatient') {
          return !ids.includes('inpatientNo');
        }
        // 住院：页眉已有姓名/住院号，patientHeader/patientBar 去掉重复列
        return !ids.includes('patientName') && !ids.includes('inpatientNo');
      });
      return { ...row, content: cells };
    });
    return { ...n, content: rows };
  });

  const chrome = (doc.emrPageChrome ?? {}) as Record<string, unknown>;
  const header = (chrome.header ?? {}) as Record<string, unknown>;
  const existingTpl = String(header.template ?? '');

  const needChrome =
    scope === 'outpatient'
      ? !existingTpl ||
        existingTpl.includes('{documentTitle}') ||
        existingTpl.includes('{patientName}') ||
        existingTpl.includes('{inpatientNo}') ||
        existingTpl.includes('住院号') ||
        existingTpl.includes('float:left') ||
        (logoSrc && !existingTpl.includes('emrImg'))
      : !existingTpl ||
        !existingTpl.includes('{documentTitle}') ||
        !existingTpl.includes('float:left') ||
        !existingTpl.includes('{wardName}') ||
        !existingTpl.includes('{bedNo}') ||
        (logoSrc && !existingTpl.includes('emrImg'));

  if (needChrome) {
    header.template =
      scope === 'outpatient'
        ? buildOutpatientHeaderTemplate(logoSrc, existingTpl, formTitleLiteral)
        : buildInpatientHeaderTemplate(logoSrc, existingTpl);
  } else if (scope === 'outpatient' && formTitleLiteral && existingTpl.includes('{documentTitle}')) {
    header.template = buildOutpatientHeaderTemplate(logoSrc, existingTpl, formTitleLiteral);
  }

  header.enabled = true;
  header.align = header.align ?? 'center';
  header.heightMm = 0;
  chrome.header = header;
  const footer = (chrome.footer ?? {}) as Record<string, unknown>;
  footer.enabled = true;
  footer.align = footer.align ?? 'center';
  footer.heightMm = footer.heightMm ?? 12;
  footer.template = footer.template || '第 {page} 页 (共 {total} 页)';
  chrome.footer = footer;
  doc.emrPageChrome = chrome;
  if (!doc.emrPageMargins) {
    doc.emrPageMargins = { top: '25.4mm', right: '31.8mm', bottom: '25.4mm', left: '31.8mm' };
  }
  return doc;
}

/** 精确匹配 attrs.name，避免 pageHeaderHint 误命中 pageHeader */
function hasNamedBlock(content: string, name: string): boolean {
  return new RegExp(`"name"\\s*:\\s*"${name}"`).test(content);
}

function collectIdsInNode(node: JSONContent, ids: string[]): void {
  collectFieldIds(node, ids);
}

/** 仅检查 patientBar 表内是否仍挂 patientName/inpatientNo（勿用全文子串） */
function patientBarStillHasIdentityFields(content: string): boolean {
  if (!hasNamedBlock(content, 'patientBar')) return false;
  try {
    const doc = JSON.parse(content) as JSONContent;
    for (const n of doc.content ?? []) {
      if (n.type !== 'table' || String(n.attrs?.name ?? '') !== 'patientBar') continue;
      const ids: string[] = [];
      collectIdsInNode(n, ids);
      if (ids.includes('patientName') || ids.includes('inpatientNo')) return true;
    }
  } catch {
    return false;
  }
  return false;
}

/** 门诊/住院统一的 dirty 判定（scope 感知） */
export function isEmrContentDirty(content: string, documentTypeCode?: string): boolean {
  const isOutpatient = OUTPATIENT_TYPE_CODES.has(String(documentTypeCode ?? ''));
  const legacyNodes =
    content.includes('"type":"emrDataElement"') &&
    !content.includes('"type":"emrMetadataChip"') &&
    !content.includes('"type":"emrFreeText"');
  const fakeHeader =
    hasNamedBlock(content, 'hospitalTitle') ||
    hasNamedBlock(content, 'formTitle') ||
    hasNamedBlock(content, 'pageHeader') ||
    hasNamedBlock(content, 'pageHeaderHint');

  if (isOutpatient) {
    return (
      fakeHeader ||
      !content.includes('emrPageChrome') ||
      content.includes('{documentTitle}') ||
      content.includes('{inpatientNo}') ||
      content.includes('住院号：{inpatientNo}') ||
      content.includes('float:left') ||
      legacyNodes ||
      (content.includes('"name":"patientHeader"') &&
        !content.includes('"fieldKey":"patientName"') &&
        !content.includes('"elementId":"patientName"'))
    );
  }

  return (
    fakeHeader ||
    !content.includes('emrPageChrome') ||
    !content.includes('{documentTitle}') ||
    !content.includes('float:left') ||
    !content.includes('{wardName}') ||
    !content.includes('{bedNo}') ||
    patientBarStillHasIdentityFields(content) ||
    legacyNodes
  );
}
