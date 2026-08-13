# Fixtures

Shared test material, used by `conformance/` and by any implementation's
own tests.

Sample skills that are valid, sample archives with the expected layout, and
— at least as important — malformed cases: a skill whose `name` disagrees
with its directory, a description over the limit, a path that is legal on
macOS and illegal on Linux, an archive declaring a location it does not
contain.

Fixtures belong to the spec, not to any one implementation. If a fixture
only makes sense for one of them, it is probably a unit test in that
implementation instead.
