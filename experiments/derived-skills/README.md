# derived-skills

Stage 1 of [RAD-0082](../../docs/knowledge/research/RAD-0082-a-skill-derived-from-the-source.md): can a skill for a library be written from its published source alone, by someone who is not its author, without inventing anything?

One directory per library. `derived/` is the skill a frontier agent wrote from the published sources jar (or npm package), following [`librarian-skill-author`](../../implementations/agent-skills/librarian-skill-author/), with a `[src: path:line]` citation after every claim and `[uncited]` on conclusions drawn from the code as a whole. `score.md` is a second agent's check of every claim against the source, and its comparison with the skill the library's own authors ship. Paths in citations are relative to the library's published source, one directory per source set; `<base>` and `<authored>` in the score sheets stand for where the inputs were unpacked.

| Library | Version | Claims | Supported (ok) | Overstated (harm) | Cannot tell | Unsupported or contradicted (harm) |
|---|---|---|---|---|---|---|
| `io.github.aughtone:types` | 4.1.0 | 350 | 338 | 11 | 1 | 0 |
| `io.github.aughtone:format-readable` | 4.1.0 | 165 | 161 | 1 | 3 | 0 |
| `io.github.aughtone:charts` | 0.0.3 | 143 | 133 | 7 | 3 | 0 |
| `io.github.aughtone.normalize:common` | 0.0.5 | 124 | 123 | 1 | 0 | 0 |
| npm `dotenv` | 17.4.2 | 131 | 125 | 6 | 0 | 0 |

Run 2026-10-08. Writer and scorer were the same model, as separate agents; only the dotenv scorer ran code. These are experiment outputs, not skills to install: a derived skill is never served as a library's own.
