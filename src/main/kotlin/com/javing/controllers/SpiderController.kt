package com.javing.controllers

import com.javing.models.SpiderConfig
import com.javing.models.SiteMap
import com.javing.models.SiteMapStatus
import com.javing.services.SpiderService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URL

/**
 * Configures the controllers for the application.
 * @param spiderService The spider service
 */
fun Application.configureControllers(spiderService: SpiderService) {
    val logger = LoggerFactory.getLogger("SpiderController")

    routing {
        // Home page
        get("/") {
            val model = mutableMapOf<String, Any>()
            model["title"] = "Website Spider"
            model["sitemaps"] = spiderService.getAllSiteMaps()

            call.respond(ThymeleafContent("index", model))
        }

        // New spider job form
        get("/new") {
            val model = mutableMapOf<String, Any>()
            model["title"] = "New Spider Job"

            call.respond(ThymeleafContent("new", model))
        }

        // Start a new spider job
        post("/start") {
            try {
                val parameters = call.receiveParameters()
                val startUrl = parameters["startUrl"] ?: ""
                val maxDepth = parameters["maxDepth"]?.toIntOrNull() ?: 3
                val maxThreads = parameters["maxThreads"]?.toIntOrNull() ?: 4
                val stayOnDomain = parameters["stayOnDomain"]?.toBoolean() ?: true
                val respectRobotsTxt = parameters["respectRobotsTxt"]?.toBoolean() ?: true
                val connectionTimeout = parameters["connectionTimeout"]?.toIntOrNull() ?: 5000

                val includePatterns = parameters["includePatterns"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

                val excludePatterns = parameters["excludePatterns"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

                val config = SpiderConfig(
                    startUrl = startUrl,
                    maxDepth = maxDepth,
                    includePatterns = includePatterns,
                    excludePatterns = excludePatterns,
                    stayOnDomain = stayOnDomain,
                    maxThreads = maxThreads,
                    respectRobotsTxt = respectRobotsTxt,
                    connectionTimeout = connectionTimeout
                )

                val errors = config.validate()
                if (errors.isNotEmpty()) {
                    val model = mutableMapOf<String, Any>()
                    model["title"] = "New Spider Job"
                    model["errors"] = errors
                    model["config"] = config

                    call.respond(ThymeleafContent("new", model))
                    return@post
                }

                val siteMapId = spiderService.startCrawling(config)
                call.respondRedirect("/view/$siteMapId")
            } catch (e: Exception) {
                logger.error("Error starting spider job: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }

        // View a site map
        get("/view/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondRedirect("/")
            val siteMap = spiderService.getSiteMap(id) ?: return@get call.respondRedirect("/")

            val model = mutableMapOf<String, Any>()
            model["title"] = "Site Map: ${siteMap.startUrl}"
            model["siteMapId"] = id
            model["siteMap"] = siteMap
            model["STATUS_IN_PROGRESS"] = SiteMapStatus.IN_PROGRESS

            call.respond(ThymeleafContent("view", model))
        }

        // Get site map status (for AJAX updates)
        get("/api/status/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val siteMap = spiderService.getSiteMap(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Site map not found")

            val status = mapOf(
                "status" to siteMap.status.name,
                "totalPages" to siteMap.totalPages,
                "successfulPages" to siteMap.successfulPages,
                "failedPages" to siteMap.failedPages,
                "durationSeconds" to siteMap.durationSeconds
            )

            call.respond(status)
        }

        // Get site map data (for visualization)
        get("/api/data/{id}") {
            try {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
                val siteMap = spiderService.getSiteMap(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Site map not found")

                try {
                    val pageTree = siteMap.getPageTree()
                    call.respond(pageTree)
                } catch (e: Exception) {
                    logger.error("Error generating page tree: ${e.message}", e)
                    // Return an empty list if there's an error
                    call.respond(emptyList<Any>())
                }
            } catch (e: Exception) {
                logger.error("Unhandled error in /api/data endpoint: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        // Cancel a spider job
        post("/cancel/{id}") {
            val id = call.parameters["id"] ?: return@post call.respondRedirect("/")
            spiderService.cancelCrawling(id)
            call.respondRedirect("/view/$id")
        }

        // Remove a site map
        post("/remove/{id}") {
            val id = call.parameters["id"] ?: return@post call.respondRedirect("/")
            spiderService.removeSiteMap(id)
            call.respondRedirect("/")
        }

        // Export site map as XML
        get("/export/xml/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val siteMap = spiderService.getSiteMap(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Site map not found")

            val xml = buildSitemapXml(siteMap)
            call.respondText(xml, ContentType.Application.Xml)
        }

        // Export site map as plain text
        get("/export/text/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val siteMap = spiderService.getSiteMap(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Site map not found")

            val text = buildSitemapText(siteMap)
            call.respondText(text, ContentType.Text.Plain)
        }
    }
}

/**
 * Builds a sitemap XML from a site map.
 * @param siteMap The site map
 * @return The sitemap XML
 */
private fun buildSitemapXml(siteMap: SiteMap): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n")

    siteMap.pages.values
        .filter { it.isSuccess }
        .forEach { page ->
            sb.append("  <url>\n")
            sb.append("    <loc>${page.url}</loc>\n")
            sb.append("  </url>\n")
        }

    sb.append("</urlset>")
    return sb.toString()
}

/**
 * Builds a sitemap text from a site map.
 * @param siteMap The site map
 * @return The sitemap text
 */
private fun buildSitemapText(siteMap: SiteMap): String {
    val sb = StringBuilder()
    sb.append("Sitemap for ${siteMap.startUrl}\n")
    sb.append("Generated on ${siteMap.endTime ?: "in progress"}\n")
    sb.append("Total pages: ${siteMap.totalPages}\n")
    sb.append("Successful pages: ${siteMap.successfulPages}\n")
    sb.append("Failed pages: ${siteMap.failedPages}\n\n")

    // Group pages by depth
    val pagesByDepth = siteMap.getPagesByDepth()
    pagesByDepth.keys.sorted().forEach { depth ->
        val pages = pagesByDepth[depth] ?: emptyList()
        sb.append("Depth $depth (${pages.size} pages):\n")

        pages.sortedBy { it.url }.forEach { page ->
            val status = if (page.isSuccess) "OK" else "ERROR: ${page.errorMessage}"
            sb.append("  ${page.url} - $status\n")
        }

        sb.append("\n")
    }

    return sb.toString()
}
