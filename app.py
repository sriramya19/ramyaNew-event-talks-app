import os
import logging
import urllib.request
from bs4 import BeautifulSoup
from flask import Flask, render_template, jsonify, request

app = Flask(__name__)

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

UPGRADE_NOTES_URL = "https://docs.spring.io/spring-ai/reference/upgrade-notes.html"

# Simple in-memory cache
cache = {
    'updates': None,
    'last_updated': None
}

def determine_category(title, parent_category=""):
    t = (title + " " + parent_category).lower()
    if any(word in t for word in ["advisor", "vector-store-advisor"]):
        return "Advisors"
    if any(word in t for word in ["tool", "mcp", "callback", "functioncall"]):
        return "Tool Calling & MCP"
    if any(word in t for word in ["memory", "chatmemory", "history"]):
        return "Chat Memory"
    if any(word in t for word in ["model", "ollama", "openai", "minimax", "genai", "anthropic", "vertex", "bedrock"]):
        return "Models & Providers"
    if any(word in t for word in ["converter", "schema", "output", "structured"]):
        return "Structured Output"
    if any(word in t for word in ["observability", "metric", "span", "trace", "instrument"]):
        return "Observability"
    if any(word in t for word in ["database", "db", "cosmos", "cassandra", "pgvector", "store", "vectordb"]):
        return "Vector Databases"
    if any(word in t for word in ["json", "jackson", "helper"]):
        return "JSON Utilities"
    return "General"

def parse_release_notes(html_content):
    soup = BeautifulSoup(html_content, 'html.parser')
    updates = []
    
    doc = soup.find('article', class_='doc')
    if not doc:
        doc = soup.body
        if not doc:
            return []
            
    sect1_divs = doc.find_all('div', class_='sect1')
    for sect1 in sect1_divs:
        h2 = sect1.find('h2')
        if not h2:
            continue
        version_text = h2.get_text().strip()
        # Clean version text if it contains anchor characters
        version_text = version_text.replace("Upgrading to ", "").strip()
        
        sect2_divs = sect1.find_all('div', class_='sect2')
        for sect2 in sect2_divs:
            h3 = sect2.find('h3')
            if not h3:
                continue
            category_text = h3.get_text().strip()
            category_id = h3.get('id', '')
            
            # Find direct sub-updates (sect3)
            sect3_divs = sect2.find_all('div', class_='sect3')
            
            # Check if all h4 headings in sect3 are generic (Impact, Migration, etc.)
            has_descriptive_subheadings = False
            generic_words = ["impact", "migration", "why", "detail"]
            
            for sect3 in sect3_divs:
                h4 = sect3.find('h4')
                if h4:
                    h4_text = h4.get_text().lower().strip()
                    if not any(gen in h4_text for gen in generic_words):
                        has_descriptive_subheadings = True
                        break
            
            if sect3_divs and has_descriptive_subheadings:
                # Treat each sect3 as an individual update
                for sect3 in sect3_divs:
                    h4 = sect3.find('h4')
                    if not h4:
                        continue
                    update_title = h4.get_text().strip()
                    update_id = h4.get('id', '')
                    
                    # Content is everything inside sect3 except h4
                    content_html = ""
                    for child in sect3.children:
                        if child == h4 or getattr(child, 'name', None) == 'h4':
                            continue
                        content_html += str(child)
                        
                    # Prepend parent category to categories list
                    cat = determine_category(update_title, category_text)
                    
                    updates.append({
                        'id': update_id or f"up-{len(updates)}",
                        'version': version_text,
                        'category': cat,
                        'sub_category': category_text,
                        'title': update_title,
                        'html_content': content_html.strip(),
                        'link': f"{UPGRADE_NOTES_URL}#{update_id}" if update_id else UPGRADE_NOTES_URL
                    })
            else:
                # Treat the entire sect2 as a single update
                content_html = ""
                for child in sect2.children:
                    if child == h3 or getattr(child, 'name', None) == 'h3':
                        continue
                    content_html += str(child)
                    
                cat = determine_category(category_text)
                updates.append({
                    'id': category_id or f"up-{len(updates)}",
                    'version': version_text,
                    'category': cat,
                    'sub_category': 'General',
                    'title': category_text,
                    'html_content': content_html.strip(),
                    'link': f"{UPGRADE_NOTES_URL}#{category_id}" if category_id else UPGRADE_NOTES_URL
                })
                
    return updates

def fetch_and_parse():
    try:
        logger.info(f"Fetching Spring AI Release notes from {UPGRADE_NOTES_URL}")
        req = urllib.request.Request(
            UPGRADE_NOTES_URL, 
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
        )
        with urllib.request.urlopen(req, timeout=20) as response:
            html_data = response.read().decode('utf-8')
        
        logger.info("Successfully fetched HTML. Parsing release notes...")
        updates = parse_release_notes(html_data)
        logger.info(f"Successfully parsed {len(updates)} updates.")
        
        cache['updates'] = updates
        import datetime
        cache['last_updated'] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return updates, None
    except Exception as e:
        logger.error(f"Error fetching/parsing release notes: {str(e)}")
        return [], str(e)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/releases')
def get_releases():
    force_refresh = request.args.get('force', 'false').lower() == 'true'
    
    if cache['updates'] is not None and not force_refresh:
        logger.info("Returning cached updates")
        return jsonify({
            'status': 'success',
            'releases': cache['updates'],
            'last_updated': cache['last_updated'],
            'cached': True
        })
        
    updates, error = fetch_and_parse()
    if error:
        # If fetch fails but we have cached updates, return them with warning
        if cache['updates'] is not None:
            return jsonify({
                'status': 'warning',
                'releases': cache['updates'],
                'last_updated': cache['last_updated'],
                'message': f"Failed to refresh: {error}. Showing last cached version."
            })
        return jsonify({
            'status': 'error',
            'message': error,
            'releases': []
        }), 500
        
    return jsonify({
        'status': 'success',
        'releases': updates,
        'last_updated': cache['last_updated'],
        'cached': False
    })

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True)
