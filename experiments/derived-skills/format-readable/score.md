# Scoring pass: derived skill for format-readable 4.1.0

Scored against the published source only (`source/`, 37 files, all read in full). Paths below are relative to `source/commonMain/io/github/aughtone/` unless they start with a source-set name.

## Method and counting rules

- A claim is one assertion about the library. A table row counts as two claims (the package, the entry-point signatures). Each output comment in a code example counts as one claim.
- The second code example carries no citations, so its five output comments are scored as uncited.
- "Other targets were not checked", "This skill describes the Kotlin surface" and "Only what the source itself says is claimed here" are statements about the document, not the library, and are not counted.
- A cited claim that rests on stdlib or platform behaviour would be OVERSTATED. An uncited one that does is CANNOT-TELL from this source; in both cases I say whether I believe it.
- Every citation in the skill was opened. Every one points at the line it says it does; there is no mis-cited line anywhere in the document.

## Totals

| | Count |
|---|---|
| Total claims | 165 |
| Cited | 139 |
| SUPPORTED | 138 |
| OVERSTATED | 1 |
| UNSUPPORTED | 0 |
| CONTRADICTED | 0 |
| Uncited | 26 |
| TRUE | 23 |
| FALSE | 0 |
| CANNOT-TELL | 3 |

## Part 1: claim by claim

### Opening paragraph

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 1 | API lives in packages under `io.github.aughtone.readable` | SUPPORTED | `readable/number/Number.functions.kt:1` and every other file's package line |
| 2 | Only thing in `io.github.aughtone.format.readable` is a generated resources accessor | SUPPORTED | `format/readable/resources/Res.kt:7,17`; the only other declarations there are the `Res.all…Resources` collectors. No file in `readable/**` references `Res` (grep) |

### What it solves

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 3 | Relative phrases for `Instant`, `LocalDateTime`, `LocalDate`, `LocalTime`; switches to absolute when the gap is large | SUPPORTED | `relative/RelativeTime.extensions.kt:71,167,235,310`; fallback at :83-85, :245-247, :320-322; "just now" at `RelativeTime.resources.kt:178` |
| 4 | Duration as one rounded unit, "2 hours" or "5m" | SUPPORTED | `duration/Duration.resources.kt:92-105` picks one unit and rounds; short units :115-118 |
| 5 | Numbers with locale separators | SUPPORTED | `number/Number.resources.kt:44-61,83` |
| 6 | Compact "1.5k", "1M" | SUPPORTED | `Number.functions.kt:204-211`; 1500/10^3 = 1.5 + symbol |
| 7 | Byte counts to "1.5 KiB" | SUPPORTED | `metrics/DataSize.functions.kt:53-61`; 1536/1024 = 1.5 (unit symbol comes from the external `UnitOfMeasure`) |
| 8 | Metres, grams, watts scaled to "1.5 km" | SUPPORTED | `metrics/Metric.functions.kt:59-86` |
| 9 | Ordinals "1st", "2e" | SUPPORTED | `ordinality/Ordinality.resources.kt:24-33`; `PluralCategory.kt:128` gives fr 2 -> Other -> "e" |
| 10 | Lists "Monday, Wednesday, and Friday", "red or green" | SUPPORTED | `list/List.resources.kt:53-54,131`; traced through `List.functions.kt:44-51` |
| 11 | Currency symbol before or after per locale | SUPPORTED | `financial/Money.resources.kt:26-38` |
| 12 | Coordinates as decimal degrees or DMS with hemisphere letter | SUPPORTED | `quantitative/Quantitative.functions.kt:72-113` |
| 13 | Bearing as "90° (E)" | SUPPORTED | `Quantitative.functions.kt:35-39,115-119`; (90+22.5)/45 = 2 -> "E" |
| 14 | Time zone as abbreviation or full name | SUPPORTED | `time/TimeZone.extensions.kt:27-36` |
| 15 | Exposes the plural-category rule | SUPPORTED | `PluralCategory.kt:8,32` (public) |
| 16 | Ready-made `Locale` constants | SUPPORTED | `Locales.kt:8` |

### How it is meant to be used

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 17 | [uncited] Nothing to construct, configure or close; every entry point is a top-level or extension function | TRUE | The only public types are enums, `Locales`, `RelativeTimeConfig` and typealiases; every formatter is a function |
| 18 | Per-locale formatters built on first use and cached | SUPPORTED | `Number.resources.kt:63-96`; same pattern in duration, relative, ordinality, money |
| 19 | [uncited] `formatReadable` is declared in seven packages | TRUE | Counted: number, metrics, duration, financial, quantitative, geo, time. ("One per kind of value" is loose: quantitative and geo both declare it for `Altitude` and `Azimuth`, as the skill's own trap says) |
| 20-43 | Table, 12 rows x (package, signatures) | SUPPORTED x24 | Every package line and every signature checked: `Number.functions.kt:22,193`; `DataSize.functions.kt:26`; `Metric.functions.kt:19,30,50`; `Ordinality.extensions.kt:13`; `Ordinality.functions.kt:18`; `List.functions.kt:37`; `List.types.kt:11-26`; `Duration.functions.kt:25`; `time/Long.extensions.kt:43`; `RelativeTime.extensions.kt:71,167,235,310`; `RelativeStyle.kt:6`; `RelativeDirection.kt:15`; `TimeZone.extensions.kt:27,52`; `Money.functions.kt:15`; `Quantitative.functions.kt:21,35,52`; `geo/Geo.functions.kt:55`; `Quantitative.types.kt:6`; `PluralCategory.kt:32,112`; `Locales.kt:8`. Parameter order matches in every case |
| 44 | Number, compact, size and metric functions on `Double`, `Float`, `Long`, `Int`, `Short`, `Byte` and four unsigned types | SUPPORTED | Ten receivers in each of the four families (grep of `^fun`) |
| 45 | Ordinals on the eight integer types only | SUPPORTED | `Ordinality.extensions.kt:13,31,50,68,86,104,122,140` |
| 46 | `precision` defaults to 1 except plain `formatReadable` on integer types and `Azimuth`, where it is 0 | SUPPORTED | `Number.functions.kt:22,38` (1) vs :54,70,86,102,118,134,150,166 (0); all abbreviated, data-size, metric = 1; `Quantitative.functions.kt:21` (1), :35 (0) |
| 47 | Value types come from `io.github.aughtone.types` | SUPPORTED | Import lines as cited |
| 48 | `Instant` and `Clock` are `kotlin.time`'s | SUPPORTED | `RelativeTime.extensions.kt:17,23` |
| 49 | `LocalDate`, `LocalDateTime`, `LocalTime`, `TimeZone`, `UtcOffset` are `kotlinx.datetime`'s | SUPPORTED | `RelativeTime.extensions.kt:7-10`; `TimeZone.extensions.kt:7` |
| 50 | `DateTimeStyle` is from `io.github.aughtone.datetime.format` | SUPPORTED | `RelativeTime.extensions.kt:3` |
| 51 | "Every call takes an optional `locale`, defaulting to `Locale.current`" | OVERSTATED | True of every `formatReadable…` function. Not true of `pluralCategoryFor(locale, n)` and `ordinalCategoryFor(locale, n)`, which the skill's own table lists as entry points and which take a required `locale` (`PluralCategory.kt:32,112`); nor of the public `formatDecimalDegrees(locale)` / `formatDegreesMinutesSeconds(locale)` (`Quantitative.functions.kt:72,91`; `Geo.functions.kt:75,94`) |
| 52 | `Locales.English` (en-US), `Locales.German`, `Locales.SwissGerman` | SUPPORTED | `Locales.kt:12,14,15` |
| 53 | Lookup: language+region, bare language, then default (English wording; dot decimal, comma grouping) | SUPPORTED | `Number.resources.kt:71-83`; `RelativeTime.resources.kt:493-516` |

### First code example (cited by the sentence after it)

| # | Line | Verdict | Trace |
|---|---|---|---|
| 54 | `1234.56.formatReadable(Locales.English)` -> "1,234.6" | SUPPORTED | "en-US" -> null, "en" -> null, default "."/","; 12345.6 rounds to 12346, /10 = 1234.6; "1234" reversed, chunked, rejoined = "1,234" |
| 55 | German -> "1.234,6" | SUPPORTED | "de" -> "," / "." (`Number.resources.kt:56`) |
| 56 | `2500.formatReadableAbbreviated` -> "2.5k" | SUPPORTED | `Number.functions.kt:204-211` |
| 57 | `1536L.formatReadableDataSize` -> "1.5 KiB" | SUPPORTED | `DataSize.functions.kt:53-61` |
| 58 | `1500.0.formatReadableMetric(Meter)` -> "1.5 km" | SUPPORTED | `Metric.functions.kt:79-86` |
| 59 | `3.formatReadableOrdinal` -> "3rd" | SUPPORTED | `PluralCategory.kt:122` Few -> "rd" |
| 60 | three-element list | SUPPORTED | start "{0}, {1}", empty middle loop, end "{0}, and {1}" |
| 61 | "red or green" | SUPPORTED | `List.resources.kt:54` `two` |
| 62 | "Those results are the ones the doc comments give, or follow from its tables" | SUPPORTED | Doc comments at the cited lines; "1.5 KiB" follows from the code, not a comment |

### Second code example (no citations, scored as uncited)

| # | Line | Verdict | Trace |
|---|---|---|---|
| 63 | 5 minutes back -> "5 minutes ago", "Yesterday" just after midnight | TRUE | `RelativeTime.extensions.kt:82-114`: same date -> time units -> "5 minutes" + "{0} ago"; different date -> `daysDelta = -1` -> label |
| 64 | 14 days back -> a date and time | TRUE | :83-84 |
| 65 | 14 days back with `relativeThreshold = 30.days` -> "2 weeks ago" | TRUE | `RelativeTime.resources.kt:144`: 1209600/604800 = 2.0, <= 3 -> week |
| 66 | `90.minutes.formatReadable` -> "2 hours" | TRUE | 1.5 hours, `roundToLong` -> 2 (tie rounds up: stdlib behaviour, and consistent with the source's own "11.days -> 2w" example) |
| 67 | Short -> "2h" | TRUE | `Duration.resources.kt:133,135` |

### Parameters and behaviour paragraphs

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 68 | `relativeThreshold`: 3 days for `Instant`, `LocalDateTime`, `LocalDate`; 3 hours for bare `LocalTime` | SUPPORTED | `RelativeTime.extensions.kt:77,173,239,315` |
| 69 | `nowThreshold` gives "just now", 1 minute by default | SUPPORTED | :78,87-92 |
| 70 | `RelativeStyle.None` switches a unit family off | SUPPORTED | :100-104. With `relativeDateStyle = None`, `useDateUnits = false`. With `relativeTimeStyle = None`, `useDateUnits = true`, so a same-day gap returns "Today" |
| 71 | For `LocalDate`, `nowThreshold` is a "Recently"/"Shortly" window; 1-day default leaves it off | SUPPORTED | :250-261 |
| 72 | `direction` defaults to `Nearest` | SUPPORTED | :312 |
| 73 | 23:00 / 01:00: `Future`, `Nearest` = 2 hours ahead; `Past`, `Present` = 22 hours back | SUPPORTED | `clockDelta` :342-351: fwd = 7200; Present = -79200; Past = 7200-86400 = -79200; Future = 7200; Nearest = 7200 |
| 74 | 22 hours is past the 3-hour default and prints a clock time | SUPPORTED | :320-322 |
| 75 | `Instant`-anchored `LocalTime` overload can give day labels | SUPPORTED | :373-394 |
| 76 | Money symbol placement by language group | SUPPORTED | `Money.resources.kt:28,31,34` |
| 77 | Placement follows the locale, not the currency | SUPPORTED | :84,94 |
| 78 | `showSymbol = false` prints the number alone | SUPPORTED | :96 |
| 79 | Ordinals are numeral plus affix, never spelled out | SUPPORTED | `Ordinality.resources.kt:14-18,24-63` |
| 80 | Decimal degrees keep at most four places | SUPPORTED | `Quantitative.functions.kt:73` |
| 81 | DMS truncates seconds | SUPPORTED | :102 `.toInt()` |
| 82 | All behaviour is common code; per-target sets hold only the resources accessor with empty maps | SUPPORTED | The five `ActualResourceCollectors.kt` files are byte-identical; no `expect`/`actual` anywhere else (grep) |
| 83 | [uncited] Targets differ only where the library leans on the platform: locale, clock and zone, time-zone names, how a `Double` prints | TRUE | Follows from #82. The list leaves out one more place: the absolute fallback is delegated to `datetime.format` (`RelativeTime.extensions.kt:84`), whose per-target behaviour this source cannot show |
| 84 | [uncited] Nothing is annotated `@JsExport` | TRUE | grep: none |
| 85 | [uncited] Nothing shows a Swift-facing package | TRUE | grep for `ObjCName`, `HiddenFromObjC`: none |
| 86 | [uncited] No function carries `@Throws` | TRUE | grep: none |

### Traps

**T1. Decimal places are a ceiling.**

| # | Claim | Verdict | Trace |
|---|---|---|---|
| 87 | Rounds to at most `precision` places; zero fraction dropped | SUPPORTED | `Number.resources.kt:18-39` |
| 88 | `2.0.formatReadable()` is "2"; 1024 bytes prints "1 KiB" though the doc comment shows "1.0 KiB" | SUPPORTED | 2.0 -> "2.0" -> fraction "0" -> integer only. 1024/1024 = 1.0 -> "1"; `DataSize.functions.kt:13,17` say "1.0 KiB". The doc comment is wrong, the skill is right |
| 89 | Money uses the same formatter with the currency's digits as precision | SUPPORTED | `Money.resources.kt:88-91` |
| 90 | [uncited] Twelve and a half prints "12.5", a whole amount "12", never "12.50" | TRUE | cents 1250, digits 2: value 12.5; factor 100; 1250 -> 12.5 -> "12.5"; fraction "5".take(2) = "5". Whole: "12.0" -> fraction "0" -> "12" |

**T2. Separator after the minus sign.**

| # | Claim | Verdict | Trace |
|---|---|---|---|
| 91 | Grouping reverses the integer text, sign included, and chunks by three | SUPPORTED | `Number.resources.kt:22,25-28` |
| 92 | [uncited] -123 -> "-,123"; -123456 -> "-,123,456"; -1234 is right | TRUE | "-123" reversed "321-" -> ["321","-"] -> "321,-" -> reversed "-,123". "-123456" -> ["654","321","-"] -> "-,123,456". "-1234" -> ["432","1-"] -> "-1,234" |
| 93 | Inherited by altitude -150 m, negative metric values, negative money | SUPPORTED | `Quantitative.functions.kt:22` -> `Metric.functions.kt:74-75` -> `formatter(-150.0)` -> "-,150 m" |

**T3. Ten million or more on JVM and Android.**

| # | Claim | Verdict | Trace |
|---|---|---|---|
| 94 | Formatter takes `toString()` and splits at the dot | SUPPORTED | `Number.resources.kt:21` |
| 95 | [uncited] There a `Double` of 10^7 or more prints "1.0E7", so `10_000_000.formatReadable()` returns "1" | CANNOT-TELL | Rests on JVM `Double.toString`, not on this source. I believe it is true: Java switches to exponent form at 10^7. Given "1.0E7", the code path is certain: split -> ["1","0E7"], integer "1", precision 0 -> "1". (With a `Double` receiver and precision 1, 12345678.0 would give "1.2", not a single digit) |
| 96 | Integer receivers are converted to `Double` first | SUPPORTED | `Number.functions.kt:55,71` |
| 97 | Money takes the same path | SUPPORTED | `Money.resources.kt:89-91` |
| 98 | Compact numbers and file sizes divide down first | SUPPORTED | `Number.functions.kt:210`; `DataSize.functions.kt:56` |

The trap as a whole: code path shown, premise is platform behaviour. Not contradicted by anything in the source; no guard exists.

**T4. `NaN` throws.**

| # | Claim | Verdict | Trace |
|---|---|---|---|
| 99 | Formatter rounds with `roundToLong()` | SUPPORTED | `Number.resources.kt:19`, no NaN guard before it |
| 100 | [uncited] `roundToLong()` rejects `NaN` with `IllegalArgumentException` | CANNOT-TELL | Stdlib behaviour. I believe it is true (the stdlib documents the throw). The source has no explicit `throw` anywhere (grep) |

**T5. Relative stops after 3 days.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 101 | 5 days back returns a date and time | SUPPORTED | `RelativeTime.extensions.kt:77,83-84` |
| 102 | Weeks, months, years only appear when `relativeThreshold` is raised | SUPPORTED | `RelativeTime.resources.kt:142-144` need >= 7 days; default cap is 3. Scope note: true of `formatReadableRelative`; the deprecated locale-first `toReadableRelative` overloads produce them with no threshold (:496-508), which the skill says itself two sections later |
| 103 | Bare `LocalTime` limit is 3 hours | SUPPORTED | :315,320 |

**T6. Calendar day wins.**

| # | Claim | Verdict | Trace |
|---|---|---|---|
| 104 | Different date in `timeZone` -> day units; one-day difference is a label | SUPPORTED | :94-110 |
| 105 | [uncited] 23:50 seen at 00:10 reads "Yesterday" | TRUE | delta -20 min; `daysDelta = -1`; `useDateUnits = true`; label for -1 |
| 106 | `relativeDateStyle = None` gives elapsed time | SUPPORTED | :101 |

**T7. `now` is the reference point.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 107 | Receiver as `now` -> every value reads "just now" | SUPPORTED | :59-60 and :82,87-92. Nuance: on the `LocalDate` overload the same mistake yields "Today" (:251), still a constant |

**T8. Duration is one rounded unit, no sign.**

| # | Claim | Verdict | Trace |
|---|---|---|---|
| 108 | 90 minutes is "2 hours" | SUPPORTED | `Duration.resources.kt:97,101,105` |
| 109 | Negative prints as positive | SUPPORTED | :91 `abs` |
| 110 | Month = 30 days, year = 365 | SUPPORTED | :93-94 |
| 111 | Weeks only up to 3; 28 days is "28 days" | SUPPORTED | 2419200 s: not >= 2592000; weeks round to 4 > 3; falls to days |
| 112 | `Long.formatReadableDuration()` reads milliseconds | SUPPORTED | `time/Long.extensions.kt:46` |
| 113 | [uncited] `90L` gives "0 seconds" in English | TRUE | 0.09 s -> rounds to 0 -> Other -> "seconds" |

**T9. `Short` only abbreviated in English.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 114 | English "5m", "2w", "3mo" | SUPPORTED | `Duration.resources.kt:115-118,135` |
| 115 | In relative time every other language ignores the style | SUPPORTED | `RelativeTime.resources.kt:213-473`: only "en-ZA" and "en" read `style` |
| 116 | In durations most others keep the word and lose the space: "5Minuten" | SUPPORTED | `Duration.resources.kt:133,138-140`. Checked which tables pass `sep`: 30-odd do; ja/ko/zh/th are always unspaced; vi, iu, hi, ar, fa, he, tr, az, uz, kk, fi, et, hu, el, eu, hy, ka keep the space. "Most" is right |
| 117 | English `Short` past has no "ago": "5d"; enum comment says "5d ago"; future "in 5d" | SUPPORTED | `RelativeTime.resources.kt:176-177,184`; `RelativeStyle.kt:9` |

**T10. A table does not mean a full translation.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 118 | Unknown language falls back to English, no error | SUPPORTED | Four fallback sites as cited |
| 119 | Today/Tomorrow/Yesterday translated only for en, af, nl, de, da, nb/no, nn, sv, is, fr, es, it, pt | SUPPORTED | Read every `config(...)` call :215-470. Exactly those pass the three strings |
| 120 | Every other table leaves English defaults; Russian or Japanese can read "Yesterday" | SUPPORTED | Defaults :129-131; ru :284-287, ja :352-354 pass none |
| 121 | Durations in af, nl, id, ms, sw use English unit words | SUPPORTED | `Duration.resources.kt:136-137` |
| 122 | cs, sk, hr, sr, sl, lt, lv take the singular for every count | SUPPORTED | `PluralCategory.kt:104` returns One/Other for them; their `u3` tables hold One/Few/Many; `get(Other)` misses twice and returns `forms.values.first()`, the One form (`RelativeTime.resources.kt:19`; `Duration.resources.kt:22`). The set is exact: the other `u3` users (ru, uk, be, pl) have rules. Relies on `mapOf` keeping insertion order, a documented stdlib guarantee |
| 123 | bn, gu, kn, ml, mr, pa, ta, te, ur listed as supported, no table, print English | SUPPORTED | `RelativeTime.resources.kt:191-192,472`; same in `Duration.resources.kt:125-126,277` |

**T11-T13.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 124 | Steps of 1024; KiB, MiB, GiB | SUPPORTED | `DataSize.functions.kt:34,53-59` |
| 125 | [uncited] No 1000-based mode | TRUE | No such parameter on any overload |
| 126 | Seven base units | SUPPORTED | `Metric.functions.kt:59-67` |
| 127 | Other units keep the raw number, including `Speed`'s m/s | SUPPORTED | :31,69-70 |
| 128 | 1 to 1000 left alone; smaller scale down | SUPPORTED | :74,79-86. 0.005 m -> exponent -3 -> "5 mm" (prefix table is external; the comment at :80 names m, μ, n) |
| 129 | SI letters; a billion is G | SUPPORTED | `Number.functions.kt:204-207` |
| 130 | No separator before the letter | SUPPORTED | :211 |

**T14. Duplicate `Altitude`/`Azimuth` formatters.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 131 | Same signatures in `quantitative` and `geo` | SUPPORTED | `Quantitative.functions.kt:21,35`; `Geo.functions.kt:24,38`; the source's own TODO at `Geo.functions.kt:100,119` admits duplication |
| 132 | [uncited] Importing `formatReadable` from both leaves those calls ambiguous | CANNOT-TELL | Kotlin overload-resolution rule, not this source. I believe it is true for two explicit imports |

**T15. Device-dependent output.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 133 | Depends on device unless locale, clock, zone passed | SUPPORTED | Defaults at the cited lines |
| 134 | [uncited] A test asserting "1,234.5" fails under a differently-grouping locale | TRUE | Follows from the `Locale.current` default and `Number.resources.kt:54-58` |
| 135 | Time-zone names come from the platform | SUPPORTED | `TimeZone.extensions.kt:17-20`. Doc comment only: the implementation is in another library and cannot be checked here |
| 136 | `UtcOffset` overload: supplement, English name, offset string | SUPPORTED | :41-45. Doc comment only, same caveat |

**T16. `formatReadableList`.**

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 137 | Elements as given, in order | SUPPORTED | `List.functions.kt:44-51` |
| 138 | Declared on `List<String>` only | SUPPORTED | :37 |
| 139 | Empty -> ""; single -> itself | SUPPORTED | :41-42 |
| 140 | Text replacement, `{0}` then `{1}` | SUPPORTED | :56 |
| 141 | [uncited] An element containing `{1}` has it replaced by the next element | TRUE | `["a{1}","b"]` -> "a{1} and {1}" -> "ab and b" |

### What moved

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 142 | Since 3.1.0 bare `LocalTime` defaults to `Nearest`; before it was same-day (`Present`) | SUPPORTED | `RelativeTime.extensions.kt:302`; `RelativeDirection.kt:20-21`. These are the only version numbers in the whole source (grep) |
| 143 | Deprecated `LocalTime.toReadableRelative(locale, style, now)` still measures within the day | SUPPORTED | :517 |
| 144 | [uncited] Every `toReadable…` is deprecated, still declared, forwards; source gives no versions | TRUE | 59 `toReadable…` functions, 59 `@Deprecated` |
| 145-153 | Nine rename bullets, including the swapped `Duration` parameters and the `LocalTime` `direction` note | SUPPORTED x9 | Each `ReplaceWith` read; `Duration.functions.kt:25-27` vs :51-53 confirms the swap; `RelativeTime.extensions.kt:417-431` forwards without `direction` |
| 154 | Older overloads: `locale` first (`timeZone` on `LocalDateTime`), `RelativeStyle` styles, no drop-in replacement | SUPPORTED | :433-519; none has a `ReplaceWith` |
| 155 | They always answer relatively | SUPPORTED | No threshold check in :441-475, :500-508, :516-518 |
| 156 | `dateStyle`/`timeStyle` are now `DateTimeStyle` | SUPPORTED | :73-76 |
| 157 | [uncited] A renamed call keeping `dateStyle = RelativeStyle.Short` does not compile | TRUE | Type mismatch against :75 |
| 158 | Package is not the artifact name | SUPPORTED | As #1, #2 |

### What it is not for

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 159 | [uncited] Nothing parses text back | TRUE | grep for parse: none; every public function returns `String` or a formatter |
| 160 | Absolute dates are formatted by `io.github.aughtone.datetime.format` | SUPPORTED | `RelativeTime.extensions.kt:4,84,246,321` |
| 161 | Value types are defined under `io.github.aughtone.types` | SUPPORTED | Imports |
| 162 | Lists: wide forms only | SUPPORTED | `List.functions.kt:19-21`; `List.resources.kt:23` |
| 163 | [uncited] Metric scales by prefix, no unit conversion, no imperial | TRUE | `Metric.functions.kt:50-90` only ever divides by a power of ten |
| 164 | Plural and ordinal rules are simplified CLDR by their own comments | SUPPORTED | `PluralCategory.kt:30,110` |
| 165 | [uncited] `Res` is not part of the formatting API | TRUE | No reference to it from `readable/**` |

### Traps and boundaries summary

The derived skill has 16 traps and 7 boundaries, 23 in all (62 individual claims).

- Fully confirmed by the source: 20 (13 traps, 7 boundaries).
- Code path confirmed, outcome depends on behaviour outside this source: 3 (T3 JVM exponent form, T4 `NaN`, T14 import ambiguity). I believe all three are true.
- Contradicted by the source: none.

## Part 2: against the authored skill

### Authored traps and boundaries, and whether the derived skill has them

Seven headed traps and four boundaries (three in "What it is not for", plus "Called from Kotlin only"), which break into 14 distinct points.

| # | Authored point | In derived? |
|---|---|---|
| A1 | Never pass the receiver as `now` | Yes (T7) |
| A2 | `Locale.current` default makes output environment-dependent | Yes (T15) |
| A3 | Trailing zeros trimmed; "1 KiB" not "1.0 KiB" | Yes (T1) |
| A4 | Units round to a step; weeks only 1-3 (29 days, 30 days -> 1 month, 10 days -> 1 week, 11 -> 2 weeks) | Yes (T8). Authored numbers check out against `Duration.resources.kt:92-99` |
| A5 | DMS truncates seconds | Yes (stated in the usage section, not as a trap) |
| A6 | Data sizes IEC only, no decimal option | Yes (T11) |
| A7 | `precision` defaults not uniform (Double 1, Int/Long 0, metric 1 everywhere, Azimuth 0) | Yes (stated in the usage section, not as a trap) |
| A8 | Plural/ordinal rules simplified CLDR; unknown locales fall back to English silently | Yes (T10, boundary). Not the parenthetical "no exceptions in this module at all", which it disputes |
| A9 | `Short` drops the affix: "5d" not "5d ago"; `None` suppresses a component | Yes (T9, usage section) |
| A10 | Bare `LocalTime` is time units only, 3-hour threshold; use the `Instant`-anchored overload for day labels | Yes |
| A11 | Kotlin only, no separate Swift or JavaScript API | Yes, hedged to what the source shows (no `@JsExport`, nothing Swift-facing) |
| A12 | Not machine-parseable; for exact notation use format-identifiers | Partly: "nothing parses" yes; the pointer to the sibling artifact no |
| A13 | Not a date/time pattern formatter; that is format-datetime | Yes, by package name rather than artifact coordinate |
| A14 | Not a full CLDR/ICU engine | Yes |

Found: 14 of 14 in substance (A12 without the sibling pointer). Missed: none.

The authored "What moved" section is a separate matter, and is where the derived skill falls short, because history is not in a source jar:

| Authored entry | In derived? |
|---|---|
| 4.1.0 silent change to `TimeZone.formatReadable` wording | Behaviour yes (T15); that it changed, and when, no |
| `formatReadableList` new in 4.1.0 | Function yes; that it is new, no |
| `toReadable*` -> `formatReadable*`, renamed in 3.0.0 | Rename yes, in more detail; version no (says so) |
| Legacy `toReadableRelative` never falls back | Yes |
| `CoordinateFormat` and `cardinalDirectionsFor` moved from `geo` to `quantitative` in 3.0.0 | No. Missed entirely |
| `Nearest` became the `LocalTime` default in 3.1.0 | Yes (the one change the source records) |
| `Money`'s `Currency` non-null since 4.0.0 | No. Has only that the currency sets the digits |

Of 7 history entries: 3 carried over, 2 as present-tense behaviour only, 2 missed.

### Derived traps and boundaries the authored skill lacks

| # | Point | Part 1 verdict |
|---|---|---|
| D1 | Money is never padded: "12.5", not "12.50" | TRUE |
| D2 | Negative numbers with 3, 6, 9 integer digits get "-,123" | TRUE (traced) |
| D3 | Ten million or more prints its leading digit on JVM/Android | CANNOT-TELL from source; believed true |
| D4 | `NaN` throws | CANNOT-TELL from source; believed true |
| D5 | Relative stops at 3 days by default | SUPPORTED |
| D6 | A different calendar day wins over elapsed time | SUPPORTED / TRUE |
| D7 | Durations carry no sign | SUPPORTED |
| D8 | `Long.formatReadableDuration` is milliseconds | SUPPORTED / TRUE |
| D9 | `Short` only abbreviates English; "5Minuten" | SUPPORTED |
| D10 | Partial translation: day labels in 12 languages only; English duration words for 5; singular-only for 7; 9 listed with no table | SUPPORTED |
| D11 | Metric scaling is seven units; a speed never becomes km/h | SUPPORTED |
| D12 | Compact numbers use SI letters (G for a billion), no space | SUPPORTED |
| D13 | `Altitude`/`Azimuth` formatters duplicated across two packages; ambiguity | Duplication SUPPORTED; ambiguity CANNOT-TELL, believed true |
| D14 | `formatReadableList` is `List<String>` only; `{1}` in an element is substituted | SUPPORTED / TRUE |
| D15 | The package is not the artifact name | SUPPORTED |
| D16 | Value types are not this library's (boundary) | SUPPORTED |
| D17 | No unit conversion, no imperial (boundary) | TRUE |
| D18 | `Res` is not part of the API (boundary) | TRUE |

18 derived-only points. Source confirms 15 outright; 3 depend on behaviour outside the source and are believed true; none refuted.

### Disagreements

1. **Exceptions.** Authored: "there are no exceptions in this module at all". Derived: `NaN` throws. The source has no `throw` statement, so the authored sentence is literally true of the module's own code; but `Number.resources.kt:19` calls `roundToLong()` on an unguarded value, and the stdlib throws for `NaN`. On observable behaviour the derived skill is right, as far as can be told without running it.
2. **Where `Altitude` and `Azimuth` formatting lives.** Authored: "`Coordinates`/`Altitude`/`Azimuth` are in `…quantitative`, `GeoPoint` extensions in `…geo`". Derived: `Altitude` and `Azimuth` are in both. Source: both (`Geo.functions.kt:24,38`). Derived is right; the authored sentence is incomplete in the direction that hides the ambiguity.
3. **Imports.** Authored example uses `import io.github.aughtone.readable.*` and then calls `7.days.formatReadable`, `formatReadableDataSize`, `formatReadableOrdinal`, `formatReadableList`. Those are declared in sub-packages (`…duration`, `…metrics`, `…ordinality`, `…list`), which a star import of the parent does not reach. Derived says the import must be per package. Source supports derived.

No point was found where the authored skill is right and the derived skill wrong.

### Shared blind spot

Both say every formatter takes a defaulted `locale`; the public `formatDecimalDegrees` / `formatDegreesMinutesSeconds` do not (`Quantitative.functions.kt:72,91`).

## Noticed, not asked for

Two further hazards on the same code paths that neither skill mentions; both depend on platform or external-type behaviour, so they are leads, not findings.

- `Number.resources.kt:21` would hit the same exponent-form problem for magnitudes under 10^-3 on the JVM (for example a latitude of 0.0005 at precision 4).
- `getCardinalDirection` (`Quantitative.functions.kt:117`) takes `% 8` of a truncated value, which is negative for a bearing below -22.5; whether `Azimuth` can hold one is not visible here.
