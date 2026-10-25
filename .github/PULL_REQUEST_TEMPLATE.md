## 变更内容

<!-- 一句话说清改了什么、为什么。 -->

## 影响范围

- [ ] `common`（协议 / `ActionCatalog` 契约）
- [ ] `plugin`（服务端插件）
- [ ] `mod/*`（客户端 Mod）
- [ ] 文档 / 技能包（`README.md`、`.claude/`）

## 验证证据（必填）

<!-- 命令 + 原始输出。涉及 run_test 的必须给全四项数字。 -->

- 复现 / 验证命令：
- 结果：`passed=? / failed=? / skipped=? / total=?`
- 拿不出 `run_test` 数字时的替代验证方式：

## 检查

- [ ] 我没有把 `run_test` 的计数当成"被测插件通过了测试"——它只反映框架 action 在**当前环境**是否可用
- [ ] 断言强度已如实标注（只校验 `status=success` 的项不算行为断言）
- [ ] 改了 `ActionCatalog` / 各端 `ActionRegistry` 时，已同步更新 `.claude/skills/blackboxpro-test/reference/action-catalog.md` 与计数
- [ ] 新增 Minecraft 版本走的是 `.claude/skills/add-mc-version/skill.md` 的流程
- [ ] 文档里的数字可由命令复算；未验证的部分已明确标注
