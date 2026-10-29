import request from './request'

/** 管理员：获取邮件（SMTP）配置（授权码脱敏） */
export function fetchMailSettings() {
  return request.get('/admin/mail-config')
}

/** 管理员：保存邮件配置（password 留空表示不修改） */
export function saveMailSettings(data) {
  return request.put('/admin/mail-config', data)
}

/** 管理员：发送测试邮件；to 留空则发给当前登录管理员邮箱 */
export function sendMailTest(to) {
  return request.post('/admin/mail-config/test', { to: to || '' })
}
