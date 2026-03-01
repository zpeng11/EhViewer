# EhViewer 本地化改造计划（可追踪执行版）

## 文档用途
本文件用于指导 agent 按阶段执行改造，并在执行过程中进行步骤跟踪。  
执行者必须在每个任务完成时更新勾选状态和执行日志。

## 跟踪规则

### 状态约定
- `[ ]` 未开始
- `[~]` 进行中（手动写入，不是 Markdown 原生语法）
- `[x]` 已完成
- `[!]` 阻塞（需在执行日志写明阻塞原因）

### 执行要求
1. 每次开始一个任务时，把该任务改为 `[~]`。
2. 任务完成后改为 `[x]`，并在“执行日志”追加一条记录。
3. 若失败或阻塞，改为 `[!]`，并记录失败命令、报错摘要、下一步建议。
4. 不允许跨阶段跳做，除非前置依赖已在日志中明确满足。

### 日志模板
```md
| 日期时间 | 阶段 | 任务ID | 状态 | 执行者 | 变更摘要 | 验证结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-02-28 18:00 | Step X | SX-XXX-YY | x | agent | 示例变更摘要 | 示例验证结果 | 示例备注 |
```

---

## 总体目标
将项目从站点浏览器改造为纯本地相册/图库浏览器，分 3 个阶段推进：
1. Step 1：硬切离线，仅保留本地下载库浏览 + 阅读器。
2. Step 2A：引入统一本地数据模型，条目来源覆盖文件夹和 archive。
3. Step 2B：去卡片信息化，UI 完全转为本地语义。

---

## Step 1：纯本地下载库浏览器（硬切离线）

### 目标与完成定义
- 应用不再依赖登录、远程列表、远程详情、远程评论、远程搜索。
- 启动默认进入本地下载库页面。
- 图库浏览中查看原图不读取远程数据
- 本地 archive 打开和阅读链路完整可用，但保持翻译和标签等网络功能。
- 下线所有下载进度管理 UI（开始、暂停、恢复、重试等）与下载任务控制入口。
- 下载管理页仅保留“本地浏览”相关设置，移除下载配置、恢复下载、冗余清理等下载运维功能。

### 任务清单（文件域）
- [x] `S1-NAV-01` 精简主导航，仅保留 Downloads/History/Settings  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/MainActivity.kt`

- [x] `S1-NAV-02` 移除站点 URL 路由与对应跳转入口  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/MainNav.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/MainActivity.kt`

- [x] `S1-UI-01` 下线远程页面与登录页面引用（先移引用再删文件）  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/GalleryListScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/GalleryListViewModel.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/GalleryDetailScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/GalleryDetailContent.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/GalleryCommentsScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/ProgressScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/ImageSearchScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/login/SignInScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/login/WebViewSignInScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/login/PostLogin.kt`

- [ ] `S1-DL-UI-01` 下线下载进度管理 UI 与下载任务控制（开始/暂停/恢复/重试/批量控制）  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/DownloadsScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/DownloadCard.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/ProgressScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/download/DownloadManager.kt`

- [ ] `S1-DL-SET-01` 下载管理页设置瘦身为“仅本地浏览”：移除并发/延时/超时/预加载/原图下载等配置，移除恢复下载与冗余清理入口  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/DownloadScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/SettingsScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/Settings.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [ ] `S1-SET-01` 下线 EH 专属设置页面中涉及网络的连接，账户和站点配置画廊和评论配置等，并清理入口  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/EhScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/UConfigScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/MyTagsScreen.kt`

- [ ] `S1-APP-01` 移除应用启动中的软件网络任务（更新检查、dailycheck），但保留日常使用的如tag拉取翻译等网络功能  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/EhApplication.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/SettingsCollector.kt`

- [ ] `S1-MAN-01` 清理 Manifest 网络权限与站点 AppLink  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/AndroidManifest.xml`

### 任务清单（依赖域）
- [ ] `S1-DEP-01` 移除不再需要的依赖并修复编译
  文件域：
  - `/home/eleven/EhViewer/app/build.gradle.kts`
  目标依赖：
  - `androidx.work.runtime`
  - `androidx.browser`
  - `androidx.webkit`
  - `ktor.client.okhttp`（仅在彻底断网后移除）

- [ ] `S1-DEL-01` 删除远程模块目录（确认无引用后）
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/client/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ktor/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/dailycheck/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/updater/**`

### 验收清单
- [ ] `S1-ACC-01` 冷启动直接到 Downloads。
- [ ] `S1-ACC-02` 不出现登录页面与站点入口。
- [ ] `S1-ACC-03` 飞行模式下主流程可用。
- [ ] `S1-ACC-04` file/content 打开 archive 可进入 Reader 并翻页。
- [ ] `S1-ACC-05` `./gradlew :app:assembleDebug` 通过。
- [ ] `S1-ACC-06` Downloads/Progress 页面不再出现下载控制动作（开始/暂停/恢复/重试/批量启动等）。
- [ ] `S1-ACC-07` 下载设置页仅保留本地浏览相关项，不再包含下载配置、恢复下载、冗余清理功能。

---

## Step 2A：统一本地数据模型（文件夹 + archive）

### 目标与完成定义
- 列表条目不再依赖 `gid/token` 作为主身份。
- 本地库统一模型可同时承载“文件夹”和“archive”。
- Reader 可以从两类来源进入。

### 新增接口与类型（必须先落地）
- [ ] `S2A-API-01` 新增 `LocalLibraryItem`（统一条目实体）
- [ ] `S2A-API-02` 新增 `LocalLibraryRepository`（扫描、分页、详情、进度、收藏）
- [ ] `S2A-API-03` 新增 `LocalScanPolicy`（根目录、递归策略、过滤规则）

建议字段基线：
- `id`
- `sourceType` (`FOLDER | ARCHIVE`)
- `uriOrPath`
- `displayName`
- `coverUri`
- `itemCount`
- `lastModified`
- `progress`
- `favorite`
- `sizeBytes`（可选）

### 任务清单（文件域）
- [ ] `S2A-DATA-01` 新建本地库域目录与扫描器
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/model/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/repo/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/scanner/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/paging/**`

- [ ] `S2A-READ-01` 新增目录阅读加载器 `FolderPageLoader`
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/gallery/FolderPageLoader.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/reader/ReaderScreen.kt`

- [ ] `S2A-SCR-01` 将 DownloadsScreen 的数据源适配为本地库统一模型（可先兼容旧下载表）
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/DownloadsScreen.kt`

- [ ] `S2A-DB-01` 新增本地索引表/DAO（如 `LOCAL_LIBRARY`）与迁移
  文件域：
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/model/**`
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/dao/**`
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/Database.kt`
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/DatabaseMigrations.kt`

- [ ] `S2A-MIG-01` 旧数据迁移：`DownloadInfo -> LocalLibraryItem`，继承阅读进度

### 验收清单
- [ ] `S2A-ACC-01` 同一列表可展示文件夹与 archive 条目。
- [ ] `S2A-ACC-02` Reader 从两类条目进入均可正常阅读。
- [ ] `S2A-ACC-03` 进度写回并重启恢复正确。
- [ ] `S2A-ACC-04` 首次全扫与二次增量扫描可区分耗时。
- [ ] `S2A-ACC-05` `./gradlew :app:assembleDebug` 通过。

---

## Step 2B：去卡片信息化（纯本地条目 UI）

### 目标与完成定义
- 去掉所有站点语义字段和交互。
- 列表/详情只展示本地语义（路径、数量、时间、大小、进度）。

### 任务清单（文件域）
- [ ] `S2B-UI-01` 列表项替换为本地条目组件
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/GalleryInfoListItem.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/GalleryInfoGridItem.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/GalleryInfo.kt`
  新增建议：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/LocalItemListCard.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/LocalItemGridCard.kt`

- [ ] `S2B-UI-02` 新增本地详情页并替代远程详情语义
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/LocalDetailScreen.kt`

- [ ] `S2B-OPS-01` 清理通用操作中的远程收藏/远程动作
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/CommonOperations.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/GalleryInfoBottomSheet.kt`

- [ ] `S2B-RES-01` i18n 文案清理与本地语义补齐
  文件域：
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`
  - 其他语言资源文件（按构建错误补齐）

### 验收清单
- [ ] `S2B-ACC-01` UI 无站点术语（EH、gallery token、comments、torrent 等）。
- [ ] `S2B-ACC-02` 列表字段全部来自本地模型。
- [ ] `S2B-ACC-03` 滚动/进出阅读/返回手势流畅性不回退。
- [ ] `S2B-ACC-04` `./gradlew :app:assembleDebug` 通过。

---

## 阶段门禁（Gate）

### Gate 1（进入 Step 2A 前）
- [ ] Step 1 所有 `S1-ACC-*` 已完成
- [ ] 搜索无残留：`EhEngine|EhUrl|SignIn|gallerySite|requestNews`

### Gate 2（进入 Step 2B 前）
- [ ] Step 2A 所有 `S2A-ACC-*` 已完成
- [ ] Reader 支持 `FOLDER` 和 `ARCHIVE` 双源

### Gate 3（收尾）
- [ ] Step 2B 所有 `S2B-ACC-*` 已完成
- [ ] 关键路径回归记录已写入执行日志

---

## 执行日志
| 日期时间 | 阶段 | 任务ID | 状态 | 执行者 | 变更摘要 | 验证结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-02-28 17:45 | Step 1 | S1-NAV-01 | ~ | agent | MainActivity 导航精简为 Downloads/History/Settings，启动页改为 Downloads | `prepareLibraryDefinitions*Release` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063） | 等待实机反馈后决定是否标记 x |
| 2026-02-28 17:48 | Step 1 | S1-NAV-01 | x | agent | 用户完成实机门禁验证 | 冷启动与导航项校验通过（用户反馈 PASS） | 进入 S1-NAV-02 |
| 2026-02-28 17:50 | Step 1 | S1-NAV-02 | ~ | agent | 移除 MainActivity 的站点 URL/SEND 跳转入口，MainNav 的 navWithUrl 降级为 no-op | `prepareLibraryDefinitions*Release` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063） | 等待实机反馈后决定是否标记 x |
| 2026-02-28 17:54 | Step 1 | S1-NAV-02 | x | agent | 用户完成实机门禁验证 | 站点 URL 与分享入口下线校验通过（用户反馈 PASS） | 进入 S1-UI-01 |
| 2026-02-28 17:57 | Step 1 | S1-UI-01 | ~ | agent | MainNav 引用改为本地 Reader/Downloads 路径，SearchBar 移除 ImageSearch 入口，MainActivity 保持 file/content 本地入口 | `prepareLibraryDefinitions*Release` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063） | 等待实机反馈后决定是否标记 x |
| 2026-02-28 18:04 | Step 1 | S1-UI-01 | x | agent | 用户完成实机门禁验证 | 登录/远程入口下线与本地阅读入口校验通过（用户反馈 PASS） | 按要求在每个 PASS 后执行 commit |
