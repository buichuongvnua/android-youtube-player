package com.moynext.yt_explode.common

abstract class BasePagedList<T> : List<T> {
    protected val base: List<T>
    
    constructor(base: List<T>) {
        this.base = base
    }
    
    override val size: Int get() = base.size
    override fun contains(element: T): Boolean = base.contains(element)
    override fun containsAll(elements: Collection<T>): Boolean = base.containsAll(elements)
    override fun get(index: Int): T = base[index]
    override fun indexOf(element: T): Int = base.indexOf(element)
    override fun isEmpty(): Boolean = base.isEmpty()
    override fun iterator(): Iterator<T> = base.iterator()
    override fun lastIndexOf(element: T): Int = base.lastIndexOf(element)
    override fun listIterator(): ListIterator<T> = base.listIterator()
    override fun listIterator(index: Int): ListIterator<T> = base.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<T> = base.subList(fromIndex, toIndex)
    
    abstract suspend fun nextPage(): BasePagedList<T>?
}
