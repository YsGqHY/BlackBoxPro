"""BlackBoxPro skill 文档校验。

设计原则（2026-10 修订）：
  * **唯一真相来源是源码，不是文档里的数字。** 本脚本所有期望值都从
    `ActionCatalog.kt` + 各客户端 `ActionRegistry.kt` 现场解析出来；
    文档里禁止写死 action 总数，脚本会把"写了总数"当作错误。
  * 文档（`reference/action-catalog.md`）的职责是**覆盖全体客户端并集**，
    并由文末 `bbp-action-matrix` 块声明"每个客户端相对并集缺哪些 id"，
    本脚本把该声明与源码 diff 逐项比对 —— 注册表一漂移，CI 立刻红。
  * 只依赖标准库，不联网、不构建、不启动 MC。

用法：
    python .claude/skills/validate_blackboxpro_skills.py
退出码 0 = 一致；1 = 有不一致（逐条打印原因）。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

ACTION_CATALOG = ROOT / "common/src/main/kotlin/com/blackboxpro/common/action/ActionCatalog.kt"
ACTION_REFERENCE = ROOT / ".claude/skills/blackboxpro-test/reference/action-catalog.md"
BLACKBOX_SKILL = ROOT / ".claude/skills/blackboxpro-test/SKILL.md"
ADD_VERSION_SKILL = ROOT / ".claude/skills/add-mc-version/skill.md"
EXECUTE_HANDLER = ROOT / "plugin/src/main/kotlin/com/blackboxpro/plugin/http/ExecuteHandler.kt"

#: 客户端注册表。key 必须与 action-catalog.md 的 bbp-action-matrix 块逐字一致
#: （key 变更时脚本会报错，逼着你同步文档）。
CLIENT_REGISTRIES = {
    "1.12.2-forge": ROOT / "mod/1.12.2/forge/src/main/kotlin/com/blackboxpro/forge/dispatcher/ActionRegistry.kt",
    "1.21.1-fabric": ROOT / "mod/1.21.1/fabric/src/main/kotlin/com/blackboxpro/fabric/dispatcher/ActionRegistry.kt",
    "1.21.1-neoforge": ROOT / "mod/1.21.1/runtime/src/main/kotlin/com/blackboxpro/neoforge/dispatcher/ActionRegistry.kt",
    "1.21.11-fabric": ROOT / "mod/1.21.11/fabric/src/main/kotlin/com/blackboxpro/fabric/dispatcher/ActionRegistry.kt",
    "1.21.11-neoforge": ROOT / "mod/1.21.11/runtime/src/main/kotlin/com/blackboxpro/neoforge/dispatcher/ActionRegistry.kt",
}

#: 插件侧本地处理、不经过客户端注册表的 action（ExecuteHandler 用字面量拦下）。
PLUGIN_ONLY_ACTIONS = {"run_test", "stop_server"}

REGISTER_RE = re.compile(r'register\("([^"]+)"')
MD_TABLE_ACTION_RE = re.compile(r"^\|\s*`([^`]+)`\s*\|", re.MULTILINE)
#: 文档里出现"共 N 个 Action"一律视为错误（计数必然过期，见模块 docstring）。
FORBIDDEN_COUNT_RE = re.compile(r"共\s*\d+\s*个\s*Action")
MATRIX_RE = re.compile(r"<!--\s*bbp-action-matrix:begin(.*?)bbp-action-matrix:end\s*-->", re.DOTALL)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def extract_registered_actions(path: Path) -> list[str]:
    return REGISTER_RE.findall(read_text(path))


def extract_reference_actions(path: Path) -> list[str]:
    return [action for action in MD_TABLE_ACTION_RE.findall(read_text(path)) if action not in PLUGIN_ONLY_ACTIONS]


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def split_ids(raw: str) -> set[str]:
    return {item.strip() for item in raw.split(",") if item.strip()}


def parse_matrix(text: str) -> tuple[set[str] | None, dict[str, set[str]]]:
    """解析文末 bbp-action-matrix 块：catalog-missing= / key= / unsupported=。"""
    match = MATRIX_RE.search(text)
    if match is None:
        return None, {}

    catalog_missing: set[str] | None = None
    deltas: dict[str, set[str]] = {}
    current_key: str | None = None
    for raw_line in match.group(1).splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("catalog-missing="):
            catalog_missing = split_ids(line.split("=", 1)[1])
        elif line.startswith("key="):
            current_key = line.split("=", 1)[1].strip()
            deltas[current_key] = set()
        elif line.startswith("unsupported="):
            if current_key is None:
                continue
            deltas[current_key] = split_ids(line.split("=", 1)[1])
    return catalog_missing, deltas


def describe(title: str, items: set[str] | list[str]) -> str:
    ordered = sorted(items)
    return f"{title}: {', '.join(ordered) if ordered else '（空）'}"


def main() -> int:
    errors: list[str] = []

    # ---------------------------------------------------------------- 源码侧
    catalog_actions = extract_registered_actions(ACTION_CATALOG)
    catalog_set = set(catalog_actions)
    require(bool(catalog_actions), "ActionCatalog 为空或解析失败", errors)
    require(len(catalog_actions) == len(catalog_set), "ActionCatalog 存在重复 action id", errors)

    registry_sets: dict[str, set[str]] = {}
    for key, path in CLIENT_REGISTRIES.items():
        ids = extract_registered_actions(path)
        require(bool(ids), f"{key} 注册表为空或解析失败（{path.relative_to(ROOT)}）", errors)
        require(len(ids) == len(set(ids)), f"{key} 注册表存在重复 action id（{path.relative_to(ROOT)}）", errors)
        registry_sets[key] = set(ids)

    universe = set().union(*registry_sets.values()) if registry_sets else set()
    require(bool(universe), "客户端注册表并集为空，无法校验", errors)

    # 共享契约不该声明任何"没有客户端实现"的 action
    phantom = catalog_set - universe
    if phantom:
        errors.append(
            f"ActionCatalog 声明了没有任何客户端注册的 action（{describe('catalog - union', phantom)}）"
        )

    # 插件本地 action 必须在 ExecuteHandler 里被真正拦下
    execute_handler_text = read_text(EXECUTE_HANDLER)
    for action in sorted(PLUGIN_ONLY_ACTIONS):
        require(
            f'"{action}"' in execute_handler_text,
            f"PLUGIN_ONLY_ACTIONS 里的 {action} 没有在 ExecuteHandler 中处理（豁免理由不成立）",
            errors,
        )

    # ---------------------------------------------------------------- 文档侧
    reference_text = read_text(ACTION_REFERENCE)
    reference_set = set(extract_reference_actions(ACTION_REFERENCE))

    require(
        FORBIDDEN_COUNT_RE.search(reference_text) is None,
        "action-catalog.md 写死了 action 总数（如“共 N 个 Action”）；计数必须由源码动态复算，正文不得出现",
        errors,
    )

    missing_in_reference = universe - reference_set
    extra_in_reference = reference_set - universe
    if missing_in_reference or extra_in_reference:
        errors.append(
            "action-catalog.md 与客户端注册表并集不一致\n"
            f"  {describe('missing in reference', missing_in_reference)}\n"
            f"  {describe('extra in reference', extra_in_reference)}"
        )

    declared_catalog_missing, declared_deltas = parse_matrix(reference_text)
    if declared_catalog_missing is None:
        errors.append("action-catalog.md 缺少 bbp-action-matrix 块（或块格式被破坏）")
    else:
        actual_catalog_missing = universe - catalog_set
        if declared_catalog_missing != actual_catalog_missing:
            errors.append(
                "bbp-action-matrix 的 catalog-missing 与源码不一致\n"
                f"  {describe('declared', declared_catalog_missing)}\n"
                f"  {describe('actual', actual_catalog_missing)}"
            )

    if set(declared_deltas) != set(CLIENT_REGISTRIES):
        errors.append(
            "bbp-action-matrix 的 key 集合与 CLIENT_REGISTRIES 不一致\n"
            f"  declared: {', '.join(sorted(declared_deltas)) if declared_deltas else '（空）'}\n"
            f"  expected: {', '.join(sorted(CLIENT_REGISTRIES))}"
        )
    for key in sorted(set(declared_deltas) & set(CLIENT_REGISTRIES)):
        actual = universe - registry_sets[key]
        if declared_deltas[key] != actual:
            errors.append(
                f"bbp-action-matrix 的 {key} unsupported 与源码不一致\n"
                f"  {describe('declared', declared_deltas[key])}\n"
                f"  {describe('actual', actual)}"
            )

    # ---------------------------------------------------------------- skill 规则
    blackbox_skill_text = read_text(BLACKBOX_SKILL)
    require("旧实例清理状态机" in blackbox_skill_text, "blackboxpro-test/SKILL.md 缺少旧实例清理状态机说明", errors)
    require("resolvedVersion" in blackbox_skill_text, "blackboxpro-test/SKILL.md 缺少 resolvedVersion 动态版本规则", errors)
    require("\"action\":\"disconnect\"" not in blackbox_skill_text, "blackboxpro-test/SKILL.md 仍在示例请求中使用过时 disconnect action", errors)

    add_version_text = read_text(ADD_VERSION_SKILL)
    require("cp -r {ref_version} {mc_version}" in add_version_text, "add-mc-version/skill.md 没有使用 ref_version 作为复制模板", errors)
    require("旧实例清理状态机" in add_version_text, "add-mc-version/skill.md 缺少旧实例清理状态机说明", errors)
    require("selected_loader" in add_version_text, "add-mc-version/skill.md 缺少 selected_loader 动态 loader 规则", errors)
    require("cp -r 1.21.11 {mc_version}" not in add_version_text, "add-mc-version/skill.md 仍写死从 1.21.11 复制模板", errors)
    require("ref_passed = 54" not in add_version_text, "add-mc-version/skill.md 仍写死 ref_passed=54", errors)

    # ---------------------------------------------------------------- 结果
    if errors:
        print("BlackBoxPro skill 校验失败：", file=sys.stderr)
        for index, error in enumerate(errors, start=1):
            print(f"{index}. {error}", file=sys.stderr)
        return 1

    print("BlackBoxPro skills 校验通过")
    print(f"- ActionCatalog（共享契约）: {len(catalog_set)} actions")
    for key in sorted(registry_sets):
        gap = len(universe - registry_sets[key])
        print(f"- {key:16} registry: {len(registry_sets[key]):3} actions（相对并集缺 {gap}）")
    print(f"- 客户端注册表并集: {len(universe)} actions；action-catalog.md 覆盖 {len(reference_set)} 个")
    print("- bbp-action-matrix 与源码 diff 一致；文档未写死计数")
    print("- blackboxpro-test / add-mc-version 关键规则已校验")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
