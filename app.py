# Import the OS module to read environment variables (e.g., port configurations)
import os
# Import logging to output descriptive event status and error trace messages
import logging
# Import urllib.request to fetch the Spring AI upgrade documentation over HTTP
import urllib.request
# Import BeautifulSoup from bs4 for parsing HTML documents and querying tags
from bs4 import BeautifulSoup
# Import Flask framework classes to build the server, render template UI, and return JSON/query payloads
from flask import Flask, render_template, jsonify, request

# Initialize the central Flask application instance
app = Flask(__name__)

# Configure the system-wide logging level to INFO for runtime status tracking
logging.basicConfig(level=logging.INFO)
# Instantiate a named logger for outputting log messages specific to this module
logger = logging.getLogger(__name__)

# Define the constant target URL containing the official Spring AI upgrade notes documentation
UPGRADE_NOTES_URL = "https://docs.spring.io/spring-ai/reference/upgrade-notes.html"

# Initialize an in-memory dictionary cache to persist scraped results and limit network requests
cache = {
    # Stores the parsed array of release updates
    'updates': None,
    # Stores the timestamp string representing when the cache was last refreshed
    'last_updated': None
}

# Define a helper function to categorize an update card based on its title or parent category text
def determine_category(title, parent_category=""):
    # Combine title and parent category name, then convert to lowercase for case-insensitive matching
    t = (title + " " + parent_category).lower()
    
    # Check if key words related to advisor structures are in the title text
    if any(word in t for word in ["advisor", "vector-store-advisor"]):
        # Return the category designation string 'Advisors'
        return "Advisors"
    # Check if key words representing tool calls, MCP, callbacks, or functions are present
    if any(word in t for word in ["tool", "mcp", "callback", "functioncall"]):
        # Return the category designation string 'Tool Calling & MCP'
        return "Tool Calling & MCP"
    # Check if key words representing memory structures or histories are present
    if any(word in t for word in ["memory", "chatmemory", "history"]):
        # Return the category designation string 'Chat Memory'
        return "Chat Memory"
    # Check if key words representing specific model architectures or provider names are present
    if any(word in t for word in ["model", "ollama", "openai", "minimax", "genai", "anthropic", "vertex", "bedrock"]):
        # Return the category designation string 'Models & Providers'
        return "Models & Providers"
    # Check if key words representing converters, schemas, or output structures are present
    if any(word in t for word in ["converter", "schema", "output", "structured"]):
        # Return the category designation string 'Structured Output'
        return "Structured Output"
    # Check if key words representing observability or tracing frameworks are present
    if any(word in t for word in ["observability", "metric", "span", "trace", "instrument"]):
        # Return the category designation string 'Observability'
        return "Observability"
    # Check if key words representing vector databases, caches, or storage layers are present
    if any(word in t for word in ["database", "db", "cosmos", "cassandra", "pgvector", "store", "vectordb"]):
        # Return the category designation string 'Vector Databases'
        return "Vector Databases"
    # Check if key words representing JSON format utilities or Jackson serializers are present
    if any(word in t for word in ["json", "jackson", "helper"]):
        # Return the category designation string 'JSON Utilities'
        return "JSON Utilities"
    # Default return string for any cards that do not match the categories above
    return "General"

# Define the HTML parsing engine to scrape and extract version updates from the page source
def parse_release_notes(html_content):
    # Initialize the BeautifulSoup compiler using the standard html.parser engine
    soup = BeautifulSoup(html_content, 'html.parser')
    # Instantiate an empty array to collect the structured update dictionaries
    updates = []
    
    # Locate the main container tag containing the documentation content
    doc = soup.find('article', class_='doc')
    # If the standard article class isn't found, fallback to searching the document body
    if not doc:
        # Assign the document body tag as the search container
        doc = soup.body
        # If the body tag is also absent, return an empty array of updates
        if not doc:
            # Return empty list
            return []
            
    # Retrieve all primary section containers (representing major version headers)
    sect1_divs = doc.find_all('div', class_='sect1')
    # Iterate through each major version section division
    for sect1 in sect1_divs:
        # Locate the h2 title element containing the version header text
        h2 = sect1.find('h2')
        # If the version header element is missing, skip to the next section
        if not h2:
            # Continue to next section
            continue
        # Get the text content of the version header and strip any external whitespace
        version_text = h2.get_text().strip()
        # Clean prefix markers out of version string to standardize presentation labels
        version_text = version_text.replace("Upgrading to ", "").strip()
        
        # Retrieve all category subsection divisions inside this version container
        sect2_divs = sect1.find_all('div', class_='sect2')
        # Iterate through each category subsection container
        for sect2 in sect2_divs:
            # Locate the h3 header element representing the sub-category title
            h3 = sect2.find('h3')
            # If the category header element is missing, skip to the next subsection
            if not h3:
                # Continue to next subsection
                continue
            # Get the text content of the category header and strip any external whitespace
            category_text = h3.get_text().strip()
            # Retrieve the id attribute representing the page anchor segment
            category_id = h3.get('id', '')
            
            # Retrieve all granular update sections (sect3 divs) nested inside the category
            sect3_divs = sect2.find_all('div', class_='sect3')
            
            # Setup indicators to determine whether the subheadings represent unique feature topics
            has_descriptive_subheadings = False
            # List generic heading keywords that represent standard layouts rather than feature names
            generic_words = ["impact", "migration", "why", "detail"]
            
            # Iterate through all granular sub-update containers to inspect headers
            for sect3 in sect3_divs:
                # Locate the h4 heading element of this sub-update block
                h4 = sect3.find('h4')
                # If an h4 heading exists in the block
                if h4:
                    # Retrieve and clean the text content of the subheading in lowercase
                    h4_text = h4.get_text().lower().strip()
                    # If the subheading is not a generic metadata section, mark it as descriptive
                    if not any(gen in h4_text for gen in generic_words):
                        # Set description flag to true
                        has_descriptive_subheadings = True
                        # Break out of loop since we found a descriptive subheading
                        break
            
            # If descriptive subheadings exist, treat each sect3 container as an individual update card
            if sect3_divs and has_descriptive_subheadings:
                # Iterate through all nested subheadings
                for sect3 in sect3_divs:
                    # Locate the h4 header tag of this specific block
                    h4 = sect3.find('h4')
                    # If the header tag is missing, skip to the next subhead block
                    if not h4:
                        # Continue to next block
                        continue
                    # Retrieve the title text representing this update card
                    update_title = h4.get_text().strip()
                    # Get the anchor ID of the subheading to construct the deep link
                    update_id = h4.get('id', '')
                    
                    # Accumulate all sibling tag strings to form the body text HTML markup
                    content_html = ""
                    # Iterate through the direct children nodes of the sub-update section
                    for child in sect3.children:
                        # Skip the title header node itself to avoid replicating the header text
                        if child == h4 or getattr(child, 'name', None) == 'h4':
                            # Skip
                            continue
                        # Append the child element's string representation to the body content string
                        content_html += str(child)
                        
                    # Calculate the category tag based on title keywords and parent section labels
                    cat = determine_category(update_title, category_text)
                    
                    # Construct and append a structured dictionary to the output collection
                    updates.append({
                        # Unique identifier for the card
                        'id': update_id or f"up-{len(updates)}",
                        # Associated release version string
                        'version': version_text,
                        # High-level category string for UI filtering
                        'category': cat,
                        # Sub-category section title string
                        'sub_category': category_text,
                        # Heading title string of the update
                        'title': update_title,
                        # HTML string content of the update details
                        'html_content': content_html.strip(),
                        # Direct URL documentation link targeting this specific change
                        'link': f"{UPGRADE_NOTES_URL}#{update_id}" if update_id else UPGRADE_NOTES_URL
                    })
            # Else, treat the entire category division (sect2) as a single consolidated update card
            else:
                # Accumulate tag strings inside the subsection to form the body text HTML markup
                content_html = ""
                # Iterate through all direct children nodes of the category section
                for child in sect2.children:
                    # Skip the main section header tag to avoid replicating the title text
                    if child == h3 or getattr(child, 'name', None) == 'h3':
                        # Skip
                        continue
                    # Append the node's string representation to the body content string
                    content_html += str(child)
                    
                # Calculate the category tag based on category section text
                cat = determine_category(category_text)
                # Construct and append the consolidated card dictionary to the output collection
                updates.append({
                    # Unique identifier matching the category anchor link
                    'id': category_id or f"up-{len(updates)}",
                    # Associated release version string
                    'version': version_text,
                    # High-level category string for UI filtering
                    'category': cat,
                    # Fallback general designator for sub-categories
                    'sub_category': 'General',
                    # Title heading of the section
                    'title': category_text,
                    # HTML string content containing all details
                    'html_content': content_html.strip(),
                    # URL link pointing to the documentation subsection
                    'link': f"{UPGRADE_NOTES_URL}#{category_id}" if category_id else UPGRADE_NOTES_URL
                })
                
    # Return the fully compiled list of update dictionaries
    return updates

# Define high-level fetch routine to handle document download, cache storage, and error flags
def fetch_and_parse():
    # Wrap network transactions and HTML processing within a safety check block
    try:
        # Output log status specifying the initiation of the documentation scrape
        logger.info(f"Fetching Spring AI Release notes from {UPGRADE_NOTES_URL}")
        # Build an HTTP Request container adding a User-Agent header to prevent crawl blockades
        req = urllib.request.Request(
            UPGRADE_NOTES_URL, 
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
        )
        # Open the network socket and retrieve the response content
        with urllib.request.urlopen(req, timeout=20) as response:
            # Decode the response body binary stream into a UTF-8 character string
            html_data = response.read().decode('utf-8')
        
        # Log successful download and announce parser initiation
        logger.info("Successfully fetched HTML. Parsing release notes...")
        # Invoke the BeautifulSoup scraper pipeline to extract cards array
        updates = parse_release_notes(html_data)
        # Log the count of successfully extracted update card entries
        logger.info(f"Successfully parsed {len(updates)} updates.")
        
        # Update cache updates field with new results list
        cache['updates'] = updates
        # Import standard datetime module to build timestamp strings
        import datetime
        # Store current date-time string as the last refresh timestamp
        cache['last_updated'] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        # Return the parsed data and None to signify no errors occurred
        return updates, None
    # Intercept any network connection failures or HTML parsing errors
    except Exception as e:
        # Log error description and complete traceback information
        logger.error(f"Error fetching/parsing release notes: {str(e)}")
        # Return an empty list and the string error message to caller
        return [], str(e)

# Register default server route pointing to main page renderer
@app.route('/')
def index():
    # Render and return index.html dashboard template file to user agent
    return render_template('index.html')

# Register API endpoint route designed to yield structured JSON updates feeds
@app.route('/api/releases')
def get_releases():
    # Read query parameter to determine if client requested an immediate live update reload
    force_refresh = request.args.get('force', 'false').lower() == 'true'
    
    # If cache is active and no forced refresh was requested, yield the cache immediately
    if cache['updates'] is not None and not force_refresh:
        # Log cache hit return status
        logger.info("Returning cached updates")
        # Compile cache fields into a successful JSON payload response
        return jsonify({
            'status': 'success',
            'releases': cache['updates'],
            'last_updated': cache['last_updated'],
            'cached': True
        })
        
    # Trigger parser routing to perform network download and scraping
    updates, error = fetch_and_parse()
    # If an error occurs during parsing or connection
    if error:
        # If scraper fails but a cached copy exists from a prior request, return it with warning info
        if cache['updates'] is not None:
            # Yield cached updates in response along with server error details
            return jsonify({
                'status': 'warning',
                'releases': cache['updates'],
                'last_updated': cache['last_updated'],
                'message': f"Failed to refresh: {error}. Showing last cached version."
            })
        # If cache is empty and scraper fails, return structured HTTP 500 error code
        return jsonify({
            'status': 'error',
            'message': error,
            'releases': []
        }), 500
        
    # Standard return returning newly fetched and processed update results array
    return jsonify({
        'status': 'success',
        'releases': updates,
        'last_updated': cache['last_updated'],
        'cached': False
    })

# Verify if the module script is executed directly from process launcher
if __name__ == '__main__':
    # Read system port environment variable fallback to standard web port 5000
    port = int(os.environ.get('PORT', 5000))
    # Run the local Flask dev server on open host binding, port, with live reloading enabled
    app.run(host='0.0.0.0', port=port, debug=True)
