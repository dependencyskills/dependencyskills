# Score: derived skill for `io.github.aughtone:charts` 0.0.3, checked against the source

Scope read: `derive/charts/source/` (all 61 files, every line except the path data of `gasbottle/GasTank.kt` past line 40), `derive/charts/derived/SKILL.md`, and the authored skill for comparison. No web, no builds. Paths below are relative to the source directory, with the `commonMain/io/github/aughtone/charts/` prefix dropped where unambiguous.

Method. Each assertion in the body of the derived skill is one claim; a sentence carrying several separately cited assertions is split. Every citation was opened and the code around it read. Verdicts: SUPPORTED, OVERSTATED, UNSUPPORTED, CONTRADICTED for cited claims; TRUE, FALSE, CANNOT-TELL for `[uncited]` claims. A claim whose last step depends on how Compose or Kotlin behaves, where the source shows only the set-up, is marked OVERSTATED (cited) or CANNOT-TELL (uncited) and tagged "rule", meaning the verdict comes from that rule and not from any sign the claim is false. Nothing could be run, so every trace is by hand.

All 200-odd citations resolve to a line that is about the thing cited; no citation points at an unrelated line.

## Totals

| | count |
|---|---|
| Claims in total | 143 |
| Cited | 131 |
| SUPPORTED | 124 |
| OVERSTATED | 7 |
| UNSUPPORTED | 0 |
| CONTRADICTED | 0 |
| `[uncited]` | 12 |
| TRUE | 9 |
| FALSE | 0 |
| CANNOT-TELL | 3 |

## Part 1 — claim by claim

### A. What it solves (23 cited, 3 uncited)

| id | claim | verdict | evidence |
|---|---|---|---|
| A1 | `LineChart`: one or more time series on shared axes, smoothed curve with faint shading, grid with value labels and time ticks | SUPPORTED | line/LineChart.kt:82, 50-52; line/Line.kt:36 (`cubicTo`), 60-72 (gradient fill at alpha/12 and /6); line/LineChartSeries.kt:83-85 |
| A2 | balloon shows every series' value under the pointer | SUPPORTED | line/LineChartOverlayInformation.kt:49-78. A series with no sample both sides is skipped (122); the skill says so itself later |
| A3 | `LineChartWithLegend` is the same chart with a legend underneath | SUPPORTED | line/LineChartWithLegend.kt:34-52 |
| A4 | `BarChart` / `BarChartWithLegend`: clusters of bars under category labels | SUPPORTED | bar/BarChart.kt:57, 157-161; bar/BarChartWithLegend.kt:20 |
| A5 | negative values allowed, drawn from a shared zero line | SUPPORTED | bar/BarChartEntry.kt:11; bar/BarChartData.kt:12, 34, 41; bar/BarChartDrawing.kt:42-46 |
| A6 | balloon naming the bar under the pointer | SUPPORTED | bar/BarChart.kt:114-122, 213 |
| A7 | pie/donut sized by ratio between values, optional gaps | SUPPORTED | pie/PieChart.kt:55, 115-123; pie/PieChartConfig.kt:24 |
| A8 | legend below or beside: `PieChartWithLegend` | SUPPORTED | pie/PieChartWithLegend.kt:29-70 |
| A9 | `Dial`: half circle filled in proportion to one Int, tick scale, value in the middle, bounds at the ends, any range | SUPPORTED | dial/Dial.kt:41, 62-65, 107-115, 125, 133-134 |
| A10 | `PercentageDial` for 0 to 100 | SUPPORTED | dial/PercentageDial.kt:16, 27-28 |
| A11 | `ArcProgressBar`: semicircle by default, any start angle, extent up to a full circle | SUPPORTED | simple/ArcProgressBar.kt:30-31, 40-41. Nothing caps `totalArcDegrees` at 360; the KDoc only names 360 as a full circle |
| A12 | `SimpleLineChart` / `SimpleBarChart` from a plain `List<Float>` | SUPPORTED | simple/SimpleLineChart.kt:39-40; simple/SimpleBarChart.kt:31-32 |
| A13 | [uncited] no axes, labels, legend, animation or touch handling | TRUE | neither file has `Text`, `animate*` or `pointerInput` |
| A14 | `BubbleChart`: circles sized relative to one another, each with an icon, a number and a name | SUPPORTED | bubble/BubbleChart.kt:49, 30-31; bubble/BubbleDefaults.kt:31-61 (default label only) |
| A15 | "pushed apart until they stop colliding" | OVERSTATED | The cited comment (bubble/BubblePacking.kt:12) says it. The code stops on a running counter (77: +1 per bubble while anything moves, -1 otherwise) or after 5000 passes (107, 116-117), so it can end with circles still inside each other's reach; the chart's default spacing is `-10f` (bubble/BubbleChart.kt:53) and its KDoc says bubbles "can overlap" (30) |
| A16 | `GasBottle`: cylinder filled to a percentage, coloured between empty and full, no number shown | SUPPORTED | gasbottle/GasBottle.kt:28-32, 42, 98-102 |
| A17 | standalone `ChartLegend` | SUPPORTED | line/ChartLegend.kt:33-46 |
| A18 | `ChartAnimation` chooses how a chart animates in | SUPPORTED | ChartAnimation.kt:6-13 |
| A19 | palette supplied through `LocalChartColors` | SUPPORTED | theme/Theme.kt:18 |
| A20 | `YAxisScale`, `TimestampXAxisScale`, `DrawScope.measureChartGrid`, `DrawScope.drawChartGrid` are public pieces | SUPPORTED | grid/axisscale/YAxisScale.kt:26; TimestampXAxisScale.kt:19; grid/GridChartDrawing.kt:17, 51. (`positionOf` and `plotArea` are internal, 147, 159, so a caller gets grid lines but not the value-to-pixel mapping; not claimed) |
| A21 | `Number.round(decimals)` formats a number as label text | SUPPORTED | Helpers.kt:72-82 |
| A22 | "Called from Kotlin only, inside a Compose composition; there is no Swift or JavaScript surface." | OVERSTATED (rule) | The source shows composables and no export annotation. Kotlin/Native exports public declarations to Objective-C without any annotation, and the library has non-composable public API (Helpers.kt:82; YAxisScale.kt:26; the data classes), so "no Swift surface" is a packaging fact the source cannot show. The authored skill states the same conclusion |
| A23 | every chart is a `@Composable` function | SUPPORTED | bar/BarChart.kt:56, and all 14 other public chart functions checked |
| A24 | [uncited] no declaration carries `@JsExport` or an Objective-C or Swift export annotation | TRUE | grep over all source sets: none |
| A25 | source sets for Android, JVM, JS, Wasm-JS and iOS arm64 | SUPPORTED | the five `*Main` directories |
| A26 | [uncited] the only platform-specific code is the generated resource accessors | TRUE | one file per platform set, all five byte-identical |

### B. How it is meant to be used (17 cited)

| id | claim | verdict | evidence |
|---|---|---|---|
| B1 | root package holds `ChartAnimation`, `round`, `mapValueToDifferentRange` | SUPPORTED | ChartAnimation.kt:1; Helpers.kt:1, 32, 50, 65, 82 |
| B2 | `theme` holds the palette | SUPPORTED | theme/ChartDefaults.kt:1; theme/ChartColors.kt:1 |
| B3 | one package per chart family plus `grid` and `grid.axisscale` | SUPPORTED | directory listing. (`resources` also exists; covered by its own trap) |
| B4 | example 1: `BarChartData` / `BarChartCategory(name, entries)` / `BarChartEntry(x, y, color)` / `chartColors(surface = …)` / `BarChartWithLegend(data, modifier)` | SUPPORTED | bar/BarChartData.kt:18-19; bar/BarChartCategory.kt:13-15; bar/BarChartEntry.kt:15-18; theme/ChartDefaults.kt:30-36; bar/BarChartWithLegend.kt:20-22 |
| B5 | `chartColors()` is `@Composable`, builds from Material 3, every colour overridable | SUPPORTED | theme/ChartDefaults.kt:3, 29-36 |
| B6 | palette charts are `BarChart`, `LineChart`, `Dial`, `PercentageDial`, `GasBottle`, each through a narrowing `colors` parameter; the `WithLegend` variants of the first two pass theirs on | SUPPORTED | the seven cited lines; narrowing getters at bar/BarChartColors.kt:20, line/LineChartColors.kt:22, dial/DialColors.kt:22, gasbottle/GasBottleColors.kt:20 |
| B7 | bars, lines, pie segments and bubbles are coloured from the data | SUPPORTED | bar/BarChartEntry.kt:18; line/LineChartSeries.kt:39; pie/PieChart.kt:38; bubble/Bubble.kt:27 |
| B8 | the three `simple` composables ignore the palette and default to the Material theme | SUPPORTED | simple/ArcProgressBar.kt:37, 39; simple/SimpleBarChart.kt:34; simple/SimpleLineChart.kt:42 |
| B9 | example 2: `LineChartData(series)` / `LineChartSeries(dataName, lineColor, listOfPoints)` / `LineChart(lineChartData, modifier, xAxisLabel, yAxisLabel, overlayHeaderLabel)` with `as Long` and `as Number` casts | SUPPORTED | signatures at line/LineChartSeries.kt:36-42, 90-91; line/LineChart.kt:82-93. Runtime types: x label gets `Long` (208), y label `Number` (grid/ChartAxis.kt:17, 30), heading `Long` (line/LineChartOverlayInformation.kt:51) |
| B10 | `LineChartPoint.x` is a `Long` in epoch ms, `y` a `Float` | SUPPORTED | line/LineChartSeries.kt:12, 17-18 |
| B11 | label parameters are composable lambdas; `GridDefaults` stock labels are there to be wrapped | SUPPORTED | grid/GridDefaults.kt:15-23, 31-64 |
| B12 | empty series, empty series list, empty category, empty category list are accepted and draw nothing or an empty grid | SUPPORTED | Traced. Empty series: line/Line.kt:27. Empty series list: bounds 0 (line/LineChartSeries.kt:118-121), `YAxisScale` widens 0..0 to 0..10 (YAxisScale.kt:42-44), `TimestampXAxisScale(0,0)` yields no ticks (first tick 1000 > max). Empty category: bar/BarChartCategory.kt:21, 24. Empty category list: bar/BarChartData.kt:24-25, 31-32, 38-39; bar/BarChartDrawing.kt:21 returns early; bar/BarChart.kt:224 gives 0. Side effect not mentioned: an empty series beside a real one adds timestamp 0 to the x-range (LineChartSeries.kt:66-67, 113-114), stretching the axis back to the epoch |
| B13 | example 3: `PercentageDial(Int)`, `Dial(Int×3)`, `GasBottle(Float 0..100)`, `ArcProgressBar(Float 0..1)`, `SimpleLineChart(List<Float>)`, `PieChartWithLegend(pieChartData, modifier, config)`; finite thickness makes a donut | SUPPORTED | the eight cited lines; pie/PieChart.kt:132 |
| B14 | every `animation` parameter defaults to `ChartAnimation.Simple()`, a 300 ms tween after a 100 ms delay | SUPPORTED | all nine composables with the parameter; ChartAnimation.kt:23-25, 42-43 |
| B15 | `Simple` and `Sequenced` take a lambda returning the spec | SUPPORTED | ChartAnimation.kt:23, 36 |
| B16 | `ChartAnimation.Disabled` is an object and draws at once | SUPPORTED | ChartAnimation.kt:14-15; Helpers.kt:121. (`PieChart` is the exception, which the skill covers under T5) |
| B17 | `Sequenced` staggers line series, bubbles, and bars by position within a category | SUPPORTED | line/LineChart.kt:110-114; bubble/BubbleChart.kt:69-73; bar/BarChart.kt:85-89; bar/BarChartDrawing.kt:35 |

### T. Invariants and traps (81 cited, 3 uncited, in 19 paragraphs)

| id | claim | verdict | evidence |
|---|---|---|---|
| T1a | `LocalChartColors` defaults every colour to `Color.Unspecified` | SUPPORTED | theme/Theme.kt:18-27 |
| T1b | nothing falls back to the Material theme unless a palette is provided | SUPPORTED | theme/Theme.kt:29-33; every `colors` default reads `ChartTheme.colors` |
| T1c | symptom: themed parts missing or wrong colour (grid on bar and line, arc and scale of a dial) while data-coloured bars and lines still appear | SUPPORTED | bar/BarChartColors.kt:20-24; dial/DialColors.kt:22-27; bar/BarChartDrawing.kt:48; line/Line.kt:50. Worded loosely enough to follow from the source |
| T2a | `chartColors()` leaves `surface` as `Color.Unspecified` | SUPPORTED | theme/ChartDefaults.kt:32 |
| T2b | `surface` is the only thing drawn behind the balloon on bar and line charts | SUPPORTED | OverlayInformation.kt:46-50; bar/BarChart.kt:210; line/LineChartOverlayInformation.kt:39 |
| T2c | symptom: the balloon's text sits straight on top of the plot | OVERSTATED (rule) | What `background(Color.Unspecified)` paints is Compose behaviour; the source shows only that this is the value passed |
| T3a | `GasBottle` takes a `Float` 0 to 100 and clamps | SUPPORTED | gasbottle/GasBottle.kt:43, 89 |
| T3b | `ArcProgressBar` takes a `Float` 0 to 1 and clamps | SUPPORTED | simple/ArcProgressBar.kt:35, 44 |
| T3c | `Dial` and `PercentageDial` take `Int` | SUPPORTED | dial/Dial.kt:63-65; dial/PercentageDial.kt:17 |
| T3d | a 0-to-1 fraction handed to `GasBottle`: a bottle that always looks empty | SUPPORTED | Traced: 0.72 maps to under 1 % of the fill range (GasBottle.kt:96-97); only a sliver and the surface oval are drawn (109-140). Exactly 1.0 is drawn as 5 % (90). "Nearly empty" would be exact |
| T3e | a percentage handed to `ArcProgressBar`: full for any value of 1 or more | SUPPORTED | simple/ArcProgressBar.kt:44 |
| T3f | `GasBottle` draws anything from 1 to 5 as 5 | SUPPORTED | gasbottle/GasBottle.kt:90 |
| T4a | `PieChart`, `Dial`, `GasBottle` throw `UnsupportedOperationException` during composition for `Sequenced` | SUPPORTED | pie/PieChart.kt:72-74; dial/Dial.kt:84-86; gasbottle/GasBottle.kt:59-61 |
| T4b | so do `PercentageDial` and `PieChartWithLegend` | SUPPORTED | dial/PercentageDial.kt:30; pie/PieChartWithLegend.kt:39, 59 |
| T4c | the message says `Sequenced` is not supported | SUPPORTED | the three message strings |
| T5a | `PieChart` with `Disabled`: the full sweep is `1f` where the animated branch runs to 360 | SUPPORTED | pie/PieChart.kt:63-65 against 68; pie/PieDefaults.kt:20 |
| T5b | every segment's angle is scaled to that total | SUPPORTED | pie/PieChart.kt:81-87, 115-123, then passed unscaled as degrees at 105 |
| T5c | symptom: an almost empty square with a hairline sliver; `Simple` is the mode that draws a whole pie | SUPPORTED | pie/PieChart.kt:93 (square), 100-109; `Sequenced` throws |
| T5d | the same holds for `PieChartWithLegend` | SUPPORTED | pie/PieChartWithLegend.kt:39, 59 |
| T6a | the flag is created afresh as "not played" per data value; the effect that sets it is keyed to `Unit` and runs once | SUPPORTED | Helpers.kt:118-128 |
| T6b | "Read as written", after data changes to an unequal value under `Simple` or `Sequenced` the chart animates back to its start and stays: bars to zero height, lines and bubbles faded, dial, bottle or pie emptied, grid and labels still showing | SUPPORTED (hand trace) | New `MutableState(false)` from `remember(data)` (120-122); `LaunchedEffect(Unit)` (123) does not relaunch, and its first run set the old state object. Targets then go to 0: bar/BarChart.kt:81, 87; line/LineChart.kt:106, 112; bubble/BubbleChart.kt:65, 71; dial/Dial.kt:80; gasbottle/GasBottle.kt:55; pie/PieChart.kt:68. The grid does not depend on the flag. This contradicts the KDoc at ChartAnimation.kt:7 ("or when its data changes"); the code is the authority. Depends on the meaning of `remember` and `LaunchedEffect` keys, which is the contract the code is written against. Not runtime-verified |
| T6c | `Disabled` starts the flag as played and is unaffected | SUPPORTED | Helpers.kt:121; every `Disabled` branch ignores the flag |
| T6d | [uncited] a chart that re-enters the composition, for example under `key(data) { … }`, runs the effect again | CANNOT-TELL (rule) | `key` is not used anywhere in the source; this is Compose behaviour |
| T6e | `Dial` and `GasBottle` key the flag on the value itself | SUPPORTED | dial/Dial.kt:73; gasbottle/GasBottle.kt:48 |
| T7a | `xAxisLabel` and `overlayHeaderLabel` default to `GridDefaults.NoLabel`, which draws nothing | SUPPORTED | line/LineChart.kt:86, 88; grid/GridDefaults.kt:66-73 |
| T7b | symptom: blank x-axis, balloon with no heading | SUPPORTED | line/LineChart.kt:208; line/LineChartOverlayInformation.kt:51 |
| T7c | the label lambdas are typed `(value: Any)`, so the value needs a cast | SUPPORTED | line/LineChart.kt:86-89. Read within the line-chart paragraph it holds; across the library the legend label is `(String)` and the dial, pie and bubble labels are typed |
| T7d | x label and heading get a `Long`, y label a `Number`, balloon entry a name and a `Float` | SUPPORTED | line/LineChart.kt:208; line/LineChartOverlayInformation.kt:31-32, 51, 76; grid/ChartAxis.kt:17, 30 |
| T7e | on `BarChart` the x label receives the category name as a `String` | SUPPORTED | bar/BarChart.kt:158, 175, 186-188 |
| T7f | time ticks fall on round times from the epoch, which is UTC | SUPPORTED | grid/axisscale/TimestampXAxisScale.kt:11-13, 36-38 |
| T8a | both grid charts round the y bounds outward to a multiple of `roundMinMaxClosestTo`, default 10 | SUPPORTED | YAxisScale.kt:37-41; grid/GridDefaults.kt:29; line/LineChart.kt:93, 138; bar/BarChartConfig.kt:24; bar/BarChart.kt:132 |
| T8b | data between 0 and 1 gets an axis from 0 to 10 | SUPPORTED | YAxisScale.kt:40-41: floor(0.x/10)·10 = 0, ceil(0.x/10)·10 = 10 |
| T8c | symptom: a line hugging the bottom, bars a tenth of the expected height | SUPPORTED | follows from T8b through grid/GridChartDrawing.kt:159-164 |
| T8d | the setting is an `Int`; below 1 is treated as 1 | SUPPORTED | YAxisScale.kt:30, 37 |
| T8e | `LineChartWithLegend` has no such parameter; inside it is a `Column` of `LineChart` and `ChartLegend(lineChartData.legendData)` | SUPPORTED | line/LineChartWithLegend.kt:21-52 |
| T9a | first parameter is `data` on `BarChart`, `BarChartWithLegend`, `PieChart`; `lineChartData` on both line charts; `pieChartData` on `PieChartWithLegend`; `bubbles` on `BubbleChart` | SUPPORTED | the seven cited lines |
| T9b | after `modifier`, `BarChart` continues `colors, config`, `BarChartWithLegend` continues `animation, colors` | SUPPORTED | bar/BarChart.kt:60-61; bar/BarChartWithLegend.kt:23-24 |
| T9c | symptom: no parameter named `data`, or type mismatches with positional arguments; named arguments avoid both | SUPPORTED | follows from the signatures. The quoted compiler message is not in the source |
| T9d | `BarChartWithLegend` has no `overlayDataEntryLabel` | SUPPORTED | bar/BarChartWithLegend.kt:20-29, 31-39 |
| T10a | the cluster label is `BarChartCategory.name`; `x` names the series; entries sharing an `x` become one legend entry, coloured from the first found | SUPPORTED | bar/BarChartCategory.kt:8; bar/BarChartEntry.kt:9-10; bar/BarChartData.kt:46-56 |
| T10b | symptom of putting the category label in `x`: a legend with one entry per bar | OVERSTATED | `distinctBy { it.x }` (bar/BarChartData.kt:48) gives one entry per distinct `x`, which is one per category. That is one per bar only when each cluster has a single bar |
| T10c | `BarChartConfig.thickness` is a maximum; bars narrow to fit | SUPPORTED | bar/BarChartDrawing.kt:22-23. The KDoc at bar/BarChartConfig.kt:10 says "Width of a single bar"; the code is as the skill says |
| T11a | the range is found by sorting, the curve is drawn in list order | SUPPORTED | line/LineChartSeries.kt:71-79; line/Line.kt:32-45, 87 |
| T11b | symptom of unsorted points: a line that doubles back | SUPPORTED | follows from T11a |
| T11c | shading is `fillColor`, default the line colour; `Color.Transparent` leaves a series unshaded | SUPPORTED | line/LineChartSeries.kt:40; line/Line.kt:65-66; Helpers.kt:110-116 |
| T11d | balloon value is interpolated linearly between the two neighbouring samples; a series with no sample on both sides is left out | SUPPORTED | line/LineChartOverlayInformation.kt:115-140, 147-149 |
| T12a | the three `simple` composables draw on a `Canvas` with only the caller's modifier | SUPPORTED | simple/ArcProgressBar.kt:46; simple/SimpleBarChart.kt:45; simple/SimpleLineChart.kt:70 |
| T12b | symptom with the default `Modifier`: nothing appears | OVERSTATED (rule) | The source shows no size is set. That the result is a zero-sized canvas is Compose layout behaviour and depends on the parent's constraints |
| T12c | every preview in the source passes a size | SUPPORTED | all 7 previews in ArcProgressBar.kt, 7 in SimpleBarChart.kt, 13 in SimpleLineChart.kt |
| T12d | `BarChart` and `LineChart` fill what the modifier allows and give the plot the height left over | SUPPORTED | bar/BarChart.kt:101, 103, 107; line/LineChart.kt:127, 131 |
| T12e | the legends are lazy vertical grids | SUPPORTED | line/ChartLegend.kt:53; pie/PieChartLegend.kt:65 |
| T12f | [uncited] which need a bounded height to be measured, so a `WithLegend` chart wants a fixed or bounded height | CANNOT-TELL (rule) | Compose behaviour; nothing in the source states or tests it |
| T12g | `PieChart` forces itself square, `Dial` is 2:1, `BubbleChart` uses a square of the smaller side | SUPPORTED | pie/PieChart.kt:93; dial/Dial.kt:35, 96; bubble/BubbleChart.kt:78, 91, 98. (The dial's 2:1 is its arc; the min and max labels sit in a row beneath, 128-135) |
| T13a | "It listens on the initial pass and consumes every change it sees, so anything else handling the same gesture receives it already consumed" | OVERSTATED | Initial pass: line/LineChart.kt:165. But `event.changes.any { it.consume(); true }` (178-181) stops at the first element, so only the first change is consumed; with one pointer that is all of them. What other handlers then see is Compose pointer-pass behaviour (rule) |
| T13b | the balloon shows only for move events inside the plot and is hidden by any other event | SUPPORTED | line/LineChart.kt:167-176, 216-233; line/LineChartOverlayInformation.kt:34 |
| T13c | symptom: a tap shows nothing; a drag or mouse hover does | SUPPORTED | follows from T13b |
| T13d | `BarChart` reads pointer events without consuming and selects the bar under any of them | SUPPORTED | bar/BarChart.kt:109-126 |
| T14a | `PieChartConfig.thickness` defaults to `Dp.Infinity`, capped at half the smaller side, which fills the circle | SUPPORTED | pie/PieDefaults.kt:23; pie/PieChart.kt:131-144 |
| T14b | symptom: a full pie where a donut was expected; a finite thickness makes the ring | SUPPORTED | as T14a. The KDoc at pie/PieChart.kt:41 calls it "Donut shaped"; the default is solid |
| T14c | `HORIZONTAL` puts the legend under the chart in `numberOfColsInLegend` columns, `VERTICAL` to the right in one column | SUPPORTED | pie/PieChartConfig.kt:14-19; pie/PieChartLegend.kt:61-64; pie/PieChartWithLegend.kt:30-69 |
| T14d | `PieChartData.value` is a `Double`; bar and line values are `Float` | SUPPORTED | pie/PieChart.kt:38; bar/BarChartEntry.kt:17; line/LineChartSeries.kt:18 |
| T15a | a value outside the range is drawn at the nearer end; `mainLabel` still gets the raw value | SUPPORTED | dial/Dial.kt:43-46, 89, 125 |
| T15b | the tick scale is always the same fifty divisions whatever the range | SUPPORTED | dial/Dial.kt:197, 211 (`0..100 step 2`, no use of min or max) |
| T15c | `DialConfig(displayScale = false)` removes it | SUPPORTED | dial/Dial.kt:107; dial/DialConfig.kt:21 |
| T16a | `Bubble.icon` is a required `ImageVector` | SUPPORTED | bubble/Bubble.kt:26 |
| T16b | `radius` defaults to `value`; only the ratios matter; before packing radii are mapped from 40 dp for the smallest to a quarter of the side for the largest | SUPPORTED | bubble/Bubble.kt:28; bubble/BubbleDefaults.kt:29; bubble/BubbleChart.kt:78-90, 126-136, 147 |
| T16c | under 160 dp on the shorter side the quarter is below the minimum, so the mapping runs backwards: the largest value is the smallest bubble | SUPPORTED | Traced: `size / 4f < 40f` when `size < 160` (bubble/BubbleChart.kt:78, 87-88); `mapValueToDifferentRange(smallest, largest, 40, <40)` (129-134) then descends; the later uniform rescale (bubble/BubblePacking.kt:144-152) keeps the order |
| T16d | the chart lays out copies, so a `Bubble` you hold never reports where it was drawn | SUPPORTED | bubble/Bubble.kt:10-13; bubble/BubbleChart.kt:83-90, 136 |
| T16e | the copy handed to a custom `bubbleLabel` has `radius` replaced by the drawn radius while `value` is yours | SUPPORTED | bubble/BubbleChart.kt:101-105, 136, 147; bubble/BubblePacking.kt:151 |
| T16f | the layout is seeded at random | SUPPORTED | bubble/BubbleChart.kt:38-39; bubble/BubblePacking.kt:99-101, 104 |
| T16g | `BubbleChartPreview` and `bubbleChartSampleData` are public and produce random data | SUPPORTED | bubble/BubbleChart.kt:159-203 |
| T17a | `SimpleBarChart` keeps zero as the floor and draws nothing for zero or negative values | SUPPORTED | simple/SimpleBarChart.kt:46-49, 62, 67 |
| T17b | `SimpleLineChart` stretches the data's own minimum and maximum over the full height | SUPPORTED | simple/SimpleLineChart.kt:73-79, 100 |
| T17c | with `adaptToData = false` both expect 0 to 1 and clamp | SUPPORTED | simple/SimpleBarChart.kt:51, 62; simple/SimpleLineChart.kt:76, 100 |
| T17d | symptom of raw values: every bar full height, or a flat line along the top | SUPPORTED | follows from T17c for values of 1 or more |
| T17e | a single point is drawn as a dashed horizontal line | SUPPORTED | simple/SimpleLineChart.kt:53-67 |
| T17f | `extendAndClipEnds` extends the line half a stroke past each edge | SUPPORTED | simple/SimpleLineChart.kt:85-90 |
| T17g | [uncited] nothing in the function clips it | TRUE | no `clip*` call in the file; the KDoc at 34-36 says "clipped", the code does not |
| T18a | `resources.Res` is a public generated accessor whose `drawable`, `string`, `array`, `plurals`, `font` objects are empty, on every platform | SUPPORTED | resources/Res.kt:17, 38-46; all five `ActualResourceCollectors.kt` build empty maps (14-37) |
| T18b | symptom of importing it in place of an app's own `Res`: unresolved references | SUPPORTED | follows from T18a |
| T19a | `round` returns a `String`: drops a trailing `.0`, returns an integral type's `toString()`, returns `"-"` when the value cannot be rounded | SUPPORTED | Helpers.kt:82-97, 107-108. The skill's wording is more accurate than the KDoc ("not a finite number"): only `NaN` throws in `roundToInt` |
| T19b | the value is scaled by 10^decimals and passed through `roundToInt()`, so beyond `Int` range at that scale (about 21 million at two decimals) it does not come back as itself | SUPPORTED | Helpers.kt:87. 2^31 / 100 ≈ 21.47 million |
| T19c | "The default labels do not use it; they print `value.toString()`." | OVERSTATED | True of `GridDefaults` (grid/GridDefaults.kt:36, 44, 54, 61) and `DialDefaults` (dial/DialDefaults.kt:28, 33). The source shows otherwise for two defaults: pie/PieDefaults.kt:36 and bubble/BubbleDefaults.kt:47 call `round(1)` |
| T19d | `mapValueToDifferentRange` divides by zero when the input bounds are equal; the `Long`-to-`Long` overload truncates | SUPPORTED | Helpers.kt:28, 37, 42-43, 55 |

### M. What moved (7 cited, 2 uncited)

| id | claim | verdict | evidence |
|---|---|---|---|
| M1 | [uncited] no `@Deprecated`, no `ReplaceWith`; earlier versions cannot be told from the source | TRUE | grep: none. A few comments hint at past behaviour (bar/BarChart.kt:221-222; TimestampXAxisScale.kt:34), none at a rename |
| M2 | no theme wrapper composable; `ChartTheme` is internal; palette goes in through `CompositionLocalProvider` | SUPPORTED | theme/Theme.kt:13, 29 |
| M3 | `ChartLegend`, `LegendItemData`, `SymbolShape` live in `line`; `ChartLegend`'s `config` is a `BarChartConfig` | SUPPORTED | line/ChartLegend.kt:1, 50, 143, 157; bar/BarChartData.kt:5-6, 52 |
| M4 | no public pie legend; `PieChartLegend` is internal | SUPPORTED | pie/PieChartLegend.kt:33 |
| M5 | enum constants are upper case; `LegendIcon.CAKE` is the default | SUPPORTED | pie/PieChartConfig.kt:26, 33-44; line/ChartLegend.kt:157-162 |
| M6 | `LineChartPoint(x: Long, y: Float)`; bounds spelled `minTimestamp` and `maxTimeStamp` | SUPPORTED | line/LineChartSeries.kt:16-19, 49, 51 |
| M7 | `lineColor` has no default and sits after a defaulted parameter | SUPPORTED | line/LineChartSeries.kt:38-39 |
| M8 | the dial is `Dial(value, minValue, maxValue)` in `Int` | SUPPORTED | dial/Dial.kt:62-65 |
| M9 | [uncited] there is no `Float` or 0-to-1 overload | TRUE | one `Dial`, one `PercentageDial` |

### N. What it is not for (3 cited, 4 uncited, in 5 boundaries)

| id | claim | verdict | evidence |
|---|---|---|---|
| N0 | [uncited] where the maintainers draw the line is not written in the source | TRUE | no such statement found beyond per-chart KDoc |
| N1 | [uncited] no scatter, stacked or horizontal bar, radar or candlestick chart | TRUE | the file list is the whole library |
| N2a | `LineChart` always scales its x-axis as epoch milliseconds | SUPPORTED | line/LineChart.kt:141-146 |
| N2b | `SimpleLineChart` spaces points evenly by index | SUPPORTED | simple/SimpleLineChart.kt:26, 96-99 |
| N3 | [uncited] no zooming, panning, scrolling, axis titles, or selection callback; pointer handling only shows the balloon | TRUE | the only `pointerInput` blocks are line/LineChart.kt:162 and bar/BarChart.kt:109; no function-typed event parameter on any chart |
| N4 | `PieChart` draws no labels; `GasBottle` never shows the number | SUPPORTED | pie/PieChart.kt:43; gasbottle/GasBottle.kt:28-30 |
| N5 | [uncited] not for rendering outside a composition, or being called from Swift or JavaScript | CANNOT-TELL (rule) | Every chart is `@Composable` and nothing is `@JsExport`ed, which the source shows. Whether Swift can reach it is a packaging fact (see A22). The authored skill says the same |

## Every claim that is not SUPPORTED or TRUE

| id | claim | citation | verdict | evidence |
|---|---|---|---|---|
| A15 | "pushed apart until they stop colliding" | bubble/BubblePacking.kt:12 | OVERSTATED | comment says so; code stops on a counter or a 5000-pass timeout (BubblePacking.kt:77, 107, 116-117); default spacing −10 and KDoc "can overlap" (BubbleChart.kt:53, 30) |
| A22 | "Called from Kotlin only … there is no Swift or JavaScript surface." | bar/BarChart.kt:56 and the uncited absence | OVERSTATED (rule) | composables and no export annotation shown; Swift reach is packaging; public non-composable API exists (Helpers.kt:82; YAxisScale.kt:26) |
| T2c | "Symptom: the balloon's text sits straight on top of the plot." | theme/ChartDefaults.kt:32; OverlayInformation.kt:49 | OVERSTATED (rule) | mechanism shown; what `background(Color.Unspecified)` paints is Compose behaviour |
| T10b | "Symptom of putting the category label in `x`: a legend with one entry per bar." | bar/BarChartData.kt:48 | OVERSTATED | `distinctBy { it.x }` gives one per category; per bar only with single-bar clusters |
| T12b | "Symptom with the default `Modifier`: nothing appears" | simple/ArcProgressBar.kt:46 and two more | OVERSTATED (rule) | no size is set, as shown; the zero-size outcome is Compose layout and depends on the parent |
| T13a | "consumes every change it sees, so anything else handling the same gesture receives it already consumed" | line/LineChart.kt:165, 179 | OVERSTATED | `changes.any { it.consume(); true }` consumes only the first change (LineChart.kt:178-181); the effect on other handlers is Compose behaviour |
| T19c | "The default labels do not use it; they print `value.toString()`." | grid/GridDefaults.kt:36 | OVERSTATED | pie/PieDefaults.kt:36 and bubble/BubbleDefaults.kt:47 call `round(1)` |
| T6d | "a chart that re-enters the composition, for example under `key(data) { … }`, runs the effect again" | [uncited] | CANNOT-TELL (rule) | `key` appears nowhere in the source |
| T12f | "lazy vertical grids, which need a bounded height to be measured, so a `WithLegend` chart wants a fixed or bounded height" | [uncited] | CANNOT-TELL (rule) | the grids are there (line/ChartLegend.kt:53; pie/PieChartLegend.kt:65); the requirement is Compose's |
| N5 | "Rendering outside a Compose composition, or being called from Swift or JavaScript." | [uncited] | CANNOT-TELL (rule) | composables and no `@JsExport` shown; Swift reach is packaging |

## Traps and boundaries

The derived skill has 19 trap paragraphs (T1 to T19) and 5 boundaries (N1 to N5): 24.

A paragraph counts as SUPPORTED only when every claim in it is SUPPORTED or TRUE.

- Fully supported: 17 of 24. Traps T1, T3, T4, T5, T7, T8, T9, T11, T14, T15, T16, T17, T18 (13) and boundaries N1, N2, N3, N4 (4).
- Central mechanism supported, one part flagged: 6 traps. T2 (symptom, rule), T6 (the `key(data)` remedy, rule), T10 (one symptom), T12 (one symptom and the lazy-grid height, rule), T13 ("every change"), T19 ("default labels do not use it").
- Not decidable from the source: boundary N5.
- CONTRADICTED: none. The closest is T19c, a generalization inside the `Number.round` trap that two default labels falsify; the trap's own point (returns a `String`, works through an `Int`) stands.

The six `What moved` bullets (M2 to M8), which the skill offers as places "where the obvious guess is wrong", are all SUPPORTED or TRUE. They are not counted in the 24.

## Part 2 — against the authored skill

### Authored traps and boundaries, and whether the derived skill has them

| # | authored point | in derived? | note |
|---|---|---|---|
| AT1 | a palette is not optional; `LocalChartColors` defaults to `Color.Unspecified` | yes (T1) | |
| AT2 | `LineChart` draws no time labels unless passed; `Long` epoch ms; ticks on round UTC times | yes (T7) | |
| AT3 | y-axis rounds out to multiples of 10; `Int`, below 1 is 1; `LineChartWithLegend` has no parameter | yes (T8) | derived adds how to write the legend variant out by hand |
| AT4 | `Sequenced` throws on `Dial`, `PercentageDial`, `GasBottle`, `PieChart`, `PieChartWithLegend` | yes (T4) | the two disagree on the remedy; see below |
| AT5 | percentage charts do not share a scale; all clamp; `GasBottle` draws 1 to 5 as 5 | yes (T3, T15) | |
| AT6 | per-chart colour classes have no default arguments; narrow a full palette instead | **no** | source confirms the authored point: bar/BarChartColors.kt:14-17; line/LineChartColors.kt:15-19; dial/DialColors.kt:15-19; gasbottle/GasBottleColors.kt:14-17 |
| AT7 | a bubble's size comes from `radius`, not `value`; largest at maximum, smallest at minimum | yes (T16) | derived omits the single-bubble and equal-radii cases (bubble/BubbleChart.kt:126-127) |
| AT8 | a `Bubble`'s `position` is not where it was drawn; layout seeded at random | yes (T16) | |
| AT9 | the simple charts ignore the palette; `adaptToData = false` expects 0 to 1 | yes (B8, T17) | |
| AT10 | `BubbleChartPreview()` and `bubbleChartSampleData()` generate random data | yes (T16) | |
| AB1 | called from Kotlin only | yes (A22, N5) | derived cannot speak to what is published |
| AB2 | draws charts, does not analyse data: no aggregation, downsampling, statistics, date bucketing, currency formatting | **no** | consistent with the source |
| AB3 | not an interactive toolkit: tooltip only, no zoom, pan, selection, export | yes (N3) | derived omits image export |
| AB4 | exposes no accessibility semantics | **no** | source confirms: no `semantics` anywhere; bubble/BubbleDefaults.kt:38 sets `contentDescription = null` |

Authored total 14 (10 traps, 4 boundaries). Found 11 (9 traps, 2 boundaries). Missed 3: AT6, AB2, AB4.

The authored usage section also says `BarChartWithLegend` has no `overlayDataEntryLabel` and `LineChartWithLegend` no `roundMinMaxClosestTo`; the derived skill has both (T9, T8). The authored `What moved` section (behaviour against 0.0.1, the fork and its old coordinates, `*Defaults` made public) is history the source does not carry, and the derived skill says as much (M1).

### Traps and boundaries only the derived skill has

Twelve trap paragraphs have no counterpart in the authored skill. The source confirms the central point of all twelve; six carry no reservation.

| trap | Part 1 verdict |
|---|---|
| T2 `chartColors()` leaves `surface` unspecified, so the balloon has no background | mechanism SUPPORTED; symptom OVERSTATED (rule) |
| T5 `PieChart` with `Disabled` draws a one-degree pie | SUPPORTED |
| T6 new data under `Simple` or `Sequenced` leaves the chart animated out | SUPPORTED by hand trace; remedy CANNOT-TELL; contradicts the KDoc at ChartAnimation.kt:7 |
| T9 first parameter named differently per chart; `WithLegend` reorders | SUPPORTED |
| T10 `BarChartEntry.x` is a series name; `thickness` is a maximum | SUPPORTED but for one symptom (OVERSTATED) |
| T11 line points joined in list order; fill colour; balloon interpolation | SUPPORTED |
| T12 size comes from the caller | facts SUPPORTED; "nothing appears" OVERSTATED (rule); lazy-grid height CANNOT-TELL (rule) |
| T13 `LineChart` consumes pointer events; balloon on movement only | SUPPORTED but for "every change" (OVERSTATED) |
| T14 a pie is solid unless given a thickness | SUPPORTED |
| T15 a dial's arc clamps but its label does not; ticks are not its range | SUPPORTED |
| T18 the library's `Res` is public and empty | SUPPORTED |
| T19 `Number.round` returns a `String` and works through an `Int` | SUPPORTED but for "default labels do not use it" (OVERSTATED) |

Derived-only points inside shared traps, all SUPPORTED: a bubble chart under 160 dp inverts sizes (T16c); the label handed to `bubbleLabel` carries the drawn radius (T16e); `SimpleBarChart` floors at zero while `SimpleLineChart` stretches min to max, and `extendAndClipEnds` does not clip (T17). Derived-only boundaries: N1 (TRUE), N2 (SUPPORTED), N4 (SUPPORTED). The six naming bullets M2 to M8 are also derived-only and all SUPPORTED or TRUE.

### Disagreements

| # | derived | authored | source supports |
|---|---|---|---|
| D1 | `PieChart` with `ChartAnimation.Disabled` draws a one-degree pie; `Simple` is the only mode that draws a whole one (T5) | for the charts that reject `Sequenced`, "use `ChartAnimation.Simple()` or `ChartAnimation.Disabled`" (AT4) | **derived.** pie/PieChart.kt:63-65 sets the total sweep to `1f`, 115-123 scales every segment to it, 105 draws it as degrees. The authored remedy is right for `Dial` and `GasBottle`, wrong for `PieChart` and `PieChartWithLegend` |
| D2 | "The default labels do not use it; they print `value.toString()`." (T19c) | `Number.round` "shows in the default bubble and pie value labels" | **authored** for pie and bubble: pie/PieDefaults.kt:36; bubble/BubbleDefaults.kt:47. Derived is right only for the grid and dial labels |
| D3 (implied) | `chartColors()` leaves `surface` unspecified, so the balloon still has no background (T2) | lists an invisible tooltip as a symptom of having no palette, and its example provides `ChartDefaults.chartColors()` with no arguments as the cure | **derived** on the mechanism: theme/ChartDefaults.kt:32. Providing the default palette does not give the balloon a background |

Not a disagreement between the two skills, but between the derived skill and the library's own KDoc: T6 against ChartAnimation.kt:7, T10c against bar/BarChartConfig.kt:10, T14 against pie/PieChart.kt:41, T17g against simple/SimpleLineChart.kt:34-36. In each the code is as the derived skill says.
