# Aggressive Patch

Minecraft 1.7.10 / Forge 网络压缩补丁（GTNH 工具链）。

1.7.10 原版**没有任何网络压缩**（压缩是 1.8 才引入的）。本 mod 在两个层面把流量打下来：

- **NBT**：压缩 `PacketBuffer` 里传输的 NBT 数据（区块、TileEntity、物品 NBT 等）。
- **TCP**：压缩服务端 → 客户端的**整条下行字节流**（含包头与长度前缀）。

---

## 安装

[![最新构建(java)](https://img.shields.io/github/actions/workflow/status/Rcrwrate/AggressivePatch/build-and-test.yml?logo=github&label=Build%20and%20test)](https://github.com/Rcrwrate/AggressivePatch/actions/workflows/build-and-test.yml)
[![最新发布](https://img.shields.io/github/v/release/Rcrwrate/AggressivePatch)](https://github.com/Rcrwrate/AggressivePatch/releases/latest)

![Docker Last Updated](https://img.shields.io/docker/last-updated/shirokasoke/mcwebapi?logo=docker&label=Env%20Last%20Updated)
![Docker size](https://img.shields.io/docker/image-size/shirokasoke/mcwebapi)
![Repo size](https://img.shields.io/github/repo-size/Rcrwrate/AggressivePatch)

目前状态：

在一定程度下向下兼容

| GTNHLib版本 | Hodgepodge版本 | zstd版本 | 最后版本 |
| ----------- | -------------- | ----------- | -------- |
| 0.11.37 | 2.7.166 | [zstd-jni-1.5.7-16.jar](https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-16/zstd-jni-1.5.7-16.jar) | 0.1 |

1. 前置依赖：**GTNHLib**、**Hodgepodge**
2. Zstd 库（点击上方下载），放置在mods文件夹下
3. 本mods

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

- **离线模式服务器（`online-mode=false`）下 TCP 流压缩不生效**，下行流量保持未压缩；NBT 层压缩照常生效。
- **单人世界没有收益**：本地连接不经过真实 socket，不会被压缩。
- **服务器列表 ping 不受影响**，安装了本mod的服务端可以正常被未安装本 mod 的客户端 ping 到，但是客户端无法进入游戏。
- **服务端与客户端必须同时安装，功能开关必须保持一致，其他配置可以不一致**，单端开启 = 对端收到乱码并断线。
- 修改 `netty.enabled` 后需要**重启**；其余选项可用 `/aps reload` 热重载
  （`netty.codecThreads` 的线程池在首次压缩连接时创建一次，改动后同样需要重启）。

---

## 配置选项

### `nbt`

> [!IMPORTANT]
> NBT压缩在主线程！最佳选择是不进行任何压缩，如果带宽不足，推荐妥协为使用zstd进行压缩

`compressLevel`：`0-22` = zstd 且该值为压缩级别；`23` = 不压缩（裸 NBT）；`24` = GZIP（与原版线格式兼容）；`25` = 按 NBT 大小分档（见下）

| 选项                 | 默认   | 说明                                                   |
| -------------------- | ------ | ------------------------------------------------------ |
| `custom.smallLimit`  | `512`  | 序列化后 NBT 小于该字节数时，使用 `smallLevel`         |
| `custom.smallLevel`  | `23`   | 小 NBT 的编码方式，默认不压缩（小 NBT 压缩通常不划算） |
| `custom.medianLimit` | `4096` | 中 NBT 与 大 NBT 分界点                                |
| `custom.medianLevel` | `3`    | 中 NBT 的编码方式                                      |
| `custom.largeLevel`  | `7`    | 大 NBT 的编码方式                                      |

> `compressLevel = 24` 时本 mod 完全不介入 NBT 处理，交给 Hodgepodge 的原生实现。

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
