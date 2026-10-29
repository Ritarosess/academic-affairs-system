import request from './request'

/** 学生本人学业红线预警 */
export function fetchMyWarning() {
  return request.get('/student/warning')
}

/** 管理员：全校学业预警名单（分页） */
export function fetchWarningList(params) {
  return request.get('/admin/warnings', { params })
}

/** 管理员：学业预警总览（各等级人数与生效阈值） */
export function fetchWarningSummary() {
  return request.get('/admin/warnings/summary')
}

/** 管理员：邮件内容预览（发送前核对收件人与文案） */
export function fetchWarningPreview(studentId) {
  return request.get(`/admin/warnings/preview/${studentId}`)
}

/** 管理员：批量发送学业预警邮件通知 */
export function notifyWarnings(studentIds) {
  return request.post('/admin/warnings/notify', { studentIds })
}

/** 管理员：邮件发送历史 */
export function fetchNotifications(params) {
  return request.get('/admin/warnings/notifications', { params })
}

/** 管理员：邮件自检 —— 向本人邮箱发一封测试邮件，返回 SMTP 配置摘要与错误详情 */
export function sendTestMail() {
  return request.post('/admin/warnings/test-mail')
}

/** 管理员：当前 SMTP 配置摘要（不含密码） */
export function fetchMailConfig() {
  return request.get('/admin/warnings/mail-config')
}
