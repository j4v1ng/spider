# Website Spider

A Kotlin and Ktor-based application for efficiently mapping website structures and generating comprehensive sitemaps.

## Technologies Used

- **Kotlin**: Programming language
- **Ktor**: Web framework
- **Kotlinx Coroutines**: For concurrent processing
- **JSoup**: For HTML parsing
- **Thymeleaf**: For HTML templating
- **Bootstrap**: For UI styling

## Using the Application

### Starting a New Spider Job

1. Open your browser and navigate to `http://localhost:8080`
2. Click on the "New Spider Job" button
3. Enter the starting URL for the website you want to map
4. Configure the spider settings:
   - **Maximum Depth**: How deep the spider should crawl (1-10)
   - **Maximum Threads**: Number of concurrent crawling threads (1-16)
   - **Include Patterns**: Comma-separated list of URL patterns to include (leave empty to include all)
   - **Exclude Patterns**: Comma-separated list of URL patterns to exclude
   - **Stay on Domain**: Only crawl URLs on the same domain as the start URL
   - **Respect robots.txt**: Follow the rules in the site's robots.txt file
   - **Connection Timeout**: Timeout for HTTP connections in milliseconds
5. Click "Start Spider" to begin the mapping process

### Viewing Results

The spider will start crawling the website and building a site map. You can view the progress in real-time:

- The status of the spider job (PENDING, IN_PROGRESS, COMPLETED, FAILED)
- The number of pages crawled (total, successful, failed)
- The duration of the crawling process
- A hierarchical visualization of the site structure

### Exporting Sitemaps

Once the spider job is completed, you can export the sitemap in different formats:

1. Click on the "Export" dropdown button
2. Choose the desired format:
   - **XML**: Standard sitemap format that can be used by search engines
   - **Text**: Plain text representation of the site structure

### Managing Spider Jobs

You can manage your spider jobs from the home page:

- View all active site maps
- Cancel a running spider job
- Remove a site map when you no longer need it

## Building & Running
Use `./gradlew build` and then run the main method.
If the server starts successfully, you'll see the following output:
```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```
