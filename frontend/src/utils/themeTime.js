// Fixed UTC+8, independent of the visitor's device timezone.
export function beijingTheme(date = new Date()) {
  const hour = (date.getUTCHours() + 8) % 24
  return hour >= 7 && hour < 19 ? 'light' : 'dark'
}
