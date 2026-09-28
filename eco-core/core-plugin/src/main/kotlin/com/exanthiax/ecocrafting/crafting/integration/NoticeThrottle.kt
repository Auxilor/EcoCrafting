package com.exanthiax.ecocrafting.crafting.integration

internal class NoticeThrottle<K> {
    private val notified = mutableSetOf<K>()

    fun shouldNotify(key: K): Boolean = notified.add(key)

    fun clear(key: K) {
        notified.remove(key)
    }
}
