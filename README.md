# 汇率监控（SGD → MYR）

CIMB Clicks 与 Wise 的新币兑马币汇率监控 App。

## 得到 APK（不用装 Android Studio）
1. 在 GitHub 新建一个仓库，把本文件夹的全部内容上传（包含隐藏的 `.github` 文件夹）。
2. 打开仓库的 **Actions** 页 → **Build APK** → 等待变绿（约 3–6 分钟）。
3. 进入这次运行，在页面底部 **Artifacts** 下载 `SgdMyrMonitor-apk`，解压得到 `app-debug.apk`，传到手机安装（允许“安装未知应用”）。

之后每次推送代码都会自动重新编译；也可在 Actions 页点 **Run workflow** 手动触发。

## 首次使用
- 允许通知权限。
- 设置 → 后台运行 → “允许后台运行（关闭电池优化）”，否则手机休眠后无法每分钟检查。
- 部分手机（小米/OPPO/vivo/华为等）还需在系统设置里把本 App 设为“自启动”“无限制”。

## 本地编译（可选）
用 Android Studio 打开本文件夹，Build → Build APK。
