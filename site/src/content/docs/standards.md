---
title: Adopted Standards
description: The standards this project adopts rather than reinventing.
---

This project does not invent a skill format. It **adopts** the
[Agent Skills standard](https://agentskills.io): a skill is a directory holding
a `SKILL.md` with `name` and `description`, optionally alongside `scripts/`,
`references/` and `assets/`.

Adopting the format is deliberate — inventing an alternative beside a working
one buys nothing and costs a migration, and it was this project's own first
mistake. The format has since been adopted unchanged by a major vendor
(see [the field as it stands](/field/)), which makes it the obviously right
call.

What this project adds sits *on top* of the format: an index that makes many
skills usable at scale — the one thing no ecosystem has. See
[what has been decided](/decisions/).

See the [Agent Skills specification](https://agentskills.io/specification) for
the format itself.
