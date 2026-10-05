package com.vaultzero.app.data.repository

import com.vaultzero.app.data.local.entity.EntryEntity
import com.vaultzero.app.data.local.entity.GroupEntity
import com.vaultzero.app.domain.model.VaultEntry

/**
 * RFC-4180-ish CSV serializer/deserializer used for encrypted vault exports.
 *
 * Format columns: title, username, password, url, notes, group, tags, favorite
 * Tags are pipe-separated inside the tags column.
 */
object CsvHelper {

    private val HEADER = listOf("title", "username", "password", "url", "notes", "group", "tags", "favorite")

    fun buildCsv(entries: List<VaultEntry>, groupNames: Map<String, String>): String {
        val sb = StringBuilder()
        sb.appendLine(buildRow(HEADER))
        entries.forEach { entry ->
            sb.appendLine(buildRow(listOf(
                entry.title,
                entry.username,
                entry.password,
                entry.url,
                entry.notes,
                groupNames[entry.groupId] ?: "",
                entry.tags.joinToString("|"),
                entry.favorite.toString()
            )))
        }
        return sb.toString()
    }

    fun parseCsv(csv: String): List<Map<String, String>> {
        val lines = parseCsvLines(csv)
        if (lines.isEmpty()) return emptyList()

        val header = lines[0].map { it.lowercase().trim() }
        val rows = mutableListOf<Map<String, String>>()
        for (i in 1 until lines.size) {
            val fields = lines[i]
            if (fields.isEmpty() || fields.all { it.isBlank() }) continue
            val row = HEADER.associateWith { col ->
                val index = header.indexOf(col)
                if (index in fields.indices) fields[index] else ""
            }
            rows.add(row)
        }
        return rows
    }

    fun parseTagString(tags: String): List<String> {
        if (tags.isBlank()) return emptyList()
        return tags.split('|').map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun buildRow(fields: List<String>): String {
        return fields.joinToString(",") { escape(it) }
    }

    private fun escape(field: String): String {
        val needsQuote = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')
        return if (needsQuote) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    private fun parseCsvLines(csv: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csv.length) {
            val ch = csv[i]
            when {
                ch == '"' && inQuotes -> {
                    if (i + 1 < csv.length && csv[i + 1] == '"') {
                        currentField.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                    }
                }
                ch == '"' && !inQuotes -> {
                    inQuotes = true
                    i++
                }
                ch == ',' && !inQuotes -> {
                    current.add(currentField.toString())
                    currentField.clear()
                    i++
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    current.add(currentField.toString())
                    currentField.clear()
                    result.add(current.toList())
                    current = mutableListOf()
                    if (ch == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') {
                        i += 2
                    } else {
                        i++
                    }
                }
                else -> {
                    currentField.append(ch)
                    i++
                }
            }
        }
        current.add(currentField.toString())
        result.add(current.toList())
        return result.filter { it.isNotEmpty() && !it.all { f -> f.isBlank() } }
    }
}
