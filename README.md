# VivoLocator - 澎湃 OS 风格 vivo 亲友定位器

这是一个专为小米澎湃 OS（HyperOS）用户打造的安卓应用源码，能够自动抓取 vivo 云服务（`cloud.vivo.com`）上的设备定位信息。

## 功能特点
1. **澎湃 OS 极简设计**：高光大圆角卡片、平滑动画与系统级调色。
2. **防封 IP 机制**：采用 50 秒 ~ 70 秒**随机浮动时间间隔**自动轮询，模拟真实用户刷新，极大地降低被云端封禁 IP 的风险。
3. **手动刷新按钮**：提供即时刷新操作。
4. **内置登录 Cookie 保持**：内嵌 WebView 完成首次登录后，凭证自动保存。

## 使用 GitHub Actions 云端自动编译 APK
1. 将本项目解压后上传至你的 GitHub 仓库（Main 分支）。
2. 点击仓库页面的 **Actions** 标签。
3. 自动触发构建任务（Build Android APK）。
4. 编译完成后，在 Actions 任务详情底部的 **Artifacts** 处即可直接下载 `VivoLocator-HyperOS-Debug.apk` 并安装到小米手机！