# Score: derived skill for dotenv 17.4.2

Scored against the package as installed (`source/`), with the authored skill (`derive-truth/dotenv/dotenv/SKILL.md`) used for comparison only. Paths below are relative to `source/` unless they start with `scratch/`. The derived skill was not edited.

## Method

- Every `[src: path:line]` in the derived skill body was resolved and the cited line printed next to the claim (177 citations). All but one land on the line they describe; `CHANGELOG.md:446` is two lines off (see claim 113).
- Each claim was read against the cited line and the code around it. Behavioural claims were run against the package's own code with Node v24.15.0, in child processes with a clean environment and their own working directory. Scripts: `scratch/parse-tests.js`, `scratch/config-tests.js`, and the ES module files in `scratch/esm/` (the package is reached by absolute path, or by a symlink at `scratch/esm/node_modules/dotenv`; nothing was installed and nothing touched the network).
- A sentence holding several assertions is split. A sentence carrying both a citation and `[uncited]` is split into the cited part and the uncited conclusion where the two can be told apart; otherwise it is scored as uncited.
- Verdicts: SUPPORTED, OVERSTATED, UNSUPPORTED, CONTRADICTED for cited claims; TRUE, FALSE, CANNOT-TELL for `[uncited]` ones. "ran" means the behaviour was observed, not only read.

## Counts

| | Count |
|---|---|
| Total claims | 131 |
| Cited claims | 121 |
| SUPPORTED | 115 |
| OVERSTATED | 6 |
| UNSUPPORTED | 0 |
| CONTRADICTED | 0 |
| `[uncited]` claims | 10 |
| TRUE | 10 |
| FALSE | 0 |
| CANNOT-TELL | 0 |

## Part 1 — every claim

### What it solves

| # | Claim | Citation | Verdict | Evidence |
|---|---|---|---|---|
| 1 | Reads a `.env` file of `KEY=value` lines and puts the entries on `process.env` | README.md:7 | SUPPORTED | README.md:7; lib/main.js:277-288 |
| 2 | No runtime dependencies | README.md:7 | SUPPORTED | "zero-dependency"; package.json has no `dependencies` key |
| 3 | `config()` loads one or more env files into `process.env` | lib/main.js:320 | SUPPORTED | lib/main.js:320-336, 258-267 |
| 4 | `parse()` turns a string or Buffer into a plain object without touching the environment | lib/main.d.ts:22 | SUPPORTED | lib/main.js:41-77; ran |
| 5 | `populate()` copies an object onto `process.env` or any object with the no-clobber rule | lib/main.d.ts:163 | SUPPORTED | lib/main.js:370-403; ran |
| 6 | `dotenv/config` loads the file with no call in application code | config.js:2 | SUPPORTED | config.js:1-9; ran with `node -r` |
| 7 | CommonJS module | lib/main.js:423 | SUPPORTED | `module.exports`; no `"type"` in package.json |
| 8 | Ships its own TypeScript definitions | package.json:6 | SUPPORTED | package.json:6, 9; lib/main.d.ts |
| 9 | Called from JS or TS on Node; no other language surface, no command-line program | [uncited] | TRUE | package.json has no `bin`; the package holds only `config.js`, `lib/*.js`, `*.d.ts` |

### How it is meant to be used

| # | Claim | Citation | Verdict | Evidence |
|---|---|---|---|---|
| 10 | `require('dotenv').config()` is the basic call | README.md:33 | SUPPORTED | README.md:33 |
| 11 | `config()` reads `.env` from the current working directory | lib/main.js:241 | SUPPORTED | ran (a1, a2) |
| 12 | Sets every key not already present | lib/main.js:383 | SUPPORTED | lib/main.js:383-399; ran |
| 13 | Returns an object rather than throwing: `{ parsed }`, or `{ parsed, error }` when a file could not be read | lib/main.js:313, 315 | SUPPORTED | ran (b1: `ENOENT`, parsed `{}`; b2: `EISDIR`). The vault path does throw; the skill says so in its own trap |
| 14 | `path` is one file or an array | lib/main.js:260 | SUPPORTED | lib/main.js:259-267 |
| 15 | `encoding` defaults to `utf8` | lib/main.js:242 | SUPPORTED | |
| 16 | `override` option | lib/main.d.ts:71 | SUPPORTED | |
| 17 | `quiet` option | lib/main.d.ts:53 | SUPPORTED | |
| 18 | `debug` option | lib/main.d.ts:62 | SUPPORTED | |
| 19 | `processEnv` is an object to write to instead of `process.env` | lib/main.js:245 | SUPPORTED | ran (o1) |
| 20 | A leading `~` in a path resolves to the home directory | lib/main.js:217 | SUPPORTED | ran (m1) |
| 21 | Array: parsed in order, first file to define a key wins; with `override: true` the last wins | README.md:658; lib/main.js:279 | SUPPORTED | ran (d1, d2) |
| 22 | In an ES module, `import 'dotenv/config'` goes first | README.md:413 | SUPPORTED | ran (`right.mjs`, `late.mjs`) |
| 23 | `dotenv/config` calls `config()` when loaded | config.js:2 | SUPPORTED | |
| 24 | The same entry point is what `node -r dotenv/config` preloads | README.md:166 | SUPPORTED | ran |
| 25 | Options come from the six `DOTENV_CONFIG_*` environment variables | lib/env-options.js:4-24 | SUPPORTED | |
| 26 | And from `dotenv_config_<option>=value` arguments, applied over the environment variables | lib/cli-options.js:1; config.js:6 | SUPPORTED | ran (h8) |
| 27a | A path given this way is a single string | lib/env-options.js:9 | SUPPORTED | |
| 27b | Nothing splits it into several files | [uncited] | TRUE | ran (h7): `.env.quiet,.env` is opened as one file name |
| 28 | Example: `parse()` output, `config({ processEnv })`, `populate(process.env, {...})` | lib/main.d.ts:19; README.md:724, 769 | SUPPORTED | ran; output matches the comment in the example |
| 29 | `populate` takes `override` and `debug` | lib/main.d.ts:123, 114 | SUPPORTED | lib/main.js:371-372 |
| 30 | `populate` returns only the keys it actually set | lib/main.js:402 | SUPPORTED | ran |
| 31 | One `KEY=value` per line; leading `export ` accepted; `KEY: value` with a space after the colon | lib/main.js:38 | SUPPORTED | ran (`A: 1` parses, `A:1` does not) |
| 32 | Keys are letters, digits, `_`, `.` and `-` | lib/main.js:38 | SUPPORTED | ran |
| 33 | Values bare or in single quotes, double quotes or backticks; quoted keeps inner whitespace and may span lines | lib/main.js:38; README.md:450, 118 | SUPPORTED | ran for all three quote kinds |
| 34 | A bare value is trimmed | lib/main.js:58 | SUPPORTED | ran |
| 35 | `KEY=` gives an empty string | lib/main.js:55 | SUPPORTED | ran |
| 36 | `#` starts a comment, on its own line or after a value | README.md:134 | SUPPORTED | ran |
| 37 | Windows line endings are normalized | lib/main.js:48 | SUPPORTED | ran |
| 38a | Lines that do not match are skipped | lib/main.js:51 | SUPPORTED | ran |
| 38b | Nothing reports them | [uncited] | TRUE | `parse` has no logging at all (lib/main.js:41-77); ran |

### Invariants and traps

| # | Trap | Claim | Citation | Verdict | Evidence |
|---|---|---|---|---|---|
| 39 | T1 | Default `.env` is looked up in the working directory, not beside the script | lib/main.js:241 | SUPPORTED | ran (a1 loads; a2, same script from another directory, does not) |
| 40 | T1 | Symptom is `undefined` plus `injected env (0) from .env`; the line names the path whether or not the file existed | lib/main.js:297, 309 | SUPPORTED | ran (a2, g7) |
| 41 | T1 | With `debug: true` a `failed to load <path> <message>` line names the path | lib/main.js:282 | SUPPORTED | ran (b1) |
| 42 | T2 | A missing or unreadable file is not thrown; the read error comes back as `result.error` | lib/main.js:284, 313 | SUPPORTED | ran (b1, b2) |
| 43 | T2 | With an array, `error` is set if any file failed — the last failure — while the others loaded, so `throw result.error` aborts over an optional missing `.env.local` | lib/main.js:284, 279, 313 | SUPPORTED | ran (c1: `ENOENT` with `.env` loaded; c2: error is for the last missing file) |
| 44 | T3 | All imports are evaluated before the importing module's body | README.md:381 | SUPPORTED | ran |
| 45 | T3 | `import * as dotenv; dotenv.config(); import client` — the client reads `process.env` first and sees it blank | README.md:402, 405, 408 | SUPPORTED | ran (`wrong.mjs`: client saw `undefined`) |
| 46 | T3 | `import 'dotenv/config'` as the first import loads first | README.md:413 | SUPPORTED | ran (`right.mjs`) |
| 47 | T3 | With options: a small module of one's own, imported first | README.md:504, 511 | SUPPORTED | ran (`wrapper.mjs`) |
| 48 | T3 | Or `DOTENV_CONFIG_*` variables with `import 'dotenv/config'` | README.md:490 | SUPPORTED | ran |
| 49 | T4 | An existing variable is never replaced unless `override: true` | lib/main.js:383, 384 | SUPPORTED | ran (e1, e2) |
| 50 | T4 | "Exists" means the key is present; an empty string in the shell blocks the file's value | lib/main.js:383; CHANGELOG.md:551 | SUPPORTED | ran (e1: `A=""` kept) |
| 51 | T4 | `result.parsed` holds what the files said, not what was applied | lib/main.js:315, 288 | SUPPORTED | ran (e1: `parsed.A` is the file's value) |
| 52 | T4 | `debug: true` logs `"KEY" is already defined and was NOT overwritten` | lib/main.js:393 | SUPPORTED | ran (e1) |
| 53 | T5 | A second `config()` call does not replace keys the first set; in an array the earlier file wins | lib/main.js:383; README.md:658 | SUPPORTED | ran (f1, d1) |
| 54 | T5 | Within one file a key defined twice takes its later value | lib/main.js:73 | SUPPORTED | ran |
| 55 | T6 | `config()` writes `◇ injected env (N) from <files>` plus a random tip to standard output by default | lib/main.js:309, 131, 20 | SUPPORTED | ran (g1: on stdout, nothing on stderr) |
| 56 | T6 | `N` counts only the keys actually set | lib/main.js:295 | SUPPORTED | ran (g2) |
| 57 | T6 | `quiet: true` suppresses it | lib/main.d.ts:49 | SUPPORTED | ran (g3) |
| 58 | T6 | So does `DOTENV_CONFIG_QUIET=true`, in the environment or in the `.env` file itself | CHANGELOG.md:74; lib/main.js:292 | SUPPORTED | ran (g4, g5) |
| 59 | T6 | `debug: true` prints it even when `quiet` is set | lib/main.js:294 | SUPPORTED | ran (g6) |
| 60 | T6 | "The `dotenv/config` entry point is the opposite: it is quiet unless `dotenv_config_quiet` is given as an argument." | lib/cli-options.js:12, 13 | OVERSTATED | Quiet by default is right (h1) and the argument does turn the line on (h2). But the argument is not the only way: `DOTENV_CONFIG_QUIET=false` in the environment prints it (h3), because lib/main.js:248 reads the variable ahead of the injected `quiet: 'true'`; so does `dotenv_config_debug=true` (h4, lib/main.js:294); so does `DOTENV_CONFIG_QUIET=false` inside the `.env` file (h5, lib/main.js:292) |
| 61 | T7 | "When either is set to a non-empty value it is used in place of `quiet` or `debug`, in both directions." | lib/main.js:247, 248; CHANGELOG.md:75 | OVERSTATED | True for `quiet` (i1). Only partly for `debug`: `populate` takes its own `debug` from the option alone (lib/main.js:371), so with `DOTENV_CONFIG_DEBUG=false` and `debug: true` the `"A" is already defined and was NOT overwritten` line still prints (i4), and with `DOTENV_CONFIG_DEBUG=true` and no option that per-key line does not print although the other debug lines do (i5) |
| 62 | T7 | They are read from the target object; with a custom `processEnv` they are looked up there | lib/main.js:245, 248 | SUPPORTED | ran (i6: `process.env.DOTENV_CONFIG_QUIET=true` ignored; i7: honoured on the target). On the vault path they are read from `process.env` (lib/main.js:221-222) |
| 63 | T7 | `false`, `0`, `no`, `off`, in any letter case, mean off | lib/main.js:25 | SUPPORTED | ran (i2) |
| 64 | T7 | An empty variable falls back to the option in code | lib/main.js:248 | SUPPORTED | ran (i3) |
| 65 | T8 | `override`: any non-empty string turns it on | lib/main.js:372 | SUPPORTED | ran (j5: `override: 'false'` overrides) |
| 66 | T8 | Through `dotenv/config` it arrives as a string, so `DOTENV_CONFIG_OVERRIDE=false` or `dotenv_config_override=false` enables overriding | lib/env-options.js:21; lib/cli-options.js:7 | SUPPORTED | ran (j1, j2 replace the shell value; j3, with neither, keeps it) |
| 67 | T9 | `#` ends an unquoted value; `SECRET=abc#123` yields `abc` | lib/main.js:38; README.md:142 | SUPPORTED | ran |
| 68 | T9 | In single quotes, double quotes or backticks the `#` is kept | README.md:139 | SUPPORTED | ran for all three |
| 69 | T10 | Values are always strings; `PORT=3000` is `'3000'`, `DEBUG=false` is `'false'` | lib/main.d.ts:6 | SUPPORTED | ran |
| 70 | T10 | `${OTHER}` and `$(command)` are left as written | [uncited]; README.md:188, 210 | TRUE | ran, bare and double-quoted |
| 71 | T10 | `\n` and `\r` become line breaks only inside double quotes | lib/main.js:67-69 | SUPPORTED | ran for double, single, backtick and bare |
| 72 | T10 | An escaped quote inside a quoted value is accepted, but the backslash stays | lib/main.js:38, 64; [uncited] | TRUE | ran: `A="a\"b"` gives `a\"b`, likewise for `'` and backtick |
| 73 | T11 | `parse()` takes one argument | lib/main.js:41; lib/main.d.ts:22 | SUPPORTED | `parse.length` is 1 |
| 74 | T11 | The README still shows `dotenv.parse(buf, { debug: true })` | README.md:755 | SUPPORTED | README.md:752-756 |
| 75 | T11 | The second argument is ignored and no debug message is printed | [uncited] | TRUE | ran: no `console.log` call during `parse(buf, { debug: true })` |
| 76 | T12 | With `DOTENV_KEY` set (option or environment) and a `.env.vault` file present, `config()` decrypts that file and never reads `.env` | lib/main.js:136, 141, 326, 335 | SUPPORTED | ran (k1, k2) with a vault built in the scratch directory: `.env` keys absent, vault keys present. Caveat: with a custom `path` the file looked for is `<path>.vault`, not `.env.vault` in the working directory (lib/main.js:195-204; ran) |
| 77 | T12 | On that path failures are thrown, with `error.code` one of `INVALID_DOTENV_KEY`, `NOT_FOUND_DOTENV_ENVIRONMENT`, `DECRYPTION_FAILED`, `MISSING_DATA` | lib/main.js:157, 185, 361, 87 | SUPPORTED | ran (k3 `DECRYPTION_FAILED`, k4 and k6 `INVALID_DOTENV_KEY`, k5 `NOT_FOUND_DOTENV_ENVIRONMENT`). `MISSING_DATA` exists in the code but `configDotenv` always returns `parsed`, so it looks unreachable; errors not recognised are rethrown as they are (lib/main.js:161, 364) |
| 78 | T12 | Key set but no vault file: a warning on standard error, and `.env` is loaded as usual | lib/main.js:330, 123, 332 | SUPPORTED | ran (k7) |
| 79 | T12 | `configDotenv()` takes the same options and only ever reads the plain files | lib/main.d.ts:142; lib/main.js:240 | SUPPORTED | ran (k8) |
| 80 | T13 | The package marks `fs` as unavailable for browser builds | package.json:60 | SUPPORTED | |
| 81 | T13 | Bundling for the browser without polyfills fails with `Can't resolve 'crypto\|os\|path'` | README.md:576, 578 | SUPPORTED | README, and lib/main.js:2-4 requires all three unconditionally. Not run (no bundler) |
| 82 | T13 | In a webpack-bundled React app `process.env` is only what the bundler injects | README.md:556 | SUPPORTED | README only; not run |

### What moved, and what it used to be called

| # | Claim | Citation | Verdict | Evidence |
|---|---|---|---|---|
| 83 | `config()` prints by default since 17.0.0 | CHANGELOG.md:128 | SUPPORTED | |
| 84 | The message was introduced, on by default, in 16.6.0 | CHANGELOG.md:142 | SUPPORTED | |
| 85 | Hidden by default in 16.6.1 | CHANGELOG.md:134 | SUPPORTED | |
| 86 | "A plain `config()` call written for a release before 16.6.0 printed nothing then and writes a line to standard output now." | none (follows from 142) | OVERSTATED | Right for a file that loads. Not for every earlier release: CHANGELOG.md:625 (1.1.0 adds a `silent` option "to silence `console.log` when `.env` missing") and CHANGELOG.md:578 (3.0.0 "Suppress error messages by default") show a plain call did print before 3.0.0 when the file was missing; CHANGELOG.md:374 (14.0.1 "Log error on failure to load `.env` file") does not say whether that is behind `debug` |
| 87 | Text was `[dotenv@16.6.0] injecting env (1) from .env` in 16.6.0 | CHANGELOG.md:142 | SUPPORTED | |
| 88 | `◇ injecting env (14) from .env` in 17.4.0 | CHANGELOG.md:27 | SUPPORTED | |
| 89 | Reads `injected` since 17.4.1 | CHANGELOG.md:17 | SUPPORTED | lib/main.js:309 |
| 90 | Since 17.0.1 the number counts only keys actually set | CHANGELOG.md:122 | SUPPORTED | |
| 91 | `#` starts a comment inside an unquoted value since 15.0.0; marked breaking; such values must be quoted | CHANGELOG.md:325 | SUPPORTED | Also README.md:142. The skill leaves out that inline comments first arrived, also as breaking, in 14.0.0 (CHANGELOG.md:380) |
| 92 | Backticks are quote characters since 16.0.0 | CHANGELOG.md:305 | SUPPORTED | |
| 93 | "A value that contains a backtick now needs single or double quotes around it." | CHANGELOG.md:307 | OVERSTATED | This repeats the changelog's advice, but the code only treats a backtick as a quote when it opens the value: `A=foo\`bar` parses to `foo\`bar` unchanged (ran); only a value wrapped in backticks loses them (lib/main.js:38, 64) |
| 94 | Multiline quoted values parse with no option since 15.0.0 | CHANGELOG.md:321 | SUPPORTED | |
| 95 | The `multiline` option was added in 14.3.0 and removed in 15.0.0 | CHANGELOG.md:349, 331 | SUPPORTED | |
| 96 | Support for multiple `.env` files was removed in 1.0.0 | CHANGELOG.md:631 | SUPPORTED | |
| 97 | "an array for `path` arrived in 16.4.1" | CHANGELOG.md:201 | OVERSTATED | Line 201 says "Patch support for array as `path` option", which is a fix, not an arrival. CHANGELOG.md:206 already speaks of file names "passed as an array" in 16.4.0, and CHANGELOG.md:191 says processing of multiple files in `options.path` was fixed in 16.4.3. The package does not show that loading several files started in 16.4.1 |
| 98 | When `path` is set `.env` is not also looked up; a 16.4.3 regression was reverted in 16.4.5 | CHANGELOG.md:179 | SUPPORTED | lib/main.js:258-267; ran (d3) |
| 99 | Since 5.0.0 the default path is the working directory's `.env` | CHANGELOG.md:550 | SUPPORTED | |
| 100 | Since 5.0.0 a key that exists with a falsy value is not written over | CHANGELOG.md:551 | SUPPORTED | |
| 101 | `load()` was an alias for `config()`, removed in 7.0.0 | CHANGELOG.md:520 | SUPPORTED | |
| 102 | `silent` was removed in 3.0.0 in favour of `verbose` | CHANGELOG.md:587 | SUPPORTED | |
| 103 | `verbose` was removed in 4.0.0 | CHANGELOG.md:566 | SUPPORTED | |
| 104 | Neither exists now; the switches are `quiet` and `debug` | lib/main.d.ts:53, 62 | SUPPORTED | |
| 105 | `config()` returns an object with `parsed` or `error` since 4.0.0; before that `false` on failure | CHANGELOG.md:562 | SUPPORTED | |
| 106 | Variable expansion was removed in 2.0.0 | CHANGELOG.md:605 | SUPPORTED | |
| 107 | A `debug` option for `parse` was added in 6.1.0; `parse` now takes only the source | CHANGELOG.md:532; lib/main.js:41 | SUPPORTED | |
| 108 | The changelog does not say when it went | [uncited] | TRUE | Every `debug` and `parse` line in the changelog was read; none removes it |
| 109 | Type definitions ship in the package since 8.2.0 | CHANGELOG.md:498 | SUPPORTED | |
| 110 | They moved from `types/index.d.ts` to `lib/main.d.ts` in 12.0.0, when Flow was dropped | CHANGELOG.md:427, 423 | SUPPORTED | |
| 111 | `browser` entries for `path`, `os`, `crypto` were removed in 16.1.3; only `fs` is stubbed | CHANGELOG.md:248; package.json:60 | SUPPORTED | |
| 112 | `import 'dotenv/config'` in 8.5.0 | CHANGELOG.md:480 | SUPPORTED | |
| 113 | `~` in `path` in 10.0.0 | CHANGELOG.md:446 | SUPPORTED | The claim is right, at CHANGELOG.md:444. The cited line 446 is the `## [9.0.2]` heading: the citation is two lines off |
| 114 | `override` in 14.1.0 | CHANGELOG.md:368 | SUPPORTED | |
| 115 | `populate` in 16.1.0 | CHANGELOG.md:270 | SUPPORTED | |
| 116 | `.env.vault` loading in 16.1.0 | CHANGELOG.md:274 | SUPPORTED | |
| 117 | `configDotenv` in 16.1.2 | CHANGELOG.md:254 | SUPPORTED | |
| 118 | `processEnv` in 16.2.0 | CHANGELOG.md:235 | SUPPORTED | |
| 119 | The `DOTENV_KEY` option in 16.3.0 | CHANGELOG.md:229 | SUPPORTED | |
| 120 | `DOTENV_CONFIG_QUIET` in 17.2.0 | CHANGELOG.md:74 | SUPPORTED | |

### What it is not for

| # | Boundary | Claim | Citation | Verdict | Evidence |
|---|---|---|---|---|---|
| 121 | B1 | Not for variable expansion; the README points to dotenvx | README.md:188 | SUPPORTED | ran: `${OTHER}` is not expanded |
| 122 | B1 | Not for command substitution; the README points to dotenvx | README.md:210 | SUPPORTED | ran: `$(whoami)` is not run |
| 123 | B1 | Not for "encrypted env files"; the README points to dotenvx | README.md:231 | OVERSTATED | The README does say that, and the package cannot encrypt anything. But it does load an encrypted file: `config()` with `DOTENV_KEY` decrypts `.env.vault` with AES-256-GCM (lib/main.js:220-238, 338-367; lib/main.d.ts:131; ran, k1). The skill's own trap T12 describes this path, so the boundary as worded is wider than the code |
| 124 | B1 | The dotenvx npm package is `@dotenvx/dotenvx` | README.md:523 | SUPPORTED | |
| 125 | B1 | The README's example of expansion as an add-on uses `dotenv-expand` | README.md:432 | SUPPORTED | |
| 126 | B2 | No validation or typing: no required-variable check, no conversion to numbers or booleans, no schema | [uncited] | TRUE | Nothing of the kind in `lib/` or `config.js`; values are stored as parsed (lib/main.js:73, 397) |
| 127 | B3 | It only reads `.env` files; it does not write or edit them | [uncited] | TRUE | The only `fs` calls are `existsSync` (lib/main.js:198, 209) and `readFileSync` (lib/main.js:277) |
| 128 | B4 | Not for supplying variables to browser code | README.md:556 | SUPPORTED | README only |
| 129 | B5 | Nothing looks at `NODE_ENV` or picks `.env.production`; the files read are exactly those in `path`, or `.env` | lib/main.js:258; [uncited] | TRUE | No `NODE_ENV` anywhere in the code; lib/main.js:258-267; ran (d3). The one other file is `<path>.vault` under `DOTENV_KEY`, which the skill covers in T12 |

## Traps and boundaries

Thirteen traps (T1–T13) and five boundaries (B1–B5): 18. A trap or boundary counts as supported only when every claim inside it is SUPPORTED or TRUE.

| | Subject | Verdict |
|---|---|---|
| T1 | Default `.env` is found by working directory | SUPPORTED |
| T2 | Missing file is returned, not thrown; one bad file in an array sets `error` | SUPPORTED |
| T3 | ES module import order | SUPPORTED |
| T4 | Existing variable is not replaced, even when empty | SUPPORTED |
| T5 | First wins across files and calls, last wins inside a file | SUPPORTED |
| T6 | A line on standard output by default | OVERSTATED in one claim (60); the rest holds |
| T7 | `DOTENV_CONFIG_QUIET` and `DOTENV_CONFIG_DEBUG` beat code options | OVERSTATED (61), for `debug` |
| T8 | `override` is on for any non-empty string | SUPPORTED |
| T9 | `#` ends an unquoted value | SUPPORTED |
| T10 | Values are strings; nothing is expanded | SUPPORTED |
| T11 | `parse()` takes one argument | SUPPORTED |
| T12 | `DOTENV_KEY` changes what `config()` does | SUPPORTED |
| T13 | Front-end code | SUPPORTED (README only, not run) |
| B1 | Expansion, command substitution, encrypted files | OVERSTATED (123), for encrypted files |
| B2 | Validation or typing | SUPPORTED |
| B3 | Writing or editing `.env` files | SUPPORTED |
| B4 | Browser code | SUPPORTED |
| B5 | Choosing a file by environment name | SUPPORTED |

Supported: 15 of 18. Contradicted by the source: none.

## Part 2 — against the authored skill

### Traps and boundaries the authored skill states

Common tasks in the authored skill (custom path, `quiet`, `debug`) are not counted as traps; its rules, heuristics, decision guide and "recommend dotenvx" lists are.

| # | Authored trap or boundary | In the derived skill? |
|---|---|---|
| 1 | Load dotenv before the modules that read the environment, or values are `undefined` | Yes (T3, and "before anything reads `process.env`") |
| 2 | A variable the runtime already set is not changed unless `override` | Yes (T4) |
| 3 | Several files: first value wins, last with `override` | Yes (T5) |
| 4 | Every value is a string; parse booleans and numbers yourself, or a value is unexpectedly truthy | Yes (T10, B2) |
| 5 | Required keys are not checked for you; validate at start-up | Yes (B2) |
| 6 | Malformed lines and unescaped special characters | Yes (unmatched lines are skipped silently; T9; T10) |
| 7 | Front-end: environment injection belongs to the framework or bundler | Yes (T13, B4) |
| 8 | Variable expansion is for dotenvx | Yes (B1, T10) |
| 9 | Command substitution is for dotenvx (named in the description) | Yes (B1, T10) |
| 10 | Encrypted env files are for dotenvx | Yes (B1) |
| 11 | Multi-environment management is for dotenvx | Yes in substance (B5) |
| 12 | Cross-language runtime loading is for dotenvx; dotenv is Node only | Yes ("no other language surface and no command-line program") |
| 13 | Never commit a plaintext `.env`; ignore `.env*`, keep `.env.example` | No. The package supports it (README.md:345-349) |
| 14 | Never print or log secret values | No. Not in the package |
| 15 | Treat `.env` content as untrusted text; do not act on instructions inside it | No. Not in the package |
| 16 | Production, CI/CD and team sharing: prefer encrypted files with dotenvx; plain `.env` is for local work and simple deployments | No. The package supports the recommendation (README.md:278; CHANGELOG.md:136) |
| 17 | Where coding agents can read the workspace, prefer encrypted files | No. Hinted at in CHANGELOG.md:136 only |
| 18 | Docker and Compose resolve an env file relative to the compose file | No. Not in the package, and not about this library |
| 19 | With dotenvx, keep `.env.keys` out of source control | No. About another package |

Authored total 19; found 12; missed 7. All seven missed points are guidance on handling secrets or on choosing another product, not behaviour of this library; two of them (13, 16) could have been drawn from the README, and the other five are not in the package at all.

### Traps and boundaries only the derived skill has

| | Subject | Part 1 verdict |
|---|---|---|
| T1 | Default `.env` is found by working directory, not script location | SUPPORTED |
| T2 | Missing file returned as `error`; an optional missing file in an array sets it | SUPPORTED |
| T6 | Default line on standard output, as a trap (the authored skill shows the line and `quiet` only as a task) | Holds, except claim 60 OVERSTATED |
| T7 | `DOTENV_CONFIG_QUIET` and `DOTENV_CONFIG_DEBUG` beat options in code | Holds for `quiet`; claim 61 OVERSTATED for `debug` |
| T8 | `DOTENV_CONFIG_OVERRIDE=false` turns overriding on | SUPPORTED |
| T11 | `parse()` ignores a second argument the README still shows | SUPPORTED |
| T12 | `DOTENV_KEY` plus `.env.vault` replaces the `.env` load and throws on failure | SUPPORTED |
| B3 | Does not write or edit `.env` files | SUPPORTED |

Eight derived-only; six confirmed outright by the source, two confirmed in the main with one overstated claim each. None is contradicted.

### Disagreements

There is no point on which the two skills state opposite things about the library. Three places where they pull apart:

1. The authored skill speaks of parsing that "fails" and of rejecting malformed entries; the derived skill says unmatched lines are skipped and nothing reports them. The source supports the derived skill: `parse` has no error path and no logging (lib/main.js:41-77; ran).
2. The authored skill sends everything about encryption to dotenvx and never mentions `DOTENV_KEY` or `.env.vault`; the derived skill documents that `config()` itself decrypts `.env.vault` when `DOTENV_KEY` is set. The source supports the derived skill (ran, k1). Both skills state "not for encrypted env files" as a boundary, which the source supports only in part.
3. The authored skill lists `.env.local`, `.env.test` and `.env.production` with intended roles; the derived skill says none is loaded unless named in `path`. The source supports the derived skill (lib/main.js:258-267).

## Claims tested by running code

- Parser (`scratch/parse-tests.js`): claims 4, 28, 31–38b, 54, 67–72, 73, 75, 93, 121, 122; and 5, 30, 65 for `populate`.
- `config()` in child processes (`scratch/config-tests.js`): 11–13, 19–21, 39–43, 49–53, 55–59, 61–64, 98, 129.
- `dotenv/config` entry point (`node -r`): 6, 24, 26, 27b, 60, 66.
- Vault path, with a `.env.vault` encrypted in the scratch directory: 76–79, 123.
- ES modules (`scratch/esm/`): 22, 44–48.
- Not run: 81, 82, 128 (they need a bundler and a browser), and the history claims, which were read against the changelog.

## Other things the runs showed

These are not claims in the derived skill, so they are not scored.

- The README's "Option 1 (Recommended)" for ES modules (README.md:470-485: `import dotenv`, call `config()`, then `import` the rest) does not work: the later import is evaluated first (ran, `readme-option1.mjs` saw `undefined`). The derived skill lists only the wrapper module and the environment variables, which are the two forms that do hold.
- On the vault path `config()` first loads the vault file through `configDotenv`, so the `DOTENV_VAULT_<ENV>` ciphertext is itself written to `process.env`, and two lines are printed (ran, k1).
- `path` given as a `URL` (allowed by lib/main.d.ts:35) loads the file but sets `result.error` to a `TypeError` whenever the log line is printed, because `path.relative` rejects a URL (lib/main.js:299-305; ran, p1).
- The frontmatter description of the derived skill repeats boundary B1 ("Not for ... encrypted env files"); the same caveat as claim 123 applies. The frontmatter was not scored.
