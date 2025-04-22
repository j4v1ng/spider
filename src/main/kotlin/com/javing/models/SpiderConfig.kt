package com.javing.models

/**
 * Configuration for the website spider.
 *
 * @property startUrl The starting URL for the spider
 * @property maxDepth Maximum depth to crawl (default: 3)
 * @property includePatterns List of URL patterns to include (empty means include all)
 * @property excludePatterns List of URL patterns to exclude
 * @property stayOnDomain Whether to stay on the same domain as the start URL (default: true)
 * @property maxThreads Maximum number of concurrent threads for crawling (default: 4)
 * @property respectRobotsTxt Whether to respect robots.txt rules (default: true)
 * @property connectionTimeout Connection timeout in milliseconds (default: 5000)
 */
data class SpiderConfig(
    val startUrl: String,
    val maxDepth: Int = 3,
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val stayOnDomain: Boolean = true,
    val maxThreads: Int = 4,
    val respectRobotsTxt: Boolean = true,
    val connectionTimeout: Int = 5000
) {
    /**
     * Validates the configuration.
     * @return List of validation errors, empty if valid
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (startUrl.isBlank()) {
            errors.add("Start URL cannot be empty")
        } else if (!startUrl.startsWith("http://") && !startUrl.startsWith("https://")) {
            errors.add("Start URL must start with http:// or https://")
        }
        
        if (maxDepth < 1) {
            errors.add("Maximum depth must be at least 1")
        }
        
        if (maxThreads < 1) {
            errors.add("Maximum threads must be at least 1")
        }
        
        if (connectionTimeout < 1000) {
            errors.add("Connection timeout must be at least 1000 ms")
        }
        
        return errors
    }
}