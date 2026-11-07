# Hermes Plugin Catalog — compliance audit and submission gate

**Audit date:** 2026-11-06  
**Source of truth:** https://hermes-agent.nousresearch.com/docs/developer-guide/plugins/catalog-submission and the canonical https://github.com/NousResearch/hermes-agent/blob/main/plugin-catalog/README.md.  
**Scope:** NaNLMind Android / NaN0S integration as a *potential* Hermes catalog plugin. This is an evidence-based preflight, not a claim that either repository is currently eligible.

## Current disposition

**NOT READY — packaging blocker.** The inspected `runner-ring-math-bootstrap` trees in both `shemshallah/NaN0S` and `shemshallah/NaNLMind_android` have no root `plugin.yaml` and no root `plugin.json`. No Hermes-loadable entrypoint (`__init__.py`, `desktop/plugin.js`, `plugin.json`, or `dashboard/manifest.json`) was found at those roots. A catalog listing must point to a loadable plugin at the exact pinned commit. Do not create a catalog entry or claim validation success until a real plugin package and its supported integration surface exist.

A dedicated, source-only plugin subdirectory may be appropriate; it must have its own manifest and recognized entrypoint. The catalog `subdir` must be a safe relative path (no absolute path, `..`, `.`, empty segments, or backslashes). The native NaN16 runtime and Android application are not automatically Hermes plugins.

## Admission-rule ledger (all 18 rules)

Status vocabulary: **PASS** = evidence currently supports compliance; **BLOCKED** = cannot pass until a plugin package exists; **REQUIRED CHECK** = must be tested or evidenced on the eventual pinned package; **CONDITIONAL** = applies only if that feature is implemented.

| # | Rule | Current status | Required evidence / implementation gate |
|---:|---|---|---|
| 1 | Human-merged gate | REQUIRED CHECK | Submit one PR adding `plugin-catalog/<name>.yaml` to `NousResearch/hermes-agent`; no self-service registration. Wait for human review and merge. |
| 2 | Exact SHA pins | BLOCKED | Catalog `sha` must be the full 40-hex commit SHA. No branch, tag, or abbreviated SHA. Test checkout at exactly that SHA. |
| 3 | No self-updating code | REQUIRED CHECK | Remove/disable catalog-build self-updaters, in-app update downloads, remote executable loaders, and code that replaces its own files. Updates only through reviewed SHA-bump PRs and `hermes plugins update <name>`. |
| 4 | SHA bumps are new PRs | REQUIRED CHECK | Every update gets a new catalog PR; reviewer sees old→new commit range. Bump quoted `version` in the same PR and re-pin commit-specific image/screenshot URLs. |
| 5 | Owner / major-contributor submission | REQUIRED CHECK | Submit as the plugin repository owner or a major contributor. No drive-by third-party submission. Confirm submitting GitHub identity has standing in the target repo. |
| 6 | Capabilities match reality | BLOCKED | Manifest `capabilities` must exactly match registered tools, hooks, middleware and environment variables. Exercise the loaded plugin and compare registrations to declarations; no undeclared capability creep. |
| 7 | Install security scanner | BLOCKED | Run `hermes plugins validate <path> --install-deps` at the pinned commit. Zero `dangerous` findings. Review and resolve every `caution` warning rather than ignoring it. |
| 8 | Desktop SDK boundary | CONDITIONAL / REQUIRED CHECK | If `desktop/plugin.js` exists: use only `@hermes/plugin-sdk` APIs; no prototype patching, `eval`, `new Function`, arbitrary `import()`, script injection, raw app bridge workarounds, DOM selectors into Hermes internals, or MutationObserver manipulation of core UI. Request missing SDK hooks instead. |
| 9 | No Hermes core overrides | REQUIRED CHECK | Extend only through public hooks, middleware, provider profiles, `ctx.register_*` APIs and Desktop SDK slots/routes. No replacing/wrapping core functions, rebinding methods, mutating private module tables, or patching `sys.modules`. Resolve undocumented/private API warnings. |
| 10 | Dependency security | REQUIRED CHECK | Keep dependencies within Hermes core constraints; never move Hermes exact pins. Avoid unbounded `>=` floors and newest-release-only floors; use oldest API-compatible floor plus upper bound. Strongly recommended: plugin CI release quarantine via `uv --exclude-newer`. |
| 11 | Credentials through Hermes | REQUIRED CHECK | Read secrets only through `agent.secret_scope.get_secret(NAME)`. Use `credentialed_provider_request` with `requires_auth` for provider sign-in; declare OAuth in `plugin.yaml` and use Hermes auth helpers. HTTPS-only OAuth URLs/hosts. Never read `.env`, `auth.json`, browser profiles, keychains, vendor CLI tokens, or refresh/write tokens directly. |
| 12 | Approvals and unattended runs | REQUIRED CHECK | Never auto-approve, disable guards, or spawn children inheriting YOLO/non-interactive authority to bypass approval. Prompts and OAuth flows must fail cleanly or time out in cron/gateway/unattended contexts. |
| 13 | Risk disclosure | REQUIRED CHECK | README and catalog PR must disclose third-party network calls, reads outside plugin data, shell commands, background processes, stored credentials, and other install-relevant behavior. Telemetry/usage reporting must be opt-in. Put clear disclosure lines in PR body. |
| 14 | Truthful compatibility metadata | BLOCKED | `requires_hermes` must be a SemVer floor (not CalVer), not newer than current Hermes, and identical in `plugin.yaml` and catalog entry. Portable `plugin.json` has no manifest floor; set catalog floor when needed. `version` must match pinned code. Validate dependencies under core constraints. |
| 15 | No bundled-plugin skins/forks | REQUIRED CHECK | If functionality changes a Hermes-bundled plugin, contribute to `hermes-agent` instead. No competing vendor-lookalike skin or Nous-branded imitation. |
| 16 | One listing per lineage | REQUIRED CHECK | Search the catalog for an existing lineage before proposing a new listing. Improvements go upstream. A fork needs material transport/architecture/capability difference and the original author must have declined or not answered for 30 days. Disclose `Derived from <entry>`. |
| 17 | No unscannable executable payloads | BLOCKED | The plugin package must not ship native binaries/libraries (ELF, Mach-O, PE/MZ, WASM), `.pyc`/`.pyo`, or opaque archives. Source-only assets are reviewable. If a native component is genuinely required, build it from repository source in public GitHub Actions and attach `actions/attest-build-provenance`; verify with `gh attestation verify --repo <owner/repo> --source-digest <pinned-sha>`. A setup-time binary fetch is allowed only from official upstream, checksum-pinned and disclosed. |
| 18 | Password-manager vendor rule | CONDITIONAL | If registering `browser_vault` / `ctx.register_login_backend`, only vendor-owned submission at `verified` tier or Nous-maintained `official` tier. No community/fork/third-party listing. Otherwise do not register a login backend. |

## Catalog entry schema gate

The eventual `plugin-catalog/<name>.yaml` must be checked against the live canonical schema:

- `name`: `[a-z0-9_-]{1,64}`; this is the install/catalog key and must agree with the plugin manifest/provider registration where applicable.
- `repo`: public cloneable `https://` URL.
- `sha`: mandatory full 40-character commit SHA.
- `subdir`: optional safe relative path only.
- `description`, `maintainer`; truthful and concise.
- `tier`: `community` by default. Only Nous maintainers may assign `verified` or `official`.
- `category`: one of `desktop | memory | platform | web | tools | voice | automation | models | general`.
- `requires_hermes`: optional SemVer floor, quoted; same floor in `plugin.yaml`.
- `docs_url`, quoted `version`, optional `image`, up to six `screenshots`, `readme`, `platforms`, `title`, `onboarding`, `known_issues`.
- `capabilities`: `provides_tools`, `provides_hooks`, `provides_middleware`, `requires_env`, all grounded in actual registrations.
- Images/screenshots must use supported GitHub-hosted URLs and be pinned to the exact commit. `onboarding` is for official/verified entries only.

## Required pre-submission procedure

1. Decide the actual Hermes plugin capability and its supported public extension surface. Do not package the whole Android app as a plugin by assumption.
2. Create a dedicated plugin package with `plugin.yaml` and a real supported entrypoint; declare the same `requires_hermes` floor in both places.
3. Keep the package source-only and narrowly scoped. Add no self-updater, no core monkey-patching, no credential-store scraping, no approval bypass, and no undeclared network/background behavior.
4. Add tests for manifest loading, actual registered capabilities, config schema, required environment, dependency constraints, unattended execution, failure/timeouts, and security-sensitive behavior.
5. From a clean checkout of the exact candidate commit, run:
   ```sh
   hermes plugins validate /path/to/your-plugin --install-deps
   ```
   Record tool version, commit SHA, exit status, all warnings, and scanner output. Resolve every failure and review every warning.
6. Re-run validation after the final commit. Only then prepare the catalog entry and PR.
7. PR body must state purpose, Hermes surfaces used, all rule-13 disclosures, screenshots for UI, repository URL, exact 40-character SHA, version, compatibility floor, and validation evidence.
8. Wait for catalog CI and human maintainer review. Do not call a plugin “catalog approved” until merged.
9. For each later release, submit a fresh SHA-bump PR and update version and pinned media in the same change.

## Live-document maintenance

The developer guide says the admission-rule section mirrors the canonical `plugin-catalog/README.md` and a test fails if they drift. This audit was checked against both live pages on 2026-11-06. Before submission and before every update, re-read both pages and compare the entry schema as well as all 18 rules; this document is a project checklist, not a replacement for either source of truth.

## Current evidence boundary

The NaN0S / NaNLMind Android repositories are public GitHub repositories and their current project branches can be inspected. That alone does not establish plugin compatibility, security-scanner cleanliness, dependency compatibility, capability accuracy, or catalog eligibility. No Hermes CLI validation was run because there is not yet a loadable plugin package to validate.
