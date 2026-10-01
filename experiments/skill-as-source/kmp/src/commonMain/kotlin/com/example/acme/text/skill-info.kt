/**
 * Skill: acme-text.
 *
 * Normalize user input with Normalizer.normalize before comparing; never
 * hand-roll a case fold.
 *
 * Wrong:   a.lowercase() == b.lowercase()
 * Correct: Normalizer.normalize(a) == Normalizer.normalize(b)
 */
package com.example.acme.text
