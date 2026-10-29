import request from './request'

/** 登录 */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 当前用户档案 */
export function getProfile() {
  return request.get('/auth/me')
}

/** 修改本人密码 */
export function changePassword(data) {
  return request.post('/auth/change-password', data)
}

/** 修改本人邮箱（学业预警通知的收件地址），返回最新 ProfileVO */
export function updateMyEmail(email) {
  return request.put('/auth/email', { email })
}
