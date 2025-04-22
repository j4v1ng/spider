package com.javing.models

import java.time.LocalDateTime

/**
 * Represents a single page in the site structure.
 *
 * @property url The URL of the page
 * @property title The title of the page (if available)
 * @property depth The depth of the page in the site structure
 * @property parentUrl The URL of the parent page (null for the root page)
 * @property childUrls The URLs of the child pages
 * @property statusCode The HTTP status code of the page
 * @property contentType The content type of the page
 * @property crawlTime The time when the page was crawled
 * @property errorMessage Error message if the page couldn't be crawled
 */
data class Page(
    val url: String,
    val title: String? = null,
    val depth: Int = 0,
    val parentUrl: String? = null,
    val childUrls: MutableSet<String> = mutableSetOf(),
    val statusCode: Int? = null,
    val contentType: String? = null,
    val crawlTime: LocalDateTime = LocalDateTime.now(),
    val errorMessage: String? = null
) {
    /**
     * Checks if the page was successfully crawled.
     */
    val isSuccess: Boolean
        get() = statusCode in 200..299 && errorMessage == null
        
    /**
     * Returns the domain of the page.
     */
    val domain: String
        get() {
            val urlPattern = Regex("https?://([^/]+)")
            val matchResult = urlPattern.find(url)
            return matchResult?.groupValues?.getOrNull(1) ?: ""
        }
}