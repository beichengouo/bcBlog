export const GAME_PACKAGE_MAX_SIZE = 3 * 1024 ** 3
export function formatPackageSize(bytes) {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(2)} GiB`
  return `${(bytes / 1024 ** 2).toFixed(1)} MiB`
}
export function packageDownloadUrl(platform) {
  return ['android', 'pc'].includes(platform) ? `/api/portal/game-packages/${platform}/download` : ''
}
