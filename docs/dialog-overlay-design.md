# 弹窗 / 浮层（Dialog / Popup）设计

> 状态：**P1/P2 已实现，P3 验收、打磨、正确性与接入阻塞修复已完成**（P1 `19c291a1`、P2 `1149eb06`、P3 验收 `164872d1`、P3 打磨 `bb61db22`、正确性 `fed2ecda`、原点修复 `b1256722`，2026-10-23 `main`）；仅剩 dim/invert 与非 modal 点击外部关闭两个设计项，见 §9/§11。
> 本文描述 API、分层方案、输入/焦点语义与测试口径；实现要点见 `docs/refactor-progress.md` §5 的 L7.39~L7.45。
> 关联待办：`docs/todo.md` §15 P3「弹窗 / 浮层」。

## 0. 结论摘要

- 现状是**单根节点 + 单一 cell 画布**：没有 Popup、Dialog、zIndex，也没有「最上层先拿输入」的概念。
- 三个候选方案中，**推荐方案 C（混合）**：
  - 用 `OverlayHost` 这个组合件负责 base/overlay 的测量、放置、绘制顺序；
  - 在**组合根**增加一个输入门控（layer 栈 + modal 屏障），由 `MosaicNode` 的 key/mouse/paste 派发入口咨询它；
  - 不在每个输入组件里加 `LocalInputEnabled` 判断，也不改帧循环。
- 分三阶段落地：**P1 非模态浮层**（OverlayHost + Popup，只动布局/绘制/命中）→ **P2 模态输入门控**（Dialog + key/mouse/paste 屏障 + 焦点域/恢复）→ **P3 打磨**（backdrop、多层层叠、光标、示例、apiDump）。
- 新 API 全部是新增；不用 `OverlayHost` 的组合树与派发路径保持原样（只多一次空引用判断）。

---

## 1. 现状核对（代码事实）

### 1.1 渲染：单根、单画布、先写的被后写的覆盖

`MosaicComposition` 在构造时创建 `rootNode`（`mosaic.kt:247`），布局时 `rootNode.measureAndPlace()`，绘制时 `rootNode.draw()`（`mosaic.kt:309`、`mosaic.kt:328`）。整棵树最终写入**同一个** `TextSurface`，没有第二个 surface 或窗口。

- `BottomLayer.drawTo` 按 `children` 的**正序**绘制（`layout/Node.kt:424`）。
- 后绘制的子节点覆盖先绘制子节点的 cell；透明区域不写 cell，所以下层仍可见。
- `TextSurface` 的 cell 是「首次写重置、后续写叠加」（`surface.kt:58`），并没有 alpha / 混合 / 逐 cell 合成。
- 因此「压在最上层」在终端里**只能靠绘制顺序 + 写入覆盖**，不能靠透明度。

现有布局容器只有 `Box`、`Row`、`Column` 等（`ui/Box.kt`、`ui/Row.kt`、`ui/Column.kt`），没有 `Popup` / `Dialog` / `zIndex`。`Box` 的后声明子节点天然后绘制（`ui/Box.kt:126`），但 Box 不会改变输入派发顺序。

### 1.2 键盘：按树序，和焦点无关；根只接管 Tab

`MosaicNode.sendKeyEvent`（`layout/Node.kt:318`）：

1. 如果当前是组合根（`parent == null`），先让 `FocusManager.preKeyEvent` 处理 Tab / Shift+Tab；
2. 否则/之后，沿 `topLayer`（这里是**修饰符链的末端**，不是浮层）向下派发。

`BottomLayer.sendKeyEvent` 对 `children` **正序**逐个尝试（`layout/Node.kt:398`），第一个返回 `true` 的消费。`KeyLayer` 的顺序是 `onPreviewKeyEvent → 子树 → onKeyEvent`（`layout/Node.kt:524`）。

关键结论：

- **键盘派发不看 `focusedNode`**。即使焦点在 B，树中更早的 A 的 `onKeyEvent` 也会先拿到事件；这与 `KeyModifier` 文档里「focused component」的措辞并不完全一致（`layout/KeyModifier.kt:7`）。
- 所以「先声明的 base 会先吃掉按键」不是 bug，是当前语义。浮层若只做可视层，必须**改变入口派发顺序**，否则 base 永远先看到键。

### 1.3 鼠标：命中测试自顶向下，再向上冒泡

`MosaicNode.sendMouseEvent` 在组合根上先跑 `PointerState.preDispatch`（`layout/Node.kt:342`），再做命中派发：

- `dispatchMouseEvent` 先检查自身 bounds，再按 `children.indices.reversed()` 让**最后声明的子节点先命中**（`layout/Node.kt:351`）；
- 子节点都不消费时，才走本节点的 `MouseLayer`（`layout/Node.kt:533`）。

也就是说，**绘制顺序和命中顺序已经天然一致**（后画的上层先命中）。浮层要做的是：让 modal 浮层在「没命中内容」时也**不要落回 base**，以及让非 modal 浮层在「没命中自己」时正常落回 base。

`PointerState`（`layout/PointerInput.kt:188`）在根上做三件跨节点的事：

- `reconcileHover`：维护 hover enter/exit（`PointerInput.kt:220`）；
- `handleFocus`：左键按下时按命中路径找 `focusable` 并聚焦（`PointerInput.kt:213`）；
- `handleClick`：按下捕获、释放时判断是否仍在按下时的 bounds 内（`PointerInput.kt:241`）。

它目前**从根遍历整棵树**（`collectPointerEntries`，`PointerInput.kt:275`）。如果只给普通 mouse dispatch 加门控而不管 `preDispatch`，base 的 hover/聚焦/click 仍会被触发，这是模态实现里最容易漏的一处。

### 1.4 粘贴：root-first，父先于子

`MosaicNode.sendPasteEvent`（`layout/Node.kt:370`）→ `PasteLayer`（`layout/Node.kt:541`）→ `BottomLayer` 正序子节点（`layout/Node.kt:411`）。语义是**根先、父先于子**，第一个返回 `true` 的消费。

粘贴是 `BasicTextField` 的主要文本入口之一；modal 打开时如果粘贴仍先到 base 字段，会直接把文本写到被遮住的输入框里。门控必须覆盖 paste。

### 1.5 焦点：单一 `FocusManager`，环遍历整棵树

`FocusManager` 懒创建在组合根（`layout/Focus.kt:127`），`preKeyEvent` 只处理 `Tab`（`Focus.kt:165`）：

- `collectFocusable` 从根开始**前序遍历**收集所有可遍历节点（`Focus.kt:201`）；
- 点击聚焦用 `collectFocusHitPath`，同样是整棵树（`Focus.kt:213`）；
- `FocusRequester.requestFocus` 直接找 `node.root()` 的 manager（`Focus.kt:93`）。

modal 打开后如果仍从根收集，Tab 会把焦点转到被遮住的 base 控件；如果直接清空焦点又不保存，关闭后无法恢复。这两点都要在方案里处理。

### 1.6 接入方现状（仓库外）

仓库外终端应用目前自己组合 base + overlay，并用应用级 `CompositionLocal` 冻结 base 输入。这是本文要替换的目标，但**不在本仓库内**，也不改它。本文只保证库侧 API 不破坏现有用法，并给出迁移路径（§9）。

---

## 2. 问题与需求

### 2.1 问题

终端 UI 的「弹窗」天然缺少窗口系统：

- 没有独立 surface，谈不上窗口合成、阴影、alpha；
- 键盘派发是树序，不是焦点序，base 会先消费；
- 鼠标/悬停/按压捕获/焦点都挂在同一个根上，base 与 overlay 共享状态；
- 粘贴、Tab 遍历、硬件光标也都没有「当前活动层」概念。

因此只加一个视觉上的 `Box { base; overlay }` 不够：**画得上去，输入下不来**。

### 2.2 需求

| 编号 | 需求 | 说明 |
| --- | --- | --- |
| R1 | overlay 画在 base 之上 | 后声明的 layer 覆盖先声明的；未写 cell 的区域保持 base 可见 |
| R2 | base 保持组合、可见 | modal 打开时 base 不 dispose、不重置 `remember`/effect；只是不接收输入（R3） |
| R3 | modal 捕获键盘 + 鼠标 | 按键、鼠标、粘贴、悬停、按压捕获、点击聚焦都不能落到 base |
| R4 | 焦点行为可预期 | 打开时记录/移走焦点，关闭后恢复；Tab 只在活动域内循环 |
| R5 | Esc / 背景点击可关闭 | 内容优先处理 Esc；backdrop 点击可配置；不误触 |
| R6 | 非模态 Popup | 不遮挡 base 输入；自己先拿输入，未处理时回落到 base |
| R7 | 多层堆叠 LIFO | 后打开的在最上；只让最上层（或最上层 modal 及以上）拿输入；逐层关闭 |
| R8 | 不破坏现有 API | 现有应用不调用新 API 时行为不变；不新增必填参数、不改既有签名 |

### 2.3 非目标

- 不做窗口管理器、不做任意兄弟节点 `zIndex`；
- 不做 alpha / 背景内容变暗 / 反色合成（终端 cell 没有 alpha，见 §5.4）；
- 不重写键盘为「焦点驱动派发」；
- 不做 IME 预组合（当前只有码点流与粘贴）；
- 本文不实现 runtime；不改仓库外应用。

---

## 3. 分层方案与取舍

### 方案 A：库级 `OverlayHost` 组合件 + 内部 `LocalInputEnabled`

做法：

- `OverlayHost(overlay, content)` 内部就是一个 `Box`：先 `content()`，后 `overlay()`；
- 提供一个内部 `LocalInputEnabled`，base 子树上 `clickable` / `hoverable` / `BasicTextField` 等读取它，false 时相当于 `enabled = false`；
- 不改 `MosaicNode` 和帧循环。

优点：

- 改动最小，只动组合层；
- 不碰 key/mouse/paste 的派发主干；
- 视觉层叠立即可用。

缺点（决定性的）：

- **键盘仍然先到 base**。`onKeyEvent` 是公开 Modifier，库无法要求所有应用 handler 都去读这个 local；即使 base 的组件都禁用了，base 上用户自己写的 `onKeyEvent` 仍会先消费。要修只能在每个 handler 里手动判断，等于把模态正确性转嫁给应用。
- **粘贴同理**：`onPaste` 是公开 Modifier，先到父/先到早声明子树；没有统一门控。
- **Tab/焦点**：`FocusManager.collectFocusable` 仍遍历整棵树；local 对内部 manager 没有天然约束，需要额外改 FocusManager，已经不再是「纯组合件」。
- **每个输入原语都要记得尊重 local**（clickable、hoverable、onMouseEvent、onPaste、onKeyEvent、BasicTextField、Cursor……），漏一个就漏一条输入通道。
- 仍然解决了「视觉层叠」，但解决不了「真正模态」。

结论：可作为 P1 的过渡形态，不足以作为最终方案。

### 方案 B：runtime 顶层输入路由

做法：

- `MosaicComposition` 自己维护一个有序 overlay 列表（或由组合根节点持有）；
- key 事件只发给最上层 overlay（有 overlay 时）；mouse 命中已经自顶向下，补上 modal 屏障；
- base 照常绘制但不派发；
- 需要改 `MosaicComposition`、`MosaicNode`、`FocusManager`、`PointerState`。

优点：

- 语义最直接，真正的「顶层窗口」；
- 非 modal 的回落规则、modal 屏障都在一个入口实现，容易推理；
- 不需要每个组件配合。

缺点：

- 侵入面大：组合根要知道 overlay 列表，而列表又来自组合树；谁拥有、何时增删、recomposition 与 applier 的时序都要处理；
- key/mouse/paste 三套入口和 `PointerState.preDispatch` 都要改；
- 焦点、光标、粘贴、Ctrl-C 逃生口都与「组合根」耦合，回归面大；
- API 上容易演化成两套布局真相（树 vs 列表），后续加弹窗动画/嵌套更复杂。

结论：正确但偏重，适合作为「如果 C 的折中不够」的备选，不作为第一步。

### 方案 C：混合（推荐）

做法：

- **布局/绘制用组合件**：`OverlayHost` 管 base 槽 + overlay 槽，layer 顺序 = 组合顺序；
- **输入用根级门控**：组合根持有一个 `OverlayHost` 的引用（或 layer 注册表）；`sendKeyEvent` / `sendMouseEvent` / `sendPasteEvent` 在根入口先看 layer 栈：
  - 有 modal 层：只派发到最上层 modal 及以上，modal 作为屏障，未处理也不落 base；
  - 全是非 modal 层：自顶向下派发，未处理落 base；
  - 无 overlay：走现有路径。
- 不在每个输入组件里加 local；不改帧循环；组合根节点只多一个「当前 host」引用（由 `OverlayHost` 挂上/摘掉），`MosaicComposition` 本身不需要改。

优点：

- 复用现有树：layer 就是节点，顺序就是 children 顺序，不需要第二份列表；
- 真正的 modal 语义集中在三个派发入口 + FocusManager + PointerState，改动可枚举；
- 不改变现有无 overlay 组合的树和派发；性能上只是一次空引用判断；
- 组件不需要知道 overlay 存在，`clickable`/`onKeyEvent`/`onPaste` 的公开 API 不变。

缺点：

- 仍然是 runtime 改动（Node + FocusManager + PointerState），不是纯组合件；
- layer 元数据需要内部 Modifier/ParentData 承载，属于新增内部概念；
- 根上多一个「当前 host」的挂接状态，需要处理 host 移除/替换。

结论：**推荐 C**。它保留了 A 的「布局/绘制只在组合层」的优点，同时把模态正确性放到唯一有全局视野的地方——组合根的派发入口。

### 方案对比

| 维度 | A：仅 OverlayHost | B：runtime 顶层 | C：混合（推荐） |
| --- | --- | --- | --- |
| 视觉层叠 | ✅ | ✅ | ✅ |
| modal 键盘 | ❌ base 先消费 | ✅ | ✅ |
| modal 鼠标/粘贴 | ⚠️ 需逐个原语改 | ✅ | ✅ |
| Tab/焦点域 | ❌ 仍遍历整树 | ✅（需改） | ✅（需改） |
| 改动面 | 小 | 大（组合根 + 帧循环周边） | 中（三个派发口 + 焦点/指针） |
| 两层真相风险 | 无 | 有（列表 vs 树） | 无（树即列表） |
| 现有应用回归面 | 最小 | 大 | 小（无 overlay 路径不变） |
| 作为最终方案 | 不够 | 可以 | **推荐** |

---

## 4. 推荐方案 C 的设计

### 4.1 结构

```text
组合根 (MosaicNode, parent == null)
└── OverlayHost
    ├── base 槽 (Box)
    │   └── 应用的 content()
    └── overlay 槽 (内部 OverlaySlot)
        ├── layer 1 (Dialog/Popup 的根节点)
        ├── layer 2
        └── layer 3   ← 最后声明 = 最上层
```

- `OverlayHost` 用 `Box` 保证 base 先绘制、overlay 槽后绘制；overlay 槽用 `matchParentSize` 跟随 base 尺寸。
- overlay 槽是一个内部 `Layout`：
  - modal layer：按 base 尺寸 `Constraints.fixed(w, h)` 测量，放在 `(0, 0)`；
  - 非 modal layer：按内容尺寸测量，按 `Alignment` + `IntOffset` 放置；
  - 所有 layer 都从 `(0, 0)` 的槽位坐标开始，绘制顺序就是 children 顺序。
- layer 携带内部元数据：`modal`、`fillParent`、`alignment`、`offset`。用内部 `ParentDataModifier` 或 `LayoutModifier` 标记，不暴露 public 类型。

> 命名提醒：`MosaicNode.topLayer` 已经是「修饰符链末端」（`layout/Node.kt:115`），本文的「layer / 浮层」不是它。实现时不要复用 `topLayer` 这个名字，避免歧义。

### 4.2 输入顺序规则（唯一需要记住的规则）

**绘制正序，输入反序；modal 是屏障。**

| 场景 | key / paste | mouse（含 preDispatch） | 焦点域 |
| --- | --- | --- | --- |
| 无 overlay | 现有路径 | 现有路径 | 整棵树 |
| 仅非 modal | 顶层 → 下层 → base | 顶层命中 → 下层 → base | 不改变（仍整棵树） |
| 有 modal | 顶层 → … → 最上层 modal；未处理即停 | 同样，在 modal 处停；不落 base | 最上层 modal + 其上的非 modal |
| modal 叠 modal | 最上层 modal 优先，屏障在下层 modal 之前 | 同上 | 只到最上层 modal |

注意：

- 「未处理即停」不是「吃掉按键」：根返回 `false` 后，帧循环仍会用 `!keyHandled && ctrlC` 触发 Ctrl-C（`mosaic.kt:504`），这是全局逃生口，不能被 overlay 门控破坏。
- 对非 modal 层，未处理继续向下，最终可以到 base。这样菜单/提示类浮层不会无意中吞掉应用快捷键。
- 对 modal 层，未处理也不向下，防止 base 的快捷键在弹窗背后生效。

### 4.3 组合根门控示意（伪代码）

```kotlin
// 仅示意：实际挂在 MosaicNode 的根分支，不改公开 API。
internal class OverlayHostState(
    val base: MosaicNode,
    val slot: MosaicNode,
) {
    val layers: List<MosaicNode> get() = slot.children // 后者在上
}

internal fun MosaicNode.sendKeyEvent(keyEvent: KeyEvent): Boolean {
    // 根分支：Tab 仍然先给 FocusManager；它内部使用当前活动焦点域。
    if (parent == null && ensureFocusManager().preKeyEvent(this, keyEvent)) return true
    if (parent != null) return topLayer.sendKeyEvent(keyEvent)

    val host = overlayHost
    if (host == null || host.layers.isEmpty()) {
        return topLayer.sendKeyEvent(keyEvent) // 现有路径
    }
    for (i in host.layers.indices.reversed()) {
        val layer = host.layers[i]
        if (layer.sendKeyEvent(keyEvent)) return true
        if (layer.isModal) return false // modal 屏障；Ctrl-C 由帧循环兜底
    }
    return host.base.sendKeyEvent(keyEvent)
}
```

鼠标入口需要同时改两处：

```kotlin
internal fun MosaicNode.sendMouseEvent(event: MouseEvent): Boolean {
    if (parent != null) return dispatchMouseEvent(event)
    val roots = inputRootsForMouse() // 顶层→…→modal；没有 modal 时最后加 base
    pointerState.preDispatch(roots, event) // 从「整棵树」改为「roots 中第一个命中」
    for (root in roots) {
        if (root.dispatchMouseEvent(event)) return true
    }
    return false
}
```

```kotlin
internal fun MosaicNode.sendPasteEvent(text: String): Boolean {
    val host = overlayHost
    if (host == null || host.layers.isEmpty()) return topLayer.sendPasteEvent(text) // 现有路径
    for (i in host.layers.indices.reversed()) {
        val layer = host.layers[i]
        if (layer.sendPasteEvent(text)) return true
        if (layer.isModal) return false
    }
    return host.base.sendPasteEvent(text)
}
```

要点：

- `pointerState.preDispatch` 必须接收**候选根列表**，而不是总是 root；否则 hover/点击聚焦/按压捕获仍会扫到 base。这是 §1.3 提到的坑。
- `dispatchMouseEvent` 目前是 `private`，实现时改成 `internal` 或加一个同文件内的入口。
- 层列表为空时完全走旧路径，保证无 overlay 的回归面最小。根 `FocusManager.preKeyEvent` 的 Tab 处理仍在门控循环之前；Tab 的活动域切换在 `FocusManager` 内部完成，门控循环只负责非 Tab 事件的层序。

### 4.4 层变化时的通知

层的增删（打开/关闭/叠层变化）需要做三件事：

1. **指针**：取消按压捕获（不能等释放时误触发 base 的 click），并让所有 hover 状态退出；
2. **焦点**：进入/退出 modal 焦点域，必要时恢复保存的焦点；
3. **重绘**：层树变化本身会触发组合/布局/绘制失效，不需要额外帧循环改动。

可以在 `OverlayHost` 的 `SideEffect` 里比较 layer 数量与 modal 签名，变化时调用根上的 `notifyOverlayStackChanged()`。这个通知不是每帧都跑，只在栈形状变化时跑。

---

## 5. API 草案

以下都是**新增** public API；名字/签名可在实现前再收敛，但语义按此实现。

### 5.1 `OverlayHost`

```kotlin
@Composable
public fun OverlayHost(
    overlay: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

语义：

- `content` 是 base，先测量、先绘制；
- `overlay == null` 时不创建 overlay 槽，尺寸与绘制与直接调用 `content()` 等价；节点树会多一层宿主节点，测试只承诺屏幕内容/尺寸不变，不承诺节点实例不变；
- `overlay` 槽里直接放 `Dialog` / `Popup`；**每个直接子节点是一个 layer**，后声明者在上层；
- 一个组合只允许一个 `OverlayHost`；嵌套/第二个 host 在 v1 抛 `IllegalStateException`（错误帧可见，见 `mosaic.kt:198`）；
- `modifier` 作用于 host 的根布局；host **不改变根的无界约束**，所以 base 仍必须自己声明画布尺寸（全屏应用用 `LocalTerminalState.current.size`）。base 为 0×0 时 overlay 也是 0×0；

用法：

```kotlin
OverlayHost(
    overlay = {
        if (showRename) {
            Dialog(onDismissRequest = { showRename = false }) {
                Text("Rename")
            }
        }
        if (showMenu) {
            Popup(alignment = Alignment.TopStart, offset = IntOffset(2, 1)) {
                Text("Open")
            }
        }
    },
) {
    AppScreen()
}
```

### 5.2 `Dialog`

```kotlin
@Composable
public fun Dialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackdropClick: Boolean = true,
    dismissOnBack: Boolean = true,
    backdrop: OverlayBackdrop = OverlayBackdrop.Solid(),
    content: @Composable ColumnScope.() -> Unit,
)
```

语义：

- **始终 modal**；layer 填满 base 区域，输入屏障；
- 默认居中；内容包在 `Column` 里（`ColumnScope` 与任务约定一致）；Dialog 默认不画边框/背景（终端主题未知），需要时由调用方用 `Modifier.border`/`Modifier.background` 提供；
- 打开时：保存当前焦点 → 清空/移入本层焦点域；关闭时：恢复保存的焦点（若仍 attached/enabled/traversable）；
- `Escape`：本层根上的 `onKeyEvent`（**post**，即内容处理之后）触发 `onDismissRequest`；内容自己消费了 Esc 就不关；
- `dismissOnBackdropClick`：点击 backdrop 区域关闭；用 press/release 的 click 语义，press 在弹窗内、release 在外不触发，反之亦然；
- `backdrop`：见 §5.4；
- 必须放在 `OverlayHost` 的 `overlay` 槽内；否则抛 `IllegalStateException`（信息里给出正确用法）。

### 5.3 `Popup`

```kotlin
@Composable
public fun Popup(
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset.Zero,
    modifier: Modifier = Modifier,
    modal: Boolean = false,
    backdrop: OverlayBackdrop = OverlayBackdrop.None,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

语义：

- 默认**非 modal**：layer 只占内容尺寸，放在 base 的 `alignment` + `offset` 位置；
- 命中：内容区域内的鼠标事件先给 popup；内容未消费时，默认由 popup 的「内层 shield」消费，避免点穿到 base（可讨论是否提供 `consumeInsideClicks`）；
- 内容区域外：非 modal，鼠标/按键继续向下到 base；
- `modal = true` 时退化为「没有 backdrop 的 Dialog」：layer 填满 base、输入屏障，内容仍按 `alignment` + `offset` 落在 layer 内；
- `onDismissRequest != null` 时，`Escape` 在本层 post 阶段触发（内容优先）；**v1 不做「点击外部自动关闭」**，因为非 modal 的 pointer 会继续向下，关闭与 base 点击的先后语义不清晰，留作开放问题；
- 非 modal 不移动焦点；popup 内如果有 `BasicTextField`，需要应用自己 `FocusRequester` 或在打开时点击聚焦。

### 5.4 `OverlayBackdrop`

```kotlin
@Immutable
public sealed interface OverlayBackdrop {
    /** 不画任何东西，base 完整可见。 */
    public data object None : OverlayBackdrop

    /**
     * 遮住 base。终端没有 alpha，这是唯一的「不保留 base」的遮挡方式。
     * [background] 为 Unspecified 时清除 base 字符/样式，回到终端默认空白；
     * 指定颜色时同时铺该背景色。
     */
    public data class Solid(
        public val background: Color = Color.Unspecified,
    ) : OverlayBackdrop
}
```

关键实现细节（已在代码中核对）：

- **不能只用公开的 `drawRect` 实现「擦除」**。`updateTextPixel` 只覆盖已指定的字段（`layout/DrawScope.kt:406`）：写一个空格且 `background` / `underline*` 为 Unspecified 时，base 的背景色、下划线样式和下划线颜色会留在 cell 上，表现为「字没了但底色/下划线还在」。另外 `drawRect` 在字符/前景/背景/样式全 Unspecified 时会走 fast path 直接返回（`layout/DrawScope.kt:150`），连空格都不会写。
- 因此 `OverlayBackdrop.Solid` 应由 **runtime 内部**实现一个矩形 clear：对 `TextSurface` 中该矩形内的 `TextPixel` 执行 `reset()`（`surface.kt:377`），再按需写入背景色/空格。这个入口不暴露 public API；`OverlayHost` / `Dialog` 在 runtime 模块内可以直接使用。**这是本设计里唯一需要「绕过 DrawScope」的绘制实现**，其余组件仍走普通组合/draw。
- 若只写 `drawRect(background = ..., textStyle = TextStyle.Empty)`，可以作为「指定实色背景」的近似实现（`TextStyle.Empty` 是 specified，`ui/TextStyle.kt:109`），但它不能清掉 base 的 background/underline；不能作为 `Solid(Color.Unspecified)` 的默认语义。
- 矩形 clear 要按 cell 边界处理宽簇：完整覆盖 backdrop 时通常没问题，但边缘切到宽字符的续列/簇头时要测试，避免留下 placeholder 或半个簇。
- 若 `background` 指定颜色，就是「实色遮罩」；若为 Unspecified，就是「reset 成终端默认空白」。
- **overlay 内容本身也可能继承 base 样式**：后绘制只覆盖指定字段（同上），所以 Dialog/Popup 若不画自己的背景，文字可能继承同一 cell 上 base 的底色/下划线。需要完全隔离时加 `Backdrop.Solid`，或让 Dialog 表面自带 `Modifier.background`。
- **没有 dim / invert 下层内容**：`TextStyle.Dim` / `Invert` 只作用于当前写入的 cell，不会修改已经画好的 base 字符。要「保留 base 字形但变暗/反色」，必须在绘制后对 `TextSurface` 的 cell 做一次后处理（读取 `TextPixel` 并改样式）。这属于 runtime-only 能力，v1 不做，列入开放问题。
- 终端的主题可能是亮色或暗色，所以 `Solid` 不默认硬编码黑/白；默认 `Color.Unspecified` 表示由终端决定。

### 5.5 Esc 与既有 `onKeyEvent` 的关系

- 终端没有独立的 Back 键，`Escape` 就是返回语义；`Dialog` / `Popup` 的 Esc 处理就是一个普通的 `Modifier.onKeyEvent`，挂在自己的 layer 根上；不新增全局键盘拦截。
- 顺序是：layer 根 `onPreviewKeyEvent` → layer 内容（pre → 子树 → post）→ layer 根 `onKeyEvent`。因此：
  - 内容里的 `onPreviewKeyEvent` 可以抢在 Dialog 前面处理 Esc；
  - 内容里的 `onKeyEvent` 也可以先于 Dialog 的 post handler；
  - 只有都没消费时才 `onDismissRequest`。
- 如果应用在 base 上也有 Esc handler：modal 时它收不到（屏障）；非 modal 时，只有 Popup 的 `onDismissRequest` 为 null **且** Popup 内容没有消费，base 才可能收到（内容一旦消费，base 同样收不到）。
- Ctrl-C 不经过 Dialog 的 Esc 逻辑；未被任何 layer 消费时仍由帧循环取消 composition。

---

## 6. 输入 / 焦点细节

### 6.1 键盘优先级

- 入口顺序：**后声明的 layer 先**；modal 处截断。
- 层内顺序保持现有语义：`onPreviewKeyEvent` 向下，`onKeyEvent` 向上；**与焦点无关**。
- 不改变「base 内的兄弟节点按树序」这一既有行为；只改变 base 与 overlay 之间的入口顺序。
- 如果未来要做「焦点驱动派发」，那是另一项独立设计，不在本文范围。

### 6.2 Tab / Shift+Tab

- 无 modal：保持现状，`FocusManager.collectFocusable(root)` 遍历整棵树。
- 有 modal：活动焦点域 = 从最上层 layer 向下，到**最上层 modal（含）**为止的所有 layer。若最上层是非 modal popup，而它下面有 modal，则 popup 也在域内。
- 在活动域内按现有环形遍历；**若域内没有 focusable，Tab 仍被消费**（吞掉），避免事件落回 base；这与现有「没有 focusable 时 Tab 不消费」的 legacy 行为不同，只在 modal 期间生效。
- 非 modal 不移动焦点、不改 Tab 域；popup 内的 focusable 只有被点击/程序化聚焦后才进入焦点链。

### 6.3 焦点保存与恢复

- 打开第一个 modal 时，把当前 `focusedNode` 压栈；清空焦点（base 的 `hasFocus` 变为 false，输入框光标随之隐藏）。
- 若 modal 内有 `FocusRequester.requestFocus()`（通常在 `LaunchedEffect` 中），允许，因为目标在活动域内；否则等用户 Tab 或点击。
- 关闭时弹栈并恢复：
  - 目标仍 attached 到 root、`enabled`、`traversable` → 恢复；
  - 否则焦点置空，不自动跳到别的 base 控件（避免「关闭弹窗后焦点乱跑」）。
- 多层 modal：栈式保存/恢复，严格 LIFO。
- `FocusRequester.requestFocus` 的目标如果在活动域外，应被忽略（防止 base 在 modal 期间抢焦点）。实现时在 `FocusManager` 里做 scope 检查。

### 6.4 粘贴

- 与 key 相同的层序和 modal 屏障；`BasicTextField` 只在活动层内收到粘贴。
- 粘贴期间逐键累积仍发生在帧循环（`mosaic.kt:491`），与 overlay 无关；overlay 只是在粘贴完成、形成 `BracketedPasteEvent` 后决定派发给谁。
- 如果 modal 内的内容不处理粘贴，事件被屏障吞掉，不会落到 base 字段。

### 6.5 鼠标捕获 / 点击穿透 / 悬停

- **命中**：从顶层 layer 的 bounds 开始；非 modal 未命中继续下一层/base；modal 未命中内容时也不落 base。
- **非 modal 的「内部 shield」**：layer 内容外面包一层 `onMouseEvent { true }` 的 surface（或对未处理事件返回 true 的节点），保证点在 popup 内部不会穿透到 base；内容自己的 `clickable` 仍先命中、先消费。
- **Dialog 的 backdrop 与内容**：
  - backdrop 是 layer 的第一个子节点，`clickable` 负责 press/release 的点击语义；
  - 弹窗表面必须再加一层内部 shield，否则点在弹窗内容上、内容没有 mouse handler 时，会继续命中兄弟 backdrop 并误关闭。这是实现时最容易漏的交互 bug。
- **按压捕获**：如果用户在 base 上按下、还没松开时 modal 打开，释放不能触发 base 的 click。层变化通知里必须取消 `PointerState.pressed`（不调用 click），并释放对应 `PointerInputState`。
- **悬停**：modal 打开时，指针可能停在 base 上且没有新的鼠标事件；必须主动让已 hover 的 base 元素 `exit()`，否则高亮会残留。关闭时无法知道指针位置，v1 等下一次鼠标事件再重新 enter；如果体验不够，可在根上缓存最后一次坐标，关闭后合成一次 hover 重算（见开放问题）。
- **点击聚焦**：`PointerState.handleFocus` 的命中路径也必须用门控后的 roots；modal 期间点 base 不应把焦点移过去。
- **滚轮**：滚轮也是 `MouseEvent` 的一种；同样受门控。非 modal 在 popup 外滚动到 base 是合理的；modal 期间只滚弹窗内的滚动容器。

### 6.6 硬件光标

- `Dialog` 打开时清空焦点，`BasicTextField` 因失焦会隐藏自己的光标（它的绘制/HW 光标与焦点绑定）。
- 任意 `Cursor`（`ui/Cursor.kt:57`）目前只按位置上报，不与焦点/活动层绑定；如果 base 里有一个不依赖焦点的 `Cursor`，modal 打开后它可能仍在原位置显示。
- v1 接受这个限制并文档化；P3 再评估让 `Cursor` 感知活动域（需要给它节点/活动层信息，当前只有 token + position）。自定义光标是开放问题。

### 6.7 多层 LIFO

- layer 顺序 = overlay 槽 children 顺序；后声明 = 后绘制 = 先拿输入。
- 关闭最上层（从组合中移除）后，下一层自动成为顶层；屏障与焦点域重新计算。
- 层变化通知在同一帧内完成指针/焦点重置，避免一帧的「旧层仍持有捕获」。
- 若中间层被移除（非 LIFO 关闭），同样重算；应用应尽量用 LIFO 以避免焦点恢复栈错位。

---

## 7. 运行时改动清单（方案 C）

只列设计范围，不在本提交实现：

| 位置 | 改动 | 风险 |
| --- | --- | --- |
| `layout/Node.kt` | 根入口 `sendKeyEvent`/`sendMouseEvent`/`sendPasteEvent` 咨询 `overlayHost`；`dispatchMouseEvent` 可见性放宽 | 中；必须保证无 overlay 路径逐字节一致 |
| `layout/Node.kt` | 新增 layer 元数据（modal/fillParent/alignment/offset）与 host 槽标记 | 低；内部类型 |
| `layout/Focus.kt` | `FocusManager` 增加活动域 roots、保存/恢复栈、域外 requestFocus 忽略、域内空 Tab 消费 | 中；焦点回归面大 |
| `layout/PointerInput.kt` | `PointerState.preDispatch` 接受候选 roots；新增取消 press / 清空 hover 的入口 | 中；影响 clickable/hoverable 全量回归 |
| `ui/` 新文件 | `OverlayHost`、`Dialog`、`Popup`、`OverlayBackdrop` 组合件 | 低；新 API |
| `mosaic.kt` | **不需要改帧循环**；host 自己挂到 root | 低 |
| `.api` / `.klib.api` | 新 public API 后 `apiDump` | 发布流程 |

明确不改：

- 不改 `MosaicComposition` 的 frame loop / `hasPendingFrameWork` / 节流；
- 不改 `TextSurface` 的 cell 模型；
- 不引入 `zIndex`；
- 不改 `KeyModifier` / `MouseModifier` / `PasteModifier` 的公开签名。

---

## 8. 测试策略

### 8.1 现有测试工具

- `runMosaicTest(NodeSnapshots)`：拿到 `MosaicNode`，直接 `root.sendKeyEvent(...)` / `root.sendMouseEvent(...)`，适合确定性派发测试（现有 `FocusTest`、`MouseEventTest` 的写法）。
- `runMosaicTest(PlainTextSnapshots)`：`awaitSnapshot()` 得到当前帧字符串，适合可视层叠的快速断言。
- `RenderingSnapshots(AnsiIncrementalRendering(TestTerminal.Capabilities()))` + `VirtualTerminal`：端到端 golden，断言**最终屏幕**而不是单帧字节流（`EndToEndGoldenTest` 的写法；`awaitSnapshot` 超时可作为稳定信号）。
- `TestMosaic.sendKeyEvent` / `sendMouseEvent` / `sendPaste`：走真实的帧循环事件队列，适合 Ctrl-C、粘贴、焦点生命周期。
- `runMosaicTestForEach` + `CapabilityMatrix`：不需要为 overlay 单独跑能力矩阵；渲染只依赖普通 cell 写入，用默认能力即可，若改 backdrop 颜色可补一条。

### 8.2 具体用例

建议新增 `mosaic-runtime/src/commonTest/kotlin/com/jakewharton/mosaic/ui/OverlayTest.kt`（或拆 `OverlayInputTest` / `OverlayGoldenTest`）：

| # | 用例 | 断言 |
| --- | --- | --- |
| 1 | overlay 后绘制 | base 与 dialog 写同一 cell，最终显示 dialog 字符（PlainText 快照或 golden） |
| 2 | base 保持组合 | 打开/关闭 dialog 后 base 的 `remember` 计数不变，base 的 `LaunchedEffect` 未重启 |
| 3 | modal 键盘屏障 | base `onKeyEvent` 记录为空；dialog 记录一次；root 返回 true |
| 4 | 非 modal 键盘回落 | popup 未处理时 base handler 收到；popup 处理时 base 不再收到 |
| 5 | Esc 关闭 | dialog 内容不处理 Esc → `onDismissRequest` 一次；内容处理 → 0 次 |
| 6 | backdrop 点击 | 点 dialog 外 → 关闭；点 dialog 内（内容无 mouse handler）→ 不关闭（内部 shield） |
| 7 | press/release 跨区 | press 在 dialog 内、release 在外 → 不关闭；press 在外、release 在内 → 不关闭 |
| 8 | Solid backdrop | 最终屏幕 base 区域的字符、背景、下划线都被清除；指定颜色时背景为该色；`None` 时 base 完整可见 |
| 8b | backdrop 样式残留 | base 有彩色背景 + 下划线，`Solid(Unspecified)` 后两者都不得残留（防止只写空格不 reset） |
| 9 | 点击穿透 | modal 时点 base `clickable` 不触发；非 modal 点 popup 外触发 base，点 popup 内不触发 |
| 10 | hover 退出 | 指针停在 base 时打开 modal，base 的 `onHoverChanged(false)` 立即触发；关闭后下一次 mouse move 重新 enter |
| 11 | 按压捕获取消 | base 上 press → 打开 modal → release：base `onClick` 不触发 |
| 12 | 焦点恢复 | base A 聚焦 → 打开 dialog → 关闭：A 的 `isFocused` 恢复 true |
| 13 | 焦点目标消失 | 打开 dialog 后从 base 移除 A，关闭：焦点为 null，不抛异常 |
| 14 | Tab 域 | modal 内两个 focusable，Tab/Shift+Tab 只在两者间循环；base focusable 不参与 |
| 15 | Tab 空域 | modal 内无 focusable，Tab 被消费（root 返回 true），base 不受影响 |
| 16 | 粘贴门控 | modal 内 `onPaste` 收到文本，base `onPaste` 未收到 |
| 17 | 多层 LIFO | D1 打开、D2 打开：键只到 D2；关 D2 后到 D1；关 D1 后到 base |
| 18 | modal 下的非 modal | popup 在最上、modal 在下：popup 先收键，未处理到 modal 屏障，base 永不收到 |
| 19 | Ctrl-C | modal 内容不处理 Ctrl-C，帧循环仍取消 |
| 20 | 空 overlay 兼容 | 不写 OverlayHost 的既有快照逐字符不变；写 `OverlayHost(overlay = null)` 的尺寸/内容不变 |
| 21 | 循环开合 | 同一 dialog 连续打开/关闭 100 次，无 hover/焦点/按压泄漏 |
| 22 | 光标 | base `BasicTextField` 聚焦时打开 dialog，光标消失；关闭后恢复 |

### 8.3 运行范围

- 先跑 `:mosaic-runtime:jvmTest` 的 overlay/focus/mouse/paste 子集；
- 再跑 `:mosaic-runtime:jvmTest` 全量，确认 clickable/hoverable/focus 无回归；
- native（`linuxX64Test --no-configuration-cache`）在实现阶段跑一次；
- 本设计提交**不跑**重测试，只做代码阅读。

---

## 9. 兼容性与迁移

### 9.1 API 兼容

- 全部新增：`OverlayHost`、`Dialog`、`Popup`、`OverlayBackdrop`；不改现有签名。
- 不用 `OverlayHost` 的应用：组合树、布局、绘制、key/mouse/paste 派发都与今天一致（根分支多一个空引用判断）。
- 用 `OverlayHost(overlay = null)`：应等价于只包一层 Box；测试 #20 锁定。
- 公开 API 增加后必须跑 `apiDump` 并提交 `.api` / `.klib.api`；本文不包含这些产物。

### 9.2 接入方迁移（仓库外应用）

1. 应用不再自己 `Box { base; overlay }`，改为 `OverlayHost(overlay = { Dialog(...) / Popup(...) }) { Base() }`；
2. 删除应用级「冻结 base 输入」的 `CompositionLocal` 在 overlay 场景下的使用；库的 modal 门控会覆盖 key/mouse/paste/focus；
3. 应用级 local 如果还用于非 overlay 的「整体禁用」场景，可以保留；库不暴露同名 public local，避免两套门控打架；
4. 应用自定义的输入组件（自己读 local 的那种）不再需要为弹窗写判断；只有库 API 覆盖不到的私有输入通道才需要自己处理。

### 9.3 稳定性

- 0.x 阶段（当前 `0.19.0-SNAPSHOT`）新 API 仍可能调整；本文建议先用文档 + 实现前的 API review 锁定签名，再 `apiDump`。
- 不建议为了这个功能改 `MosaicComposition` 或 `Mosaic` 接口；方案 C 的边界就在 Node/Focus/Pointer + 新组合件。
- 不在本提交写 CHANGELOG（没有 feature）；实现落地时再补。

---

## 10. 分阶段计划

### P1：非模态浮层（最小可见闭环）

- `OverlayHost` + `Popup`（`modal = false`）+ `OverlayBackdrop.None`；
- layer 元数据、overlay 槽测量/放置/绘制；
- 鼠标命中自顶向下 + popup 内部 shield；键盘/粘贴暂不门控（非 modal 本来就要回落）；
- 测试 #1、#2、#4、#9、#20；
- 退出标准：base + popup 画对、点对、不破坏既有快照。

### P2：模态输入门控（核心）

- `Dialog`、`Popup(modal = true)`、`OverlayBackdrop.Solid`（含内部矩形 reset；Dialog 默认 backdrop 依赖它）；
- 根入口 key/mouse/paste 门控 + modal 屏障；
- `FocusManager` 活动域、保存/恢复、Tab 空域消费；
- `PointerState` 候选 roots、取消 press、清空 hover；
- 测试 #3、#5、#7、#10、#11、#12、#13、#14、#15、#16、#19、#21；
- 退出标准：modal 期间 base 的任何输入通道都不可达（有测试逐条覆盖），Ctrl-C 仍可退出。

### P3：打磨与发布

- `OverlayBackdrop.Solid` 的颜色/主题细节、宽簇边缘的 reset、`dismissOnBackdropClick` 的 shield 细节；
- 多层 LIFO（#17、#18）、光标限制（#22 的 BasicTextField 部分）、golden（#8）；
- 示例/文档、`apiDump`、CHANGELOG（实现落地时）；
- 可选：backdrop 的 dim/invert 后处理、非 modal 的外部点击关闭、关闭后 hover 重算；
- 退出标准：JVM + native 全绿，API 基线更新，文档与 todo 勾销。

> 不建议把 P2 拆成「只做键盘门控」再补鼠标：`PointerState.preDispatch` 和焦点是同一条链，分两次改会让中间状态的 bug 更难定位。

---

## 11. 开放问题

1. **一个组合只允许一个 OverlayHost 是否足够？** 嵌套 host 的 layer 层级、输入顺序、焦点域都会翻倍复杂；v1 建议禁止。
2. **backdrop 的 dim / invert 是否值得做？** 需要绘制后遍历 cell 改 `TextPixel.textStyle`，属于 runtime-only；如果做，要定义它与已有 Bold/Dim/Invert 的合并规则。
3. **非 modal Popup 的「点击外部关闭」语义**：是否关闭、是否消费该次点击、与 base click 的先后；v1 先不做。
4. **关闭后 hover 是否立即恢复？** 需要根上缓存最后鼠标坐标；否则等下一次鼠标事件。缓存会影响「无鼠标终端」的内存与状态，需评估。
5. **任意 `Cursor` 的活动域感知**：当前 `Cursor` 只有 token + position，没有节点/层信息；是否值得为它增加节点绑定。
6. **Tab 在 modal 空域被吞** 是否会让某些应用不习惯；是否需要 `Dialog(tabBehavior = ...)` 开关。
7. **layer 被非 LIFO 移除** 时焦点栈如何对齐；v1 文档建议 LIFO，是否要在 debug 下断言。
8. **键盘是否应改为焦点驱动**：本文刻意不碰；如果未来改，overlay 门控需要与新的 focus dispatch 重新对齐。
9. **Backdrop 默认颜色**：`Color.Unspecified`（终端默认）还是跟随 `LocalTerminalState.theme` 的派生色；需要主题 API 后再定。
10. **API 命名**：`Dialog` / `Popup` 是否与未来可能的其他模态组件冲突；是否需要 `Modal` 作为低层名。

## 12. 不做（Out of scope）

- 任意 z-order / `Modifier.zIndex`；
- 真正的窗口、阴影、alpha、背景模糊/变暗；
- IME 预组合、输入法候选窗；
- 焦点驱动的全局键盘重写；
- 静态日志区（`StaticEffect`）与 overlay 的交互；
- 仓库外终端应用的改造；
- 本提交的任何 runtime 实现、CHANGELOG、示例。

---

## 13. 关键代码位置索引

| 主题 | 位置 |
| --- | --- |
| 组合根 / 事件入口 | `mosaic.kt:247`、`mosaic.kt:309`、`mosaic.kt:504`、`mosaic.kt:511`、`mosaic.kt:519` |
| 键盘树序 | `layout/Node.kt:318`、`layout/Node.kt:398`、`layout/Node.kt:524` |
| 鼠标命中/冒泡 | `layout/Node.kt:342`、`layout/Node.kt:351`、`layout/MouseEvent.kt:44` |
| 鼠标预派发/捕获/悬停 | `layout/PointerInput.kt:188`、`layout/PointerInput.kt:205`、`layout/PointerInput.kt:220`、`layout/PointerInput.kt:241`、`layout/PointerInput.kt:275` |
| 粘贴 | `layout/Node.kt:370`、`layout/Node.kt:541`、`layout/PasteEvent.kt:12` |
| 焦点 | `layout/Focus.kt:127`、`layout/Focus.kt:165`、`layout/Focus.kt:201`、`layout/Focus.kt:213` |
| 绘制/画布 | `layout/Node.kt:424`、`surface.kt:58`、`surface.kt:327` |
| Box 测量/放置 | `ui/Box.kt:51`、`ui/Box.kt:136` |
| 背景/样式/cell 合并 | `layout/Background.kt:8`、`layout/DrawScope.kt:150`、`layout/DrawScope.kt:406`、`surface.kt:377`、`ui/TextStyle.kt:109` |
| 光标 | `ui/Cursor.kt:57`、`ui/Cursor.kt:120` |
| 测试范式 | `EndToEndGoldenTest.kt`、`FocusTest.kt`、`MouseEventTest.kt`、`mosaic-testing/.../TestMosaic.kt` |
