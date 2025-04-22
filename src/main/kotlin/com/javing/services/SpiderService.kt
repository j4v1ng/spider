package com.javing.services

import com.javing.models.*
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.HttpStatusException
import org.slf4j.LoggerFactory
import java.net.URL
import java.net.MalformedURLException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for crawling websites and building site maps.
 */
class SpiderService {
    private val logger = LoggerFactory.getLogger(SpiderService::class.java)
    private val activeSiteMaps = ConcurrentHashMap<String, SiteMap>()
    private val robotsTxtCache = ConcurrentHashMap<String, RobotsTxt>()

    /**
     * Starts a new crawling job with the given configuration.
     * @param config The spider configuration
     * @return The ID of the created site map
     */
    suspend fun startCrawling(config: SpiderConfig): String {
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Invalid configuration: ${errors.joinToString(", ")}")
        }

        val siteMapId = generateSiteMapId()
        val siteMap = SiteMap(config.startUrl, config)
        activeSiteMaps[siteMapId] = siteMap

        // Start crawling in a coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                crawlSite(siteMapId, siteMap)
            } catch (e: Exception) {
                logger.error("Error crawling site: ${e.message}", e)
                siteMap.status = SiteMapStatus.FAILED
                siteMap.endTime = LocalDateTime.now()
            }
        }

        return siteMapId
    }

    /**
     * Gets a site map by ID.
     * @param id The ID of the site map
     * @return The site map, or null if not found
     */
    fun getSiteMap(id: String): SiteMap? {
        return activeSiteMaps[id]
    }

    /**
     * Gets all active site maps.
     * @return List of all site maps
     */
    fun getAllSiteMaps(): List<Pair<String, SiteMap>> {
        return activeSiteMaps.entries.map { it.key to it.value }
    }

    /**
     * Cancels a crawling job.
     * @param id The ID of the site map to cancel
     * @return True if the job was cancelled, false if not found
     */
    fun cancelCrawling(id: String): Boolean {
        val siteMap = activeSiteMaps[id] ?: return false
        siteMap.status = SiteMapStatus.FAILED
        siteMap.endTime = LocalDateTime.now()
        return true
    }

    /**
     * Removes a site map.
     * @param id The ID of the site map to remove
     * @return True if the site map was removed, false if not found
     */
    fun removeSiteMap(id: String): Boolean {
        return activeSiteMaps.remove(id) != null
    }

    /**
     * Crawls a website and builds a site map.
     * @param siteMapId The ID of the site map
     * @param siteMap The site map to build
     */
    private suspend fun crawlSite(siteMapId: String, siteMap: SiteMap) {
        val config = siteMap.config
        val urlQueue = ConcurrentLinkedQueue<UrlToCrawl>()
        val visitedUrls = ConcurrentHashMap.newKeySet<String>()
        val activeJobs = AtomicInteger(0)
        val isCompleted = AtomicBoolean(false)

        // Add the start URL to the queue
        urlQueue.add(UrlToCrawl(config.startUrl, 0, null))
        siteMap.status = SiteMapStatus.IN_PROGRESS

        // Create a dispatcher with a fixed thread pool
        val dispatcher = Dispatchers.IO.limitedParallelism(config.maxThreads)

        // Create coroutines for crawling
        val jobs = List(config.maxThreads) {
            CoroutineScope(dispatcher).launch {
                while (!isCompleted.get()) {
                    val urlToCrawl = urlQueue.poll()
                    if (urlToCrawl != null) {
                        activeJobs.incrementAndGet()
                        try {
                            crawlUrl(urlToCrawl, siteMap, urlQueue, visitedUrls)
                        } finally {
                            activeJobs.decrementAndGet()
                        }
                    } else if (activeJobs.get() == 0) {
                        // No more URLs to crawl and no active jobs
                        isCompleted.set(true)
                    } else {
                        // Wait for other jobs to finish or add more URLs
                        delay(100)
                    }
                }
            }
        }

        // Wait for all jobs to complete
        jobs.forEach { it.join() }

        // Mark the site map as completed
        siteMap.status = SiteMapStatus.COMPLETED
        siteMap.endTime = LocalDateTime.now()

        logger.info("Crawling completed for site map $siteMapId: ${siteMap.totalPages} pages crawled")
    }

    /**
     * Crawls a single URL and adds it to the site map.
     * @param urlToCrawl The URL to crawl
     * @param siteMap The site map to add the page to
     * @param urlQueue The queue of URLs to crawl
     * @param visitedUrls Set of already visited URLs
     */
    private suspend fun crawlUrl(
        urlToCrawl: UrlToCrawl,
        siteMap: SiteMap,
        urlQueue: ConcurrentLinkedQueue<UrlToCrawl>,
        visitedUrls: MutableSet<String>
    ) {
        val url = urlToCrawl.url
        val depth = urlToCrawl.depth
        val parentUrl = urlToCrawl.parentUrl

        // Skip if already visited
        if (!visitedUrls.add(url)) {
            return
        }

        logger.debug("Crawling URL: $url (depth: $depth)")

        // Create a page for this URL
        val page = Page(url = url, depth = depth, parentUrl = parentUrl)
        siteMap.pages[url] = page

        // Add this URL as a child to the parent page
        if (parentUrl != null) {
            siteMap.pages[parentUrl]?.childUrls?.add(url)
        }

        // Skip further processing if we've reached the maximum depth
        if (depth >= siteMap.config.maxDepth) {
            return
        }

        try {
            // Check robots.txt if enabled
            if (siteMap.config.respectRobotsTxt && !isAllowedByRobotsTxt(url)) {
                siteMap.pages[url] = page.copy(errorMessage = "Blocked by robots.txt")
                return
            }

            // Connect to the URL and get the document
            val connection = Jsoup.connect(url)
                .timeout(siteMap.config.connectionTimeout)
                .followRedirects(true)

            val response = connection.execute()
            val document = response.parse()

            // Update the page with the response information
            page.apply {
                siteMap.pages[url] = copy(
                    title = document.title(),
                    statusCode = response.statusCode(),
                    contentType = response.contentType()
                )
            }

            // Extract links from the document
            val links = document.select("a[href]")
                .map { it.attr("abs:href") }
                .filter { it.isNotBlank() }
                .map { normalizeUrl(it) }
                .filter { shouldCrawl(it, url, siteMap.config) }
                .distinct()

            // Add links to the queue
            links.forEach { link ->
                urlQueue.add(UrlToCrawl(link, depth + 1, url))
            }

        } catch (e: HttpStatusException) {
            logger.warn("HTTP error for URL $url: ${e.statusCode} ${e.message}")
            page.apply {
                siteMap.pages[url] = copy(
                    statusCode = e.statusCode,
                    errorMessage = "HTTP error: ${e.statusCode} ${e.message}"
                )
            }
        } catch (e: Exception) {
            logger.warn("Error crawling URL $url: ${e.message}")
            page.apply {
                siteMap.pages[url] = copy(
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Checks if a URL should be crawled based on the configuration.
     * @param url The URL to check
     * @param parentUrl The parent URL
     * @param config The spider configuration
     * @return True if the URL should be crawled, false otherwise
     */
    private fun shouldCrawl(url: String, parentUrl: String, config: SpiderConfig): Boolean {
        try {
            // Skip non-HTTP URLs
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return false
            }

            // Check domain restriction
            if (config.stayOnDomain) {
                val parentDomain = extractDomain(parentUrl)
                val urlDomain = extractDomain(url)
                if (parentDomain != urlDomain) {
                    return false
                }
            }

            // Check include patterns
            if (config.includePatterns.isNotEmpty() && 
                !config.includePatterns.any { url.contains(it) }) {
                return false
            }

            // Check exclude patterns
            if (config.excludePatterns.any { url.contains(it) }) {
                return false
            }

            return true
        } catch (e: Exception) {
            logger.warn("Error checking URL $url: ${e.message}")
            return false
        }
    }

    /**
     * Checks if a URL is allowed by robots.txt.
     * @param url The URL to check
     * @return True if the URL is allowed, false otherwise
     */
    private fun isAllowedByRobotsTxt(url: String): Boolean {
        try {
            val domain = extractDomain(url)
            val robotsTxt = robotsTxtCache.computeIfAbsent(domain) {
                fetchRobotsTxt(domain)
            }

            return robotsTxt.isAllowed(url)
        } catch (e: Exception) {
            logger.warn("Error checking robots.txt for URL $url: ${e.message}")
            return true // Allow by default if there's an error
        }
    }

    /**
     * Fetches the robots.txt file for a domain.
     * @param domain The domain to fetch robots.txt for
     * @return A RobotsTxt object
     */
    private fun fetchRobotsTxt(domain: String): RobotsTxt {
        try {
            val robotsUrl = "https://$domain/robots.txt"
            val content = URL(robotsUrl).readText()
            return RobotsTxt.parse(content)
        } catch (e: Exception) {
            logger.warn("Error fetching robots.txt for domain $domain: ${e.message}")
            return RobotsTxt.empty() // Return an empty robots.txt if there's an error
        }
    }

    /**
     * Extracts the domain from a URL.
     * @param url The URL to extract the domain from
     * @return The domain
     */
    private fun extractDomain(url: String): String {
        val urlObj = URL(url)
        return urlObj.host
    }

    /**
     * Normalizes a URL by removing fragments and trailing slashes.
     * @param url The URL to normalize
     * @return The normalized URL
     */
    private fun normalizeUrl(url: String): String {
        try {
            var result = url

            // Remove fragment
            val fragmentIndex = result.indexOf('#')
            if (fragmentIndex > 0) {
                result = result.substring(0, fragmentIndex)
            }

            // Remove trailing slash
            if (result.endsWith("/") && result.count { it == '/' } > 2) {
                result = result.dropLast(1)
            }

            return result
        } catch (e: Exception) {
            return url
        }
    }

    /**
     * Generates a unique ID for a site map.
     * @return A unique ID
     */
    private fun generateSiteMapId(): String {
        return "sitemap-${System.currentTimeMillis()}-${(0..9999).random()}"
    }

    /**
     * Data class representing a URL to crawl.
     * @property url The URL to crawl
     * @property depth The depth of the URL in the site structure
     * @property parentUrl The URL of the parent page
     */
    private data class UrlToCrawl(
        val url: String,
        val depth: Int,
        val parentUrl: String?
    )

    /**
     * Class representing a robots.txt file.
     * @property disallowedPaths List of disallowed paths
     */
    private class RobotsTxt(
        private val disallowedPaths: List<String>
    ) {
        /**
         * Checks if a URL is allowed by this robots.txt.
         * @param url The URL to check
         * @return True if the URL is allowed, false otherwise
         */
        fun isAllowed(url: String): Boolean {
            try {
                val urlObj = URL(url)
                val path = urlObj.path

                return !disallowedPaths.any { disallowedPath ->
                    path.startsWith(disallowedPath)
                }
            } catch (e: MalformedURLException) {
                return true
            }
        }

        companion object {
            /**
             * Parses a robots.txt file.
             * @param content The content of the robots.txt file
             * @return A RobotsTxt object
             */
            fun parse(content: String): RobotsTxt {
                val disallowedPaths = mutableListOf<String>()

                content.lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("Disallow:", ignoreCase = true) }
                    .forEach { line ->
                        val path = line.substringAfter(':').trim()
                        if (path.isNotEmpty()) {
                            disallowedPaths.add(path)
                        }
                    }

                return RobotsTxt(disallowedPaths)
            }

            /**
             * Creates an empty robots.txt.
             * @return An empty RobotsTxt object
             */
            fun empty(): RobotsTxt {
                return RobotsTxt(emptyList())
            }
        }
    }
}
