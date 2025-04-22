package com.javing.models

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents the entire site structure.
 *
 * @property startUrl The starting URL for the site map
 * @property config The configuration used for crawling
 * @property pages Map of URL to Page objects
 * @property startTime The time when the crawling started
 * @property endTime The time when the crawling ended (null if still in progress)
 * @property status The current status of the site map
 */
data class SiteMap(
    val startUrl: String,
    val config: SpiderConfig,
    val pages: ConcurrentHashMap<String, Page> = ConcurrentHashMap(),
    val startTime: LocalDateTime = LocalDateTime.now(),
    var endTime: LocalDateTime? = null,
    var status: SiteMapStatus = SiteMapStatus.PENDING
) {
    /**
     * Returns the root page of the site map.
     */
    val rootPage: Page?
        get() = pages[startUrl]

    /**
     * Returns the total number of pages in the site map.
     */
    val totalPages: Int
        get() = pages.size

    /**
     * Returns the number of successfully crawled pages.
     */
    val successfulPages: Int
        get() = pages.values.count { it.isSuccess }

    /**
     * Returns the number of failed pages.
     */
    val failedPages: Int
        get() = pages.values.count { !it.isSuccess }

    /**
     * Returns the duration of the crawling in seconds.
     */
    val durationSeconds: Long
        get() {
            val end = endTime ?: LocalDateTime.now()
            return java.time.Duration.between(startTime, end).seconds
        }

    /**
     * Returns the pages grouped by depth.
     */
    fun getPagesByDepth(): Map<Int, List<Page>> {
        return pages.values.groupBy { it.depth }
    }

    /**
     * Returns the pages grouped by domain.
     */
    fun getPagesByDomain(): Map<String, List<Page>> {
        return pages.values.groupBy { it.domain }
    }

    /**
     * Returns the child pages of a given URL.
     */
    fun getChildPages(url: String): List<Page> {
        val page = pages[url] ?: return emptyList()
        return page.childUrls.mapNotNull { pages[it] }
    }

    /**
     * Returns the page tree starting from the root page.
     */
    fun getPageTree(): List<PageTreeNode> {
        val rootPage = rootPage ?: return emptyList()
        return listOf(buildPageTree(rootPage, mutableSetOf()))
    }

    private fun buildPageTree(page: Page, visitedUrls: MutableSet<String>): PageTreeNode {
        // Add current URL to visited set to prevent circular references
        visitedUrls.add(page.url)

        // Only process child URLs that haven't been visited yet
        val children = page.childUrls
            .filter { !visitedUrls.contains(it) }
            .mapNotNull { pages[it] }
            .map { buildPageTree(it, visitedUrls.toMutableSet()) }

        return PageTreeNode(page, children)
    }
}

/**
 * Represents a node in the page tree.
 */
data class PageTreeNode(
    val page: Page,
    val children: List<PageTreeNode> = emptyList()
)

/**
 * Enum representing the status of a site map.
 */
enum class SiteMapStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
