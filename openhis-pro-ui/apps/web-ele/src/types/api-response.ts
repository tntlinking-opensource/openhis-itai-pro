/**
 * 标准 API 响应结构
 * 所有接口返回的统一格式
 */
export interface ApiResponse<T> {
  /** 状态码，200 表示成功 */
  code: number;
  /** 响应消息 */
  msg: string;
  /** 响应数据 */
  data: T;
}

export interface ApiOptionItem {
  label: string;
  value: string;
  sort?: number;
}
/** 分页请求字段：pageNo 为 1-based（与后端 AbstractPageQuery / PagedResult 一致）。 */
export interface PageApiRequest {
  pageNo: number;
  pageSize: number;
  totalCount: number;
  totalElements: number;
}

/** 分页列表响应：pageNo 为当前页（1-based），非 Spring Page.number。
 * 行数据字段优先为 {@code content}；兼容旧字段 {@code records}。
 * 若网关/应用在标准 {@code data} 下再包一层 {@code content} 指向分页块，见 {@link parsePagedRowsFromApiData}。
 */
export interface PageApiResponse<T> extends PageApiRequest {
  content: T[];
}

export interface PageParams {
  /** 页码，从 1 开始 */
  pageNo: number;
  pageSize: number;
}

export type GetInfoParams = Partial<{
  busNo: string;
  id: string;
  ids: string[];
}>;
