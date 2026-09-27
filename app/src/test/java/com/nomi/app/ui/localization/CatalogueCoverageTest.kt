package com.nomi.app.ui.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural guards for the catalogue that no other test provides.
 *
 * [NomiTranslationCatalogTest] proves the catalogue is internally consistent - every entry
 * translated into every language, placeholders intact, no mojibake. What it cannot see is the
 * one way a catalogue goes wrong from the outside:
 *
 * a `nomiString("…")` call site whose key is not in the catalogue renders as English, with no
 * warning anywhere. That is invisible by design - `NomiTranslations.translate` falls back to the
 * key, so "missing" and "not yet translated" look identical on screen. A structural test is the
 * only place that can tell them apart.
 *
 * It also asserts that no key is defined twice, because the per-area maps are merged with
 * `putAll` and the last one wins: a collision silently discards the earlier entry's nine
 * translations with no error at all.
 *
 * The scanner is deliberately line-based rather than a single pattern. Catalogue keys and call
 * sites are both written as `+`-joined literals across several lines, and nesting
 * (`nomiFormat("{0}", nomiString("x"))`) means a call site's own argument list can contain
 * further calls. Reading it linearly is both simpler and immune to the backtracking that
 * overflowed the stack on a 2 000-line screen file.
 */
class CatalogueCoverageTest {

    private val mainSources: File = locate("app/src/main/java/com/nomi/app")
    private val catalogueSources: File = File(mainSources, "ui/localization")

    @Test
    fun `every nomiString and nomiFormat key exists in the catalogue`() {
        val catalogue = catalogueKeys()
        val missing = LinkedHashMap<String, String>()
        kotlinFiles(mainSources)
            .filterNot { it.invariantPath().contains("/ui/localization/") }
            .forEach { file ->
                callSiteKeys(file.readText()).forEach { (key, line) ->
                    if (key !in catalogue) {
                        missing.putIfAbsent(key, "${file.name}:$line")
                    }
                }
            }
        assertEquals(
            "nomiString keys with no catalogue entry",
            emptyMap<String, String>(),
            missing,
        )
    }

    @Test
    fun `no translation key is defined twice`() {
        val seen = HashMap<String, String>()
        val duplicates = LinkedHashMap<String, String>()
        // The same scanner as the coverage test, so a key is compared the same way it is looked
        // up. Matching the key's own line would skip the wrapped and `put` spellings, and a
        // duplicate hidden that way is a duplicate that silently discards nine translations.
        kotlinFiles(catalogueSources).forEach { file ->
            val text = file.readText()
            var from = 0
            while (true) {
                val at = text.indexOf("NomiTranslation(", from)
                if (at < 0) break
                from = at + 1
                val key = keyRunBefore(text, at)
                if (key.isEmpty()) continue
                val where = "${file.name}:${text.take(at).count { it == '\n' } + 1}"
                val previous = seen.put(key, where)
                if (previous != null) duplicates.putIfAbsent(key, "$previous and $where")
            }
        }
        assertEquals(
            "duplicate catalogue keys; the later area silently wins",
            emptyMap<String, String>(),
            duplicates,
        )
    }

    @Test
    fun `the catalogue still covers every language for every entry`() {
        // A floor, so a bulk deletion is caught; the exact per-language guarantee is
        // NomiTranslationCatalogTest's job.
        assertTrue(
            "catalogue is only ${NomiTranslations.catalogue.size} entries",
            NomiTranslations.catalogue.size >= 670,
        )
    }

    /**
     * Every `"literal"` run joined by `+` that precedes a `to NomiTranslation(`.
     *
     * A long key is written across two or more lines, with the `+` at the end of the line *above*
     * the one carrying `to NomiTranslation(`:
     *
     * ```
     * "Nomi scans common EAN and UPC barcodes automatically. Nothing is captured until a code is " +
     *     "visible." to NomiTranslation(
     * ```
     *
     * So the run is read upwards from the `to NomiTranslation(` line, and only while the line
     * above actually ends in `+`. The `+` is stripped *after* deciding to keep walking, otherwise
     * the check that the continuation is still a bare literal never sees it.
     */
    /**
     * Every key in the catalogue, found by walking backwards from each `NomiTranslation(` value.
     *
     * Anchoring on the *value* rather than on the key's own line is what makes this work across
     * the three spellings the catalogue actually uses - a long key wrapped over several lines, a
     * long key whose `to` wrapped onto its own line, and the `put("key", NomiTranslation(` form.
     * Matching the key line instead silently misses two of the three, and a key the scanner cannot
     * see looks exactly like a key nobody translated.
     */
    private fun catalogueKeys(): Set<String> {
        val keys = HashSet<String>()
        kotlinFiles(catalogueSources).forEach { file ->
            val text = file.readText()
            var from = 0
            while (true) {
                val at = text.indexOf("NomiTranslation(", from)
                if (at < 0) break
                keyRunBefore(text, at).takeIf(String::isNotEmpty)?.let(keys::add)
                from = at + 1
            }
        }
        return keys
    }

    /** The `+`-joined string literals ending immediately before [endExclusive], or `""`. */
    private fun keyRunBefore(text: String, endExclusive: Int): String {
        var i = backOverWhitespace(text, endExclusive - 1)
        // Whatever introduces the value: `to`, the `(` of `put(`, the argument `,`. Only these,
        // and only before the first literal - a `,` *between* two entries must stop the scan, or
        // two adjacent keys would be read as one.
        while (i >= 0) {
            when (text[i]) {
                '(', ',' -> i = backOverWhitespace(text, i - 1)
                else -> if (isAtToKeyword(text, i)) {
                    i = backOverWhitespace(text, i - 2)
                } else {
                    return finishKeyRun(text, i)
                }
            }
        }
        return ""
    }

    private fun finishKeyRun(text: String, from: Int): String {
        if (from < 0 || text[from] != '"') return ""
        val pieces = ArrayList<String>()
        var i = from
        while (i >= 0 && text[i] == '"') {
            val close = i
            var start = close - 1
            while (start >= 0) {
                when (text[start]) {
                    // Scanning backwards an escaped quote is met as the quote first, with its
                    // backslash *before* it, so that pair is content rather than a terminator.
                    '\\' -> start -= 2
                    '"' -> if (start >= 1 && text[start - 1] == '\\') start -= 2 else break
                    else -> start--
                }
            }
            if (start < 0) break
            pieces.add(0, decode(text.substring(start, close + 1)))
            val next = backOverWhitespace(text, start - 1)
            if (next >= 0 && text[next] == '+') {
                i = backOverWhitespace(text, next - 1)
                continue
            }
            break
        }
        return pieces.joinToString("")
    }

    private fun backOverWhitespace(text: String, from: Int): Int {
        var i = from
        while (i >= 0 && text[i].isWhitespace()) i--
        return i
    }

    /** True when [i] is the `o` of a `to` sitting in front of a value. */
    private fun isAtToKeyword(text: String, i: Int): Boolean =
        i >= 1 && text[i] == 'o' && text[i - 1] == 't'

    /**
     * The leading run of `+`-joined string literals in every `nomiString(` / `nomiFormat(` call.
     *
     * Scanning stops at the first character that is not a literal, whitespace or `+`, so the
     * arguments of a nested call are attributed to the nested call rather than to the outer one.
     */
    private fun callSiteKeys(text: String): List<Pair<String, Int>> {
        val found = ArrayList<Pair<String, Int>>()
        var index = 0
        while (index < text.length) {
            val at = nextCall(text, index) ?: break
            val open = text.indexOf('(', at)
            var i = open + 1
            val builder = StringBuilder()
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '"' -> {
                        val end = endOfLiteral(text, i)
                        // An unterminated quote - a stray " in a comment, say - leaves end at
                        // text.length, and substring would throw rather than report the key.
                        builder.append(decode(text.substring(i, minOf(end + 1, text.length))))
                        i = end + 1
                    }
                    c.isWhitespace() || c == '+' -> i++
                    else -> break
                }
            }
            if (builder.isNotEmpty()) {
                found += builder.toString() to (text.take(at).count { it == '\n' } + 1)
            }
            index = at + 1
        }
        return found
    }

    private fun nextCall(text: String, from: Int): Int? {
        val a = text.indexOf("nomiString(", from)
        val b = text.indexOf("nomiFormat(", from)
        return when {
            // Both arms matter. Reporting -1 instead of null here makes the caller's
            // `?: break` unreachable, so the scan restarts from 0 and spins forever on the first
            // file that runs out of call sites - which is every file, eventually.
            a < 0 && b < 0 -> null
            a < 0 -> b
            b < 0 -> a
            else -> minOf(a, b)
        }
    }

    private fun endOfLiteral(text: String, openQuote: Int): Int {
        var i = openQuote + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i
                else -> i++
            }
        }
        return text.length
    }

    /** Unescapes a `"…"` literal and strips the quotes. */
    private fun decode(literal: String): String {
        var body = literal.trim().removePrefix("\"").removeSuffix("\"")
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                when (val next = body[i + 1]) {
                    'u' -> {
                        if (i + 5 < body.length) {
                            out.append(String(Character.toChars(body.substring(i + 2, i + 6).toInt(16))))
                            i += 6
                            continue
                        }
                    }
                    'n' -> { out.append('\n'); i += 2; continue }
                    't' -> { out.append('\t'); i += 2; continue }
                    else -> { out.append(next); i += 2; continue }
                }
            }
            out.append(c)
            i++
        }
        body = out.toString()
        return body
    }

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

    private fun locate(relative: String): File {
        // The seed is typed non-null so the sequence's element type is File rather than File?,
        // which is what the working directory actually is.
        val start: File = File(System.getProperty("user.dir") ?: ".")
        return generateSequence(start) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.isDirectory }
            ?: File(relative)
    }
}
