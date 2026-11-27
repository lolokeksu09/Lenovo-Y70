package com.ramwiper.next.domain

/** Sets only prepare a selection. They never execute commands or change protection. */
data class SelectionPreset(val name: String, val packages: Set<String>)
object SelectionPresets {
    const val LIMIT = 8
    const val NAME_LIMIT = 32
    fun add(existing: List<SelectionPreset>, name: String, selected: Set<String>,
            protected: Set<String>, targets: List<AppTarget>): List<SelectionPreset> {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= NAME_LIMIT && clean.none { it.isISOControl() }) { "Название: от 1 до 32 символов" }
        require(existing.size < LIMIT) { "Не больше 8 наборов" }
        require(existing.none { it.name.equals(clean, ignoreCase = true) }) { "Такой набор уже существует" }
        val allowed = SelectionPolicy.eligible(selected, protected, targets)
        require(allowed.isNotEmpty()) { "Выбери хотя бы одно доступное приложение" }
        return existing + SelectionPreset(clean, allowed)
    }
    fun apply(preset: SelectionPreset, protected: Set<String>, targets: List<AppTarget>): Set<String> =
        SelectionPolicy.eligible(preset.packages, protected, targets)
    fun remove(existing: List<SelectionPreset>, name: String): List<SelectionPreset> =
        existing.filterNot { it.name == name }
}
