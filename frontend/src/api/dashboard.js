import request from './request'

/** 按当前角色返回工作台统计卡片数据 */
export function fetchDashboardSummary() {
  return request.get('/dashboard/summary')
}
