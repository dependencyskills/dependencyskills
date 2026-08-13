# Conformance

How an implementation demonstrates it follows `spec/` rather than claiming
to.

The harness shells out to whichever implementations are present and runs
them against `fixtures/`. The test worth having is cross-implementation:
publish with one, consume with another, and confirm the result. That test
is the reason this is a monorepo, and it cannot exist across separate ones.

Build it while there is one implementation, not after there are three —
it is what stops the second one quietly diverging.
