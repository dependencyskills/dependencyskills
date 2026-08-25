# Corpus manifest — the harvest this project's measurements rest on

`corpus.json` is a derived artifact and stays gitignored: it is 9.7 MB and rebuildable. What must
not be lost is *what it was built from*. This manifest is that, and it exists for a specific
threat: the negative class in any future classifier is real library documentation, and real
library documentation is written by anyone who can publish a package.

**Why this is a weaker exposure than it first appears.** Every coordinate below is pinned to an
exact version, and a published Maven version is immutable — it cannot be replaced after the fact.
So this harvest cannot be poisoned retroactively; only a *future* harvest of *newer* versions
could be. Indexing only declared dependencies raises the bar further: a poisoned library has to
be genuinely depended upon, not merely published.

**How to use it.** Re-fetch these exact coordinates and re-harvest; the content hash below should
reproduce. A mismatch means the upstream artifacts changed, which for immutable coordinates is
itself the finding.

- entries: **14899**
- distinct libraries: **59**
- `corpus.json` sha256: `d4079f488f5ab36583bd1b4081414fe89485510ee53fe9e432ace0aae562914b`
- bytes: 9760635

| entries | coordinate |
|---:|---|
| 7276 | `org.jetbrains.kotlin:kotlin-stdlib:2.4.10` |
| 800 | `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0` |
| 722 | `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0` |
| 532 | `io.ktor:ktor-server-core-jvm:3.5.2` |
| 449 | `io.ktor:ktor-client-core-jvm:3.5.2` |
| 441 | `io.ktor:ktor-client-core:3.5.2` |
| 429 | `io.ktor:ktor-server-core:3.5.2` |
| 315 | `io.ktor:ktor-http-jvm:3.5.2` |
| 315 | `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0` |
| 303 | `org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0` |
| 293 | `io.ktor:ktor-http:3.5.2` |
| 273 | `org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0` |
| 273 | `org.jetbrains.kotlinx:kotlinx-html:0.12.0` |
| 265 | `io.ktor:ktor-utils-jvm:3.5.2` |
| 246 | `io.ktor:ktor-utils:3.5.2` |
| 189 | `org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.1` |
| 187 | `org.jetbrains.kotlinx:kotlinx-io-core:0.9.1` |
| 145 | `org.jetbrains.kotlin:kotlin-reflect:2.3.21` |
| 134 | `io.ktor:ktor-io-jvm:3.5.2` |
| 132 | `io.ktor:ktor-io:3.5.2` |
| 95 | `io.ktor:ktor-network-jvm:3.5.2` |
| 95 | `org.jetbrains.kotlin:kotlin-test:2.4.10` |
| 85 | `io.ktor:ktor-websockets-jvm:3.5.2` |
| 84 | `io.ktor:ktor-websockets:3.5.2` |
| 81 | `io.ktor:ktor-network:3.5.2` |
| 72 | `io.ktor:ktor-http-cio:3.5.2` |
| 64 | `io.ktor:ktor-http-cio-jvm:3.5.2` |
| 60 | `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.1` |
| 55 | `org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0` |
| 55 | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0` |
| 55 | `org.jetbrains.kotlinx:kotlinx-io-bytestring:0.9.1` |
| 51 | `io.ktor:ktor-network-tls-jvm:3.5.2` |
| 44 | `io.ktor:ktor-server-test-host-jvm:3.5.2` |
| 40 | `io.ktor:ktor-server-netty-jvm:3.5.2` |
| 34 | `org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.11.0` |
| 30 | `io.ktor:ktor-network-tls:3.5.2` |
| 18 | `io.ktor:ktor-server-websockets-jvm:3.5.2` |
| 18 | `io.ktor:ktor-server-websockets:3.5.2` |
| 14 | `io.ktor:ktor-client-cio-jvm:3.5.2` |
| 13 | `io.ktor:ktor-network-tls-certificates-jvm:3.5.2` |
| 12 | `io.ktor:ktor-serialization-jvm:3.5.2` |
| 12 | `io.ktor:ktor-serialization:3.5.2` |
| 11 | `io.ktor:ktor-server-html-builder-jvm:3.5.2` |
| 11 | `io.ktor:ktor-server-html-builder:3.5.2` |
| 10 | `io.ktor:ktor-client-cio:3.5.2` |
| 8 | `io.ktor:ktor-server-call-logging-jvm:3.5.2` |
| 6 | `io.ktor:ktor-client-apache5-jvm:3.5.2` |
| 6 | `org.jetbrains.kotlin:kotlin-test-junit:2.4.10` |
| 5 | `io.ktor:ktor-events-jvm:3.5.2` |
| 5 | `io.ktor:ktor-events:3.5.2` |
| 4 | `io.ktor:ktor-server-default-headers-jvm:3.5.2` |
| 4 | `io.ktor:ktor-server-default-headers:3.5.2` |
| 4 | `io.ktor:ktor-sse-jvm:3.5.2` |
| 4 | `io.ktor:ktor-sse:3.5.2` |
| 4 | `io.ktor:ktor-websocket-serialization-jvm:3.5.2` |
| 4 | `io.ktor:ktor-websocket-serialization:3.5.2` |
| 3 | `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.11.0` |
| 2 | `io.ktor:ktor-test-dispatcher-jvm:3.5.2` |
| 2 | `io.ktor:ktor-test-dispatcher:3.5.2` |
