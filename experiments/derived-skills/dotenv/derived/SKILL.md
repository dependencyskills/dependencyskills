---
name: dotenv
description: >-
  Load environment variables from a .env file into process.env in Node.js: read
  API keys, database URLs, secrets and per-machine config or settings from a
  dotenv file at startup, instead of hand-rolling a KEY=VALUE file reader. Also
  parse .env-format text (a string or Buffer) into a plain object without
  touching the environment; load several env files in priority order, such as
  .env.local before .env; choose whether file values override variables already
  set in the shell; write the values into your own object instead of
  process.env; preload with node -r dotenv/config or import 'dotenv/config' so
  no application code changes; handle quoted values, multiline values such as
  private keys, export prefixes and # comments. Not for variable expansion or
  interpolation, command substitution or encrypted env files; those live in
  @dotenvx/dotenvx.
license: BSD-2-Clause
metadata:
  version: "17.4.2"
  repository: git://github.com/motdotla/dotenv.git
---

# dotenv

## What it solves

A Node.js program needs its configuration — API keys, database URLs, ports, secrets, per-machine settings — kept out of the code and read from a `.env` file of `KEY=value` lines; this package reads that file and puts each entry on `process.env`. [src: README.md:7] It has no runtime dependencies. [src: README.md:7]

It covers four needs. Loading one or more env files into `process.env` at startup is `config()`. [src: lib/main.js:320] Turning `.env`-format text, a string or a Buffer, into a plain object without touching the environment is `parse()`. [src: lib/main.d.ts:22] Copying an object of values onto `process.env`, or onto any object, with the same "do not clobber what is already set" rule is `populate()`. [src: lib/main.d.ts:163] Loading the file with no call in application code is the `dotenv/config` entry point. [src: config.js:2]

It is a CommonJS module with its own TypeScript definitions, called from JavaScript or TypeScript on Node.js; there is no other language surface and no command-line program. [src: lib/main.js:423] [src: package.json:6] [uncited]

## How it is meant to be used

**Load `.env` at startup, before anything reads `process.env`.**

```js
require('dotenv').config()
console.log(process.env.HELLO)
```

[src: README.md:33]

`config()` reads `.env` from the current working directory, [src: lib/main.js:241] sets every key that is not already present on `process.env`, [src: lib/main.js:383] and returns an object rather than throwing: `{ parsed }` on success, `{ parsed, error }` when a file could not be read. [src: lib/main.js:313] [src: lib/main.js:315]

The options are `path` (one file, or an array of files), [src: lib/main.js:260] `encoding` (default `utf8`), [src: lib/main.js:242] `override`, [src: lib/main.d.ts:71] `quiet`, [src: lib/main.d.ts:53] `debug`, [src: lib/main.d.ts:62] and `processEnv`, an object to write to instead of `process.env`. [src: lib/main.js:245] A leading `~` in a path is resolved to the home directory. [src: lib/main.js:217]

```js
const dotenv = require('dotenv')
const result = dotenv.config({ path: ['.env.local', '.env'], quiet: true })
if (result.error) console.warn(result.error.message)
```

With an array the files are parsed in order and the first file to define a key wins; with `override: true` the last one wins. [src: README.md:658] [src: lib/main.js:279]

**In an ES module, load it as the first import.**

```js
import 'dotenv/config'
import express from 'express'
```

[src: README.md:413]

`dotenv/config` calls `config()` when it is loaded, [src: config.js:2] and the same entry point is what `node -r dotenv/config app.js` preloads. [src: README.md:166] It takes its options from environment variables named `DOTENV_CONFIG_ENCODING`, `DOTENV_CONFIG_PATH`, `DOTENV_CONFIG_QUIET`, `DOTENV_CONFIG_DEBUG`, `DOTENV_CONFIG_OVERRIDE` and `DOTENV_CONFIG_DOTENV_KEY`, [src: lib/env-options.js:4] [src: lib/env-options.js:8] [src: lib/env-options.js:12] [src: lib/env-options.js:16] [src: lib/env-options.js:20] [src: lib/env-options.js:24] and from command-line arguments of the form `dotenv_config_path=/some/.env`, which are applied over the environment variables. [src: lib/cli-options.js:1] [src: config.js:6] The path given this way is a single string; nothing splits it into several files. [src: lib/env-options.js:9] [uncited]

**Parse text, or fill an object, without touching `process.env`.**

```js
const dotenv = require('dotenv')

const values = dotenv.parse('DB_HOST=localhost\nDB_PORT=5432')
// { DB_HOST: 'localhost', DB_PORT: '5432' }

const target = {}
dotenv.config({ processEnv: target })      // file values land on target
dotenv.populate(process.env, { HELLO: 'world' })
```

[src: lib/main.d.ts:19] [src: README.md:724] [src: README.md:769]

`populate(target, source, options)` takes `override` and `debug`, and returns only the keys it actually set. [src: lib/main.d.ts:123] [src: lib/main.d.ts:114] [src: lib/main.js:402]

**The file format.** One `KEY=value` per line; a leading `export ` is accepted, and so is `KEY: value` with a space after the colon. [src: lib/main.js:38] Keys are letters, digits, `_`, `.` and `-`. [src: lib/main.js:38] Values may be bare, or wrapped in single quotes, double quotes or backticks; a quoted value keeps its inner whitespace and may run over several lines, which is how a private key is stored. [src: lib/main.js:38] [src: README.md:450] [src: README.md:118] A bare value is trimmed. [src: lib/main.js:58] `KEY=` gives an empty string. [src: lib/main.js:55] `#` starts a comment, on its own line or after a value. [src: README.md:134] Windows line endings are normalized. [src: lib/main.js:48] Lines that do not match are skipped, and nothing reports them. [src: lib/main.js:51] [uncited]

## Invariants and traps

**The default `.env` is looked up in the working directory, not beside the script.** [src: lib/main.js:241] Started from another directory — a monorepo root, a process manager, a test runner — the file is not found. The symptom is variables that are `undefined` and a log line reading `injected env (0) from .env`: the line names the path that was tried whether or not the file existed. [src: lib/main.js:297] [src: lib/main.js:309] With `debug: true` a `failed to load <path> <message>` line says which path was read. [src: lib/main.js:282]

**A missing or unreadable file is not thrown.** The read error is caught and returned as `result.error`. [src: lib/main.js:284] [src: lib/main.js:313] Code that ignores the return value carries on with nothing loaded. With an array of paths, `error` is set if any one file failed — it is the last failure — while the other files were still loaded, so `if (result.error) throw result.error` aborts a start-up whose only fault is an optional `.env.local` that does not exist. [src: lib/main.js:284] [src: lib/main.js:279] [src: lib/main.js:313]

**In an ES module, calling `config()` in the module body is too late for other imports.** All imports are evaluated before the importing module's body runs, [src: README.md:381] so with `import * as dotenv from 'dotenv'; dotenv.config(); import client from './client.mjs'`, `client.mjs` reads `process.env` before `config()` has run and sees the value blank. [src: README.md:402] [src: README.md:405] [src: README.md:408] `import 'dotenv/config'` as the first import is the form that loads first. [src: README.md:413] When options are needed, the forms that hold are a small module of one's own that calls `config({...})` and is imported first, [src: README.md:504] [src: README.md:511] or the `DOTENV_CONFIG_*` environment variables alongside `import 'dotenv/config'`. [src: README.md:490]

**A variable that already exists is never replaced unless `override: true`.** [src: lib/main.js:383] [src: lib/main.js:384] "Exists" means the key is present, so a variable exported as an empty string in the shell blocks the file's value. [src: lib/main.js:383] [src: CHANGELOG.md:551] The symptom is an edited `.env` whose change does not take effect. `result.parsed` holds what the files said, not what was applied, so it can show the file's value while `process.env` still has the earlier one. [src: lib/main.js:315] [src: lib/main.js:288] With `debug: true` each such key is logged as `"KEY" is already defined and was NOT overwritten`. [src: lib/main.js:393]

**First value wins across files and across calls; last wins inside one file.** Calling `config()` a second time with another file does not replace keys the first call set, and in a `path` array the earlier file wins, both for the same reason. [src: lib/main.js:383] [src: README.md:658] Within a single file a key defined twice takes its later value. [src: lib/main.js:73]

**`config()` writes a line to standard output by default.** It is `◇ injected env (N) from <files>` followed by a randomly chosen tip, written with `console.log`. [src: lib/main.js:309] [src: lib/main.js:131] [src: lib/main.js:20] `N` counts only the keys actually set. [src: lib/main.js:295] A program whose standard output is consumed by something else gets this line first. `quiet: true` suppresses it, [src: lib/main.d.ts:49] and so does `DOTENV_CONFIG_QUIET=true`, in the environment or in the `.env` file itself. [src: CHANGELOG.md:74] [src: lib/main.js:292] `debug: true` prints it even when `quiet` is set. [src: lib/main.js:294] The `dotenv/config` entry point is the opposite: it is quiet unless `dotenv_config_quiet` is given as an argument. [src: lib/cli-options.js:12] [src: lib/cli-options.js:13]

**`DOTENV_CONFIG_QUIET` and `DOTENV_CONFIG_DEBUG` beat the options passed in code.** When either is set to a non-empty value it is used in place of `quiet` or `debug`, in both directions. [src: lib/main.js:247] [src: lib/main.js:248] [src: CHANGELOG.md:75] They are read from the target object, so with a custom `processEnv` they are looked up there. [src: lib/main.js:245] [src: lib/main.js:248] For these two, `false`, `0`, `no` and `off`, in any letter case, mean off; [src: lib/main.js:25] an empty variable falls back to the option given in code. [src: lib/main.js:248]

**`override` is not parsed that way: any non-empty string turns it on.** [src: lib/main.js:372] Through `dotenv/config` the value arrives as a string, [src: lib/env-options.js:21] [src: lib/cli-options.js:7] so `DOTENV_CONFIG_OVERRIDE=false` or `dotenv_config_override=false` enables overriding. The symptom is variables set in the shell or by the host being replaced by the file's values after overriding was "switched off".

**`#` ends an unquoted value.** `SECRET=abc#123` yields `abc`. [src: lib/main.js:38] [src: README.md:142] The symptom is a truncated password, token or URL fragment. In single quotes, double quotes or backticks the `#` is kept. [src: README.md:139]

**Values are always strings, and nothing is expanded.** `PORT=3000` is `'3000'` and `DEBUG=false` is the non-empty string `'false'`. [src: lib/main.d.ts:6] `${OTHER}` and `$(command)` are left in the value as written. [uncited] [src: README.md:188] [src: README.md:210] `\n` and `\r` become real line breaks only inside double quotes; in single quotes, backticks or a bare value they stay as a backslash and a letter. [src: lib/main.js:67] [src: lib/main.js:68] [src: lib/main.js:69] An escaped quote inside a quoted value is accepted, but the backslash stays in the result. [src: lib/main.js:38] [src: lib/main.js:64] [uncited]

**`parse()` takes one argument.** [src: lib/main.js:41] [src: lib/main.d.ts:22] The README still shows `dotenv.parse(buf, { debug: true })`; [src: README.md:755] the second argument is ignored and no debug message is printed. [uncited]

**A `DOTENV_KEY` in the environment changes what `config()` does.** When `DOTENV_KEY` is set, as an option or as an environment variable, and a `.env.vault` file exists, `config()` decrypts that file and never reads `.env`. [src: lib/main.js:136] [src: lib/main.js:141] [src: lib/main.js:326] [src: lib/main.js:335] On that path failures are thrown rather than returned, with `error.code` one of `INVALID_DOTENV_KEY`, `NOT_FOUND_DOTENV_ENVIRONMENT`, `DECRYPTION_FAILED` or `MISSING_DATA`. [src: lib/main.js:157] [src: lib/main.js:185] [src: lib/main.js:361] [src: lib/main.js:87] If the key is set but no vault file is found, a `you set DOTENV_KEY but you are missing a .env.vault file` warning goes to standard error and `.env` is loaded as usual. [src: lib/main.js:330] [src: lib/main.js:123] [src: lib/main.js:332] `configDotenv()` takes the same options and only ever reads the plain files. [src: lib/main.d.ts:142] [src: lib/main.js:240]

**It does not work in front-end code as it does in Node.** The package marks `fs` as unavailable for browser builds, [src: package.json:60] and bundling it for the browser without polyfills fails with `Module not found: Error: Can't resolve 'crypto|os|path'`. [src: README.md:576] [src: README.md:578] In a webpack-bundled React app `process.env` is only what the bundler injects. [src: README.md:556]

## What moved, and what it used to be called

Same call, new behaviour, first:

- **`config()` prints by default since 17.0.0.** [src: CHANGELOG.md:128] The message was introduced, on by default, in 16.6.0, [src: CHANGELOG.md:142] hidden by default in 16.6.1, [src: CHANGELOG.md:134] and shown again from 17.0.0. A plain `config()` call written for a release before 16.6.0 printed nothing then and writes a line to standard output now.
- **The log text changed.** It was `[dotenv@16.6.0] injecting env (1) from .env` in 16.6.0, [src: CHANGELOG.md:142] `◇ injecting env (14) from .env` in 17.4.0, [src: CHANGELOG.md:27] and reads `injected` since 17.4.1. [src: CHANGELOG.md:17] Since 17.0.1 the number counts only keys actually set. [src: CHANGELOG.md:122]
- **`#` starts a comment inside an unquoted value since 15.0.0.** [src: CHANGELOG.md:325] The changelog marks it breaking: from then on a value containing `#` has to be quoted. [src: CHANGELOG.md:325]
- **Backticks are quote characters since 16.0.0.** [src: CHANGELOG.md:305] A value that contains a backtick now needs single or double quotes around it. [src: CHANGELOG.md:307]
- **Multiline quoted values parse with no option since 15.0.0.** [src: CHANGELOG.md:321] The `multiline` option, added in 14.3.0, was removed in 15.0.0. [src: CHANGELOG.md:349] [src: CHANGELOG.md:331]
- **Several files are supported again.** Support for multiple `.env` files was removed in 1.0.0; [src: CHANGELOG.md:631] an array for `path` arrived in 16.4.1. [src: CHANGELOG.md:201] When `path` is set, `.env` is not also looked up; a regression in 16.4.3 that did so was reverted in 16.4.5. [src: CHANGELOG.md:179]
- **Since 5.0.0 the default path is the working directory's `.env`, and a key that exists with a falsy value is not written over.** [src: CHANGELOG.md:550] [src: CHANGELOG.md:551]

Renamed or removed:

- `dotenv.load()` was an alias for `config()` and was removed in 7.0.0; only `config()` exists. [src: CHANGELOG.md:520]
- The `silent` option was removed in 3.0.0 in favour of `verbose`, [src: CHANGELOG.md:587] and `verbose` was removed in 4.0.0. [src: CHANGELOG.md:566] Neither exists now; the current switches are `quiet` and `debug`. [src: lib/main.d.ts:53] [src: lib/main.d.ts:62]
- `config()` returns an object with `parsed` or `error` since 4.0.0; before that it returned `false` on failure. [src: CHANGELOG.md:562]
- Variable expansion was removed in 2.0.0. [src: CHANGELOG.md:605]
- A `debug` option for `parse` was added in 6.1.0; [src: CHANGELOG.md:532] `parse` in this version takes only the source. [src: lib/main.js:41] The changelog does not say when it went. [uncited]
- The type definitions ship in the package since 8.2.0, [src: CHANGELOG.md:498] and moved from `types/index.d.ts` to `lib/main.d.ts` in 12.0.0, when Flow support was dropped. [src: CHANGELOG.md:427] [src: CHANGELOG.md:423]
- The `browser` entries that stubbed `path`, `os` and `crypto` were removed in 16.1.3; only `fs` is stubbed. [src: CHANGELOG.md:248] [src: package.json:60]

Added, for anyone unsure whether a call exists in an older release: `import 'dotenv/config'` in 8.5.0, [src: CHANGELOG.md:480] `~` in `path` in 10.0.0, [src: CHANGELOG.md:446] `override` in 14.1.0, [src: CHANGELOG.md:368] `populate` and `.env.vault` loading in 16.1.0, [src: CHANGELOG.md:270] [src: CHANGELOG.md:274] `configDotenv` in 16.1.2, [src: CHANGELOG.md:254] `processEnv` in 16.2.0, [src: CHANGELOG.md:235] the `DOTENV_KEY` option in 16.3.0, [src: CHANGELOG.md:229] and `DOTENV_CONFIG_QUIET` in 17.2.0. [src: CHANGELOG.md:74]

## What it is not for

This section is drawn from the package alone, not from its maintainers.

- **Variable expansion, command substitution and encrypted env files.** The README points to dotenvx for each of these; [src: README.md:188] [src: README.md:210] [src: README.md:231] its npm package is `@dotenvx/dotenvx`. [src: README.md:523] The README's own example of expansion as an add-on uses a separate module, `dotenv-expand`. [src: README.md:432]
- **Validation or typing of configuration.** It does not check that a required variable is present, convert values to numbers or booleans, or apply a schema. [uncited]
- **Writing or editing `.env` files.** It only reads them. [uncited]
- **Supplying variables to browser code.** See the front-end trap above. [src: README.md:556]
- **Choosing a file by environment name.** Nothing looks at `NODE_ENV` or picks `.env.production` on its own; the files read are exactly the ones in `path`, or `.env`. [src: lib/main.js:258] [uncited]
