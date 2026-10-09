# Score: derived skill for io.github.aughtone.normalize:common 0.0.5

Scored against `source/commonMain/io/github/aughtone/normalize/common/` (9 files, 906 lines). All paths below are relative to that directory. The derived skill was not edited.

## Method and counting rules

- A claim is one assertion about the library. Every occurrence gets a verdict, including restatements (two of the 124 restate an earlier claim: 95 restates 85, 123 restates 47).
- Each of the three code examples is one claim: does it compile and behave as shown against the declarations in the source.
- Frontmatter is not scored, only the body.
- Two statements of absence carry no `[uncited]` marker but are conclusions from the code as a whole (105, 121). They are scored as uncited, giving 13 uncited claims where the skill marks 11.
- `Outcome` and `runOutcome` come from another library (`io.github.aughtone.types.outcome`) whose source is not in the three locations. Claims about them are judged by how this library's own code uses them (`PolicyResolver.kt:102-103`, `:128-139`).
- OVERSTATED is reserved for a claim where a concrete code path gives a different result than the claim states and the skill does not disclose the exception elsewhere. Loose wording that the skill itself corrects in another sentence is SUPPORTED with a caveat, listed separately.
- Every cited line was opened; every citation points at the line it claims to (no off-by-n citations found).

## Part 1 - every claim

### Intro and "What it solves"

| # | Claim (short) | Citation | Verdict | Evidence |
|---|---|---|---|---|
| 1 | Everything is in package `io.github.aughtone.normalize.common` | Policy.kt:1 | SUPPORTED | line 1 of all nine files |
| 2 | A result carries canonical string, policy id, policy version | Normalized.kt:20,22,25,28 | SUPPORTED | interface with exactly those three properties |
| 3 | Id and version together identify the bytes | Policy.kt:6 | SUPPORTED | Policy.kt:6 |
| 4 | A resolver turns a stored `(id, version)` into the `Policy` | PolicyResolver.kt:46 | SUPPORTED | PolicyResolver.kt:7, :46 |
| 5 | A new row is normalized under the policy existing rows used, not a hardcoded pairing | PolicyResolver.kt:13 | SUPPORTED | PolicyResolver.kt:12-15 |
| 6 | Same resolution serves an id typed into a properties file or env var | PolicyResolver.kt:15 | SUPPORTED | PolicyResolver.kt:15-17 |
| 7 | Id = links joined by `:`, each link `[a-z0-9]` segments joined by `.` | PolicyId.kt:17,18,19 | SUPPORTED | grammar block; enforced at PolicyLink.kt:122,130 and PolicyId.kt:153-155 |
| 8 | It can be checked lexically | PolicyId.kt:151 | SUPPORTED | `split` |
| 9 | Or parsed against the links modules publish | PolicyId.kt:136 | SUPPORTED | `parse(id, known)` |
| 10 | Values from different policies comparable only in a form both declare | ComparableForm.kt:16,17 | SUPPORTED | doc, and code at ComparableForm.kt:123 (`a.forms.intersect(b.forms)`) |
| 11 | One call gives the verdict for two stored identities | ComparableForm.kt:117 | SUPPORTED | `comparability(idA, versionA, idB, versionB)` |
| 12 | Portable spelling with `_` for a Kubernetes label value or container image tag, and back | PolicyId.kt:165,174,185 | SUPPORTED | doc :165-166, `toPortable` :174, `fromPortable` :185 |
| 13 | Publish policies or contribute a step via `Policy`, `PublishedPolicies`, `NormalizationStep` | Policy.kt:21; PolicyResolver.kt:189; NormalizationStep.kt:23 | SUPPORTED | all three are public and open for implementation |
| 14 | No normalizer here: nothing takes a value and a policy and returns a `Normalized` | [uncited] | TRUE | `Normalized` is an interface (Normalized.kt:20); no function in the source returns it or implements it. The only value-taking function is the abstract `NormalizationStep.apply(String): String` (NormalizationStep.kt:37) |
| 15 | Those functions and the named policy constants belong to the suite's other modules | Policy.kt:4, :18 | SUPPORTED | Policy.kt:4 ("every normalizer in this suite takes as its second argument"), :18-19; the only `Policy` implementations here are `OptedInPolicy` and `ComposedPolicy`, both wrappers |

### "How it is meant to be used"

| # | Claim (short) | Citation | Verdict | Evidence |
|---|---|---|---|---|
| 16 | The functions return `Outcome`, imported from `io.github.aughtone.types.outcome` | PolicyId.kt:3 | SUPPORTED | import at PolicyId.kt:3, PolicyResolver.kt:3, ComparableForm.kt:3 |
| 17 | Success exposes `data`, failure exposes `exception` | PolicyResolver.kt:102,103 | SUPPORTED | `direct.data`, `(direct as Outcome.Failure).exception` |
| 18 | Doc comment says store all three together; id and version are the only record | Normalized.kt:7 | SUPPORTED | Normalized.kt:7-9 |
| 19 | Example 1: `Triple(result.canonical, result.policyId, result.policyVersion)` typed `Triple<String, String, Int>` | (code) | SUPPORTED | property types at Normalized.kt:22,25,28 |
| 20 | Each module exposes one resolver; `+` combines them | PolicyResolver.kt:21, :58 | SUPPORTED | doc :21-22, `operator fun PolicyResolver.plus` :58 |
| 21 | `plus` and `comparability` are top-level extension functions needing their own import | PolicyResolver.kt:58; ComparableForm.kt:117 | SUPPORTED | both declared at file top level |
| 22 | Example 2: combine, resolve, read `Outcome.Success.data` / `Outcome.Failure.exception`, branch on `VersionMismatch` and `UnknownLink` with an `else` | (code) | SUPPORTED | same shape as the library's own code at PolicyResolver.kt:101-103 and :128-131; fields at PolicyIdentityError.kt:39, :69; `exception` is assignable to `Throwable?` (PolicyResolver.kt:126,133) so `error.message` is valid |
| 23 | The error is a sealed class extending `Exception` | PolicyIdentityError.kt:14 | SUPPORTED | line 14 |
| 24 | Doc says match on the subclass; message text is not API | PolicyIdentityError.kt:12 | SUPPORTED | line 12 |
| 25 | `VersionMismatch` carries `id`, `requested`, `available` | PolicyIdentityError.kt:69 | SUPPORTED | line 69 |
| 26 | `UnknownLink` carries `id` and `link` | PolicyIdentityError.kt:39 | SUPPORTED | line 39 |
| 27 | `comparability(idA, versionA, idB, versionB)` returns `Outcome<Comparability>` | ComparableForm.kt:117 | SUPPORTED | line 117 |
| 28 | `SamePolicy` when id and version are both equal | ComparableForm.kt:122 | SUPPORTED | line 122 |
| 29 | `InForm(form)` when the policies share a declared form, else `NotComparable` | ComparableForm.kt:123 | SUPPORTED | line 123 |
| 30 | Fails if either id does not resolve | ComparableForm.kt:115 | SUPPORTED | doc :115; code :119-120 (`getOrThrow()` on both) |
| 31 | Extend `PublishedPolicies` once, supply `policies` and `links`; parsing, ordering, version checking inherited | PolicyResolver.kt:184, :187 | SUPPORTED | doc :184-187; `policies`/`links` are the unimplemented members of `PolicyResolver` (:37, :40) |
| 32 | Example 3: `object AcmePolicies : PublishedPolicies()` overriding `policies` and `links` with `PolicyLink("acme", LinkKind.Base)` and `PolicyLink.Lenient` | (code) | SUPPORTED | abstract class with implicit public no-arg constructor (:189); link passes all four `require`s (PolicyLink.kt:75-86); `Lenient` at PolicyLink.kt:140 |
| 33 | `PolicyId.split(id)` checks the lexical grammar only | PolicyId.kt:148 | SUPPORTED | doc :148-149, code :151-158 |
| 34 | `PolicyId.parse(id, resolver.links)` also resolves each link and checks order | PolicyId.kt:132, :144 | SUPPORTED | :139-144; `links` is `List<PolicyLink>`, accepted as `Collection<PolicyLink>` |
| 35 | `PolicyId.of(links)` builds a chain, validating order and duplicates | PolicyId.kt:76 | SUPPORTED | :79-129 |
| 36 | `PolicyId` exposes `rendered` (`:`), `portable` (`_`), `base`, `links` | PolicyId.kt:54,60,63,51 | SUPPORTED | all four lines |
| 37 | `toPortable(id)` / `fromPortable(portable)` convert between the spellings as strings | PolicyId.kt:174, :185 | SUPPORTED | both return `Outcome<String>` |
| 38 | First link is the `LinkKind.Base` | PolicyId.kt:83 | SUPPORTED | line 83 |
| 39 | Each later link with a phase opens a new group; phase ranks may not decrease | PolicyId.kt:109 | SUPPORTED | :108-112 (rank check, then `sawRelaxation = false`) |
| 40 | `Map` 0, `Normalize` 1, `Encode` 2 | PolicyLink.kt:48,49,50 | SUPPORTED | lines 48-50 |
| 41 | Within a group, `Parameter` before `Relaxation` | PolicyId.kt:119 | SUPPORTED | :116-122 |
| 42 | `Form` links close the chain, in ascending name order | PolicyId.kt:101, :104 | SUPPORTED | :101 (non-form after a form refused), :104 (`link.name <= lastForm` refused) |

### "Invariants and traps" (17 traps)

| # | Trap | Claim (short) | Citation | Verdict | Evidence / trace |
|---|---|---|---|---|---|
| 43 | T1 | `of`, `parse`, `split`, `toPortable`, `fromPortable`, `resolve`, `comparability` all return `Outcome` | PolicyId.kt:79,136,151,174,185; PolicyResolver.kt:46; ComparableForm.kt:117 | SUPPORTED | seven signatures. Every `throw` in the source sits inside a `runOutcome { }` block (grep: PolicyId.kt:81-186, PolicyResolver.kt:104-238) |
| 44 | T1 | So a `try`/`catch` for `PolicyIdentityError` catches nothing and a bad id seems accepted | (same) | SUPPORTED | PolicyResolver.kt:138-139: the library itself calls `PolicyId.parse` and tests `is Outcome.Failure`, so a parse failure arrives as a value. Depends on `runOutcome` (external) capturing throws, which that usage demonstrates |
| 45 | T1 | The library's own code reads `Outcome.Failure` and its `exception` | PolicyResolver.kt:139, :131 | SUPPORTED | both lines |
| 46 | T1 | That code allows for a failure whose exception is not a `PolicyIdentityError`; a `when` wants an `else` | PolicyResolver.kt:104, :132 | SUPPORTED | `directFailure !is PolicyIdentityError`, `error !is PolicyIdentityError`. A concrete source of one: PolicyResolver.kt:216 and :219 can raise `IllegalArgumentException` inside `runOutcome` |
| 47 | T2 | Three things throw `IllegalArgumentException`: `PolicyLink` constructor, `ComparableForm` constructor, `Policy.withForms` for a form not in `offeredForms` | PolicyLink.kt:75; ComparableForm.kt:38; Policy.kt:47 | SUPPORTED (caveat) | grep for `require(`/`throw`: exactly six `require` calls, in those three places; no `check`, `error(` or `!!`. Caveat: the public `ComparableForm.ofLink` (ComparableForm.kt:59-60) reaches the `ComparableForm` constructor, so `ofLink("form.A")` throws rather than returning `null` - a fourth call site of the second cause, not named |
| 48 | T2 | `PolicyLink` refused for invalid name, `Step` without phase, `Parameter`/`Relaxation`/`Form` with one, `Form` kind and `form.` prefix disagreeing | PolicyLink.kt:75,78,81,84 | SUPPORTED | four `require`s, each matches |
| 49 | T2 | Messages such as "policy link name must be lowercase segments of [a-z0-9] joined by '.'" and "a Step link must declare the phase at which it runs" | PolicyLink.kt:76, :79 | SUPPORTED | both are prefixes of the real messages |
| 50 | T2 | `PolicyLink.isValidLinkName(name)` tests without throwing | PolicyLink.kt:129 | SUPPORTED | :129-130 |
| 51 | T3 | A segment is `[a-z0-9]+` and nothing else (no uppercase, hyphen, underscore) | PolicyLink.kt:122 | SUPPORTED | `SEGMENT.matches(it)` is a whole-string match (:130) |
| 52 | T3 | A region is `region.ca`, never `region.CA` | PolicyId.kt:22 | SUPPORTED | line 22 |
| 53 | T3 | Symptom: `MalformedLink`, whose `link` is the offending name | PolicyId.kt:155; PolicyIdentityError.kt:17 | OVERSTATED (narrow) | True for `split`, `parse`, `toPortable`, `fromPortable`, and any non-form link through `resolve`. Not true for a malformed trailing form link through `resolve`: `resolve("email:form.A", v)` -> PolicyResolver.kt:209-210 counts `form.A` as a form link -> base `email` resolves (:214) -> :216 `ComparableForm.ofLink("form.A")` -> ComparableForm.kt:60 constructs `ComparableForm("A")` -> ComparableForm.kt:38 `require` fails -> `IllegalArgumentException`, not `MalformedLink`. The `?: throw MalformedLink` on :216 only fires for the bare `form.` |
| 54 | T4 | `split` returns `EmptyId` only for the empty string, `of` only for the empty list | PolicyId.kt:152, :81 | SUPPORTED | grep `EmptyId`: thrown at exactly those two lines |
| 55 | T4 | `:` or a trailing `:` splits into empty names -> `MalformedLink` with an empty `link` | PolicyLink.kt:130; PolicyId.kt:155 | SUPPORTED | `":".split(':')` = `["", ""]`; `isValidLinkName("")` is false on `isNotEmpty()`; :155 throws `MalformedLink("")`. Same through `resolve(":")` (PolicyResolver.kt:209-211 -> :234 -> `parse` -> `split`) |
| 56 | T4 | The `EmptyId` doc comment mentions ids consisting only of separators | PolicyIdentityError.kt:19 | SUPPORTED | line 19. The doc comment is wrong and the skill is right: no path yields `EmptyId` for a separators-only id |
| 57 | T5 | One policy has exactly one valid id; wrong order is refused, never sorted | PolicyId.kt:41; PolicyLink.kt:7 | SUPPORTED | docs, and `of` only throws, never reorders (:79-129) |
| 58 | T5 | `MissingBase` when the first link is not a base | PolicyId.kt:83 | SUPPORTED | line 83 |
| 59 | T5 | `DuplicateLink` when a name repeats | PolicyId.kt:87 | SUPPORTED | line 87 |
| 60 | T5 | `MultipleBases` for a later base link that has no phase | PolicyId.kt:114 | SUPPORTED | a later `Base` with a phase is taken by the `linkPhase != null` branch first (:108); only a phase-less one reaches :114 |
| 61 | T5 | `OutOfOrder`, naming the link, for a parameter after a relaxation, a phase going backwards, or anything after a form link | PolicyId.kt:119, :109, :101 | SUPPORTED (caveat) | all three lines. "Anything" is loose: a further form link in ascending name order is accepted (:103-105). The skill says so itself in claim 42 |
| 62 | T6 | `PolicyId.of` accepts any correctly ordered links, including caller-constructed ones | PolicyId.kt:79 | SUPPORTED | `of` makes no publication check |
| 63 | T6 | Constructing a link creates no policy | PolicyLink.kt:57 | SUPPORTED | :57-59 |
| 64 | T6 | Only `parse` and a resolver check links against what modules publish; symptom `UnknownLink` | PolicyId.kt:142 | SUPPORTED | line 142; resolver path PolicyResolver.kt:234 |
| 65 | T6 | `PolicyId` constructor is private; `PolicyId(listOf(...))` does not compile | PolicyId.kt:51 | SUPPORTED | `private constructor` |
| 66 | T7 | `parse` recognizes a form link from its name, not from the known links | PolicyId.kt:140, :142 | SUPPORTED | `byName[name] ?: ComparableForm.ofLink(name)?.link` |
| 67 | T7 | Whether the policy offers the form is the resolver's check; the id parses, then fails with `FormNotOffered` | PolicyResolver.kt:218 | SUPPORTED | traced `resolve("email:form.x", v)`: :210 formCount 1 -> :214 base resolves -> :218 `x !in offeredForms` -> `FormNotOffered(id, "x")` |
| 68 | T8 | A stored `(id, 1)` never resolves to version 2, the newest, or a default | PolicyResolver.kt:29; PolicyIdentityError.kt:66 | SUPPORTED | docs, and code :237-238 matches `it.version == version` exactly |
| 69 | T8 | In the inherited lookup: `VersionMismatch`, published versions in `available` | PolicyResolver.kt:238 | SUPPORTED | `matches.map { it.version }.sorted()` |
| 70 | T8 | A chain that parses but no listed policy carries is `UnknownPolicy` | PolicyResolver.kt:236 | SUPPORTED | line 236 |
| 71 | T9 | `email_subaddress.removed` passed to `split` or `parse` gives `MalformedLink`; `_` is not in the grammar | PolicyId.kt:167; PolicyLink.kt:122; PolicyId.kt:155 | SUPPORTED | no `:` so one name; segment `email_subaddress` fails `[a-z0-9]+` |
| 72 | T9 | The inherited `PublishedPolicies` lookup parses the id the same way | PolicyResolver.kt:234 | SUPPORTED | `PolicyId.parse(id, links)` |
| 73 | T9 | `fromPortable` refuses any string containing `:` with `NotPortable` | PolicyId.kt:186 | SUPPORTED | line 186: any `:`, with or without `_` |
| 74 | T9 | Both conversions check the lexical grammar only | PolicyId.kt:172, :188 | SUPPORTED | both call `split` only |
| 75 | T9 | Doc says store and compare the canonical id, not the portable one | PolicyId.kt:170 | SUPPORTED | line 170 |
| 76 | T10 | Contract: values under different policies never match, even where canonical strings agree | Normalized.kt:11 | SUPPORTED | :11-13 |
| 77 | T10 | Nothing in this module enforces that on a plain string comparison | [uncited] | TRUE | `canonical` is a bare `String` (Normalized.kt:22); the only check is the opt-in `comparability` call |
| 78 | T10 | The same id at two different versions is not `SamePolicy` | ComparableForm.kt:122 | SUPPORTED | `a.id == b.id && a.version == b.version` |
| 79 | T10 | With several shared forms, `InForm.form` is the first by name | ComparableForm.kt:100, :123 | SUPPORTED | `minOrNull()` over `compareTo` by name (:46) |
| 80 | T11 | When no single module owns an id, a combined resolver splits at each link published with a phase and resolves each group with its owner | PolicyResolver.kt:107, :78 | SUPPORTED | :101-104 (direct attempt first), :107 openers, :108 `splitGroups`, :111-117 `resolveDirect` per group |
| 81 | T11 | Returns a `ComposedPolicy` exposing `base` and `steps` | PolicyResolver.kt:169 | SUPPORTED | line 169 |
| 82 | T11 | Doc says derive new values by passing `base` and `steps` back to the normalizer owning the base | PolicyResolver.kt:165 | SUPPORTED | :165-167 |
| 83 | T11 | Its version is the base's | PolicyResolver.kt:173 | SUPPORTED | line 173 |
| 84 | T11 | It declares no forms of its own | [uncited] | TRUE | `ComposedPolicy` (:169-181) overrides neither `forms` nor `offeredForms` |
| 85 | T11 | Default for `forms` and `offeredForms` is empty | Policy.kt:32, :35 | SUPPORTED | both lines |
| 86 | T11 | So an id that resolves to a composed policy and also opts into a form fails with `FormNotOffered` | PolicyResolver.kt:218 | SUPPORTED | traced `(A + B).resolve("x:step:form.f", v)`: :96 `resolveWithForms` strips `form.f` -> :97 `resolveComposite("x:step")` returns `ComposedPolicy` -> :218 `f !in emptySet()` -> `FormNotOffered` |
| 87 | T12 | Any error other than `UnknownLink`/`UnknownPolicy` from one module is returned at once, later modules unasked | PolicyResolver.kt:132, :148 | SUPPORTED | `return outcome` inside the loop at :132; `isOwned()` at :148; a non-`PolicyIdentityError` also returns at once |
| 88 | T13 | `PublishedPolicies.resolve` is final; overriding it is a compile error | PolicyResolver.kt:195 | SUPPORTED | `final override fun resolve` |
| 89 | T13 | The default looks up by exact id among `policies` after parsing against `links` | PolicyResolver.kt:234, :235 | SUPPORTED | both lines, reached from `resolveBase` (:201) |
| 90 | T13 | A policy whose id uses a link missing from `links` never resolves: `UnknownLink` for the module's own policy | PolicyId.kt:142 | SUPPORTED | `parse` fails at :142 before the lookup at PolicyResolver.kt:235 is reached |
| 91 | T14 | A composed group is resolved at version 1 always | PolicyResolver.kt:116, :145 | SUPPORTED (caveat) | true of every step group (`groups.drop(1)`, :112-116). The first, base group is resolved at the requested version (:111). The paragraph's heading scopes the claim to steps |
| 92 | T14 | The policy it resolves to must also implement `NormalizationStep`, else `NotAStep` | PolicyResolver.kt:117 | SUPPORTED | `resolved as? NormalizationStep ?: throw NotAStep` |
| 93 | T14 | A step's first link declares a phase; further links are its qualifiers | NormalizationStep.kt:26 | SUPPORTED | :26-28 |
| 94 | T14 | A changed rule is a new step with a new link, never an edit | NormalizationStep.kt:19 | SUPPORTED | :19-21 |
| 95 | T15 | `forms` and `offeredForms` default to empty | Policy.kt:32, :35 | SUPPORTED | restates 85 |
| 96 | T15 | Default `withForms` returns an `OptedInPolicy`, or `this` for an empty set | Policy.kt:48 | SUPPORTED | line 48 |
| 97 | T15 | `OptedInPolicy` cannot be constructed directly: internal constructor | ComparableForm.kt:71 | SUPPORTED | `internal constructor` |
| 98 | T15 | A normalizer taking a concrete policy type will not accept the wrapper, hence the doc says override `withForms` to return the module's own type | Policy.kt:40 | SUPPORTED | :40-41; `OptedInPolicy` implements only `Policy` (ComparableForm.kt:71) |
| 99 | T15 | A published policy's output is frozen: a rule change is a new constant with its own id or a bumped `version` | Policy.kt:15 | SUPPORTED | :15-16 |
| 100 | T16 | `@InternalNormalizeApi` is an error-level opt-in; no stability promise | InternalNormalizeApi.kt:16, :11 | SUPPORTED | `RequiresOptIn.Level.ERROR`; doc :11-13 |
| 101 | T16 | Nothing in this module is marked with it | [uncited] | TRUE | grep: the only occurrence is the declaration at InternalNormalizeApi.kt:21 |
| 102 | T16 | It exists for the suite's other modules | InternalNormalizeApi.kt:4 | SUPPORTED | :4-9 |
| 103 | T17 | Error messages contain the policy id on purpose; an id is public identity, unlike the values normalized | PolicyIdentityError.kt:6 | SUPPORTED (caveat) | :6-10. 11 of 13 subclasses interpolate `$id`; `MalformedLink` (:17) carries only the offending link and `EmptyId` (:20) nothing. The practical point - identity text reaches logs - holds |

### "What moved, and what it used to be called"

| # | Claim (short) | Citation | Verdict | Evidence |
|---|---|---|---|---|
| 104 | Nothing carries `@Deprecated` or `ReplaceWith` | [uncited] | TRUE | grep: no match |
| 105 | The source does not say what earlier versions looked like | (unmarked) | TRUE | grep for `used to`, `previously`, `formerly`, `renamed`, `0.0.x`: no history. The nearest hints are "a name carries no hyphens" (PolicyLink.kt:102) and "No hyphens" (:121), which state the present rule only |
| 106 | No global registry, no `register` call; resolvers combined explicitly with `+` | PolicyResolver.kt:22, :58 | SUPPORTED | doc :22; grep for `register`/`registry` finds only that doc comment |
| 107 | Not exceptions, not `kotlin.Result`: parsing and resolving return `io.github.aughtone.types.outcome.Outcome` | PolicyResolver.kt:3, :46 | SUPPORTED | import and signature |
| 108 | Enum members are PascalCase: `LinkKind.Base`/`Form`/`Parameter`/`Relaxation`/`Step`, `StepPhase.Map`/`Normalize`/`Encode` | PolicyLink.kt:18,24,27,30,33,48,49,50 | SUPPORTED | all eight lines |
| 109 | A phase's position is its explicit `rank`, not declaration order | PolicyLink.kt:44 | SUPPORTED | doc :44-45; code compares `linkPhase.rank` (PolicyId.kt:109) |
| 110 | `policyId` is a `String`, `policyVersion` an `Int` | Normalized.kt:25, :28 | SUPPORTED | both lines |
| 111 | There is no combined `id@version` string | [uncited] | TRUE | grep for `@` in a string or id context: none; version is always a separate `Int` |
| 112 | Separators are `:` between links and `.` inside one | PolicyId.kt:17, :18 | SUPPORTED | grammar; `SEPARATOR = ':'` (:72) |
| 113 | `_` appears only in the portable spelling | PolicyId.kt:60 | SUPPORTED | :60, :73, :167 |
| 114 | The shared relaxation link is `PolicyLink.Lenient`, named `lenient` | PolicyLink.kt:140 | SUPPORTED | line 140 |

### "What it is not for" (4 boundaries)

| # | Boundary | Claim (short) | Citation | Verdict | Evidence |
|---|---|---|---|---|---|
| 115 | B1 | It does not normalize, validate or hash any value; it holds the contracts | [uncited] | TRUE | no transform, no table, no hash function; the only `hash` hits are `hashCode()` overrides and prose. What it validates is ids, not values |
| 116 | B2 | `PolicyId` does not turn an id into a usable policy; that is a resolver's job | PolicyId.kt:49 | SUPPORTED | :46-49; `PolicyId` holds links only (:51) |
| 117 | B3 | A `ComposedPolicy` is a record of what produced some bytes, not a normalizer | PolicyResolver.kt:165 | SUPPORTED | doc :165; the class (:169-181) has no `apply` or any transform |
| 118 | B4 | A comparable form is not a way to assert two arbitrary policies match: it must be declared by the policy, or offered and opted into in the id | ComparableForm.kt:21,22,23 | SUPPORTED | docs; enforced at Policy.kt:46-47 and PolicyResolver.kt:218; verdict computed from `forms` only (ComparableForm.kt:123) |

### "Other languages"

| # | Claim (short) | Citation | Verdict | Evidence |
|---|---|---|---|---|
| 119 | The published source is one common source set with no platform-specific declarations, so everything holds on every target | [uncited] | TRUE | the source tree has only `commonMain`; grep for `expect`/`actual`: none, so no platform `actual` is required. Limit: a root multiplatform sources artifact carries only the common source set, so the absence of extra platform-only declarations cannot be proved from it |
| 120 | Nothing is annotated `@JsExport`, so no surface is exported to JavaScript or TypeScript callers | [uncited] | TRUE | grep: no match |
| 121 | The source does not show whether an Apple framework is published for Swift callers | (unmarked) | TRUE | nothing in the source bears on packaging |
| 122 | No declaration carries a `@Throws` annotation | [uncited] | TRUE | grep: no match |
| 123 | `PolicyLink` constructor, `ComparableForm` constructor and `Policy.withForms` throw `IllegalArgumentException` | PolicyLink.kt:75; ComparableForm.kt:38; Policy.kt:47 | SUPPORTED | restates 47 |
| 124 | There are no suspend functions | [uncited] | TRUE | grep: no match |

### Totals

- Total claims: 124. Cited: 111. Uncited: 13 (11 marked `[uncited]`, 2 unmarked statements of absence).
- Cited: SUPPORTED 110, OVERSTATED 1, UNSUPPORTED 0, CONTRADICTED 0.
- Uncited: TRUE 13, FALSE 0, CANNOT-TELL 0.

### The one claim that is not SUPPORTED

- Claim 53, "Symptom: `MalformedLink`, whose `link` is the offending name" [src: PolicyId.kt:155] [src: PolicyIdentityError.kt:17] - OVERSTATED, narrowly. The symptom is right for `split`, `parse`, `toPortable`, `fromPortable` and for any non-form link through `resolve`. A bad character in a trailing `form.` link through `resolve`, when the base resolves, surfaces `IllegalArgumentException` from the `ComparableForm` constructor instead (PolicyResolver.kt:216 -> ComparableForm.kt:60 -> ComparableForm.kt:38). The skill's own advice in T1 to keep an `else` branch covers the handling, but the stated symptom is wrong on that path.

### SUPPORTED with a caveat (not counted against the skill)

- Claim 47 ("Three things do throw"): three causes, confirmed exhaustive by grep, but `ComparableForm.ofLink` is a fourth public call site of the second cause and throws rather than returning `null` for `form.<bad name>` (ComparableForm.kt:59-60).
- Claim 61 ("anything after a form link"): a further form link in ascending name order is accepted (PolicyId.kt:103-105); the skill says so itself in claim 42.
- Claim 91 ("a composed group is resolved at version 1 always"): step groups only; the base group uses the requested version (PolicyResolver.kt:111).
- Claim 103 ("error messages contain the policy id"): `MalformedLink` carries only the link and `EmptyId` nothing (PolicyIdentityError.kt:17, :20).
- Claim 119: true of the published source; the limit of a common-only sources artifact is noted in the row.

### Omissions noticed while tracing (not claims, so not scored)

- `PolicyIdentityError.NotCanonical` is never mentioned in the body, though it is declared (PolicyIdentityError.kt:47) and thrown on two paths (PolicyResolver.kt:120, :220).
- `MissingBase` is also what an id made only of form links gets (PolicyResolver.kt:212), and `DuplicateLink` for a repeated form link (:217); the skill gives only the `PolicyId.of` causes.
- `PolicyLink.dataVersion` (PolicyLink.kt:97-105) and `ComparableForm.ofLink` / `.link` are not described.
- That opting into a form changes the id and not the bytes (ComparableForm.kt:67, Policy.kt:38) is not stated.

## Part 2 - against the authored skill

### Authored traps and boundaries, and whether the derived skill has them

"Invariants and traps" (11):

| # | Authored point | In derived? | Where / note |
|---|---|---|---|
| A1 | A resolver only knows the modules combined into it; a cross-module chain needs each; symptom `UnknownLink`/`UnknownPolicy` for an id known to be real | PARTIAL | The mechanism is there (L40 combine with `+`; example prints "no module publishes"; T11 composed chains), but the trap - a single module's resolver failing on a real cross-module id - is never stated |
| A2 | Resolution never approximates | FOUND | T8 |
| A3 | One policy, one id; refused rather than repaired; `OutOfOrder`, `DuplicateLink`, `NotCanonical` | FOUND | T5. `NotCanonical` is absent from the derived skill |
| A4 | `PolicyIdentityError` messages quote the id, deliberately | FOUND | T17 |
| A5 | Not everything fails through `Outcome`; three things throw `IllegalArgumentException` | FOUND | T1 + T2 |
| A6 | A comparable form is usable only when both policies carry it; `forms` vs `offeredForms`; opting in changes the id and no bytes | FOUND | L22, L63, B4. The "no bytes change" half is missing |
| A7 | `SamePolicy` needs the same id and the same version | FOUND | T10 |
| A8 | `ComposedPolicy` is a record, not a normalizer; version is the base's | FOUND | T11, B3 |
| A9 | Constructing a `PolicyLink` grants no authority | FOUND | T6, B2 |
| A10 | The portable spelling is transport, never storage; `NotPortable` | FOUND | T9 |
| A11 | `@InternalNormalizeApi` carries no stability promise | FOUND | T16 |

"What it is not for" (4):

| # | Authored point | In derived? | Where / note |
|---|---|---|---|
| A12 | Not a normalizer | FOUND | L26, B1 |
| A13 | Not a registry; resolution is not discovery | FOUND | L119 ("Not a global registry") |
| A14 | Not a general-purpose result type; `Outcome` belongs to another library | PARTIAL | The derived skill says where `Outcome` is imported from (L30, L120) but does not state the boundary |
| A15 | Encodes universal standards, never one provider's behaviour | MISSED | Nothing in the source says this (grep `provider`, `vendor`: none); not recoverable from source |

Of 15: 12 found, 2 partial, 1 missed.

Outside those two sections the authored skill also carries:

| # | Authored point | In derived? | Note |
|---|---|---|---|
| A16 | Kotlin only: no `@JsExport`; no XCFramework, Swift package or podspec published; all in `commonMain`, no `expect`/`actual` | PARTIAL | `@JsExport` and common-only found; on Swift the derived skill says the source does not show it. Publication facts are not in the source |
| A17 | Every id was respelled in 0.0.4 (`+` to `:`, no hyphens, `<subject>.<what was done>`); 0.0.3 ids refused; old portable spellings no longer convert to a resolvable id | MISSED | History is not in the source. The current grammar is found (T3, L123) |
| A18 | `PublishedPolicies.resolve` became final in 0.0.3; override `resolveBase` | FOUND (current state) | T13, without the version history |
| A19 | `Outcome` accessors renamed in 0.0.5; failure callbacks now receive the `Throwable`; `it.message` silently becomes nullable | MISSED | Belongs to the `Outcome` library and to history; neither is in the source |

Every missed item (A15, A17, A19) is one the source cannot supply.

### Traps and boundaries only the derived skill has

Whole traps the authored skill lacks (6), all confirmed by the source:

| Derived trap | Verdict |
|---|---|
| T3 No uppercase, hyphen or underscore in an id; `MalformedLink` | Grammar SUPPORTED; the symptom line is OVERSTATED on one path (claim 53) |
| T4 `EmptyId` means empty string or empty list, not "only separators" | SUPPORTED - and it corrects the library's own doc comment (PolicyIdentityError.kt:19 vs PolicyId.kt:152-155) |
| T7 `parse` accepts any `form.<name>` link; `FormNotOffered` only at resolve | SUPPORTED |
| T12 In a combined resolver, a module's refusal wins and later modules are not asked | SUPPORTED |
| T14 Implementing a step: step groups resolve at version 1, must implement `NormalizationStep` or `NotAStep`, frozen | SUPPORTED (caveat on claim 91) |
| T15 Implementing a policy: empty defaults, `OptedInPolicy` wrapper and internal constructor, override `withForms`, frozen output | SUPPORTED |

Derived-only points inside traps both skills share (8), all confirmed:

- T1: a failure's exception need not be a `PolicyIdentityError`, so keep an `else` (PolicyResolver.kt:104, :132).
- T2: the four `PolicyLink` refusal conditions and `isValidLinkName` as the non-throwing test.
- T5: the exact conditions for `MissingBase` and `MultipleBases`.
- T6: `PolicyId.of` makes no publication check; the constructor is private.
- T9: the portable spelling fed to `split`, `parse` or an inherited resolver gives `MalformedLink`.
- T10: with several shared forms, `InForm.form` is the first by name.
- T11: a composed id that also opts into a form fails with `FormNotOffered`.
- T13: a link left out of `links` makes the module's own policy fail with `UnknownLink`.

### Disagreements

No point where the two skills assert opposite things. Three differences of precision:

- `NotPortable`. Authored: "a value containing both separators is refused". Derived: "any string containing `:`". The source supports the derived wording (PolicyId.kt:186 tests only for `:`); the authored statement is true but narrower than the code.
- `EmptyId`. The authored skill is silent; the derived skill contradicts the library's doc comment. The code supports the derived skill (PolicyId.kt:81, :152, :155).
- Swift packaging. Authored: none is published. Derived: the source does not show. The source cannot settle it; the derived skill abstains rather than disagrees.

One authored imprecision unrelated to the derived skill: A1 says the failure names "the link whose module is missing", which holds for `UnknownLink` but not `UnknownPolicy`, which carries only the id (PolicyIdentityError.kt:62).
