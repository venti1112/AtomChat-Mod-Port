#!/usr/bin/env python3
"""校验 versions/ 里的目标矩阵 —— 本地与 CI 跑的是同一份。

目标写进 versions/targets.json，本脚本是那句声明的执行者：
  * 矩阵的规则（每个 Minecraft 版本都要有 Fabric / NeoForge / Forge 三条）
  * mappings 全目标一致（shared/ 里那些 net.minecraft.* 名字的前提）
  * buildable: true 的目标不许留 unverified
  * 工程目录存在就必须在矩阵里，反之亦然（不许有"没有任何地方编"的源码）
  * 各平台不许自己定义身份键（mod_version 等只在仓库根那一份）
  * 矩阵里写的 java / gradle 与工程里实际的 toolchain / wrapper 一致（防止两边各写一份然后漂移）
  * overlay_exclude 只排除基础平台真实存在、构建期会复制的文件；Java 文件必须有同路径的
    本地实现，补丁不许再碰它；versions/overlay-baselines.json 记录上游基线，漂移即失败

用法：
  python3 tools/verify_targets.py                       # 只校验，打印摘要
  python3 tools/verify_targets.py --matrix-out m.json   # 顺便把 CI 矩阵写成 JSON
  python3 tools/verify_targets.py --update-overlay-baselines  # 人工确认本地实现后接受上游新基线
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOADERS = ["Fabric", "NeoForge", "Forge"]
REQUIRED_FIELDS = ["minecraft", "loader", "mappings", "java", "gradle", "project", "buildable", "layers"]
IDENTITY_KEYS = [
    "mod_id", "mod_name", "mod_license", "mod_group_id",
    "mod_authors", "mod_description", "mod_version",
]
OVERLAY_BASELINES = "versions/overlay-baselines.json"
# 与 gradle/atomchat-overlay.gradle 的复制集合一致：只有这些前缀下的文件才可能被排除。
OVERLAY_COPY_ROOTS = ("src/main/java/", "src/main/resources/",
                      "src/test/java/", "src/test/resources/")

errors: list[str] = []
notes: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


def load_json(rel: str) -> dict:
    path = ROOT / rel
    if not path.is_file():
        fail(f"{rel} 不存在")
        return {}
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as exc:
        fail(f"{rel} 不是合法 JSON：{exc}")
        return {}


def entries_of(raw: dict) -> dict:
    return {k: v for k, v in raw.items() if not k.startswith("_")}


def read_props(path: pathlib.Path) -> dict:
    out = {}
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def check_root_identity() -> None:
    props = read_props(ROOT / "gradle.properties")
    if not props:
        fail("仓库根的 gradle.properties 不存在或读不出键 —— 身份没有来源")
        return
    missing = [k for k in IDENTITY_KEYS if k not in props]
    if missing:
        fail(f"仓库根的 gradle.properties 缺身份键：{', '.join(missing)}")


def check_targets(targets: dict, layers: dict, aliases: dict) -> None:
    if not targets:
        fail("versions/targets.json 里一个目标都没有 —— 文件坏了或被改空了")
        return

    mappings = {}
    seen_combo = set()

    for name, t in targets.items():
        for field in REQUIRED_FIELDS:
            if field not in t:
                fail(f"目标 {name} 缺字段 {field}")
        if any(f not in t for f in REQUIRED_FIELDS):
            continue

        mc, loader = t["minecraft"], t["loader"]
        seen_combo.add((mc, loader))
        mappings.setdefault(t["mappings"], []).append(name)

        for layer in t["layers"]:
            if layer not in layers:
                fail(f"目标 {name} 引用了 layers.json 里没有的层：{layer}")
                continue
            gap = predicate_gap(t, layer, layers[layer])
            if gap:
                fail(f"目标 {name} 挂了层 {layer}，但它满足不了该层的谓词：{gap}")

        project = ROOT / t["project"]
        exists = project.is_dir()

        # 增量目标：目录里没有源码树，构建期从 overlay_base 复制后再打补丁。
        # 基础平台必须也在矩阵里、且是实体目标 —— 否则源码树没有来源，或者来源本身也没人校验。
        overlay_base = t.get("overlay_base")
        if overlay_base is not None:
            if not isinstance(overlay_base, str) or not overlay_base.strip():
                fail(f"目标 {name} 的 overlay_base 必须是非空字符串（基础平台的工程路径）")
            else:
                base_name = next((k for k, bt in targets.items()
                                  if isinstance(bt, dict) and bt.get("project") == overlay_base), None)
                if overlay_base == t["project"]:
                    fail(f"目标 {name} 的 overlay_base 不能指向自己")
                elif base_name is None:
                    fail(f"目标 {name} 的 overlay_base={overlay_base} 不在矩阵里 —— "
                         f"增量目标的源码树必须有来源，且来源也要由同一份矩阵校验")
                else:
                    base = targets[base_name]
                    if base.get("overlay_base"):
                        fail(f"目标 {name} 的 overlay_base 指向 {base_name}，但 {base_name} 自己也是"
                             f"增量目标 —— 装配脚本只复制实体平台的 src/，不支持链式增量")
                    if base.get("mappings") != t["mappings"]:
                        fail(f"目标 {name}（mappings={t['mappings']}）的 overlay_base 是 {base_name}"
                             f"（mappings={base.get('mappings')}）—— 复制过来的源码用的是另一套映射名，编不过")
                    if base.get("loader") != t["loader"]:
                        fail(f"目标 {name}（loader={t['loader']}）的 overlay_base 是 {base_name}"
                             f"（loader={base.get('loader')}）—— 复制过来的平台代码绑的是另一个加载器")
                    if not (ROOT / overlay_base).is_dir():
                        fail(f"目标 {name} 的 overlay_base 工程目录 {overlay_base} 不存在")
            if exists and not (project / "patches").is_dir():
                fail(f"目标 {name} 是增量目标，但工程目录里没有 patches/ —— 差异没有落点")

        if t["buildable"] and not exists:
            fail(f"目标 {name} 声明 buildable: true，但工程目录 {t['project']} 不存在")
        if t["buildable"] and t.get("unverified"):
            fail(f"目标 {name} 是 buildable: true，却还留着 unverified：{t['unverified']}")
        if exists:
            for needed in ("build.gradle", "settings.gradle", "gradlew"):
                if not (project / needed).is_file():
                    fail(f"目标 {name} 的工程目录缺 {needed}")
            check_platform_identity(name, project)
            check_platform_toolchain(name, project, t)

    # 映射家族不止一个时（本仓就是：Fabric 走 Yarn，另两端走官方名），
    # 每个【有两个以上成员在建】的家族都要有对应的映射层 —— 那种家族里的平行副本是可共享的，
    # 让它们各存一份就是把「三端同步」缩小成「两端同步」。只有一个成员的家族不必成层（没人可共享）；
    # 层存在时，未挂该层的目标要在自己平台目录里保留同路径副本，那份副本由孪生检查盯着。
    if len(mappings) > 1:
        for family in sorted(mappings):
            # 增量目标不保留自己的物理副本：它在构建期复制 overlay_base 的源码树，
            # 所以同一映射家族里多一个增量目标并没有多出一份手抄副本。
            built = [n for n in mappings[family]
                     if (ROOT / targets[n]["project"]).is_dir()
                     and not targets[n].get("overlay_base")]
            declared = [n for n, d in layers.items()
                        if layer_axes(d) == ["mappings"] and d.get("mappings") == family]
            if len(built) > 1 and not declared:
                fail(f"mappings={family} 有 {len(built)} 个在建目标（{', '.join(built)}），"
                     f"但 layers.json 里没有纯映射层声明 mappings={family} —— "
                     f"它们本可共用一份，现在只能各存一份没人盯着的手抄副本")
                continue
            for layer_name in declared:
                check_mapping_twins(layer_name, layers[layer_name], targets, family,
                                    aliases.get(f"layers/mapping/{layer_name}", {}))

    # 反方向：某一家族已经有映射层了，同家族的目标就必须挂它 —— 否则它会留一份私藏副本，
    # 而那份副本与层里的内容本应逐字相同，没有任何东西会告诉你它慢慢不一样了。
    for name, t in targets.items():
        for layer_name, d in layers.items():
            if layer_axes(d) != ["mappings"] or d.get("mappings") != t.get("mappings"):
                continue
            if layer_name not in t["layers"]:
                fail(f"目标 {name} 的 mappings 是 {t.get('mappings')}，映射层 {layer_name} 就是为这个家族准备的，"
                     f"但它的 layers 里没有这一层 —— 那样它会留一份没人盯着的私藏副本")

    # 矩阵是规则：每个出现过的 Minecraft 版本都要凑齐三个加载器
    mcs = sorted({mc for mc, _ in seen_combo})
    for mc in mcs:
        for loader in LOADERS:
            if (mc, loader) not in seen_combo:
                fail(f"Minecraft {mc} 缺 {loader} 条目 —— 新增一个版本 = 加三条；实在不做也要写 "
                     f"buildable: false 并在 note 里写清为什么，缺了要在这里看得见")

    # 工程目录存在就必须在矩阵里
    platforms = ROOT / "platforms"
    if platforms.is_dir():
        declared = {t["project"] for t in targets.values() if isinstance(t.get("project"), str)}
        for child in sorted(p for p in platforms.iterdir() if p.is_dir()):
            rel = f"platforms/{child.name}"
            if rel not in declared:
                fail(f"{rel} 存在但不在 targets.json 里 —— 那样它是一份没有任何地方编的源码")


def _patch_paths(patch: pathlib.Path) -> set:
    """补丁头里的文件路径：生成树根相对，与 overlay_exclude 同一坐标系。"""
    paths = set()
    for line in patch.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.startswith("+++ b/") or line.startswith("--- a/"):
            paths.add(line[6:].split("\t", 1)[0].strip())
    return paths


def check_overlay_exclusions(targets: dict, baselines_raw: dict, update: bool):
    """overlay_exclude 的两条硬约束：文件确实来自基础平台，且目标真的拿本地实现顶上。

    排除不是删除：被排除的 Java 文件若没有目标本地实现，上游调用方按类名引用时编不过。
    资源文件允许只排除不替换（例如新版本已不再读取的着色器定义 JSON），所以本地实现检查
    只针对 .java。

    基线哈希盯的是另一半：本地实现是对着基础平台当时那份源码写的。上游一改，两者就可能
    悄悄对不上，所以漂移默认失败；人工确认后用 --update-overlay-baselines 接受新基线 ——
    这个动作本身要留下评审痕迹，不能是构建时自动放行。

    返回 update 模式下要写入的完整基线表；只校验时返回 None。
    """
    baselines = {k: v for k, v in baselines_raw.items() if not k.startswith("_")}
    current_all = {}
    excluded_targets = set()

    for name, t in targets.items():
        if not isinstance(t, dict) or "overlay_exclude" not in t:
            continue
        excluded_targets.add(name)
        if not isinstance(t.get("project"), str):
            continue  # 缺字段由 check_targets 报，这里不重复也不崩
        excludes = t.get("overlay_exclude")
        base_rel = t.get("overlay_base")
        if not isinstance(base_rel, str) or not base_rel:
            fail(f"目标 {name} 有 overlay_exclude，但没有 overlay_base —— "
                 f"只有增量目标才有「哪些基础文件不复制」这回事")
            continue
        if not isinstance(excludes, list) or not excludes:
            fail(f"目标 {name} 的 overlay_exclude 必须是非空数组 —— 空表就该删掉这个键")
            continue
        project = ROOT / t["project"]
        seen = set()
        current = {}
        for entry in excludes:
            if not isinstance(entry, str) or not entry.strip():
                fail(f"目标 {name} 的 overlay_exclude 含空值或非字符串项")
                continue
            rel = entry.strip().replace("\\", "/")
            if rel.startswith("/") or ".." in rel.split("/"):
                fail(f"目标 {name} 的 overlay_exclude 必须是基础平台工程内的相对路径：{rel}")
                continue
            if rel in seen:
                fail(f"目标 {name} 的 overlay_exclude 有重复项：{rel}")
                continue
            seen.add(rel)
            if not rel.startswith(OVERLAY_COPY_ROOTS):
                fail(f"目标 {name} 的 overlay_exclude 只能排除构建期会复制的那四类路径"
                     f"（src/main|test 下的 java / resources）：{rel}")
                continue
            base_file = ROOT / base_rel / rel
            if not base_file.is_file():
                fail(f"目标 {name} 的 overlay_exclude 指向基础平台不存在的文件：{base_rel}/{rel}")
                continue
            current[rel] = digest(base_file)
            if rel.endswith(".java") and project.is_dir() and not (project / rel).is_file():
                fail(f"目标 {name} 排除了 {rel}，但工程目录里没有同路径的本地实现 —— "
                     f"排除不是删除：类名与公开 API 仍要对上游调用方成立")

        patches = project / "patches"
        if patches.is_dir():
            for patch in sorted(patches.glob("*.patch")):
                for rel in sorted(seen & _patch_paths(patch)):
                    fail(f"补丁 {patch.relative_to(ROOT).as_posix()} 修改了被 overlay_exclude 排除的"
                         f"文件 {rel} —— 它根本不在生成树里；该文件应由目标本地实现维护")

        current_all[name] = current
        if update:
            continue
        declared = baselines.get(name)
        if not isinstance(declared, dict):
            fail(f"目标 {name} 声明了 overlay_exclude，但 {OVERLAY_BASELINES} 里没有它的基线 —— "
                 f"跑 python3 tools/verify_targets.py --update-overlay-baselines 生成")
            continue
        for rel in sorted(set(declared) - set(current)):
            fail(f"{OVERLAY_BASELINES} 里 {name} 记录了已不在 overlay_exclude 的文件：{rel} —— "
                 f"删掉这条基线，或把它放回排除表")
        for rel in sorted(set(current) & set(declared)):
            if current[rel] != declared[rel]:
                fail(f"基础平台的上游文件变了：{base_rel}/{rel}（目标 {name} 的本地实现以旧版本"
                     f"为基准）。先人工确认本地实现仍然对得上，再跑 "
                     f"python3 tools/verify_targets.py --update-overlay-baselines 接受新基线")

    if not update:
        for name in sorted(set(baselines) - excluded_targets):
            fail(f"{OVERLAY_BASELINES} 里有目标 {name} 的基线，但 targets.json 里它没有 "
                 f"overlay_exclude —— 删掉这条基线")
    return current_all if update else None


def check_platform_identity(name: str, project: pathlib.Path) -> None:
    props = read_props(project / "gradle.properties")
    dupes = [k for k in IDENTITY_KEYS if k in props]
    if dupes:
        fail(f"目标 {name} 的 gradle.properties 自己定义了身份键：{', '.join(dupes)} —— "
             f"身份只在仓库根那一份里，这里留一份就会各自漂移")


def check_platform_toolchain(name: str, project: pathlib.Path, t: dict) -> None:
    text = (project / "build.gradle").read_text(encoding="utf-8", errors="replace")
    m = re.search(r"JavaLanguageVersion\.of\((\d+)\)", text)
    if m and int(m.group(1)) != int(t["java"]):
        fail(f"目标 {name} 的 targets.json 写 java={t['java']}，"
             f"但 build.gradle 的 toolchain 是 {m.group(1)}")

    wrapper = project / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if wrapper.is_file():
        m = re.search(r"gradle-([0-9][^-]*)", wrapper.read_text(encoding="utf-8", errors="replace"))
        if m and m.group(1) != str(t["gradle"]):
            fail(f"目标 {name} 的 targets.json 写 gradle={t['gradle']}，"
                 f"但 wrapper 是 {m.group(1)}")


def _root_of(line: str) -> str:
    """从「包名  — 说明」这种声明行里取出包名。"""
    return line.split(" — ")[0].strip()


def layer_axes(d: dict) -> list:
    return [a for a in ("since", "loader", "mappings") if a in d]


def layer_dir(layer_name: str, d: dict) -> pathlib.Path:
    """目录由声明的轴推出来 —— 声明与目录不会互相矛盾。"""
    axis_dirs = {"since": "version", "loader": "loader", "mappings": "mapping"}
    return ROOT / "layers" / "+".join(sorted(axis_dirs[a] for a in layer_axes(d))) / layer_name


def version_at_least(have: str, want: str) -> bool:
    def parts(v):
        return [int(p) if str(p).isdigit() else 0 for p in str(v).split(".")]

    a, b = parts(have), parts(want)
    for i in range(max(len(a), len(b))):
        x, y = (a[i] if i < len(a) else 0), (b[i] if i < len(b) else 0)
        if x != y:
            return x > y
    return True


def predicate_gap(t: dict, layer_name: str, d: dict):
    """目标挂了这个层，但它满足不了该层的谓词 —— 返回原因，满足则 None。"""
    for axis in layer_axes(d):
        want = str(d[axis])
        if axis == "since":
            if not version_at_least(t["minecraft"], want):
                return f"Minecraft {t['minecraft']} 够不到 since {want}"
        else:
            have = str(t.get(axis))
            if have != want:
                return f"它的 {axis} 是 {have}，层要求 {axis}={want}"
    return None


def digest(path: pathlib.Path) -> str:
    """行尾无关的比较。

    工作区是 CRLF、仓库里存的是 LF（`.gitattributes` 的 `* text=auto` 管这件事），
    所以直读字节会把"只有行尾不同"判成内容不同 —— 那样"孪生副本逐字相同"这条检查
    会假阴性（该报的不报）。比较前先折叠行尾，让这条检查只看内容。
    """
    return hashlib.sha1(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def check_mapping_twins(layer_name: str, d: dict, targets: dict, family: str, aliases: dict) -> None:
    """映射层的代价是「另一个家族保留同路径的孪生副本」。这份副本是手抄的，
    所以必须有人在盯着：少一个、或者其实逐字相同（那它本该进 shared/）都要红。

    aliases 是路径别名：有的类在两个家族上包路径不同（Fabric 侧故意住在原版包下，
    借包私有访问达成与 AT 同样的目的）。别名把这一处写下来，检查照样按对应路径去找，
    找不到一样红 —— 别名不是豁免，只是让例外变成可检查的声明。"""
    base = layer_dir(layer_name, d)
    if not base.is_dir():
        return
    files = [p.relative_to(base).as_posix() for p in base.rglob("*.java")]
    for rel in sorted(files):
        if not rel.startswith(("src/main/java/", "src/test/java/")):
            continue
        twin_rel = aliases.get(rel, rel)
        for name, t in targets.items():
            if t.get("mappings") == family:
                continue
            if t.get("overlay_base"):
                # 增量目标没有自己的源码树，它用的孪生副本就是 overlay_base 平台里那一份。
                # base 必须是矩阵里同映射家族的实体目标（见 check_targets），那一轮已经
                # 按同一批路径查过「存在且不是逐字相同」，这里不重复报。
                continue
            project = ROOT / t["project"]
            if not project.is_dir():
                continue  # 还没建起来的目标不欠副本
            twin = project / twin_rel
            if not twin.is_file():
                hint = f"（按别名找的是 {twin_rel}）" if twin_rel != rel else ""
                fail(f"映射层 {layer_name} 里有 {rel}，但目标 {name}"
                     f"（mappings={t.get('mappings')}）的平台目录里没有对应的孪生副本{hint} —— "
                     f"这条路线的代价就是那份平行副本，少了它意味着这个类在那个目标上根本不存在")
            elif digest(twin) == digest(base / rel):
                fail(f"{rel} 的孪生副本与映射层里那份【逐字相同】—— 它其实是映射中立的，"
                     f"应该进 shared/，不必在这里共存两份")


def check_layers(layers: dict) -> None:
    if not layers:
        fail("versions/layers.json 里一层都没有")
        return
    for name, d in layers.items():
        axes = layer_axes(d)
        if not axes:
            fail(f"层 {name} 一个轴都没钉（since / loader / mappings）—— 一个都不写就是 shared/，不是层")
        if not isinstance(d.get("populated"), bool):
            fail(f"层 {name} 没有声明 populated（true/false）—— 「空」要是显式声明的状态")
        directory = layer_dir(name, d)
        if d.get("populated") and not directory.is_dir():
            fail(f"层 {name} 声明 populated: true，但目录 {directory.relative_to(ROOT).as_posix()} 不存在")
        if directory.is_dir() and not d.get("populated"):
            files = [p for p in directory.rglob("*") if p.is_file()]
            if files:
                fail(f"层 {name} 声明 populated: false，但目录里有 {len(files)} 个文件 —— "
                     f"要么翻成 true，要么把内容挪走")


def read_lines(rel: str) -> list:
    path = ROOT / rel
    if not path.is_file():
        fail(f"{rel} 不存在")
        return []
    return [l.strip() for l in path.read_text(encoding="utf-8", errors="replace").splitlines()
            if l.strip() and not l.strip().startswith("#")]


def parse_accesswidener(rel: str) -> set:
    """AW 行形如：accessible field <owner> <member> <desc>（命名空间在文件头的 named）。"""
    out = set()
    for line in read_lines(rel):
        parts = line.split()
        if len(parts) >= 4 and parts[0] == "accessible":
            out.add(f"{parts[2]} {parts[3]}")
    return out


def parse_accesstransformer(rel: str) -> set:
    """AT 行形如：public <owner.dotted> <member>。"""
    out = set()
    for line in read_lines(rel):
        parts = line.split()
        if len(parts) >= 3 and parts[0] in ("public", "public-f"):
            out.add(f"{parts[1]} {parts[2]}")
    return out


def check_access_parity(parity: dict) -> None:
    """三份访问扩宽文件必须覆盖同一批成员，各写自己家族的命名。

    判据（不需要映射表也能咬住真实的漂移）：
      · Fabric AW 的成员集合 == 声明表 yarn 侧
      · NeoForge AT 的成员集合 == 声明表非 null 的 official 侧
      · Forge AT 的**类名集合**与 NeoForge 相同、条数相同，成员名必须是 SRG 形状
      · NeoForge AT 的成员名不许是 SRG 形状（写错家族的常见翻车）
    """
    files = parity.get("files", {})
    members = parity.get("members", [])
    if not files or not members:
        fail("versions/access-parity.json 缺 files 或 members")
        return

    yarn = parse_accesswidener(files["yarn"])
    official = parse_accesstransformer(files["official"])
    srg = parse_accesstransformer(files["srg"])

    declared_yarn = {m["yarn"] for m in members}
    declared_official = {m["official"] for m in members if m.get("official")}

    for label, actual, declared in (("Fabric AW", yarn, declared_yarn),
                                    ("NeoForge AT", official, declared_official)):
        for missing in sorted(declared - actual):
            fail(f"{label} 里没有声明表写着的成员：{missing} —— 声明与文件不一致")
        for extra in sorted(actual - declared):
            fail(f"{label} 里有声明表没有的成员：{extra} —— "
                 f"把它写进 versions/access-parity.json，并给出另一侧的对应项或说明为什么不需要")

    srg_shape = re.compile(r"^f_\d+_$")
    for entry in sorted(official):
        if srg_shape.match(entry.split()[-1]):
            fail(f"NeoForge 的 AT 写了 SRG 名（{entry}）—— 那一侧要写官方名；SRG 名只在 Forge 那侧")

    official_owners = {e.split()[0] for e in official}
    srg_owners = {e.split()[0] for e in srg}
    if official_owners != srg_owners:
        fail(f"两份 AT 的类名集合不一致：NeoForge {sorted(official_owners)} vs Forge {sorted(srg_owners)}")
    if len(official) != len(srg):
        fail(f"两份 AT 条数不一致：NeoForge {len(official)} 条，Forge {len(srg)} 条")
    for entry in sorted(srg):
        if not srg_shape.match(entry.split()[-1]):
            fail(f"Forge 的 AT 写了非 SRG 名（{entry}）—— 那一侧运行时用 SRG 名，写官方名会静默不生效")


def check_resource_paths(paths: dict) -> None:
    """逐目标比对资源落点：着色器命名空间、语言文件、sounds.json。

    放错命名空间不会报错，只会在运行期静默降级（模糊没了 / 界面退回英文），
    所以这里按声明逐项相等来卡 —— 多一个少一个都红。
    """
    targets = paths.get("targets", {})
    if not targets:
        fail("versions/resource-paths.json 缺 targets")
        return
    names = paths.get("shader_names", [])
    exts = paths.get("shader_extensions", [])
    langs = paths.get("languages", [])
    also = paths.get("also_required", [])

    for name, t in targets.items():
        res = ROOT / "platforms" / name / "src/main/resources"
        if not res.is_dir():
            continue  # 还没建起来的目标
        expected = set(also)
        for shader in names:
            for ext in exts:
                expected.add(f"{t['shaders_root']}/{shader}{ext}")
        for lang in langs:
            expected.add(f"{t['lang_root']}/{lang}.json")

        # 只比声明的两类根（着色器 / 语言）与显式列出的文件 —— 元数据与 mixin 配置
        # （fabric.mod.json、atomchat.*.mixins.json）不属于这里，早先按「所有 .json」
        # 过滤会把它们算成「多出来的资源」。
        def interesting(rel: str) -> bool:
            return ("/shaders/" in rel or "/lang/" in rel or rel in also)

        actual = {p.relative_to(res).as_posix() for p in res.rglob("*")
                  if p.is_file() and interesting(p.relative_to(res).as_posix())}
        for missing in sorted(expected - actual):
            fail(f"目标 {name} 缺资源 {missing} —— 放错命名空间是静默失效，不报错")
        for extra in sorted(actual - expected):
            fail(f"目标 {name} 多出未声明的资源 {extra} —— 写进 versions/resource-paths.json，"
                 f"或删掉它（多出来的资源不会被加载，只会让人以为生效了）")


def _strip_comments(text):
    """逐行剥掉注释（块注释 + 行注释）。

    不用正则：这份文件要跨三端与 CI 跑到，少一层转义就少一类「本地能跑、CI 不能跑」的麻烦。
    近似：字符串字面量里的 // 也会被剥掉，但服务端侧这些类里没有那种写法。
    """
    NL = chr(10)  # 不写字面量反斜杠：这份文件要跨三端与 CI，少一层转义少一类环境差异
    out = []
    in_block = False
    for line in text.split(NL):
        if in_block:
            idx = line.find('*/')
            if idx < 0:
                continue
            line = line[idx + 2:]
            in_block = False
        start = line.find('/*')
        while start >= 0:
            idx = line.find('*/', start + 2)
            if idx < 0:
                line = line[:start]
                in_block = True
                break
            line = line[:start] + line[idx + 2:]
            start = line.find('/*', start)
        cut = line.find('//')
        if cut >= 0:
            line = line[:cut]
        out.append(line)
    return NL.join(out)


def check_server_side(cfg: dict) -> None:
    """服务端侧的类不许引用客户端类型（专用服务端上会 NoClassDefFoundError）。

    判据是源码级标记扫描（先剥注释）。patterns 是匹配式而不是清单 —— 新加的服务端类自动
    被覆盖，不必记得来登记；每个匹配式至少命中一个文件，命中为零说明匹配式坏了或布局变了，
    那也要红（否则这类检查会悄悄变成什么都不查）。
    """
    markers = cfg.get("markers", [])
    patterns = cfg.get("patterns", [])
    if not markers or not patterns:
        fail("versions/server-side.json 缺 markers 或 patterns")
        return

    seen = 0
    for pattern in patterns:
        hits = sorted(ROOT.glob(pattern))
        if not hits:
            fail(f"server-side.json 的匹配式一个文件都没命中：{pattern} —— 布局变了？")
            continue
        for f in hits:
            if not f.is_file():
                continue
            seen += 1
            text = _strip_comments(f.read_text(encoding="utf-8", errors="replace"))
            for marker in markers:
                if marker in text:
                    fail(f"{f.relative_to(ROOT).as_posix()} 是服务端侧的类，却引用了客户端类型 "
                         f"{marker} —— 专用服务端上会 NoClassDefFoundError")
    minimum = int(cfg.get("min_files", 1))
    if seen < minimum:
        fail(f"服务端侧只扫到 {seen} 个文件（声明的下限 {minimum}）—— 匹配式或布局出问题了")


def check_shared_java_level() -> None:
    """共享层必须能在**最低**的编译级别上编过，否则某个目标才炸。

    shared/ 一份源码进所有目标，而各目标的 java 级别不同（本仓是 17 与 21）。Java 17 上
    「模式匹配 switch」还只是预览特性 —— 用了它的后果不是这里报错，而是 Java 17 那个目标的
    `./gradlew build` 突然红（踩过一次：Wire.write 里的 sealed 分派）。

    判据是源码级的形状扫描：`case <类型> <名字> ->`／`:` 这种开关分支。写在注释里的例子会被
    先剥掉；真要用这类语法，就说明这段代码属于映射层或平台层，不属于共享层。
    """
    import re

    pattern = re.compile(r"^\s*case\s+[A-Za-z_][\w.]*(<[^>]*>)?\s+[a-z_]\w*\s*(->|:)", re.M)
    levels = [t.get("java") for t in entries_of(load_json("versions/targets.json")).values()
              if isinstance(t, dict) and t.get("java")]
    if not levels:
        fail("versions/targets.json 里没有任何目标声明 java 级别")
        return
    lowest = min(int(v) for v in levels)
    if lowest >= 21:
        notes.append(f"所有目标的 java 级别都 ≥ {lowest}，共享层不受 17 的预览特性限制")
        return
    scanned = 0
    for f in sorted((ROOT / "shared" / "src").rglob("*.java")):
        scanned += 1
        text = _strip_comments(f.read_text(encoding="utf-8", errors="replace"))
        for m in pattern.finditer(text):
            line = text[:m.start()].count("\n") + 1
            fail(f"{f.relative_to(ROOT).as_posix()}:{line} 在共享层里用了模式匹配 switch —— "
                 f"java {lowest} 的目标编不过（那边这还是预览特性）。"
                 f"把它挪到映射层/平台层，或在共享层改用 instanceof 链")
    if scanned < 20:
        fail(f"共享层只扫到 {scanned} 个文件 —— 路径或布局变了？")


def check_shared_dependencies(apis: dict) -> None:
    """共享层与各层里出现的第三方包，必须落在声明表内。

    这不是白名单制度，而是一张「我确实依赖了这个」的声明表：漏声明的表现是某个目标没带上
    那个库 —— 编得过、跑起来 NoClassDefFoundError。原版与加载器提供的包单列一档（不必声明），
    测试框架也单列（不进产物）。
    """
    import re

    declared = [k for k in apis if not k.startswith("_")]
    provided = [_root_of(line) for line in apis.get("_provided", []) if " — " in line]
    test_only = [_root_of(line) for line in apis.get("_test_only", []) if " — " in line]
    allowed = declared + provided
    if not declared:
        fail("versions/third-party-apis.json 里一个第三方包都没声明")
        return

    imports = re.compile(r"^\s*import\s+(?:static\s+)?([A-Za-z_][A-Za-z0-9_.]*)"
                        r"(?:\.([A-Za-z_][A-Za-z0-9_.]*))?;", re.M)
    for base in ("shared/src", "layers"):
        for f in sorted((ROOT / base).rglob("*.java")):
            is_test = "/test/" in f.as_posix()
            ok_prefixes = list(allowed) + (test_only if is_test else [])
            for m in imports.finditer(f.read_text(encoding="utf-8", errors="replace")):
                pkg = m.group(1) + ("." + m.group(2) if m.group(2) else "")
                if pkg.startswith(("java.", "javax.", "com.atom.")):
                    continue
                if any(pkg == pre or pkg.startswith(pre + ".") for pre in ok_prefixes):
                    continue
                fail(f"{f.relative_to(ROOT).as_posix()} 引用了未声明的第三方包 {pkg} —— "
                     f"把它写进 versions/third-party-apis.json（@provided 那一档是原版/加载器提供的）")


def check_seams_doc() -> None:
    """接缝清单必须与代码同步。

    清单是生成物（tools/gen_seams.py 扫 platforms/ 与 shared/layers 产出），
    这里只调它的 --check，免得同一套渲染逻辑写两份。
    """
    import subprocess

    proc = subprocess.run(
        [sys.executable, str(ROOT / "tools/gen_seams.py"), "--check"],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        detail = (proc.stderr or proc.stdout or "").strip().splitlines()
        fail("shared/PLATFORM-SEAMS.md 与代码不同步 —— 跑 python3 tools/gen_seams.py 重新生成"
             + (f"（{detail[0]}）" if detail else ""))


def check_aliases(aliases: dict) -> None:
    """别名必须指向真实存在的层内文件 —— 别名的价值在于它可检查，腐坏的别名会静默失效。"""
    for layer_rel, mapping in aliases.items():
        for key, value in mapping.items():
            if not (ROOT / layer_rel / key).is_file():
                fail(f"versions/mapping-aliases.json 里的 {layer_rel}/{key} 不存在 —— "
                     f"别名指向了一个没有的文件，这条对应关系已经腐坏")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix-out", help="把 CI 矩阵写成 JSON 到这个路径")
    parser.add_argument("--update-overlay-baselines", action="store_true",
                        help="人工确认目标本地实现与基础平台新版本一致后，接受新的上游基线")
    args = parser.parse_args()

    baselines_path = ROOT / OVERLAY_BASELINES
    if baselines_path.is_file() or not args.update_overlay_baselines:
        baselines_raw = load_json(OVERLAY_BASELINES)
    else:
        baselines_raw = {}

    targets_raw = load_json("versions/targets.json")
    layers_raw = load_json("versions/layers.json")
    load_json("versions/third-party-apis.json")
    aliases_raw = load_json("versions/mapping-aliases.json")
    parity_raw = load_json("versions/access-parity.json")
    paths_raw = load_json("versions/resource-paths.json")
    server_raw = load_json("versions/server-side.json")
    targets = entries_of(targets_raw)
    layers = entries_of(layers_raw)
    aliases = {k: v for k, v in aliases_raw.items() if not k.startswith("_")}

    check_root_identity()
    check_layers(layers)
    check_aliases(aliases)
    check_access_parity(parity_raw)
    check_resource_paths(paths_raw)
    check_shared_dependencies(load_json("versions/third-party-apis.json"))
    check_shared_java_level()
    check_server_side(server_raw)
    check_seams_doc()
    check_targets(targets, layers, aliases)
    updated_baselines = check_overlay_exclusions(targets, baselines_raw,
                                                 args.update_overlay_baselines)

    # 摘要（CI 日志里看这一份）
    print(f"目标 {len(targets)} 条：")
    for name, t in targets.items():
        if not isinstance(t, dict) or "project" not in t:
            continue
        mark = "发" if t.get("buildable") else "不发"
        exists = "有工程" if (ROOT / t["project"]).is_dir() else "无工程"
        excluded = t.get("overlay_exclude") or []
        excl = f" excl={len(excluded)}" if excluded else ""
        print(f"  {name:18} mc={t.get('minecraft'):7} loader={t.get('loader'):9} "
              f"java={t.get('java')} gradle={t.get('gradle')} {mark}/{exists} "
              f"layers={t.get('layers')}{excl}")

    if errors:
        print("\n校验未通过：", file=sys.stderr)
        for e in errors:
            print(f"  ✘ {e}", file=sys.stderr)
        if args.update_overlay_baselines:
            print(f"\n{OVERLAY_BASELINES} 未写入：先修掉上面的问题。", file=sys.stderr)
        return 1

    print("\n校验通过。")
    for n in notes:
        print(f"  · {n}")

    if args.update_overlay_baselines:
        payload = {}
        if isinstance(baselines_raw.get("_comment"), list):
            payload["_comment"] = baselines_raw["_comment"]
        for name in sorted(updated_baselines or {}):
            payload[name] = dict(sorted(updated_baselines[name].items()))
        baselines_path.write_text(
            json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        count = sum(len(v) for v in (updated_baselines or {}).values())
        print(f"\n{OVERLAY_BASELINES} 已更新：{len(updated_baselines or {})} 个目标，{count} 条基线。")

    if args.matrix_out:
        # 产物名后缀在这里算好，不交给工作流里的表达式：
        # GitHub 表达式的 `a && '' || b` 因为空串是 falsy 会静默取到 b，
        # 于是"没验证"的标记会贴到正式产物上（踩过一次）。
        matrix = [
            {
                "target": name,
                "project": t["project"],
                "java": t["java"],
                "buildable": bool(t["buildable"]),
                "suffix": "" if t["buildable"] else "-未验证",
            }
            for name, t in targets.items()
            if isinstance(t, dict) and (ROOT / t.get("project", "")).is_dir()
        ]
        matrix.sort(key=lambda e: e["target"])
        with open(args.matrix_out, "w", encoding="utf-8") as f:
            json.dump(matrix, f)
        print(f"\nCI 矩阵（{len(matrix)} 个目标）已写入 {args.matrix_out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
