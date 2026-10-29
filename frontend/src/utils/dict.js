/**
 * 字典与格式化工具，选项与后端契约保持一致。
 */

/** 课程性质 */
export const COURSE_TYPE_OPTIONS = [
  { label: '必修', value: '必修' },
  { label: '选修', value: '选修' },
  { label: '限选', value: '限选' }
]

/** 性别 */
export const GENDER_OPTIONS = [
  { label: '男', value: '男' },
  { label: '女', value: '女' }
]

/** 角色 */
export const ROLE_OPTIONS = [
  { label: '管理员', value: 'ADMIN' },
  { label: '教师', value: 'TEACHER' },
  { label: '学生', value: 'STUDENT' }
]

/** 角色中文标签映射 */
export const ROLE_LABELS = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生'
}

/** 用户状态 */
export const STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]

/** 星期（1~7） */
export const WEEKDAY_OPTIONS = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 }
]

/** 节次（1~12） */
export const SECTION_OPTIONS = Array.from({ length: 12 }, (_, i) => ({
  label: `第${i + 1}节`,
  value: i + 1
}))

/** 星期标签 */
export function formatWeekday(weekday) {
  const found = WEEKDAY_OPTIONS.find((item) => item.value === Number(weekday))
  return found ? found.label : '-'
}

/** 课程性质标签类型 */
export function courseTypeTag(type) {
  if (type === '必修') return 'danger'
  if (type === '限选') return 'warning'
  return 'success'
}

/** 状态标签类型 */
export function statusTag(status) {
  return Number(status) === 1 ? 'success' : 'info'
}

/** 状态文本 */
export function statusText(status) {
  return Number(status) === 1 ? '启用' : '停用'
}

/** 课程审核状态选项（value 为空表示全部） */
export const COURSE_AUDIT_OPTIONS = [
  { label: '全部', value: '' },
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' }
]

/** 课程审核状态标签类型 */
export function courseAuditTag(status) {
  if (status === 'PENDING') return 'warning'
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

/** 课程审核状态文本 */
export function courseAuditText(status) {
  return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }[status] || status || '-'
}

/** 拼接上课时间地点 */
export function formatSchedule(row) {
  if (!row) return '-'
  const day = row.weekdayLabel || formatWeekday(row.weekday)
  const sections =
    row.startSection != null && row.endSection != null
      ? `第${row.startSection}-${row.endSection}节`
      : '-'
  const location = row.location ? ` ${row.location}` : ''
  return `${day} ${sections}${location}`
}

/* ------------------------------------------------------------------ *
 * 学业红线预警
 * ------------------------------------------------------------------ */

/** 预警等级筛选项（value 为空表示只看已触发预警的学生） */
export const WARNING_LEVEL_OPTIONS = [
  { label: '仅已预警', value: '' },
  { label: '红色（学业红线）', value: 'RED' },
  { label: '橙色（学业预警）', value: 'ORANGE' },
  { label: '黄色（学业关注）', value: 'YELLOW' },
  { label: '学业正常', value: 'NORMAL' },
  { label: '全部学生', value: 'ALL' }
]

/** 等级对应的强调色（用于 el-tag 的 color 属性，保证三档颜色可区分） */
export const WARNING_LEVEL_COLORS = {
  RED: '#f56c6c',
  ORANGE: '#e6862c',
  YELLOW: '#d9a404',
  NORMAL: '#67c23a'
}

/** 等级短标签 */
export const WARNING_LEVEL_SHORT = {
  RED: '红色预警',
  ORANGE: '橙色预警',
  YELLOW: '黄色预警',
  NORMAL: '正常'
}

export function warningColor(level) {
  return WARNING_LEVEL_COLORS[level] || '#909399'
}

export function warningShortLabel(level) {
  return WARNING_LEVEL_SHORT[level] || level || '-'
}

/** 等级对应的 el-alert 类型 */
export function warningAlertType(level) {
  if (level === 'RED') return 'error'
  if (level === 'ORANGE') return 'warning'
  if (level === 'YELLOW') return 'warning'
  return 'success'
}

/** 等级数值（用于排序展示） */
export function warningSeverity(level) {
  return { RED: 3, ORANGE: 2, YELLOW: 1, NORMAL: 0 }[level] ?? 0
}

