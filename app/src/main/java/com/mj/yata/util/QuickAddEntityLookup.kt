package com.mj.yata.util

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activeLists
import com.mj.yata.domain.model.activePeople
import com.mj.yata.domain.model.activeProjects

/** Batch-local lookup; recreate it when the entity snapshot changes. Matching rules stay shared. */
class QuickAddEntityLookup(
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>
) {
    private val lists = lists.toList()
    private val projects = projects.toList()
    private val people = people.toList()
    private val tags = tags.toList()
    private val projectMatches = mutableMapOf<String?, MemoizedEntityMatcher<Project>>()
    private val listMatches = mutableMapOf<String?, MemoizedEntityMatcher<YataList>>()
    private val personMatches = mutableMapOf<Set<String>, MemoizedEntityMatcher<Person>>()
    private val tagMatches by lazy { MemoizedEntityMatcher(this.tags) { it.name } }

    fun project(name: String, includeId: String? = null): Project? = projectMatches.getOrPut(includeId) {
        MemoizedEntityMatcher(projects.activeProjects(includeId)) { it.name }
    }.find(name)

    fun list(name: String, includeId: String? = null): YataList? = listMatches.getOrPut(includeId) {
        MemoizedEntityMatcher(lists.activeLists(includeId)) { it.name }
    }.find(name)

    fun person(name: String, includeIds: Set<String> = emptySet()): Person? = personMatches.getOrPut(includeIds.toSet()) {
        MemoizedEntityMatcher(people.activePeople(includeIds)) { it.name }
    }.find(name)

    fun tag(name: String): Tag? = tagMatches.find(name)
}

/** Exact names are indexed; fuzzy results and misses are computed once per normalized query. */
internal class MemoizedEntityMatcher<T>(private val candidates: List<T>, private val name: (T) -> String) {
    private val exact = buildMap {
        candidates.forEach { candidate ->
            val key = name(candidate).trim().lowercase()
            if (!containsKey(key)) put(key, candidate) // Preserve the shared matcher's first-match order.
        }
    }
    private val results = mutableMapOf<String, T?>()

    fun find(query: String): T? {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return null
        if (results.containsKey(key)) return results[key]
        val result = exact[key] ?: findBestEntityMatch(key, candidates, name)
        results[key] = result
        return result
    }
}
