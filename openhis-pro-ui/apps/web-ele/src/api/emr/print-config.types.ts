/** 与后端 PrintConfig（《打印预览设计》§4.1）对齐 */

export interface PrintConfigPaperDto {
  widthMm?: number;
  heightMm?: number;
  orientation?: 'landscape' | 'portrait';
}

export interface PrintConfigMarginsDto {
  topMm?: number;
  rightMm?: number;
  bottomMm?: number;
  leftMm?: number;
}

export interface PrintConfigHeaderDto {
  enabled?: boolean;
  heightMm?: number;
  template?: string;
  firstPageDifferent?: boolean;
}

export interface PrintConfigFooterDto {
  enabled?: boolean;
  heightMm?: number;
  template?: string;
  showPageNumber?: boolean;
}

export interface PrintConfigWatermarkDto {
  enabled?: boolean;
  text?: string;
  opacity?: number;
  angle?: number;
  fontSizePt?: number;
}

export interface PrintConfigFontDto {
  family?: string;
  defaultSizePt?: number;
  titleSizePt?: number;
  lineHeight?: number;
}

export interface PrintConfigDto {
  paper?: PrintConfigPaperDto;
  margins?: PrintConfigMarginsDto;
  header?: PrintConfigHeaderDto;
  footer?: PrintConfigFooterDto;
  watermark?: PrintConfigWatermarkDto;
  font?: PrintConfigFontDto;
  dateFormat?: string;
  datetimeFormat?: string;
  showDeletedNodes?: boolean;
  blankPagePolicy?: string;
}
