# Aggressive Patch

## 功能简述

- **NBT压缩**：修改NBT压缩方式（NBT压缩位于主线程，会影响TPS）
- **TCP压缩**：为网络IO新增单向压缩：只压缩服务端 → 客户端的整条下行字节流

- **OC像素画针对优化**：针对性优化，可以降低50%+的NBT大小

> [!IMPORTANT]
> 优化就得激进，不激进叫什么优化，当然得确保没有BUG

---

## 安装

[![最新构建(java)](https://img.shields.io/github/actions/workflow/status/Rcrwrate/AggressivePatch/build-and-test.yml?logo=github&label=Build%20and%20test)](https://github.com/Rcrwrate/AggressivePatch/actions/workflows/build-and-test.yml)
[![最新发布](https://img.shields.io/github/v/release/Rcrwrate/AggressivePatch)](https://github.com/Rcrwrate/AggressivePatch/releases/latest)

![Docker Last Updated](https://img.shields.io/docker/last-updated/shirokasoke/mcwebapi?logo=docker&label=Env%20Last%20Updated)
![Docker size](https://img.shields.io/docker/image-size/shirokasoke/mcwebapi)
![Repo size](https://img.shields.io/github/repo-size/Rcrwrate/AggressivePatch)

目前状态：

在一定程度下向下兼容

| GTNH版本 | GTNHLib版本 | Hodgepodge版本 | OC版本(可无视) | zstd版本 | 最后版本 |
| --- | --- | --- | --- | --- | --- |
| 2.9.0-beta3 | 0.11.37 | 2.7.166 | 1.12.61 | [zstd-jni-1.5.7-16.jar](https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-16/zstd-jni-1.5.7-16.jar) | 0.3 |
| 2.9.0 | 0.11.54 | 2.7.221 | 1.12.68 | [zstd-jni-1.5.7-25.jar](https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-25/zstd-jni-1.5.7-25.jar) | 0.4 |

1. 前置依赖：**GTNHLib**、**Hodgepodge**
2. Zstd 库（点击上方下载），放置在mods文件夹下
3. 本mods

## 其他mod

[![](https://img.shields.io/github/v/release/Rcrwrate/MCWebAPI?logo=github&label=MCWebAPI)](https://github.com/Rcrwrate/MCWebAPI)
[![](https://img.shields.io/github/v/release/Rcrwrate/AggressivePatch?logo=github&label=AggressivePatch)](https://github.com/Rcrwrate/AggressivePatch)
[![](https://img.shields.io/github/v/release/Rcrwrate/CropsPower?logo=github&label=CropsPower)](https://github.com/Rcrwrate/CropsPower)

---

## 使用说明

### 命令

| 命令                  | 位置   | 权限        | 说明             |
| --------------------- | ------ | ----------- | ---------------- |
| `/aps` 或 `/aps show` | 服务端 | 4（OP）     | 打印当前压缩配置 |
| `/aps reload`         | 服务端 | 4（OP）     | 热重载配置       |
| `/apc` 或 `/apc show` | 客户端 | 0（所有人） | 打印当前压缩配置 |
| `/apc reload`         | 客户端 | 0（所有人） | 热重载配置       |

配置文件路径：`config/shirokasoke/AggressivePatch.cfg`。

### 生效范围与注意事项

- **离线模式服务器（`online-mode=false`）下 TCP 流压缩不生效**，NBT 层压缩正常
- **单人世界没有收益**：本地连接不经过真实 socket，不会被压缩（你为什么要单人模式用这个？）
- **服务器列表 ping 不受影响**，安装了本mod的服务端可以正常被未安装本 mod 的客户端 ping 到，但是客户端无法进入游戏
- **服务端与客户端必须同时安装，功能开关必须保持一致，其他配置可以不一致**，单端开启 = 对端收到乱码并断线
- 修改任意开关后需要**重启**；其余部分选项可用 `/aps reload` 热重载

---

## 配置选项

### `nbt`

> [!IMPORTANT]
> NBT压缩在主线程！最佳选择是不进行任何压缩，如果带宽不足，推荐妥协为使用zstd进行压缩

`compressLevel`：`0-22` = zstd 且该值为压缩级别；`23` = 不压缩（裸 NBT）；`24` = GZIP（GTNH默认方式，不变动）；`25` = 按 NBT 大小分档（见下）

| 选项                 | 默认   | 说明                                                   |
| -------------------- | ------ | ------------------------------------------------------ |
| `custom.smallLimit`  | `512`  | 序列化后 NBT 小于该字节数时，使用 `smallLevel`         |
| `custom.smallLevel`  | `23`   | 小 NBT 的编码方式，默认不压缩（小 NBT 压缩通常不划算） |
| `custom.medianLimit` | `4096` | 中 NBT 与 大 NBT 分界点                                |
| `custom.medianLevel` | `3`    | 中 NBT 的编码方式                                      |
| `custom.largeLevel`  | `7`    | 大 NBT 的编码方式                                      |

### `netty`

> [!TIP]
> 压缩是 CPU 密集型操作：`codecThreads` 推荐设为 CPU **物理核心数**-1（勿超过物理核心数）
>
> 玩家数不超过物理核心数时可设 `0`，直接在 Netty IO 线程上压缩。

| 选项                | 默认    | 说明                                                                                                                                                               |
| ------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `enabled`           | `true`  | 总开关，两端必须一致；修改需重启                                                                                                                                   |
| `compressLevel`     | `3`     | 出站压缩级别（`1-22`），越大越占 CPU                                                                                                                               |
| `blockSizeBytes`    | `65536` | 累积块大小（字节）。填满即压成一个独立压缩块，未填满的部分在下一次发送时吐出。块越大单次压缩率越高，但一般只对大块数据有额外收益，每个连接会常驻一个该大小的缓冲区 |
| `windowLog`         | `18`    | zstd 共享历史窗口（2 的幂，`10-27`）。越大对重复数据压缩率越高；两端每连接内存约 3 倍窗口大小                                                                      |
| `codecThreads`      | `0`     | `0` = 在 Netty IO 线程压缩；`> 0` = 独立压缩线程池大小（推荐 `2-4`）                                                                                               |
| `useStreamCompress` | `true`  | 是否使用流式压缩，压缩率更高                                                                                                                                       |

`codecThreads` 取舍：`0` 时压缩占用共享 Netty IO 线程，级别过高会拖慢同线程上的其他玩家（**默认级别因此只有 3**）；`> 0` 时可用空闲核心换更高级别而不影响他人延迟，代价是每次转发稍增延迟，线程池跟不上时数据会堆积在内存。

---

### 邪道用法（或者说，这才是本意）

直接禁用大NBT数据的压缩，然后通过Netty+线程池的高压缩补偿流量大小的膨胀

### `oc`

针对 OC 像素画的 NBT 优化

| 选项             | 默认    | 说明                                                                              |
| ---------------- | ------- | --------------------------------------------------------------------------------- |
| `enabled`        | `false` | 总开关                                                                            |
| `enabled_ASMFIX` | `true`  | 通过ASM后注入保持与源代码的一致(无需关心，如果存在问题再禁用)                     |
| `recover`        | `false` | **恢复模式**：停止写入压缩数据，并将存量数据恢复至未安装本 mod 之前的状态（见下） |

#### recover 恢复模式操作指南

> [!IMPORTANT]
> recover 用于**准备卸载本 mod**：把存档中已被压缩的数据全部还原，卸载后不留垃圾数据。开启需同时满足 `enabled = true`，修改后需重启
> 
> 恢复模式需要一定的时间，推荐在恢复模式下游玩一段时间，**未被加载的区块**中的数据不会被还原

推荐操作流程：

1. 配置 `enabled = true`、`recover = true`，重启服务器（启动加载的区块即被修复）
2. 正常游玩（游戏内存在玩家），让存在像素画的区块加载；隔段时间执行 `save-all` 或依赖自动保存（`save-all` 保存后会将 spawn 128 格外的区块强制卸载，再次进入时从磁盘重读、触发修复）
3. 启动时日志中不再出现 `Recovered ... compressed PrintData texture(s)` 即恢复完毕
4. 移除mod或者禁用OC优化功能

目前无法主动触发全量重存，恢复挂载在数据路径上

1. 停止写入新的压缩数据
2. 读取时就地还原（覆盖被读到的物品，随区块/玩家数据自然保存）
3. 区块从磁盘加载时扫描原始 NBT 并还原，随该区块下次保存

