package com.ngr.app

/**
 * Builds a dependency-first plan for a manually selected catalog component.
 * It performs no download, extraction, activation or execution.
 */
object NgrComponentInstallPlanner {
    data class Plan(
        val valid: Boolean,
        val installOrder: List<NgrComponentCatalog.Component> = emptyList(),
        val alreadyInstalled: Set<String> = emptySet(),
        val errors: List<String> = emptyList()
    )

    fun resolve(
        catalog: NgrComponentCatalog,
        requestedIds: Set<String>,
        installedIds: Set<String> = emptySet(),
        installedVersions: Map<String, String> = emptyMap()
    ): Plan {
        val errors = catalog.validate().toMutableList()
        if (requestedIds.isEmpty()) errors += "At least one component must be selected"
        if (errors.isNotEmpty()) return Plan(false, errors = errors.distinct())

        val byId = catalog.components.associateBy { it.id }
        val order = mutableListOf<NgrComponentCatalog.Component>()
        val selected = linkedSetOf<String>()
        val already = linkedSetOf<String>()
        val visiting = linkedSetOf<String>()
        val visited = linkedSetOf<String>()

        fun visit(id: String) {
            if (id in visited || errors.isNotEmpty()) return
            val component = byId[id]
            if (component == null) {
                errors += "Component is not present in catalog: $id"
                return
            }
            if (!visiting.add(id)) {
                errors += "Component dependency cycle encountered: $id"
                return
            }
            (component.dependencies + component.constraints.requiredComponents).sorted().forEach(::visit)
            visiting.remove(id)
            visited += id
            if (id in installedIds && installedVersions[id] == component.version) already += id
            else {
                selected += id
                order += component
            }
        }

        requestedIds.sorted().forEach(::visit)
        val all = selected + already + installedIds
        all.forEach { id ->
            val component = byId[id] ?: return@forEach
            component.conflicts.intersect(all - id).forEach { errors += "Component $id conflicts with $it" }
            component.constraints.forbiddenComponents.intersect(all - id).forEach {
                errors += "Component $id forbids $it"
            }
        }
        return if (errors.isEmpty()) {
            Plan(true, order, already)
        } else {
            Plan(false, errors = errors.distinct())
        }
    }
}
