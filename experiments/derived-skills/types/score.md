# Score: derived skill for io.github.aughtone:types 4.1.0

Scored against the published source only (`derive/types/source/`, one directory per source set). No builds, no web. The derived skill was not edited.

Path shorthand used below: `cM/` = `commonMain/io/github/aughtone/types/`; `jvm/`, `android/`, `apple/`, `js/`, `wasm/`, `linux/` = the same package path under `jvmMain`, `androidMain`, `appleMain`, `jsMain`, `wasmJsMain`, `linuxMain`. `D:` = `derived/SKILL.md` line, `DS:` = `derived/references/swift.md` line.

## Method

- Every `[src: path:line]` citation was resolved mechanically (394 citation instances, 0 missing files, 0 out-of-range lines) and each cited line was read together with the code around it. All source files were read in full except the three generated data tables (`Locale.resource.kt`, `LocaleDisplayNameSupplement.kt`, `linux/LocaleDisplayNameTable.kt`) and `Currency.resource.kt`, where the header, the cited lines and the lookup code were read and the keys were checked by pattern.
- A claim is one assertion. A sentence with several citations for one assertion is one claim; a sentence with several assertions is several.
- Where a cited line is a doc comment, the code beside it was checked too; the code is the authority.
- Traps and boundaries were traced by hand with concrete inputs across every source set that implements the path (traces are in the notes column and in the "Traces" section).
- `[uncited]` claims were checked by searching the whole source tree.
- Five claims carry neither a citation nor an `[uncited]` tag (three code examples, one heading, one recap). They are scored on the cited scale and marked "untagged".

## Counts

| | n |
|---|---|
| Total claims | 350 |
| Cited claims | 320 |
| Untagged claims (no citation, no `[uncited]`) | 5 |
| `[uncited]` claims | 25 |
| SUPPORTED | 314 (310 cited + 4 untagged) |
| OVERSTATED | 11 (10 cited + 1 untagged) |
| UNSUPPORTED | 0 |
| CONTRADICTED | 0 |
| `[uncited]` TRUE | 24 |
| `[uncited]` FALSE | 0 |
| `[uncited]` CANNOT-TELL | 1 |

## Part 1 — every claim

Verdict codes: S = SUPPORTED, O = OVERSTATED, U = UNSUPPORTED, C = CONTRADICTED; for `[uncited]`: T = TRUE, F = FALSE, ? = CANNOT-TELL.

### derived/SKILL.md — What it solves

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 1 | D:24 | Everything is under `io.github.aughtone.types`, one sub-package per area | cM/financial/Money.kt:1, cM/uri/Url.kt:1 | S | package lines; all source files sit under financial, number, locale, quantitative, geo, uri, outcome, units, util |
| 2 | D:28 | Amount held as arbitrary-precision decimal | Money.kt:10 | S | `val value: BigDecimal` Money.kt:32 |
| 3 | D:28 | Can track fractions of a cent | Money.kt:11 | S | |
| 4 | D:28 | Currency lookup by three-letter code | Currency.functions.kt:12 | S | |
| 5 | D:28 | ...by locale | Currency.functions.kt:52 | S | |
| 6 | D:28 | ...by name fragment | Currency.functions.kt:41 | S | `contains(name, ignoreCase)` |
| 7 | D:28 | ...listed in full | Currency.functions.kt:32 | S | |
| 8 | D:28 | Each carries code, numeric code, digits, name, symbol | Currency.kt:28 | S | Currency.kt:29-38 (also obsolete, replacedBy) |
| 9 | D:30 | `BigInteger` and `BigDecimal` are pure Kotlin "so they behave identically on JVM, JS, Wasm and Native" | BigInteger.kt:11 | O | The cited comment says this of `BigInteger` only. `BigDecimal.valueOf(Double)` is `parseString(value.toString())` (BigDecimal.kt:346-348) and `Double.toString()` is platform-defined; the library itself works around that difference elsewhere (GeoUri.kt:145-147, 155-183). So the scale of a `BigDecimal` built from a `Double` is not shown to be identical across targets. Belief: true for integer and string inputs, not guaranteed for `Double` inputs (e.g. `1.0` prints as `1` on Kotlin/JS, giving scale 0 instead of 1). |
| 10 | D:30 | Own `RoundingMode` enum | RoundingMode.kt:6 | S | |
| 11 | D:30 | `BankersValue`: a `Long` count of cents whose arithmetic rounds half to even | BankersValue.kt:10 | S | BankersValue.kt:16, 108-109 |
| 12 | D:32 | `Locale` data class of language, region, script, variant codes plus display name | Locale.kt:32 | S | Locale.kt:33-42 |
| 13 | D:32 | Built from a BCP 47 tag | Locale.functions.kt:100 | S | |
| 14 | D:32 | Reads the device's current locale | Locale.kt:85 | S | |
| 15 | D:32 | Lists the bundled locales | Locale.functions.kt:178 | S | |
| 16 | D:32 | Searches them by name | Locale.functions.kt:187 | S | |
| 17 | D:32 | Renders a locale's name in another language | Locale.functions.kt:209 | S | |
| 18 | D:34 | `Coordinates` (latitude, longitude) | Coordinates.kt:42 | S | |
| 19 | D:34 | `Distance` in metres | Distance.kt:19 | S | |
| 20 | D:34 | `Speed` in metres per second | Speed.kt:19 | S | |
| 21 | D:34 | `Altitude` in metres | Altitude.kt:27 | S | |
| 22 | D:34 | `Azimuth`, compass heading in degrees; each of the five has an optional accuracy | Azimuth.kt:25 | S | `accuracy: Float? = null` on all five |
| 23 | D:34 | Subtracting two coordinates gives haversine great-circle distance | Coordinates.kt:57 | S | code Coordinates.kt:67-93 |
| 24 | D:34 | A point can be moved by a distance along a bearing | Coordinates.kt:106 | S | |
| 25 | D:34 | `Telemetry` bundles position with optional heading, speed, altitude, timestamp | Telemetry.kt:17 | S | |
| 26 | D:36 | `GeoPoint` is a `@Serializable` RFC 7946 class | geo/GeoPoint.kt:27 | S | |
| 27 | D:36 | `GeoMultiPoint` | GeoMultiPoint.kt:8 | S | |
| 28 | D:36 | `GeoLineString` | GeoLineString.kt:8 | S | |
| 29 | D:36 | `GeoMultiLineString` | GeoMultiLineString.kt:8 | S | |
| 30 | D:36 | `GeoPolygon` | GeoPolygon.kt:8 | S | |
| 31 | D:36 | `GeoMultiPolygon` | GeoMultiPolygon.kt:8 | S | |
| 32 | D:36 | `GeometryCollection` | GeometryCollection.kt:11 | S | |
| 33 | D:36 | `GeoFeature` | GeoFeature.kt:38 | S | |
| 34 | D:36 | `GeoFeatureCollection` | GeoFeatureCollection.kt:11 | S | |
| 35 | D:36 | `GeoBoundingBox` helper | GeoBoundingBox.kt:31 | S | |
| 36 | D:36 | Polygon winding checks | GeoValidation.kt:70 | S | |
| 37 | D:38 | Parse a string into a `Uri` | UriParsing.kt:81 | S | |
| 38 | D:38 | ...a `Url` with host, port, user information broken out | UriParsing.kt:105 | S | |
| 39 | D:38 | ...a `Urn` | Urn.kt:143 | S | |
| 40 | D:38 | ...an RFC 5870 `geo:` link | UriParsing.kt:169 | S | |
| 41 | D:38 | Assemble a URL with `UrlBuilder` | UrlBuilder.kt:29 | S | |
| 42 | D:38 | Percent- or form-encode with `UrlEncoder` | UrlEncoder.kt:16 | S | |
| 43 | D:40 | `Outcome<T>` covers the same ground as `kotlin.Result` | Outcome.kt:9 | S | |
| 44 | D:40 | Sealed class, ordinary class hierarchy on every target, other languages can branch on it | Outcome.kt:19 | S | stated by the source doc; `sealed class` at Outcome.kt:38 |
| 45 | D:42 | `UnitOfMeasure` is an enum of units with symbols | UnitOfMeasure.kt:32 | S | |
| 46 | D:42 | `MetricPrefix`: SI prefixes with power of ten | MetricPrefix.kt:13 | S | |
| 47 | D:42 | `LazyMap` computes each value on first access and caches it | LazyMap.kt:11 | S | LazyMap.kt:53-66 |
| 48 | D:42 | `BitSet` is a compact fixed-size set of booleans | BitSet.kt:10 | S | |

### derived/SKILL.md — How it is meant to be used

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 49 | D:46 | A `Double` is an amount in major units | Money.kt:105 | S | |
| 50 | D:46 | A `Long` is a count of minor units | Money.kt:94 | S | Money.kt:95 |
| 51 | D:48-58 | Example: `Money(19.99,usd)`; `Money(250L,usd)` is 2.50; sum 22.49; `/ 3L` is a plain divisor; `minorUnits` 2249 | untagged | S | Traced: `valueOf(19.99)` → 1999 scale 2 (BigDecimal.kt:347, 399-428); `BigDecimal(BigInteger(250), 2)`; add at max scale; `setScale(2)` unscaled 2249 |
| 52 | D:60 | Adding and subtracting keep full precision | Money.kt:115 | S | BigDecimal.kt:140-168 |
| 53 | D:60 | Scalar division exact when terminating, else half-even at scale `max(value.scale, currency.digits) + 2` | Money.kt:177 | S | code Money.kt:247-256 |
| 54 | D:60 | `minorUnits` is where rounding to the currency's digits happens, half-even | Money.kt:81 | S | |
| 55 | D:62 | `uri`/`uriOrNull` | UriParsing.kt:94 | S | |
| 56 | D:62 | `url`/`urlOrNull` | UriParsing.kt:158 | S | |
| 57 | D:62 | `geoUri`/`geoUriOrNull` | UriParsing.kt:209 | S | |
| 58 | D:62 | `urn`/`urnOrNull` | UriParsing.kt:212 | S | |
| 59 | D:62 | `runOutcome` turns anything thrown into `Outcome.Failure` | Outcome.kt:209 | S | catch `Throwable` |
| 60 | D:62 | ...except `CancellationException`, re-thrown | Outcome.kt:207 | S | |
| 61 | D:64-73 | Example: `outcome.data` is a `Url`; `outcome.message` on failure | untagged | S | Outcome.kt:46, 70 |
| 62 | D:77-86 | Example: `here - there` is haversine metres; `plus(1000.0.toDistance(), 90.0.toAzimuth())`; `toGeoPoint()` position `[-75.69, 45.42]` | untagged | S | `Double.toAzimuth` resolves to companion `invoke` (constructor is private, Azimuth.kt:23, 42, 118); GeoPoint.extensions.kt:16, 29 |
| 63 | D:88 | `toGeoPoint()` reorders to longitude-first | GeoPoint.extensions.kt:29 | S | |
| 64 | D:88 | `Int`, `Long`, `Double` have `toDistance`, `toSpeed`, `toAltitude`, `toAzimuth` | Distance.kt:165, Speed.kt:151, Altitude.kt:93, Azimuth.kt:118 | S | all twelve exist (Distance.kt:146-165, Speed.kt:134-151, Altitude.kt:89-93, Azimuth.kt:114-118) |
| 65 | D:90 | `localeFor(tag)` is an exact match against the bundled table, `null` otherwise | Locale.functions.kt:49 | S | code line 57 |
| 66 | D:90 | `resolveLocale` drops subtags from the right (`en-GB-scouse` → `en-GB` → `en`) | Locale.functions.kt:64 | S | code lines 76-82 |
| 67 | D:90 | ...then asks the platform | Locale.functions.kt:83 | S | |
| 68 | D:90 | `parseLocale` always returns a `Locale`, splitting by hand when the table has no entry | Locale.functions.kt:100 | S | lines 101-133; `parseLocale("")` still returns a `Locale` |
| 69 | D:90 | All three accept `_` and normalize case | Locale.functions.kt:12 | S | lines 11-44; each of the three calls `normalizeLanguageTag` |
| 70 | D:90 | Legacy `iw`, `in`, `ji` map to `he`, `id`, `yi` | Locale.functions.kt:5 | S | lines 5-9, 17 |
| 71 | D:90 | `currencyFor(locale)` gives that locale's currency, or `null` | Currency.functions.kt:52 | S | |
| 72 | D:92 | "The value types are `@Serializable` ... with explicit serial names" | Money.kt:29, Coordinates.kt:41, Url.kt:21 | O | True of the three cited classes' constructor properties. Not true of every value type: `BankersValue(private val cents: Long)` has no `@SerialName` (BankersValue.kt:15-16); the geometry classes name the class but not `coordinates`/`bbox`/`geometries`/`features` (GeoPoint.kt:28-29, GeoMultiPoint.kt:9-10, GeoLineString.kt:9-10, GeoPolygon.kt:9-10, GeometryCollection.kt:12-13, GeoFeatureCollection.kt:12-13); body properties with backing fields are unnamed too (Url.kt:78, 93; Locale.kt:57; GeoUri.kt:96). |
| 73 | D:92 | GeoJSON classes are a sealed hierarchy whose `type` member is the class discriminator | GeoJson.kt:9 | S | stated by the source doc; `sealed class GeoJson` line 13 |
| 74 | D:92 | Each subclass's serial name is its RFC 7946 type name (`Point`, `Feature`) | GeoPoint.kt:26, GeoFeature.kt:37 | S | |

### derived/SKILL.md — Invariants and traps: Money and numbers

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 75 | D:98 | A `Long` means minor units when constructing/adding/subtracting; `Money(1000L, usd) + 250L` is 10.00 plus 2.50 | Money.kt:188 | S | code: line 191 → `plusMinorUnits` 205-207 |
| 76 | D:98 | `Money(1000L, usd) * 2L` is 20.00 | Money.kt:218 | S | code 226-228 |
| 77 | D:98 | A `Long` is scaled by the currency's digits; confusing the two is off by that factor | Money.kt:95 | S | |
| 78 | D:98 | `plusMinorUnits`/`minusMinorUnits` say which is meant | Money.kt:205, 212 | S | |
| 79 | D:100 | Every `Money` constructor, `Money.zero()` and `toMoney()` default the currency to `Currency.current` | Money.kt:34, 94, 105, 271; Money.extensions.kt:12 | S | also Money.extensions.kt:16 |
| 80 | D:100 | ...which throws `IllegalArgumentException` with the quoted message when no currency is mapped | Currency.kt:99 | S | `requireNotNull`; doc Currency.kt:93 |
| 81 | D:100 | The mapping is keyed by language-and-region tags | LocaleToCurrency.resource.kt:9 | S | all 112 keys match `ll-RR` |
| 82 | D:100 | [uncited] The mapping holds no bare-language key, so a locale without a region resolves to nothing | — | T | pattern check over LocaleToCurrency.resource.kt:9-120; `currencyCodeFor` candidates end in the bare language (Currency.kt:129-146), which is never a key |
| 83 | D:100 | `Currency.currentOrNull` reports the same lookup as `null` | Currency.kt:112 | S | line 113 |
| 84 | D:100 | [uncited] An amount built with an explicit currency does not depend on the device | — | T | default argument is not evaluated when a currency is passed. Caveat, not in the claim: a `Double`-built amount's stored scale follows the platform's `Double.toString()` (see #9) |
| 85 | D:102 | `plus`, `minus`, `times`, `div` between two `Money` require matching currencies | Money.kt:114, 124, 138, 148 | S | |
| 86 | D:102 | ...and so does `compareTo` | Money.kt:70 | S | |
| 87 | D:102 | Sorting or `max` on a mixed list throws `IllegalArgumentException` "Cannot compare USD to EUR: ..." | Money.kt:71 | S | traced: `sorted()` → `compareTo` → `require` |
| 88 | D:104 | A `Money` keeps sub-minor precision | Money.kt:11 | S | |
| 89 | D:104 | A non-terminating division keeps two guard digits | Money.kt:178 | S | |
| 90 | D:104 | `share` is `7.4967` where `7.50` was expected: 22.49 / 3 at scale 4 | Money.kt:253 | S | traced: exact divide throws (BigDecimal.kt:232-233); fallback `divide(3, max(2,2)+2, HALF_EVEN)`: 224900/3 = 74966 r 2 → 74967 → `7.4967` |
| 91 | D:104 | `minorUnits` rounds | Money.kt:81 | S | |
| 92 | D:104 | ...as does `value.setScale(currency.digits, RoundingMode.HALF_EVEN)` | BigDecimal.kt:297 | S | |
| 93 | D:106 | `Money / Money` is a `Double` ratio | Money.kt:147 | S | |
| 94 | D:106 | ...computed in floating point | Money.kt:149 | S | |
| 95 | D:106 | `Money * Money` returns a `Money`; the source calls it unusual | Money.kt:132 | S | |
| 96 | D:108 | `Money(5.1, usd)` equals `Money(5.10, usd)` | Money.kt:15 | S | code 45-49; traced with `BigDecimal("5.1")` vs `("5.10")`: `compareTo` 0 (BigDecimal.kt:59-79), hash strips zeros (Money.kt:57) |
| 97 | D:108 | `BigDecimal` compares unscaled value and scale | BigDecimal.kt:112 | S | |
| 98 | D:108 | `2.0` and `2.00` are not `==` although `compareTo` is 0 | BigDecimal.kt:46 | S | |
| 99 | D:108 | Symptom: equality on `.value` fails; `value.scale` differs | Money.kt:22 | S | |
| 100 | D:108 | Two equal `Money` values can serialize differently | Money.kt:20 | S | |
| 101 | D:110 | `Currency` equality is the ISO code alone | Currency.kt:63 | S | |
| 102 | D:110 | `usd.copy(symbol = "US$") == usd`; a `Set`/map keeps one entry per code | Currency.kt:58 | S | hashCode Currency.kt:71 |
| 103 | D:112 | `Money` serializes `value` and `currency` | Money.kt:31, 33 | S | |
| 104 | D:112 | `BigDecimal` serializes as `unscaledValue` and `scale` | BigDecimal.kt:19, 21 | S | |
| 105 | D:112 | `BigInteger` as `signum` and a `magnitude` array of 32-bit words | BigInteger.kt:20, 23 | S | `@SerialName("magnitude")` line 22 |
| 106 | D:112 | Currency as all of its fields | Currency.kt:29 | S | seven serial properties, lines 29-42 (whether defaulted ones are emitted is an encoder setting) |
| 107 | D:112 | [uncited] No custom serializer flattens any of this | — | T | no `KSerializer`, `@Serializable(with=`, `@Serializer`, `@Contextual` anywhere in the source |
| 108 | D:112 | [uncited] Symptom: a consumer expecting `"19.99"` receives an object | — | T | follows from #103-107; rests on the standard generated-serializer encoding |
| 109 | D:114 | `currencyFor(code)` is an exact-key lookup | Currency.functions.kt:12 | S | |
| 110 | D:114 | Keys are upper-case ISO codes; a lower-case code returns `null` | Currency.resource.kt:171 | S | all 166 keys match `[A-Z]{3}`; `LazyMap.get` returns `null` for an absent key (LazyMap.kt:57) |
| 111 | D:114 | `currencyForNative` asks the platform instead and returns different data on different platforms | Currency.functions.kt:20 | S | true on JVM, Android, Apple; on JS, Wasm, Linux it reads the same table (js/...Currency.functions.js.kt:3, linux/...:2, wasm/...:6), which the cited doc also says (line 18) |
| 112 | D:114 | On Apple the numeric code is `-1` when the bundled table lacks the currency | apple/financial/Currency.functions.apple.kt:33 | S | |
| 113 | D:114 | On the JVM the name is the platform's display name | jvm/financial/Currency.functions.jvm.kt:15 | S | |
| 114 | D:116 | `BigDecimal` and `BigInteger` are the library's own | BigDecimal.kt:18, BigInteger.kt:19 | S | |
| 115 | D:116 | `Money.value` is this `BigDecimal` | Money.kt:3 | S | |
| 116 | D:116 | [uncited] No `MathContext`, `pow`, `abs`, `negate`, `remainder`, `precision`, `toPlainString`, `toBigInteger` on `BigDecimal`; no public `pow`, `abs`, `gcd`, `sqrt`, `toByteArray` on `BigInteger` | — | T | BigDecimal public surface: BigDecimal.kt:59-335, 338-428 (the `gcd`, `negate` helpers are private, 521-538). BigInteger: `abs` is private (112); there is `modPow` (343) and `fromByteArray` (587) but none of the listed names |
| 117 | D:116 | Negation is unary minus on both | BigDecimal.kt:180, BigInteger.kt:181 | S | |
| 118 | D:116 | "the sign is the `signum` property, not a function" | BigInteger.kt:21 | O | True of `BigInteger` only. The sentence reads as applying to both classes, and `BigDecimal` has no `signum` member at all; the sign is `unscaledValue.signum` (BigDecimal.kt:18-23, used so at 120-121). `bigDecimal.signum` is itself an unresolved reference. |
| 119 | D:116 | [uncited] Symptom: an unresolved reference on a call that exists in the JDK | — | T | consequence of #116 |
| 120 | D:118 | `a / b` is `divide(b)` | BigDecimal.kt:179 | S | |
| 121 | D:118 | ...which throws `ArithmeticException` "Non-terminating decimal expansion; ..." for `1 / 3` | BigDecimal.kt:233 | S | traced: r ≠ 0, reduced denominator 3 ≠ 1 |
| 122 | D:118 | `divide(other, scale, roundingMode)` names scale and rounding | BigDecimal.kt:250 | S | |
| 123 | D:118 | `setScale(n)` never rounds; throws "Rounding necessary" | BigDecimal.kt:285, 311 | S | |
| 124 | D:120 | `BigDecimal.valueOf(0.1)` goes through the shortest round-tripping string and is `0.1` | BigDecimal.kt:354 | S | code 346-348 |
| 125 | D:120 | The `BigDecimal(Double)` constructor delegates to it | BigDecimal.kt:37 | S | the constructor's own doc comment (line 34, "preserving its binary precision") is wrong; the code is as the skill says |
| 126 | D:120 | `valueOfExact` gives the exact binary value | BigDecimal.kt:352 | S | |
| 127 | D:120 | ...and throws `ArithmeticException` for `NaN` or infinity | BigDecimal.kt:364 | S | |
| 128 | D:120 | `toString` never uses scientific notation except for a negative scale (`123E+2`) | BigDecimal.kt:136 | S | lines 119-137 |
| 129 | D:122 | `BigInteger.toInt()`/`toLong()` truncate silently to the low-order bits | BigInteger.kt:234, 244 | S | code 236-252 |
| 130 | D:122 | `rem`/`%` takes the dividend's sign, `mod` is non-negative: `(-7).mod(3)` = 2, `(-7).rem(3)` = -1 | BigInteger.kt:278 | S | traced: rSign = this.signum (271); `mod` adds m when negative (287) |
| 131 | D:122 | Division by zero throws `ArithmeticException` | BigInteger.kt:265 | S | |
| 132 | D:124 | Heading: "its two factories disagree on units" | untagged | O | There are three factories: `fromLong` (BankersValue.kt:87), `fromInt` (90) and `fromDouble` (98). The disagreement itself (cents vs major units) is real. |
| 133 | D:124 | Constructor and `fromLong` take cents — `100` is 1.00 | BankersValue.kt:13, 87 | S | |
| 134 | D:124 | `fromDouble` takes major units and rounds to the nearest cent | BankersValue.kt:98 | S | lines 101-104 |
| 135 | D:124 | `toLong()` returns cents, `toDouble()` major units | BankersValue.kt:74, 77 | S | |
| 136 | D:124 | Multiplying two values divides the product by 100 | BankersValue.kt:26 | S | |
| 137 | D:124 | [uncited] It carries no currency | — | T | BankersValue.kt:16 |

### derived/SKILL.md — Invariants and traps: Positions and measurements

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 138 | D:128 | `Coordinates` takes latitude first | Coordinates.kt:20 | S | 43-46 |
| 139 | D:128 | ...and so does `GeoUri` | GeoUri.kt:26 | S | |
| 140 | D:128 | `GeoPoint` takes longitude first because RFC 7946 requires it | GeoPoint.kt:34 | S | line 43 |
| 141 | D:128 | `DoubleArray.toCoordinates()` reads index 0 as longitude | Coordinates.kt:152 | S | line 163 |
| 142 | D:128 | Both values are `Double`, so transposing compiles | Coordinates.kt:22 | S | |
| 143 | D:128 | Symptom: wrong hemisphere, or `IllegalArgumentException` "Latitude must be between -90 and 90." when a longitude lands in the latitude slot | Coordinates.kt:51 | S | only when the misplaced value is outside ±90; the "or" covers both outcomes |
| 144 | D:128 | `GeoPoint` checks only that it has at least two elements; the mistake is silent there | GeoPoint.kt:79 | S | |
| 145 | D:128 | [uncited] There is no `GeoPoint`-to-`Coordinates` extension | — | T | only `DoubleArray.toCoordinates` exists (Coordinates.kt:161) |
| 146 | D:128 | `point.latitude`/`point.longitude` are the named accessors | GeoPoint.kt:70, 65 | S | |
| 147 | D:130 | On `Distance` and `Speed` accuracy is a fraction; `0.05` is five percent | Distance.kt:11, Speed.kt:11 | S | Distance.kt:12; used so at Distance.kt:40, Coordinates.kt:115 |
| 148 | D:130 | On `Coordinates` and `Altitude` it is metres | Coordinates.kt:35, Altitude.kt:16 | S | |
| 149 | D:130 | On `Azimuth` degrees | Azimuth.kt:16 | S | |
| 150 | D:130 | `Distance(100.0, accuracy = 5f)` claims five hundred percent | Distance.kt:12 | S | |
| 151 | D:130 | Subtracting two `Coordinates` gives relative accuracy, except coincident points give the absolute sum in metres | Coordinates.kt:59 | S | code 81-90 |
| 152 | D:132 | `Distance - Distance`, `Speed - Speed` throw `IllegalArgumentException` when the right side is larger | Distance.kt:70, Speed.kt:70 | S | traced `Distance(3.0) - Distance(5.0)` |
| 153 | D:132 | Message ends "a Distance cannot be negative." / "a Speed cannot be negative." | Distance.kt:71, Speed.kt:71 | S | |
| 154 | D:132 | A negative value at construction throws | Distance.kt:25, Speed.kt:24 | S | |
| 155 | D:132 | A negative scalar for `Speed` throws | Speed.kt:103 | S | also Speed.kt:123; a negative `Int` divisor on `Distance` throws too (Distance.kt:121), not mentioned |
| 156 | D:132 | Dividing by zero throws `ArithmeticException` | Distance.kt:120, Speed.kt:122 | S | |
| 157 | D:132 | `Distance / Distance` is a `Double` ratio | Distance.kt:99 | S | |
| 158 | D:132 | `Distance` has `/ Int` | Distance.kt:119 | S | |
| 159 | D:132 | [uncited] `Distance` has no multiplication and no division by a `Double` | — | T | members: plus, minus, div(Distance), div(Int), rem (Distance.kt:37-137) |
| 160 | D:134 | Azimuth construction accepts only 0 to 360 degrees | Azimuth.kt:30 | S | |
| 161 | D:134 | 360 stored as 0 | Azimuth.kt:43 | S | via companion `invoke`; decoding bypasses it and would keep 360.0 (not claimed) |
| 162 | D:134 | `-10.0` or `450.0` throws `IllegalArgumentException` "Azimuth must be between 0 and 360 degrees." | Azimuth.kt:30 | S | traced through `invoke` (42-43) → private constructor → `init` |
| 163 | D:134 | "Only arithmetic between azimuths wraps around" | Azimuth.kt:58 | O | Scalar `times(Double)` and `div(Double)` wrap as well (Azimuth.kt:93, 109; docs 86-87, 100-101). "Only construction does not wrap" is what the source shows; "only between azimuths" is too narrow. |
| 164 | D:136 | `a - b` is the distance between them | Coordinates.kt:67 | S | |
| 165 | D:136 | `-a` is the antipodal point | Coordinates.kt:133 | S | |
| 166 | D:136 | Moving a point is `a.plus(distance, bearing)`, which the `+` operator cannot reach | Coordinates.kt:106 | S | no `operator` modifier, two parameters |
| 167 | D:136 | `Coordinates.add(...)` is plain addition with no wrapping | Coordinates.extensions.kt:9 | S | |
| 168 | D:136 | An out-of-range result fails the constructor's range check | Coordinates.kt:52 | S | lines 51-52 |
| 169 | D:136 | The result is built without the original accuracy | Coordinates.extensions.kt:25 | S | third argument defaulted to `null` |
| 170 | D:138 | `Distance` and `Altitude` are metres | Distance.kt:19, Altitude.kt:27 | S | |
| 171 | D:138 | `Speed` is metres per second | Speed.kt:19 | S | |
| 172 | D:138 | `Telemetry.timestamp` is ms since the epoch, `0` meaning unavailable | Telemetry.kt:14 | S | default `0` at line 27 |
| 173 | D:138 | [uncited] Nothing converts to kilometres, miles, feet or km/h | — | T | no such function in the quantitative or units packages |

### derived/SKILL.md — Invariants and traps: GeoJSON

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 174 | D:142 | A `GeoLineString` needs at least two positions | GeoValidation.kt:29 | S | called from GeoLineString.kt:12 |
| 175 | D:142 | Each polygon ring needs at least four and must end where it starts | GeoValidation.kt:36, 40 | S | |
| 176 | D:142 | The checks sit in `init`, so they also run when decoding | GeoValidation.kt:6 | S | stated by the source doc; `init` blocks at GeoPolygon.kt:12 etc. |
| 177 | D:142 | Symptom: `IllegalArgumentException` "a linear ring must be closed…" | GeoValidation.kt:41 | S | |
| 178 | D:144 | Winding is left unchecked in `init` so foreign documents still parse | GeoValidation.kt:10 | S | |
| 179 | D:144 | `geoPolygon` throws on wrong winding; exterior counter-clockwise, holes clockwise; a ring enclosing no area is let through | GeoValidation.kt:116, 118 | S | |
| 180 | D:144 | `geoPolygonRewinding` accepts any winding and corrects it | GeoValidation.kt:132 | S | 95-100, 135 |
| 181 | D:144 | `polygon.rewound()` corrects one already parsed | GeoValidation.kt:93 | S | |
| 182 | D:144 | `polygon.windingOf(ringIndex)` reports it | GeoValidation.kt:70 | S | |
| 183 | D:146 | `GeoFeature.properties` is a `JsonObject`, not a `Map<String, String>` | GeoFeature.kt:42 | S | |
| 184 | D:146 | `id` is a `JsonPrimitive` that must be a string or a number | GeoFeature.kt:44, 50 | S | 50-53 |
| 185 | D:146 | `geometry` has no default and must be passed, as `null` if none | GeoFeature.kt:40 | S | |
| 186 | D:146 | Values are read with `stringProperty`, `intProperty`, `doubleProperty`, `booleanProperty`, `objectProperty`, `arrayProperty` | GeoFeature.kt:65, 75, 79, 83, 87, 90 | S | |
| 187 | D:146 | `stringProperty` returns `null` both for an absent key and for JSON `null` | GeoFeature.kt:62 | S | code 66-68 |
| 188 | D:148 | `GeoBoundingBox` is a helper, not a `GeoGeometry` | GeoBoundingBox.kt:13 | S | class header 31-44 has no supertype |
| 189 | D:148 | Every GeoJSON object's `bbox` member is a `List<Double>?` | GeoJson.kt:19 | S | |
| 190 | D:148 | ...which `toBbox()` returns | GeoBoundingBox.kt:90 | S | |
| 191 | D:148 | `toDoubleArray()` gives the same numbers as a `DoubleArray`, which will not assign to `bbox` | GeoBoundingBox.kt:20 | S | |
| 192 | D:148 | The list constructor accepts exactly four or six numbers | GeoBoundingBox.kt:54 | S | |
| 193 | D:150 | The geometry collection is `GeometryCollection`, without the `Geo` prefix | GeometryCollection.kt:11 | S | |

### derived/SKILL.md — Invariants and traps: Locales

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 194 | D:154 | `displayName` has no default, so `Locale("en", "US")` does not compile | Locale.kt:42 | S | |
| 195 | D:154 | `parseLocale("en-US")` is the way to get one from a tag | Locale.functions.kt:100 | S | |
| 196 | D:154 | `parseLocale` never fails; for an unknown tag `displayName` is the normalized tag itself (raw `xx-YY` shown as a name) | Locale.functions.kt:132 | S | traced `parseLocale("xx-YY")` → `Locale("xx", "YY", displayName = "xx-YY")` |
| 197 | D:156 | `Locale.current` throws when the platform reports nothing | Locale.kt:86 | S | |
| 198 | D:156 | `Locale.currentOrNull` returns `null` instead | Locale.kt:101 | S | |
| 199 | D:156 | On JVM, Android and Apple the lookup ends by parsing the platform's tag, so it always produces a `Locale` | jvm/locale/Locale.functions.jvm.kt:68, android/...:51, apple/...:59 | S | `... ?: parseLocale(tag)` on all three |
| 200 | D:156 | On Kotlin/JS the tag is `window.navigator.language` and a failure reading it yields `null` | js/locale/Locale.functions.js.kt:11 | S | lines 6-11 |
| 201 | D:156 | On Wasm the same when there is no `window` | wasm/locale/Locale.functions.wasmJs.kt:11 | S | lines 10-15 |
| 202 | D:156 | On Linux when `LANG` is unset | linux/locale/Locale.functions.linux.kt:12 | S | lines 9-12 |
| 203 | D:156 | `currentNativeLocale(fallbackTag)` takes a tag to use in that case | Locale.functions.kt:171 | S | |
| 204 | D:158 | `resolveLocale`'s last step is the platform lookup | Locale.functions.kt:83 | S | |
| 205 | D:158 | On JS, Wasm and Linux that is the strict table lookup again, so an unknown tag gives `null` | js/...:18, wasm/...:6, linux/...:19 | S | |
| 206 | D:158 | On JVM and Android it always constructs a `Locale` | jvm/...:8, android/...:8 | S | traced `resolveLocale("xx-YY")` on JVM: loop misses, `localeForNative` builds from `parseLocale` |
| 207 | D:158 | `localeFor` is strict on every target | Locale.functions.kt:57 | S | common code |
| 208 | D:160 | "`localizedDisplayName(displayIn)` falls back to the English `displayName` when the platform has no translation" | Locale.functions.kt:210 | O | Line 210 delegates to `localizedDisplayNameOrNull`, which consults a bundled common table first: `localizedDisplayNameForNative(this, displayIn) ?: LocaleDisplayNameSupplement.nameFor(displayIn.languageCode, languageTag)` (Locale.functions.kt:232-234; LocaleDisplayNameSupplement.kt:5-14, 229-230). The English fallback happens only when the platform and the supplement both miss. Also `displayName` is English only for table-sourced locales (see #215, #196). The heading ("may silently be the English one") is still right. |
| 209 | D:160 | "`localizedDisplayNameOrNull` returns `null` there instead" | Locale.functions.kt:232 | O | Same omission: when the platform has no translation it returns the supplement's name if there is one, `null` only when both miss (Locale.functions.kt:233-234). |
| 210 | D:160 | Both default `displayIn` to `Locale.current`, which can throw | Locale.functions.kt:227 | S | signatures at 209, 232 |
| 211 | D:160 | A browser and a native platform disagree on roughly 40% of the pairs both can name | Locale.functions.kt:250 | S | doc comment; not checkable in code |
| 212 | D:160 | Rendered names do not belong in cache keys or snapshot tests | Locale.functions.kt:252 | S | |
| 213 | D:162 | `Locale` is a data class with `displayName` in its constructor | Locale.kt:32, 42 | S | |
| 214 | D:162 | [uncited] ...and no `equals` of its own | — | T | Locale.kt has no `equals`/`hashCode` override |
| 215 | D:162 | The name a platform lookup supplies is the platform's | jvm/locale/Locale.functions.jvm.kt:13 | S | also android/...:13, apple/...:29 |
| 216 | D:162 | The bundled table's is English | Locale.resource.kt:70 | S | |
| 217 | D:162 | Two locales for one tag from different lookups can be unequal; `languageTag` is the stable thing to compare | Locale.kt:57 | S | e.g. `localeFor("en-US")` vs JVM `localeForNative("en-US")` on a non-English default locale |

### derived/SKILL.md — Invariants and traps: URLs, URIs and URNs

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 218 | D:166 | `mailto:someone@example.com` has no authority and throws `UriParseException` "a URL needs an authority" | UriParsing.kt:109 | S | traced: no `//` → authority `null` (UriParsing.kt:53) → `fail` |
| 219 | D:166 | `uri(...)` parses it | UriParsing.kt:81 | S | authority `""`, path `someone@example.com` |
| 220 | D:166 | A port outside 0..65535 is rejected | UriParsing.kt:153 | S | |
| 221 | D:166 | An absent port is `null`, not the scheme's default | UriParsing.kt:131 | S | |
| 222 | D:168 | `UriParseException` extends `IllegalArgumentException` | UriParsing.kt:24 | S | |
| 223 | D:168 | `uri`, `url`, `geoUri` throw it | UriParsing.kt:79, 103, 167 | S | all failure paths go through `fail` (26-27) or the wrap at 203-205 |
| 224 | D:168 | `urn(...)` builds a `Urn` whose constructor rejects an invalid namespace with a plain `IllegalArgumentException` | Urn.kt:50 | S | traced `urn("urn:x:abc")`: NID regex needs 2+ characters → `require` fails |
| 225 | D:168 | ...which is why `urnOrNull` catches the wider type | UriParsing.kt:212 | S | |
| 226 | D:168 | `Uri.toUrl()` and `Uri.toUrn()` throw plain `IllegalArgumentException` | Uri.kt:137, 115 | S | code Uri.kt:148, 154, 162; Urn init |
| 227 | D:168 | A `catch (e: UriParseException)` lets an invalid URN through; `urn` constructs the `Urn` outside any such catch | Urn.kt:181 | S | |
| 228 | D:170 | `uri` lowercases the scheme | UriParsing.kt:75 | S | line 42 |
| 229 | D:170 | `url` the scheme and host | UriParsing.kt:100 | S | line 142 |
| 230 | D:170 | Percent-encoding preserved, dot segments not resolved | UriParsing.kt:76 | S | |
| 231 | D:170 | `query` is the raw string after `?` | Url.kt:14 | S | UriParsing.kt:63-64 |
| 232 | D:170 | [uncited] Nothing splits it into parameters | — | T | no query-parsing function in the uri package |
| 233 | D:170 | The constructors lowercase nothing | Url.kt:45 | S | also Uri.kt:55-57 |
| 234 | D:170 | All of `Url`'s components except `port` are required arguments | Url.kt:22, 30 | S | |
| 235 | D:172 | `Url` compares scheme and host case-insensitively | Url.kt:53, 54 | S | |
| 236 | D:172 | `Uri` the scheme and the whole authority | Uri.kt:64, 65 | S | |
| 237 | D:172 | `Urn` compares the namespace case-insensitively and ignores r-, q-, f-components | Urn.kt:119 | S | 119-121 |
| 238 | D:172 | `urn:isbn:0451450523#page1` equals `urn:isbn:0451450523` | Urn.kt:115 | S | |
| 239 | D:172 | `GeoUri` treats a missing `crs` and `wgs84` as the same | GeoUri.kt:73 | S | `effectiveCrs` line 51 |
| 240 | D:172 | Hashing follows the same rules; a `Set` or map holds fewer entries | Urn.kt:124 | S | also Url.kt:63-72, Uri.kt:72-79, GeoUri.kt:77-84 |
| 241 | D:172 | The values given are still stored and serialized as given | Urn.kt:117 | S | |
| 242 | D:174 | `UrlBuilder.build()` returns a `String`, not a `Url` | UrlBuilder.kt:98 | S | |
| 243 | D:174 | ...and throws `IllegalStateException` unless both `scheme` and `host` are set | UrlBuilder.kt:101 | S | |
| 244 | D:174 | Each path segment is percent-encoded whole | UrlBuilder.kt:65 | S | |
| 245 | D:174 | `/` is not unreserved, so `addPathSegment("api/v1")` produces `api%2Fv1` | UrlEncoder.kt:18 | S | traced through `encodeInternal` (UrlEncoder.kt:50-91) |
| 246 | D:174 | `addPathSegments("api", "v1")` adds two segments | UrlBuilder.kt:75 | S | |
| 247 | D:174 | Repeating a query parameter name appends | UrlBuilder.kt:81 | S | line 88 |
| 248 | D:174 | [uncited] No fragment or user-information setter | — | T | members: scheme, host, port, addPathSegment(s), addQueryParameter, build |
| 249 | D:176 | `UrlEncoder.encode` follows RFC 3986; space is `%20` | UrlEncoder.kt:7 | S | |
| 250 | D:176 | `encodeFormData` writes `+`, leaves `*` bare, encodes `~` | UrlEncoder.kt:12 | S | 22-24, 65-67 |
| 251 | D:176 | `decode` keeps `+` literal (so mixing them leaves a literal `+`) | UrlEncoder.kt:96 | S | line 141 |
| 252 | D:176 | `decodeFormData` turns it into a space | UrlEncoder.kt:107 | S | |
| 253 | D:176 | Both decoders throw `IllegalArgumentException` on a malformed percent sequence or invalid UTF-8 | UrlEncoder.kt:100, 111 | S | code 122-126, 136, 153 |
| 254 | D:178 | `GeoUri`'s `crs` defaults to `"wgs84"` | GeoUri.kt:32 | S | |
| 255 | D:178 | The parser fills in the same default | UriParsing.kt:202 | S | |
| 256 | D:178 | `toString` writes any non-null `crs`; `geoUri("geo:1,2").toString()` is `geo:1,2;crs=wgs84` | GeoUri.kt:130 | S | traced; `formatCoordinate(1.0)` → `1` (GeoUri.kt:158-183) |
| 257 | D:178 | Passing `crs = null` omits it | GeoUri.kt:130 | S | |
| 258 | D:178 | Unknown parameters are dropped on parsing | UriParsing.kt:197 | S | |
| 259 | D:178 | The uncertainty `u` must be an integer | UriParsing.kt:195 | S | 195-196 |

### derived/SKILL.md — Invariants and traps: Outcome, units, collections

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 260 | D:182 | The payload is `data`, not `value` | Outcome.kt:46 | S | |
| 261 | D:182 | The rest is shaped like `kotlin.Result` (member list; `recover`, `getOrElse`, `getOrDefault` as extensions) | Outcome.kt:10 | S | members 76-166; extensions 224, 239, 255 |
| 262 | D:182 | `Failure.message` is extra and never `null` | Outcome.kt:70 | S | |
| 263 | D:184 | `map` does not catch | Outcome.kt:141 | S | code 147-150 |
| 264 | D:184 | `mapCatching` captures it as a `Failure` | Outcome.kt:164 | S | |
| 265 | D:184 | `runOutcome` and `mapCatching` both re-throw `CancellationException` | Outcome.kt:206, 156 | S | |
| 266 | D:184 | `runOutcome` is `inline` and not `suspend`, so it can wrap suspending calls | Outcome.kt:192 | S | line 203 |
| 267 | D:186 | Two failures are equal only if they hold the same exception instance | Outcome.kt:54 | S | data class over `Throwable` (61); holds for any exception type that does not itself override `equals` |
| 268 | D:186 | The source says to assert on the exception's type or message | Outcome.kt:56 | S | |
| 269 | D:186 | `Outcome` is deliberately not `@Serializable`; `fold` it first | Outcome.kt:31 | S | |
| 270 | D:190 | `UnitOfMeasure` names units; it converts nothing | UnitOfMeasure.kt:28 | S | |
| 271 | D:190 | "`findFirst(symbol)` returns `null` both for an unknown symbol and for an ambiguous one" (`"gal"`) | UnitOfMeasure.kt:166 | O | Right for `"gal"` (traced: no primary symbol `gal`; two alternates → `singleOrNull()` → `null`). But a primary-symbol match returns first (UnitOfMeasure.kt:180, doc 162), so a symbol that `findAll` reports twice can still return a unit: `"ton"` → `Ton` although `MetricTon` lists it (105, 145); `"′"` → `ArcMinute` although `Foot` lists it (36, 62); `"″"` → `ArcSecond` not `Inch` (37, 77); `"a"` → `Year` not `YearJulian` (155-156). `null` is returned only when the clash is among alternates. The authored skill makes the same generalization. |
| 272 | D:190 | `findAll(symbol)` tells the two apart | UnitOfMeasure.kt:175 | S | 195-199 |
| 273 | D:190 | Lookup is case-sensitive: `GB` and `Gb` are different units | UnitOfMeasure.kt:25 | S | lines 66-67 |
| 274 | D:190 | Byte multiples lead with the IEC symbol: `Kilobyte.symbol` is `KiB`, `kB` an alternative | UnitOfMeasure.kt:83 | S | |
| 275 | D:192 | `BitSet` has a fixed size and does not grow | BitSet.kt:10 | S | |
| 276 | D:192 | `BitSet()` holds one bit | BitSet.kt:16 | S | |
| 277 | D:192 | `bitSet()` none | BitSet.functions.kt:7 | S | |
| 278 | D:192 | An index at or past the size throws `IndexOutOfBoundsException` "Index: 5, Size: 1" | BitSet.kt:110 | S | |
| 279 | D:192 | `size()` is a function; `hasIndex(i)` tests without throwing | BitSet.kt:98, 120 | S | |
| 280 | D:194 | `LazyMap` is safe for concurrent access but a function may run more than once per key under contention, the loser discarded | LazyMap.kt:28 | S | code 53-66 |
| 281 | D:194 | `equals` and `hashCode` may force every value | LazyMap.kt:21 | S | 122-137 |
| 282 | D:194 | `lazyMapOf("key" to { compute() })` builds one | LazyMap.functions.kt:11 | S | |

### derived/SKILL.md — What moved, What it is not for, Other languages

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 283 | D:198 | [uncited] The source records few changes and gives a version for only one | — | T | the only version string in non-data source is `4.1.0` at Locale.functions.kt:246 |
| 284 | D:200 | Before 4.1.0 `localizedDisplayNameForNative` returned `null` for every pair on Linux; it now answers from a bundled table | Locale.functions.kt:246 | S | doc comment 244-247; code linux/locale/Locale.functions.linux.kt:22-26 |
| 285 | D:200 | ...that ships only in the Linux artifact | linux/locale/Locale.functions.linux.kt:25 | S | the table file is in `linuxMain` |
| 286 | D:200 | "Code that relied on always getting the English fallback from `localizedDisplayName` on Linux now gets translations" | Locale.functions.kt:210 | O | Line 210 shows today's fallback, not what `localizedDisplayName` returned before 4.1.0. The source states only what the native function returned then; whether the common supplement (Locale.functions.kt:234) existed before 4.1.0 is not in the source. An inference beyond what is stated, against the section's own promise. Belief: probably true. |
| 287 | D:202 | `Location` is deprecated in favour of `Telemetry`, same package, same properties | Telemetry.kt:47, 49 | S | property lists 18-27 and 53-62 are identical |
| 288 | D:202 | `Location` still exists and compiles with a deprecation warning | Telemetry.kt:52 | S | `@Deprecated` with no level |
| 289 | D:202 | [uncited] No version given | — | T | |
| 290 | D:204 | `Money.cents` is the old name for `minorUnits`, kept for backward compatibility | Money.kt:84 | S | |
| 291 | D:204 | [uncited] ...and not marked deprecated | — | T | the only `@Deprecated` in the source is Telemetry.kt:47 |
| 292 | D:206 | `GeoBoundingBox` used to extend `GeoGeometry` and no longer does | GeoBoundingBox.kt:14 | S | |
| 293 | D:206 | Passing one where a geometry is expected does not compile; use `toBbox()` | GeoBoundingBox.kt:18 | S | |
| 294 | D:206 | [uncited] No version given | — | T | |
| 295 | D:208 | Recap: `outcome.value`, `java.math` methods on `Money.value`, `Locale("en", "US")`, `GeoPoint(latitude, longitude)`, `UrlBuilder().build()` as a `Url`, a growing `BitSet` are each contradicted above | untagged | S | restates #260, #116, #194, #140, #242, #275 |
| 296 | D:214 | `UnitOfMeasure` is names and symbols only | UnitOfMeasure.kt:28 | S | |
| 297 | D:214 | Measurement types are fixed to metres and metres per second | Distance.kt:19, Speed.kt:19 | S | |
| 298 | D:214 | [uncited] No function formats a `Money`, a number, a distance or a date for display, and none converts between currencies | — | T | only `toString` representations exist; `formatCoordinate` is private (GeoUri.kt:158) |
| 299 | D:214 | `Uri`/`Url` do not resolve dot segments or decode percent-encoding | UriParsing.kt:76 | S | |
| 300 | D:214 | The bundled locale table is maintained by hand and is not CLDR data | Locale.resource.kt:6 | S | true of `localeResourceMap`; the display-name tables are generated from CLDR (LocaleDisplayNameSupplement.kt:16), which the skill does not say otherwise |
| 301 | D:218 | Source set for the JVM | jvm/financial/Currency.functions.jvm.kt:5 | S | |
| 302 | D:218 | ...Android | android/financial/Currency.functions.android.kt:5 | S | |
| 303 | D:218 | ...Apple targets | apple/financial/Currency.functions.apple.kt:9 | S | |
| 304 | D:218 | ...Linux | linux/financial/Currency.functions.linux.kt:2 | S | |
| 305 | D:218 | ...Kotlin/JS | js/financial/Currency.functions.js.kt:3 | S | |
| 306 | D:218 | ...Kotlin/Wasm | wasm/financial/Currency.functions.wasmJs.kt:3 | S | |
| 307 | D:220 | [uncited] Nothing is annotated `@JsExport`, so no surface for hand-written JS/TS | — | T | no `JsExport`/`JsName` in the source |
| 308 | D:222 | The source addresses Swift callers in its doc comments | Money.kt:221 | S | |
| 309 | D:222 | `Outcome` exists so failures reach other languages as values | Outcome.kt:21 | S | |
| 310 | D:222 | [uncited] Whether and how an Apple framework is published is not shown | — | T | |

### derived/references/swift.md

| # | Where | Claim | Citation | V | Evidence / note |
|---|---|---|---|---|---|
| 311 | DS:3 | [uncited] The source does not show how an Apple framework is packaged | — | T | |
| 312 | DS:7 | Objective-C cannot express overloads differing only by number type, so the `Long` forms of `Money` multiplication and division arrive with a trailing underscore | Money.kt:222 | S | stated by the source doc (221-224, 234-237); declaration order (Double at 169/180, Long at 226/243) is consistent with it |
| 313 | DS:7 | `times(scalar_:)` is `Long`, `times(scalar:)` is `Double` | Money.kt:221 | S | |
| 314 | DS:7 | `div(scalar_:)` is `Long`, `div(scalar:)` is `Double`, `div(other:)` returns a ratio | Money.kt:234, 236 | S | |
| 315 | DS:7 | The underscore is the only thing telling them apart; the wrong one silently changes the arithmetic | Money.kt:223 | S | |
| 316 | DS:9 | The same split exists in Kotlin for addition and subtraction: `Long` minor units, `Double` major units | Money.kt:187, 155 | S | |
| 317 | DS:9 | [uncited] The source does not state the Swift names of those overloads | — | T | only `times` and `div` carry a "Swift callers" note |
| 318 | DS:9 | `plusMinorUnits`/`minusMinorUnits` are separately named | Money.kt:205, 212 | S | |
| 319 | DS:13 | `kotlin.Result` is a value class with no stable representation outside Kotlin; it reaches Swift as an opaque box | Outcome.kt:17 | S | stated by the source doc |
| 320 | DS:13 | `Outcome` is sealed: a Swift caller branches on `Success`/`Failure` and reads the payload | Outcome.kt:20 | S | stated by the source doc |
| 321 | DS:13 | `Success` carries `data` | Outcome.kt:46 | S | |
| 322 | DS:13 | `Failure` carries `exception` | Outcome.kt:61 | S | |
| 323 | DS:13 | `Failure.message` is non-null, provided for Swift and JavaScript callers | Outcome.kt:66, 67 | S | |
| 324 | DS:17 | [uncited] No declaration carries `@Throws` | — | T | no `Throws` annotation anywhere (only KDoc `@throws`) |
| 325 | DS:17 | [uncited] In Kotlin/Native generally, an exception from a function without that annotation does not become a Swift `Error` | — | ? | Not in the source, as the skill itself says. Belief: true, and understated — the authored skill, measured against a toolchain, says the process is terminated. |
| 326 | DS:21 | `uri`, `url`, `geoUri` throw on a malformed string | UriParsing.kt:79, 103, 167 | S | |
| 327 | DS:21 | Alternatives `uriOrNull`, `urlOrNull`, `geoUriOrNull` | UriParsing.kt:94, 158, 209 | S | |
| 328 | DS:22 | `urn` throws when the string or its namespace is invalid | Urn.kt:141 | S | |
| 329 | DS:22 | Alternative `urnOrNull` | UriParsing.kt:212 | S | |
| 330 | DS:23 | `Locale.current` throws when the platform reports no locale | Locale.kt:86 | S | |
| 331 | DS:23 | Alternative `Locale.currentOrNull` | Locale.kt:101 | S | |
| 332 | DS:24 | "`Currency.current`, and any `Money` built without a currency" throw when no currency is mapped | Currency.kt:99, Money.kt:34 | O | The Kotlin facts are right. On a page about Swift callers, "built without a currency" presumes a Kotlin default argument can be omitted from Swift; the source does not show that, and the authored reference (read from the generated header) says every parameter is required. Belief: a Swift caller cannot build a `Money` without a currency, so this half of the row describes a case that does not arise there. |
| 333 | DS:24 | Alternative `Currency.currentOrNull` | Currency.kt:112 | S | |
| 334 | DS:24 | ...or `currencyFor(code)`, `null` when unknown | Currency.functions.kt:12 | S | |
| 335 | DS:25 | "`localizedDisplayName`, `localizedDisplayNameOrNull` with `displayIn` left out" throw because it defaults to `Locale.current` | Locale.functions.kt:227 | O | Same as #332: the default exists in Kotlin (Locale.functions.kt:209, 232); that a Swift caller can leave the argument out is not shown by the source and is contradicted by the authored, measured reference. The advice to pass `displayIn` explicitly is right either way. |
| 336 | DS:26 | `Money` `plus`, `minus`, `times`, `div`, `compareTo` with another `Money` throw when currencies differ | Money.kt:114, 70 | S | also 124, 138, 148 |
| 337 | DS:27 | `Distance` and `Speed` subtraction throws when the right side is larger | Distance.kt:70, Speed.kt:70 | S | |
| 338 | DS:28 | `Coordinates`, `Azimuth`, `GeoUri` construction throws when a value is out of range | Coordinates.kt:51, Azimuth.kt:30, GeoUri.kt:37 | S | |
| 339 | DS:29 | `geoPolygon` throws when a ring is wound the wrong way | GeoValidation.kt:118 | S | |
| 340 | DS:29 | `geoPolygonRewinding` still throws on structural errors | GeoValidation.kt:130 | S | line 135 calls the `GeoPolygon` constructor |
| 341 | DS:30 | `BigDecimal` division without a scale throws when the quotient does not terminate or the divisor is zero | BigDecimal.kt:198 | S | 202-203, 232-233 |
| 342 | DS:30 | `divide(other, scale, roundingMode)` still throws on a zero divisor | BigDecimal.kt:247 | S | 251-253 |
| 343 | DS:31 | `UrlBuilder.build` throws when `scheme` or `host` is unset | UrlBuilder.kt:101 | S | |
| 344 | DS:32 | `UrlEncoder.decode`, `decodeFormData` throw on a malformed or non-UTF-8 sequence | UrlEncoder.kt:100, 111 | S | |
| 345 | DS:32 | [uncited] No non-throwing alternative in the source | — | T | |
| 346 | DS:33 | `Outcome.getOrThrow` throws when the outcome is a failure | Outcome.kt:122 | S | |
| 347 | DS:33 | Alternatives `getOrNull`, `exceptionOrNull` | Outcome.kt:109, 114 | S | |
| 348 | DS:34 | `BitSet` `get`, `set`, `clear` throw when the index is outside the fixed size | BitSet.kt:110 | S | 43-44, 57-58, 72-73, 86-87 |
| 349 | DS:34 | Alternative `hasIndex` | BitSet.kt:120 | S | |
| 350 | DS:36 | `UriParseException` is a named type so a failure can be identified across the language boundary | UriParsing.kt:10 | S | stated by the source doc |

The frontmatter `description` (D:3-17) is a summary with no citations; it was read against the body and the source, holds nothing the body does not, and is not counted.

## Traces for traps and boundaries

Concrete inputs followed through the code, per source set where the path differs.

- **Leaving out the currency (D:100).** `Money()` → `Currency.current` → `getCurrency(Locale.current)` → `currencyCodeFor` (Currency.kt:129-146). JVM/Android default locale `en` (no country): tag `en` → `localeFor("en")` hits → candidates `["en"]` → not a key → `requireNotNull` throws. JVM `en-IN`: candidates `en-IN`, `en` → no key → throws. Apple: `en_US` → `en-US` → `USD`. JS/Wasm `navigator.language = "en"` → throws. Linux `LANG=en_US.UTF-8` → `en-US` → `USD`; `LANG=C` → `parseLocale("C")` gives language `c` → throws from `Currency.current`; `LANG` unset → `Locale.current` itself throws (Locale.kt:86). Supported on every target.
- **`Locale.current` (D:156).** JVM/Android/Apple end in `?: parseLocale(tag)`, never `null`. JS: `try { window.navigator.language } catch → null`, then `?: fallbackTag ?: return null`. Wasm: `typeof window !== 'undefined' ...  : null`. Linux: `getenv("LANG")` `null` → `return null`. Supported.
- **`resolveLocale` (D:158).** `resolveLocale("xx-YY")`: common loop misses `xx-YY`, `xx`; JVM/Android `localeForNative` builds a `Locale` from `parseLocale` → non-null; JS/Wasm/Linux `localeFor("xx-YY")` → `null`. Apple depends on `NSLocale` (apple/...:13-31), which the skill does not characterize. Supported.
- **Localized names (D:160).** `x.localizedDisplayName(fr)` → native (JVM: `null` when the language subtag is echoed or the root form comes back, jvm/...:26-39; Android 22-23; Apple 37-53; JS/Wasm `Intl.DisplayNames` with `fallback: 'none'`; Linux bundled matrix) → **supplement table** (Locale.functions.kt:234) → `displayName`. The skill skips the middle step: overstated, not contradicted.
- **Mixed currencies (D:102).** `listOf(Money(1.0, usd), Money(1.0, eur)).sorted()` → `compareTo` → `require` → `IllegalArgumentException`. Supported.
- **Rounding (D:104).** See #90. Supported.
- **`BigDecimal` division (D:118).** `BigDecimal(1L) / BigDecimal(3L)` → remainder non-zero, reduced denominator 3 → throws. Supported.
- **`BigInteger` (D:122).** `(-7).rem(3)`: remainder magnitude 1 with the dividend's sign → -1; `mod` → -1 + 3 = 2. Supported.
- **Azimuth (D:134).** `Azimuth(450.0)` → `invoke` → private constructor → `init` `require` fails. `Azimuth(90.0) * -1.0` → `normalize(-90)` = 270: scalar arithmetic wraps too, which is what makes #163 too narrow.
- **Polygon winding (D:144).** `GeoPolygon(cwRing)` succeeds (only structure checked, GeoValidation.kt:45-49); `geoPolygon(cwRing)` → `windingOfRing` `Clockwise` ≠ `CounterClockwise` → throws; `geoPolygonRewinding` reverses it. Supported.
- **`url` vs `uri` (D:166), URN failures (D:168), `UrlBuilder` (D:174), encodings (D:176), `geo:` round trip (D:178).** See #218-219, #224-227, #245, #251-253, #256. All supported.
- **`findFirst` (D:190).** `"gal"` → `null`; `"ton"` → `Ton`; `"′"` → `ArcMinute`. See #271.
- **`BitSet` (D:192).** `BitSet().set(5)` → `checkIndex` → "Index: 5, Size: 1". Supported.
- **Boundaries (D:214).** No conversion function in `units` or `quantitative`; no formatting or currency-conversion function anywhere; `uri`/`url` copy path and query verbatim (UriParsing.kt:50-68); locale table header says first-party. All supported or true.

## Traps and boundaries — tally

Counted as one item each: every bold-headed item under "Invariants and traps" (43), each boundary under "What it is not for" (4: no unit conversion; no formatting or currency conversion; no dot-segment resolution or decoding; locale table is not CLDR), the no-JavaScript-surface boundary (1), and the two Swift traps (underscore overloads; no `@Throws`, use the non-throwing form) — 50 items.

- Fully supported (every component claim SUPPORTED or TRUE): 44.
- Supported in substance with one or more overstated or undecidable components: 6 — "These are not `java.math` classes" (#118), "`BankersValue` counts cents" (#132), "`Azimuth` rejects out-of-range input" (#163), "A localized name may silently be the English one" (#208, #209), "`UnitOfMeasure` names units" (#271), Swift "Calls that throw" (#325 cannot-tell, #332, #335).
- Contradicted by the source: none.

## Part 2 — against the authored skill

### Authored traps and boundaries, and whether the derived skill has them

| # | Authored trap or boundary | In derived? | Note |
|---|---|---|---|
| A1 | `Coordinates` and `GeoPoint` take arguments in opposite orders; use `toGeoPoint()` | Yes (D:128, D:88) | derived adds `GeoUri` and `DoubleArray.toCoordinates` |
| A2 | `Money` equality ignores scale, serialization preserves it; `BigDecimal` is scale-sensitive | Yes (D:108) | |
| A3 | Every `Money` operation across two currencies throws, including sorting | Yes (D:102) | derived quotes the message |
| A4 | `Currency` equality is its ISO code alone | Yes (D:110) | authored's "before 4.1.0 compared all seven fields" is absent (not in the source) |
| A5 | `Distance` and `Speed` reject negative results instead of clamping | Yes (D:132) | derived omits the negative `Int` divisor on `Distance` (Distance.kt:121) |
| A6 | `Locale.current` and `Currency.current` throw when the platform cannot say; use `currentOrNull` | Yes (D:156, D:100) | derived adds the per-target analysis and the `Money` default |
| A7 | `UnitOfMeasure.findFirst` returns `null` for an ambiguous symbol; `findAll`; case-sensitive | Yes (D:190) | both over-generalize (#271) |
| A8 | Invalid GeoJSON rejected on construction and deserialization; winding not enforced except by `geoPolygon` | Yes (D:142, D:144) | |
| A9 | URI types compare by their specification's equivalence | Yes (D:172) | |
| A10 | `localizedDisplayName` falls back to English silently; platform CLDR, then a bundled table, then English; use `...OrNull` | Yes, weaker (D:160) | derived omits the bundled-table tier (#208, #209) |
| A11 | Never depend on the exact name (about 40% disagreement) | Yes (D:160) | |
| A12 | A `LazyMap` value function must be pure | Yes (D:194) | |
| A13 | `Success` carries `data`, not `value`; `Failure.message` is non-null | Yes (D:182) | |
| A14 | `runOutcome`/`mapCatching` re-throw `CancellationException`; `inline`, not `suspend` | Yes (D:62, D:184) | |
| A15 | `Outcome` is not `@Serializable`; `fold` first | Yes (D:186) | |
| B1 | Models values; does not format them for display | Yes (D:214, uncited, true) | |
| B2 | Not a datetime library; `kotlinx-datetime` is an `api` dependency | No | not recoverable: the published source has no `kotlinx.datetime` import at all |
| B3 | Not an internationalization framework | No | the wording caveat is present (D:160) but the boundary is not stated |
| B4 | No I/O, no networking, reads no files or resources at runtime | No | consistent with the source (only `getenv("LANG")` and `window.navigator`), so it could have been derived |
| B5 | No hand-written-JavaScript surface (no `@JsExport`) | Yes (D:220) | |
| B6 | Swift: nothing is `@Throws`; an exception at the boundary terminates the process | Partly (DS:17, table) | derived has "no `@Throws`" and the non-throwing alternatives, but says only "does not become a Swift `Error`", not that the app crashes |
| S1 | Framework is `AOTypesKit` (was `AughtoneTypesKit`) | No | not in the source |
| S2 | Exported types carry the `AOTK` prefix | No | not in the source |
| S3 | Top-level functions live in `<File>Kt` classes (`UriParsingKt`, `Money_extensionsKt`) | No | toolchain behaviour, not in the source |
| S4 | Default arguments do not survive the export | No | and DS:24, DS:25 assume the opposite (#332, #335) |
| S5 | Number-type overloads gain a trailing underscore | Yes (DS:7) | |
| S6 | `Outcome` is not a Swift enum; no exhaustive switch | No | |
| S7 | Accessors are methods with parentheses; failure callbacks receive `KotlinThrowable` | No | |
| S8 | Nullable and generic numbers arrive boxed | No | |
| S10 | Coordinate-order trap applies in Swift; `toGeoPoint()` drops `accuracy` | Partly | order trap is in the main skill; "drops accuracy" is missing although the source states it (GeoPoint.extensions.kt:19) |

Total 30: found 18, partly found 2, missed 10. Of the 10 missed, 8 (B2, S1-S4, S6-S8) concern the build or the generated Objective-C header and are not in the published source; B3 and B4 could have been written from it.

### "What moved" compared

The authored skill lists 23 history facts (5 removals and 7 shape changes in 4.0.0; 4 `Outcome` changes, 2 behaviour changes in 4.1.0; 4 earlier renames; `Currency` equality before 4.1.0). The derived skill recovers 1 of them as history (`GeoBoundingBox` no longer a `GeoGeometry`, without a version) and describes the current state of several others without knowing they changed. It misses the one the authored skill flags as silent (the failure callback's `it.message` becoming nullable in 4.1.0), which the source cannot show. It has 3 the authored skill lacks, all confirmed by the source: Linux localized names starting in 4.1.0 (Locale.functions.kt:244-247), `Location` deprecated for `Telemetry` (Telemetry.kt:47-52), `Money.cents` kept as the old name of `minorUnits` (Money.kt:84-86).

### Traps and boundaries only the derived skill has

28 of the derived skill's 50 items have no counterpart among the authored traps and boundaries.

| Derived-only item | Part 1 verdict |
|---|---|
| A `Long` is minor units for construct/add/subtract, a multiplier for times/div (D:98) | supported |
| Results are not rounded to the currency's digits (D:104) | supported |
| `Money / Money` is a `Double` ratio; `Money * Money` is a `Money` (D:106) | supported |
| A serialized `Money` is a nested object (D:112) | supported / true |
| `currencyFor(code)` is exact-key and case-sensitive; `currencyForNative` varies (D:114) | supported |
| These are not `java.math` classes (D:116) | true; one sub-claim overstated (#118) |
| `BigDecimal` `/` must be exact; `setScale(n)` never rounds (D:118) | supported |
| A `Double` becomes the decimal it prints as; `valueOfExact` (D:120) | supported |
| `BigInteger.toInt()`/`toLong()` truncate; `rem` vs `mod` (D:122) | supported |
| `BankersValue` units (D:124) | supported; heading miscounts the factories (#132) |
| `accuracy` means different things on different types (D:130) | supported |
| `Azimuth` rejects out-of-range input (D:134) | supported; last sentence overstated (#163) |
| Three look-alike `Coordinates` operations; `add` does not wrap and drops accuracy (D:136) | supported |
| Units are fixed; nothing converts (D:138) | supported / true |
| `GeometryCollection` lacks the `Geo` prefix (D:150) | supported |
| `Locale` cannot be built from codes alone; `parseLocale` never fails and may yield a raw tag as name (D:154) | supported |
| `resolveLocale` returning `null` is not portable (D:158) | supported |
| `displayName` takes part in `Locale` equality (D:162) | supported |
| `url(...)` needs an authority; port rules (D:166) | supported |
| Not every failure is a `UriParseException` (D:168) | supported |
| Parsing normalizes almost nothing; `query` is raw (D:170) | supported |
| `UrlBuilder.build()` returns a `String`; whole-segment encoding (D:174) | supported |
| Two encodings, not interchangeable (D:176) | supported |
| A `geo:` link gains `;crs=wgs84` (D:178) | supported |
| `BitSet` has a fixed size (D:192) | supported |
| No unit conversion (D:214) | supported |
| `Uri`/`Url` do not resolve dot segments or decode (D:214) | supported |
| The locale table is hand-maintained, not CLDR (D:214) | supported |

The source confirms 25 of the 28 in full and the other 3 in substance (one overstated sub-claim each). None is contradicted. Two further derived items extend an authored one with a point the authored skill lacks, both supported: "`map` does not catch" (D:184) and "two failures are equal only if they hold the same exception instance" (D:186). `GeoFeature.properties` (D:146) and `GeoBoundingBox` (D:148) appear in the authored skill only as history.

### Disagreements

| # | Point | Authored says | Derived says | Source supports |
|---|---|---|---|---|
| 1 | Exception type from `urn(...)` | every parser's malformed input raises `UriParseException` | an invalid namespace raises a plain `IllegalArgumentException`; `catch (e: UriParseException)` misses it | Derived — Urn.kt:50, 53, 181; UriParsing.kt:212 |
| 2 | `localizedDisplayName` fallback chain | platform, then a bundled table, then English | platform, then English | Authored — Locale.functions.kt:232-234 |
| 3 | What changed for localized names in 4.1.0 | the JS target returned English and now translates; "the other targets are unaffected in kind" | Linux returned nothing before 4.1.0 and now answers from a bundled table | Derived on Linux — Locale.functions.kt:244-247 contradicts "other targets unaffected". The source also records the JS defect (js/locale/Locale.functions.js.kt:36-38, no version), which the derived skill missed. Each is right about its own target. |
| 4 | Scope of case-insensitive `Uri` equality | scheme and host case-insensitive, everything else case-sensitive | scheme and the whole authority | Derived — Uri.kt:65 lowercases the entire authority, user information included (for `Url` the authored statement is right, Url.kt:53-55) |
| 5 | Swift default arguments | defaults do not survive the export; every parameter is required | table rows speak of `displayIn` "left out" and a `Money` "built without a currency" | Not decidable from the source. The authored reference was read from the generated header; believed right. |

Not disagreements, but authored statements the source does not bear out: "explicit `@SerialName` on every property" (see #72: `BankersValue.cents`, the geometry classes' members); "`Outcome` is the one type here that is not `@Serializable`" (`UrlBuilder`, `BitSet`, `LazyMap` and the enums are not either); "`kotlinx-datetime` is an `api` dependency" (no trace in the published source); and the same `findFirst` generalization as #271.
