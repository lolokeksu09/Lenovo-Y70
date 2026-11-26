package com.ramwiper.next.domain

object SelectionPolicy {
    const val LIMIT = 12
    fun eligible(selected: Set<String>, protected: Set<String>, targets: List<AppTarget>): Set<String> =
        (selected.intersect(targets.map { it.pkg }.toSet()) - protected).take(LIMIT).toSet()
    fun toggle(selected: Set<String>, pkg: String, checked: Boolean,
               protected: Set<String>, targets: List<AppTarget>): Set<String> {
        val safe = eligible(selected, protected, targets)
        if (!checked) return safe - pkg
        return if (pkg in protected || targets.none { it.pkg == pkg } || safe.size >= LIMIT) safe else safe + pkg
    }
}
