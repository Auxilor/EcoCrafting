package com.exanthiax.ecocrafting.crafting.integration

import java.util.concurrent.ConcurrentHashMap

internal class NoticeThrottle<K> {
    private val notified: MutableSet<K> = ConcurrentHashMap.newKeySet()

    fun shouldNotify(key: K): Boolean = notified.add(key)

    fun clear(key: K) {
        notified.remove(key)
    }
}
