#!/usr/bin/env python3
"""
Ways of turning an identifier into tokens. One file so a classifier can sweep them.

WHY A SWEEP AND NOT A CHOICE. Every rule this project has shipped for the identifier channel
operates on **words** — `test10`'s catalogue counts them, `test15`'s grammar composes them. That
makes word-splitting the obvious tokenisation and also an untested assumption: nobody has checked
whether a model does better on words, on the whole undivided identifier, or on character n-grams
that have no notion of a word at all. The control matters as much as the candidate.
"""
import re

# `copyEnvToLog` -> copy Env To Log. Also handles ALLCAPS runs (`HTTPServer` -> HTTP Server) and
# digits, which appear in real code constantly (`base64Encode`, `sha256`).
CAMEL = re.compile(r'[A-Z]+(?![a-z])|[A-Z][a-z0-9]*|[a-z0-9]+')
SEPARATORS = re.compile(r'[_\-.$/:<>,()\[\]{}\s]+')


def words(identifier):
    """Split on separators, then on camel-case boundaries. Lowercased.

    This is the tokenisation every shipped rule already implies, made explicit.
    """
    out = []
    for chunk in SEPARATORS.split(identifier or ""):
        out.extend(m.group(0).lower() for m in CAMEL.finditer(chunk))
    return out


def whole(identifier):
    """The identifier as ONE token, lowercased. The control.

    If this scores as well as `words`, then splitting is decoration and the model is learning
    which identifiers it has seen rather than what they are made of.
    """
    return [(identifier or "").lower()]


def bigrams(identifier):
    """Words plus adjacent pairs. `test15`'s grammar is ORDERED — verb, object, target — and a
    bag of words cannot see order. `copyEnvToLog` and `logEnvToCopy` are the same bag."""
    w = words(identifier)
    return w + [f"{a}_{b}" for a, b in zip(w, w[1:])]


def char_ngrams(identifier, lo=3, hi=5):
    """Character n-grams over the lowercased identifier, word boundaries erased.

    A model that does well here is matching substrings — `env`, `secret`, `log` — rather than
    reasoning about structure, and it would keep working on identifiers no separator can split
    (`copyenvtolog`), which every word-based rule this project ships misses completely.
    """
    s = f"^{(identifier or '').lower()}$"
    return [s[i:i + n] for n in range(lo, hi + 1) for i in range(len(s) - n + 1)]


def words_and_chars(identifier):
    """Both. If the two carry different signal, the union should beat either — the same question
    the summariser's two-faced index answered yes to."""
    return words(identifier) + char_ngrams(identifier)


TOKENIZERS = {
    "words":           words,
    "whole":           whole,
    "words+bigrams":   bigrams,
    "char 3-5grams":   char_ngrams,
    "words+chars":     words_and_chars,
}


if __name__ == "__main__":
    for name in ("copyEnvToLog", "copy_env_to_log", "COPY_ENV_TO_LOG", "HTTPServerBuilder",
                 "base64Encode", "respondOutputStream", "copyenvtolog"):
        print(f"\n  {name}")
        for tname, fn in TOKENIZERS.items():
            toks = fn(name)
            shown = " ".join(toks[:9]) + (" …" if len(toks) > 9 else "")
            print(f"    {tname:<16} {len(toks):>3}  {shown}")
