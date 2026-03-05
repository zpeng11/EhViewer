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
5. 实现完成后需要参考 wsl-local-build-install.md 进行构建和安装测试。
6. 测试成功与否的结果由用户反馈，严厉禁止在用户反馈前擅自跳过测试。
7. 当用户反馈测试成功后，需要将将本次任务中进行的变更提交到代码库再进行下一任务。

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
- 阅读器长按菜单移除“刷新”和“查看原图”选项，默认假设资源均在本地可用。
- 走 `EhPageLoader`（本地优先）时，UI 仅提供“重试本地加载”和“隐藏”两个动作，并且完全避免尝试对远端资源执行 fetch。
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

- [x] `S1-DL-UI-01` 下线下载进度管理 UI 与下载任务控制（开始/暂停/恢复/重试/批量控制）  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/DownloadsScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/DownloadCard.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/ProgressScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/download/DownloadManager.kt`

- [x] `S1-DL-SET-01` 下载管理页设置瘦身为“仅本地浏览”：移除并发/延时/超时/预加载/原图下载等配置，移除恢复下载与冗余清理入口  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/DownloadScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/SettingsScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/Settings.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [x] `S1-READ-01` 阅读器长按菜单动作精简：移除“刷新/查看原图”，替换为“重试本地加载/隐藏/显示所有隐藏” ，创建隐藏和显示某些图片的 UI 逻辑
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/eu/kanade/tachiyomi/ui/reader/ReaderPageSheet.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [x] `S1-READ-02` 本地重试语义落地：`EhPageLoader` 重试仅触发本地源重读，不触发远端 URL 解析与图片下载  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/gallery/PageLoader.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/gallery/EhPageLoader.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/spider/SpiderQueen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/spider/SpiderDen.kt`

- [x] `S1-READ-03` 远端访问防护：为阅读链路增加“禁远端 fetch”保护开关/断言，避免回归时误触发网络分支  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/gallery/EhPageLoader.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/spider/SpiderQueen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/client/EhEngine.kt`

- [x] `S1-READ-04` 本地失败态交互：图像加载失败时提供“重试”和“隐藏”，并补充提示文案  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/reader/PagerItem.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/reader/ReaderScreen.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [x] `S1-SET-01` EH 专属设置页面瘦身：下线涉及网络的连接，账户和站点配置画廊和评论配置等，并清理入口，但保留缩略图翻译主题等配置
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/EhScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/UConfigScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/settings/MyTagsScreen.kt`

- [x] `S1-APP-01` 移除应用启动中的软件网络任务（更新检查、dailycheck），但保留日常使用的如tag拉取翻译等网络功能  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/EhApplication.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/SettingsCollector.kt`

- [x] `S1-MAN-01` 清理 Manifest 网络权限与站点 AppLink  
  文件域：
  - `/home/eleven/EhViewer/app/src/main/AndroidManifest.xml`

- [x] `S1-FAV-01` 恢复原有收藏功能并本地化：仅保留本地收藏能力；在“下载项目->长按”选项中新增“加入收藏/移出收藏”入口并与本地收藏状态联动
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/DownloadsScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/DownloadCard.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/CommonOperations.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/util/FavouriteStatusRouter.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/EhDB.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [x] `S1-FAV-02` 本地收藏数据模型升级：引入“额外收藏夹定义 + 额外归属”结构，保证单条收藏最多归属一个额外收藏夹，且额外收藏夹 ID 限定 1..9
  文件域：
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/model/**`
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/dao/**`
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/Database.kt`
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/DatabaseMigrations.kt`
  - `/home/eleven/EhViewer/core/data/schemas/com.ehviewer.core.database.EhDatabase/**`

- [ ] `S1-FAV-03` 收藏仓储与操作语义扩展：新增创建/删除/重命名额外收藏夹、批量分配到额外收藏夹、批量回归默认收藏夹的本地 API，并统一收藏状态路由
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/EhDB.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/CommonOperations.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/util/FavouriteStatusRouter.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/EhApplication.kt`

- [ ] `S1-FAV-04` 收藏页侧栏重构为“收藏夹入口 + 管理入口”：支持浏览本地收藏夹与额外收藏夹，支持创建/删除/重命名额外收藏夹并显示各夹计数
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/FavoritesScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/FavoritesViewModel.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/client/data/FavListUrlBuilder.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [ ] `S1-FAV-05` 收藏项可视化增强：在“本地收藏夹”视图中，对已归属额外收藏夹的条目展示额外收藏夹代号（1-9）徽标，帮助用户定位目标夹
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/GalleryInfo.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/GalleryInfoListItem.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/main/GalleryInfoGridItem.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [ ] `S1-FAV-06` 收藏页多选动作扩展：在所有收藏夹页面的多选工具栏新增爱心动作，支持“移动到指定额外收藏夹（1-9）”或“全部回归默认收藏夹（仅本地）”
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/FavoritesScreen.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/CommonOperations.kt`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/base/strings.xml`
  - `/home/eleven/EhViewer/core/i18n/src/commonMain/moko-resources/zh-rCN/strings.xml`

- [ ] `S1-FAV-07` 兼容与迁移策略：老版本本地收藏平滑迁移为“仅本地收藏、无额外归属”，删除额外收藏夹时成员自动回归默认收藏夹
  文件域：
  - `/home/eleven/EhViewer/core/data/src/commonMain/kotlin/com/ehviewer/core/database/DatabaseMigrations.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/EhDB.kt`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/ui/screen/FavoritesViewModel.kt`

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
- [ ] `S1-ACC-08` Reader 长按菜单不再出现“刷新/查看原图”；`EhPageLoader` 路径仅出现“重试本地加载/隐藏”。
- [ ] `S1-ACC-09` `EhPageLoader` 阅读流程在断网/飞行模式下不触发远端资源 fetch（以请求日志或抓包为准）。
- [ ] `S1-ACC-10` 收藏功能仅使用本地能力：Downloads 项长按可执行“加入收藏/移出收藏”，状态实时联动且断网/飞行模式下可用，不触发远端收藏请求。
- [ ] `S1-ACC-11` 额外收藏夹数量上限生效：最多可创建 9 个额外收藏夹，且逻辑代号稳定为 1..9。
- [ ] `S1-ACC-12` 被收藏条目必定出现在本地收藏夹；若分配到额外收藏夹，则同时出现在本地收藏夹和目标额外收藏夹。
- [ ] `S1-ACC-13` 单个收藏条目在任意时刻最多仅归属一个额外收藏夹，不允许同时出现在多个额外收藏夹。
- [ ] `S1-ACC-14` 收藏页左拉侧栏可完成额外收藏夹的进入/创建/删除/重命名，计数展示正确。
- [ ] `S1-ACC-15` 在本地收藏夹列表中，已归属额外收藏夹的条目显示对应代号（1-9）徽标，且代号与目标夹一致。
- [ ] `S1-ACC-16` 在任意收藏夹页面，多选爱心动作可批量分配到目标额外收藏夹，或批量回归默认收藏夹，结果实时联动。
- [ ] `S1-ACC-17` 删除额外收藏夹后，原归属条目自动回归默认收藏夹（本地收藏仍保留），断网/飞行模式下全流程可用且不触发远端请求。

---

## Step 2A：统一本地数据模型（文件夹 + archive）

### 目标与完成定义
- 列表条目不再依赖 `gid/token` 作为主身份。
- 本地库统一模型可同时承载“文件夹”和“archive”。
- Reader 可以从两类来源进入。
- 扫描同时覆盖两类输入：带下载结果 meta 的历史目录/archive，以及用户在指定 URI 根目录下自行组织的无 meta 纯文件夹相册。

### 新增接口与类型（必须先落地）
- [ ] `S2A-API-01` 新增 `LocalLibraryItem`（统一条目实体）
- [ ] `S2A-API-02` 新增 `LocalLibraryRepository`（扫描、分页、详情、进度、收藏）
- [ ] `S2A-API-03` 新增 `LocalScanPolicy`（根目录、递归策略、过滤规则、来源策略）

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
- `hasDownloadMeta`（是否存在下载结果 meta，可选）
- `scanRootId`（命中的扫描根标识，可选）

### 任务清单（文件域）
- [ ] `S2A-DATA-01` 新建本地库域目录与扫描器
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/model/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/repo/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/scanner/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/paging/**`

- [ ] `S2A-SCAN-01` 扫描器兼容无 meta 纯文件夹相册：在 `LocalScanPolicy` 指定 URI 根目录下发现目录即建档，按图片文件集合推导封面/数量/排序
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/scanner/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/repo/**`

- [ ] `S2A-SCAN-02` 建立混合来源去重与优先级策略（同路径同时命中下载 meta 与纯文件夹时，统一 ID 且优先使用 meta 字段）
  文件域：
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/model/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/repo/**`
  - `/home/eleven/EhViewer/app/src/main/kotlin/com/hippo/ehviewer/local/scanner/**`

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
- [ ] `S2A-ACC-06` 在配置的 URI 根目录下，用户自行组织的无 meta 纯文件夹相册可被扫描入库并展示。
- [ ] `S2A-ACC-07` 同一路径同时存在 meta 与无 meta 信息时去重稳定，且字段优先级符合策略（meta 优先，缺失字段可由扫描补齐）。

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
| 2026-02-28 23:06 | Step 1 | S1-DL-UI-01 | ~ | agent | Downloads 页面移除单条开始/暂停与批量开始/暂停/全量控制入口，仅保留本地浏览相关动作（删除/移动/重置阅读进度） | `prepareLibraryDefinitions*Release` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 12:06:39） | `ProgressScreen`/`DownloadManager` 本步未改动，避免跨任务 side effect，等待实机反馈后决定是否标记 x |
| 2026-02-28 23:10 | Step 1 | S1-DL-UI-01 | ~ | agent | DownloadCard 进一步移除实时下载速度/进度条，仅保留本地阅读信息；Downloads 控制入口保持下线 | `prepareLibraryDefinitions*Release` 成功；`assembleRelease` 在 `marshmallow/armeabi-v7a` 因 `AHardwareBuffer_*` 链接失败；`assembleDefaultRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 12:09:59） | 为避免跨任务引入 native side effect，本步未改动 `DownloadManager`/native 代码；等待实机反馈后决定是否标记 x |
| 2026-02-28 23:13 | Step 1 | S1-DL-UI-01 | x | agent | 用户完成实机门禁验证 | 下载控制入口下线校验通过（用户反馈 PASS） | 进入 S1-DL-SET-01 |
| 2026-02-28 23:19 | Step 1 | S1-DL-SET-01 | ~ | agent | Download 设置页瘦身为本地浏览：移除并发/延时/超时/预载/原图下载、元数据重载、恢复下载、冗余清理入口；仅保留下载路径与媒体扫描 | `prepareLibraryDefinitions*Release` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 12:19:09） | `Settings.kt` 保留下载参数键作为兼容配置（隐藏 UI，不改运行时），避免影响仍存留的阅读/蜘蛛逻辑 |
| 2026-02-28 23:26 | Step 1 | S1-DL-SET-01 | x | agent | 用户完成实机门禁验证 | 下载设置页瘦身校验通过（用户反馈 PASS） | 按要求本步提交后暂停，不继续下一项 |
| 2026-03-01 01:33 | Step 1 | S1-READ-01 | ~ | agent | 阅读器长按菜单移除“刷新/查看原图”，新增“重试本地加载/隐藏/显示所有隐藏”；基于 `PageStatus.Blocked` 增加手动隐藏/批量显示 UI 逻辑 | 本地代码改造完成，待 `prepareLibraryDefinitions*Release`、`assembleRelease` 与 ADB 安装验证 | 复用现有 Blocked 状态以降低改动面；后续 `S1-READ-04` 再细化失败态提示 |
| 2026-03-01 01:36 | Step 1 | S1-READ-01 | ~ | agent | 完成 release 构建并安装至实机（default universal） | `prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 14:36:24） | 等待用户实机验证菜单与隐藏/显示交互后再标记 x 并提交 |
| 2026-03-01 01:44 | Step 1 | S1-READ-01 | ~ | agent | 根据用户反馈重构隐藏语义：不再复用 Blocked，占位图移除；改为独立隐藏索引集合并仅渲染可见页，隐藏项从阅读序列中视觉移除；“显示所有隐藏图片”改为任意状态长按菜单均可出现（存在隐藏项时） | `prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 14:44:31） | 等待用户实机验证通过后标记 x 并提交 |
| 2026-03-01 01:50 | Step 1 | S1-READ-01 | x | agent | 用户完成实机门禁验证并确认隐藏语义符合预期（隐藏项从阅读序列移除，且“显示所有隐藏图片”在任意长按菜单可见） | 实机验证通过（用户反馈 PASS） | 按要求提交本步后进入 S1-READ-02 |
| 2026-03-01 01:53 | Step 1 | S1-READ-02 | ~ | agent | 启动“仅本地重试”改造：为阅读重试链路新增 `localOnly` 分支，命中本地源则重读，缺失则直接本地失败，不触发 pToken/页面解析/图片下载网络流程 | 进行中 | 目标是将网络访问防护限定在重试路径，避免影响正常首次读取和预取 |
| 2026-03-01 01:54 | Step 1 | S1-READ-02 | ~ | agent | 完成“仅本地重试”实现并构建安装实机（default universal） | `prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 14:54:12） | 等待用户实机验证重试路径不触发远端 fetch 后再标记 x 并提交 |
| 2026-03-01 02:01 | Step 1 | S1-READ-02 | ~ | agent | 修复隐藏后长按定位错位：Pager/Webtoon 长按回调改为绑定当前可见序列最新页对象，避免持有隐藏前旧引用 | `prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 15:01:26） | 等待用户复测长按定位与本地重试路径后统一标记 x 并提交 |
| 2026-03-01 02:17 | Step 1 | S1-READ-02 | x | agent | 用户确认 `S1-READ-02 PASS`：本地缺图场景下首次可能出现历史远端错误文案，点击“重试本地加载”后稳定转为本地读取失败；长按定位错位修复通过 | 实机验证通过（用户反馈 PASS） | 按要求提交本步后进入 S1-READ-03 |
| 2026-03-01 02:18 | Step 1 | S1-READ-03 | ~ | agent | 启动阅读链路禁远端防护：计划在 `EhPageLoader` 增加常量开关并默认阻断远端 fetch，同时在 `SpiderQueen`/`EhEngine` 增加断言防回归 | 进行中 | 实现后将验证首次加载也不再走远端分支 |
| 2026-03-01 02:21 | Step 1 | S1-READ-03 | ~ | agent | 完成“禁远端 fetch”防护实现并安装实机：`EhPageLoader` 默认本地优先阻断远端，`SpiderQueen` 增加 `remoteFetchAllowed` 防回归参数，`EhEngine` 增加断言方法 | `prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（versionName=1.14.6, versionCode=180063, lastUpdateTime=2026-03-01 15:21:36） | 等待用户实机验证首次加载路径不再触发远端 fetch 后再标记 x 并提交 |
| 2026-03-01 02:24 | Step 1 | S1-READ-03 | x | agent | 用户确认 `S1-READ-03 PASS`：阅读链路远端 fetch 防护生效 | 实机验证通过（用户反馈 PASS） | 按要求提交本步后进入 S1-READ-04 |
| 2026-03-01 02:25 | Step 1 | S1-READ-04 | ~ | agent | 启动失败态交互改造：错误卡片仅保留“重试本地加载/隐藏”并补充提示文案；隐藏操作复用当前阅读序列逻辑 | 进行中 | 完成后将构建 release 并上机验证 |
| 2026-03-01 02:28 | Step 1 | S1-READ-04 | ~ | agent | 完成失败态交互改造并安装实机：错误卡片新增“本地读取失败提示”，仅保留“重试本地加载/隐藏”；ReaderScreen 透传隐藏回调到 Pager/Webtoon，失败态也可直接隐藏当前页 | `prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（serial=192.168.2.93:34537，package=moe.tarsin.ehviewer，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-01 15:28:58） | 等待用户实机验证失败态交互与隐藏行为后再标记 x 并提交 |
| 2026-03-01 02:31 | Step 1 | S1-READ-04 | x | agent | 用户确认 `S1-READ-04 PASS`：失败态仅提供“重试本地加载/隐藏”，并可恢复“显示所有隐藏图片” | 实机验证通过（用户反馈 PASS） | 按要求提交本步后进入 S1-SET-01 |
| 2026-03-01 07:53 | Step 1 | S1-READ-03 | ~ | agent | 回归修复阅读预取链路：`EhPageLoader.prefetchPages` 透传 `localOnly=true` 且 `remoteFetchAllowed=false`；`SpiderQueen.preloadPages/updateRAList` 新增参数下沉，避免滑动/翻页预取误触远端 fetch | 按 `docs/wsl-local-build-install.md` 执行：`prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（serial=192.168.2.93:34537，package=moe.tarsin.ehviewer，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-01 20:52:35） | 待用户复测“滑动/翻页进入缺页页”路径，确认不再出现远端分支提示 |
| 2026-03-01 07:54 | Step 1 | S1-READ-04 | ~ | agent | 回归防护补强失败态文案：阅读链路启用禁远端模式时，`onPageFailure` 统一收敛为 `error_reading_failed`，避免透出“GP 不足”等远端错误文案 | 同版本安装包复测就绪（default universal release） | 待用户复测缺页场景，确认失败卡片文案稳定为“读取失败”且仅保留“重试本地加载/隐藏” |
| 2026-03-01 08:01 | Step 1 | S1-READ-03 | x | agent | 用户确认回归复测通过：滑动/翻页进入缺页页不再触发远端分支提示 | 实机验证通过（用户反馈 PASS） | 保留阅读链路禁远端断言，防止后续回归 |
| 2026-03-01 08:01 | Step 1 | S1-READ-04 | x | agent | 用户确认失败态文案符合预期：缺页场景稳定显示“读取失败”，并仅保留“重试本地加载/隐藏” | 实机验证通过（用户反馈 PASS） | 本次回归修复闭环完成 |
| 2026-03-01 18:52 | Step 1 | S1-SET-01 | ~ | agent | EH 设置页完成本地化瘦身：移除账户/站点/画廊评论/新闻等网络配置与入口；`UConfigScreen`/`MyTagsScreen` 降级为禁用静态页防止误触网；保留缩略图、阅读进度、标签翻译、主题与过滤配置 | 按 `docs/wsl-local-build-install.md` 执行：`prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（serial=192.168.2.93:36057，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-02 07:52:10） | 等待用户实机验证设置页瘦身结果后再标记 x 并提交 |
| 2026-03-01 18:57 | Step 1 | S1-SET-01 | x | agent | 用户确认 `S1-SET-01 PASS`，EH 设置页瘦身验收通过 | 实机验证通过（用户反馈 PASS） | 按流程提交本步后进入 S1-APP-01 |
| 2026-03-01 19:01 | Step 1 | S1-APP-01 | ~ | agent | 启动阶段网络任务瘦身：`EhApplication` 移除 `checkDawn` 启动触发，`SettingsCollector.updateWhenRequestNewsChanges` 改为 no-op；并补充移除 `MainActivity` 冷启动自动更新检查（`AppUpdater.checkForUpdate`）以满足“更新检查+dailycheck”目标，保留标签翻译自动更新 | 按 `docs/wsl-local-build-install.md` 执行：`prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（serial=192.168.2.93:36057，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-02 08:01:11） | 等待用户实机验证“启动不再自动更新检查/dailycheck”后再标记 x 并提交 |
| 2026-03-01 19:15 | Step 1 | S1-APP-01 | x | agent | 用户确认 `S1-APP-01 PASS`，启动网络任务下线验收通过 | 实机验证通过（用户反馈 PASS） | 按流程提交本步后进入 S1-MAN-01 |
| 2026-03-01 19:19 | Step 1 | S1-MAN-01 | ~ | agent | Manifest 清理站点 AppLink：移除 `e-hentai.org/exhentai.org` 的 `http/https` VIEW 过滤器；网络权限最小化：移除 `ACCESS_NETWORK_STATE`，并同步删除 `MainActivity` 的计费网络提示分支以避免无权限访问网络状态 | 按 `docs/wsl-local-build-install.md` 执行：`prepareLibraryDefinitionsDefaultRelease`/`prepareLibraryDefinitionsMarshmallowRelease` 成功，`assembleRelease` 成功，ADB 安装成功（serial=192.168.2.93:36057，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-02 08:19:20） | 等待用户实机验证“无站点 AppLink 与权限清理后主流程正常”后再标记 x 并提交 |
| 2026-03-01 21:18 | Step 1 | S1-MAN-01 | x | agent | 用户确认 `S1-MAN-01 PASS`，Manifest 权限与 AppLink 清理验收通过 | 实机验证通过（用户反馈 PASS） | 按流程提交本步后进入 S1-DEP-01 |
| 2026-03-04 20:14 | Step 1 | S1-FAV-01 | ~ | agent | 收藏逻辑本地化：`CommonOperations` 下线云端收藏分支，新增本地收藏 add/remove；Downloads 项长按新增“加入收藏/移出收藏 + 让我选择”动作；`DownloadCard` 增加收藏状态图标；`FavouriteStatusRouter`/`EhDB` 同步收藏名称与备注字段；补充本地收藏 i18n 文案 | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 09:14:29） | 等待用户实机验收 `S1-ACC-10`（长按收藏动作、状态联动、飞行模式/断网不触发远端收藏）后再标记 x 并提交 |
| 2026-03-04 20:23 | Step 1 | S1-FAV-01 | ~ | agent | 根据用户反馈补齐收藏浏览入口：主导航恢复“收藏”入口；`FavoritesScreen` 与 `FavoritesViewModel` 强制本地收藏模式（屏蔽云端分类与远端收藏操作），仅保留本地搜索/随机/删除/批量下载 | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 09:22:56） | 等待用户复测：可进入收藏页浏览既有本地收藏，且断网/飞行模式不触发云端收藏请求 |
| 2026-03-04 20:36 | Step 1 | S1-FAV-01 | ~ | agent | 根据用户交互反馈调整下载页收藏入口：长按仅进入多选；多选 FAB 新增“批量加入收藏/批量移出收藏”；批量操作按当前收藏状态差量执行，支持混合状态选择（仅对需变更项执行 add/remove） | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 09:35:52） | 等待用户复测多选收藏体验（含混合收藏状态）并确认 `S1-ACC-10` |
| 2026-03-04 20:45 | Step 1 | S1-FAV-01 | ~ | agent | 根据用户反馈同步收藏页入口与多选操作：`FavoritesScreen` 移除用户入口（Avatar）并移除多选 FAB 的“下载”动作，保持与当前“纯本地浏览”设计一致，仅保留全选/删除 | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 09:45:22） | 等待用户复测收藏页交互一致性，并确认 `S1-ACC-10` |
| 2026-03-04 20:50 | Step 1 | S1-FAV-01 | ~ | agent | 根据用户反馈修正收藏页多选语义：将多选栏“删除”替换为“移出收藏”动作（碎心图标），执行逻辑改为批量 `removeFromFavorites`，与下载页收藏语义一致 | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 09:50:22） | 等待用户复测收藏页多选操作语义，并确认 `S1-ACC-10` |
| 2026-03-04 20:55 | Step 1 | S1-FAV-01 | ~ | agent | 修复跨页面收藏状态同步：`FavouriteStatusRouter` 在写入 `EhDB` 后同步更新 `DownloadManager` 内存 `DownloadInfo` 的 `favoriteSlot/favoriteName/favoriteNote`，避免“收藏页移除后下载页红心残留” | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 09:55:32） | 等待用户复测：在“收藏”页移除后返回“下载”页，红心应同步取消 |
| 2026-03-04 21:02 | Step 1 | S1-FAV-01 | ~ | agent | 根据用户反馈增强收藏页浏览入口：`FavoritesScreen` 搜索栏右侧新增“列表/方格”切换按钮（与下载页图标语义一致），直接切换 `Settings.listMode` 以在收藏页即时切换列表/网格浏览 | `:app:compileDefaultDebugKotlin` 成功；`:app:assembleDebug` 失败（已知 native 链接错误：`AHardwareBuffer_*` / `ASharedMemory_*` 缺失，任务 `:app:buildCMakeDebug[arm64-v8a]-2`）；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 10:01:56） | 等待用户复测收藏页右上角列表/方格切换与浏览一致性 |
| 2026-03-04 21:10 | Step 1 | S1-FAV-01 | ~ | agent | 修复收藏通知丢事件导致的单向不同步：`FavouriteStatusRouter.notify` 从 `tryEmit` 改为挂起 `emit`，保证“收藏页移除收藏”事件不会因缓冲区满而丢失，从而稳定同步到下载页红心状态 | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 10:10:34） | 等待用户复测：在收藏页移除收藏后返回下载页，红心应同步消失 |
| 2026-03-04 21:18 | Step 1 | S1-FAV-01 | ~ | agent | 根据用户反馈对齐下载页与收藏页的多选动效：下载页网格（gallery）多选项补充 `CheckableItem` 包裹，显示打钩覆盖并复用同一交互源，确保勾选视觉反馈与动画表现一致 | `:app:compileDefaultDebugKotlin` 成功，`:app:assembleDebug` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 10:18:29） | 等待用户复测下载页网格多选：应出现与收藏页一致的打钩和勾选动画反馈 |
| 2026-03-04 21:22 | Step 1 | S1-FAV-01 | x | agent | 用户确认 `S1-FAV-01 PASS`，本地收藏功能与下载/收藏双页联动、多选收藏动作及下载页网格多选动效对齐验收通过 | 实机验证通过（用户反馈 PASS） | 按流程执行提交并推送远端 |
| 2026-03-04 23:41 | Step 1 | S1-FAV-02 | ~ | agent | 启动本地收藏数据模型升级：准备引入“额外收藏夹定义 + 额外归属”结构，约束额外收藏夹 ID 仅允许 1..9，并保持单条收藏最多一个额外归属 | 进行中 | 先完成 core/data 模型与数据库迁移，再执行构建安装与实机验收 |
| 2026-03-04 23:45 | Step 1 | S1-FAV-02 | ~ | agent | 完成 `core/data` 模型升级：新增 `LOCAL_FAVORITE_FOLDERS` 实体/DAO；`LOCAL_FAVORITES` 新增 `EXTRA_FAVORITE_SLOT`（可空）并扩展查询/更新接口；数据库版本升级到 24，新增 `Schema23to24` 清洗越界 slot | `:app:compileDefaultDebugKotlin` 成功；按 `docs/wsl-local-build-install.md` 执行 `prepareLibraryDefinitions*Release`、`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 12:44:52） | 安装阶段出现 incremental install not allowed，已自动回退 streamed install 并成功；等待用户实机验收后决定是否标记 x |
| 2026-03-05 01:27 | Step 1 | S1-FAV-02 | ~ | agent | 按用户要求重复执行 `docs/wsl-local-build-install.md` 构建安装流程：重跑 `prepareLibraryDefinitions*Release` 与 `assembleRelease -Prelease`，并重新安装 default universal release 包 | `prepareLibraryDefinitionsDefaultRelease/MarshmallowRelease` 成功，`assembleRelease -Prelease` 成功；ADB 安装成功（serial=192.168.2.93:43091，versionName=1.14.6，versionCode=180063，lastUpdateTime=2026-03-05 14:26:56） | 安装时 incremental install 被拒后自动回退 streamed install 并成功 |
| 2026-03-05 01:31 | Step 1 | S1-FAV-02 | x | agent | 用户确认 `S1-FAV-02 PASS`，本地收藏数据模型升级验收通过 | 实机验证通过（用户反馈 PASS） | 按流程提交本步后进入 S1-FAV-03 |
