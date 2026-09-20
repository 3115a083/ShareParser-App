package cc.stkmn.shareparser.engine

/** Materializes all user regex results on bounded workers, including lazy matches. */
class SafeRegex(pattern: String, options: Set<RegexOption> = emptySet()) {
    private val regex = work.run { Regex(pattern, options) }
    fun containsMatchIn(input: CharSequence): Boolean = work.run { regex.containsMatchIn(input) }
    fun matches(input: CharSequence): Boolean = work.run { regex.matches(input) }
    fun find(input: CharSequence): MatchResult? = work.run { regex.find(input)?.freeze() }
    fun lastMatch(input: CharSequence): MatchResult? = work.run { regex.findAll(input).lastOrNull()?.freeze() }
    fun findAll(input: CharSequence, limit: Int = 200): List<MatchResult> = work.run { regex.findAll(input).take(limit).map { it.freeze() }.toList() }
    fun replace(input: CharSequence, replacement: String): String = work.run { regex.replace(input, replacement) }
    private fun MatchResult.freeze(): MatchResult {
        val savedRange = range
        val savedValue = value
        val savedGroups = groups.map { it?.let { group -> MatchGroup(group.value, group.range) } }
        return object : MatchResult {
            override val range = savedRange
            override val value = savedValue
            override val groupValues = savedGroups.map { it?.value.orEmpty() }
            override val groups: MatchGroupCollection = object : AbstractCollection<MatchGroup?>(), MatchGroupCollection {
                override val size = savedGroups.size
                override fun get(index: Int): MatchGroup? = savedGroups[index]
                override fun iterator(): Iterator<MatchGroup?> = savedGroups.iterator()
            }
            override fun next(): MatchResult? = null
        }
    }
    companion object { private val work = BoundedWork("ShareParser-regex", timeoutMs = 750) }
}
