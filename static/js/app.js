/* ==========================================================================
   SPRING AI RELEASE PULSE - APPLICATION SCRIPT
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
    // Application State
    const state = {
        updates: [],
        selectedUpdates: new Set(),
        currentFilter: 'all',
        searchQuery: '',
        theme: localStorage.getItem('theme') || 'dark'
    };

    // DOM Elements
    const elements = {
        body: document.body,
        themeToggle: document.getElementById('theme-toggle'),
        refreshBtn: document.getElementById('refresh-btn'),
        refreshIcon: document.getElementById('refresh-icon'),
        searchBar: document.getElementById('search-bar'),
        clearSearchBtn: document.getElementById('clear-search-btn'),
        filterPills: document.getElementById('filter-pills-container'),
        timelineWrapper: document.getElementById('timeline-wrapper'),
        loadingState: document.getElementById('loading-state'),
        errorState: document.getElementById('error-state'),
        errorMessageText: document.getElementById('error-message-text'),
        emptyState: document.getElementById('empty-state'),
        retryBtn: document.getElementById('retry-btn'),
        
        // Stats Values
        valLatestVersion: document.getElementById('val-latest-version'),
        valTotalUpdates: document.getElementById('val-total-updates'),
        valToolingUpdates: document.getElementById('val-tooling-updates'),
        valDbUpdates: document.getElementById('val-db-updates'),
        
        // Tweet Drawer
        tweetDrawer: document.getElementById('tweet-drawer'),
        selectedBadge: document.getElementById('selected-badge'),
        closeDrawerBtn: document.getElementById('close-drawer-btn'),
        tweetTextarea: document.getElementById('tweet-textarea'),
        progressFill: document.getElementById('progress-fill'),
        charCountText: document.getElementById('char-count-text'),
        clearSelectionBtn: document.getElementById('clear-selection-btn'),
        copyTweetBtn: document.getElementById('copy-tweet-btn'),
        postXBtn: document.getElementById('post-x-btn'),
        exportCsvBtn: document.getElementById('export-csv-btn'),
        
        toastContainer: document.getElementById('toast-container')
    };

    // Initialize Theme
    initTheme();
    
    // Initial Fetch
    fetchUpdates(false);

    // Event Listeners
    elements.themeToggle.addEventListener('click', toggleTheme);
    elements.refreshBtn.addEventListener('click', () => fetchUpdates(true));
    elements.retryBtn.addEventListener('click', () => fetchUpdates(true));
    elements.searchBar.addEventListener('input', handleSearch);
    elements.clearSearchBtn.addEventListener('click', clearSearch);
    elements.closeDrawerBtn.addEventListener('click', () => toggleDrawer(false));
    elements.clearSelectionBtn.addEventListener('click', clearAllSelections);
    elements.copyTweetBtn.addEventListener('click', copyTweetToClipboard);
    elements.postXBtn.addEventListener('click', postTweetToX);
    elements.exportCsvBtn.addEventListener('click', exportToCSV);
    elements.tweetTextarea.addEventListener('input', updateCharProgressBar);

    // Filter Pills Event Delegation
    elements.filterPills.addEventListener('click', (e) => {
        const pill = e.target.closest('.pill');
        if (!pill) return;
        
        elements.filterPills.querySelectorAll('.pill').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        
        state.currentFilter = pill.dataset.filter;
        renderTimeline();
    });

    // ==========================================
    // BACKEND DATA SYNC
    // ==========================================

    async function fetchUpdates(force = false) {
        showState('loading');
        elements.refreshIcon.classList.add('spinning');
        elements.refreshBtn.disabled = true;

        try {
            const url = force ? '/api/releases?force=true' : '/api/releases';
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`Server returned HTTP ${response.status}`);
            }
            const data = await response.json();
            
            if (data.status === 'success' || data.status === 'warning') {
                state.updates = data.releases;
                calculateStats();
                renderTimeline();
                showState('content');
                
                if (data.status === 'warning') {
                    showToast(data.message, 'error');
                } else {
                    const cacheMsg = data.cached ? 'Loaded cached release notes' : 'Scraped live release notes';
                    showToast(cacheMsg, 'success');
                }
            } else {
                throw new Error(data.message || 'Unknown server error');
            }
        } catch (error) {
            console.error('Error Syncing release notes:', error);
            elements.errorMessageText.textContent = `Scrape Error: ${error.message}`;
            showState('error');
            showToast('Failed to sync release notes', 'error');
        } finally {
            elements.refreshIcon.classList.remove('spinning');
            elements.refreshBtn.disabled = false;
        }
    }

    // ==========================================
    // STATISTICS CALCULATION
    // ==========================================

    function calculateStats() {
        if (state.updates.length === 0) {
            elements.valLatestVersion.textContent = 'N/A';
            elements.valTotalUpdates.textContent = '0';
            elements.valToolingUpdates.textContent = '0';
            elements.valDbUpdates.textContent = '0';
            return;
        }

        // The latest release is the first one parsed
        elements.valLatestVersion.textContent = state.updates[0].version;
        elements.valTotalUpdates.textContent = state.updates.length;

        const tooling = state.updates.filter(u => 
            u.category === 'Advisors' || u.category === 'Tool Calling & MCP'
        ).length;
        
        const dbs = state.updates.filter(u => u.category === 'Vector Databases').length;

        elements.valToolingUpdates.textContent = tooling;
        elements.valDbUpdates.textContent = dbs;
    }

    // ==========================================
    // TIMELINE RENDERER
    // ==========================================

    function renderTimeline() {
        const filtered = filterUpdates();
        
        if (filtered.length === 0) {
            showState('empty');
            return;
        }

        showState('content');
        elements.timelineWrapper.innerHTML = '';

        // Group updates by Version
        const groups = {};
        filtered.forEach(update => {
            if (!groups[update.version]) {
                groups[update.version] = [];
            }
            groups[update.version].push(update);
        });

        // Generate timeline UI elements
        Object.entries(groups).forEach(([version, updates]) => {
            const groupDiv = document.createElement('div');
            groupDiv.className = 'timeline-group';

            // Version marker
            const marker = document.createElement('div');
            marker.className = 'timeline-version-marker';
            marker.innerHTML = `
                <div class="version-dot"></div>
                <div class="version-title">Version ${version}</div>
            `;
            groupDiv.appendChild(marker);

            // Card list container
            const cardsList = document.createElement('div');
            cardsList.className = 'cards-list';

            updates.forEach(update => {
                const card = document.createElement('div');
                const isSelected = state.selectedUpdates.has(update.id);
                card.className = `release-card ${isSelected ? 'selected' : ''}`;
                card.dataset.id = update.id;

                const badgeClass = getBadgeClass(update.category);

                card.innerHTML = `
                    <div class="checkbox-column">
                        <div class="custom-checkbox" aria-label="Select update to tweet">
                            <i class="fa-solid fa-check"></i>
                        </div>
                    </div>
                    <div class="card-body">
                        <div class="card-header">
                            <div class="card-meta">
                                <span class="badge ${badgeClass}">${update.category}</span>
                                <span class="sub-category-tag">${update.sub_category}</span>
                            </div>
                            <div class="card-actions">
                                <button class="btn-inline-action copy-action" title="Copy update text">
                                    <i class="fa-regular fa-copy"></i>
                                </button>
                                <button class="btn-inline-action tweet-action" title="Tweet this update">
                                    <i class="fa-brands fa-x-twitter"></i>
                                </button>
                            </div>
                        </div>
                        <h3 class="card-title">${update.title}</h3>
                        <div class="card-text">
                            ${update.html_content}
                        </div>
                    </div>
                `;

                // Card Click - toggle selection (prevent triggers on interactive elements)
                card.addEventListener('click', (e) => {
                    if (e.target.closest('a') || e.target.closest('.btn-inline-action') || e.target.closest('pre') || e.target.closest('code')) {
                        return; 
                    }
                    toggleCardSelection(update.id);
                });

                // Copy single action click
                card.querySelector('.copy-action').addEventListener('click', (e) => {
                    e.stopPropagation();
                    const cleanText = `${update.title}\n\n${cleanHtml(update.html_content)}`;
                    copyToClipboard(cleanText);
                    showToast('Copied update to clipboard', 'info');
                });

                // Tweet single action click
                card.querySelector('.tweet-action').addEventListener('click', (e) => {
                    e.stopPropagation();
                    const tweetTxt = formatSingleTweet(update);
                    const tweetUrl = `https://x.com/intent/tweet?text=${encodeURIComponent(tweetTxt)}`;
                    window.open(tweetUrl, '_blank');
                });

                cardsList.appendChild(card);
            });

            groupDiv.appendChild(cardsList);
            elements.timelineWrapper.appendChild(groupDiv);
        });
    }

    function filterUpdates() {
        return state.updates.filter(update => {
            // Pill filter match
            if (state.currentFilter !== 'all' && update.category !== state.currentFilter) {
                return false;
            }
            
            // Search query match
            if (state.searchQuery.trim() !== '') {
                const query = state.searchQuery.toLowerCase();
                const inTitle = update.title.toLowerCase().includes(query);
                const inContent = update.html_content.toLowerCase().includes(query);
                const inCategory = update.category.toLowerCase().includes(query);
                const inSubCategory = update.sub_category.toLowerCase().includes(query);
                const inVersion = update.version.toLowerCase().includes(query);
                
                return inTitle || inContent || inCategory || inSubCategory || inVersion;
            }

            return true;
        });
    }

    function getBadgeClass(category) {
        switch (category) {
            case 'Advisors': return 'badge-advisors';
            case 'Tool Calling & MCP': return 'badge-tooling';
            case 'Models & Providers': return 'badge-models';
            default: return 'badge-general';
        }
    }

    // ==========================================
    // INTERACTIVE SELECTION & DRAWER LOGIC
    // ==========================================

    function toggleCardSelection(updateId) {
        if (state.selectedUpdates.has(updateId)) {
            state.selectedUpdates.delete(updateId);
        } else {
            state.selectedUpdates.add(updateId);
        }

        // Fast DOM toggle
        const card = elements.timelineWrapper.querySelector(`.release-card[data-id="${updateId}"]`);
        if (card) {
            card.classList.toggle('selected');
        }

        updateTweetComposer();
    }

    function clearAllSelections() {
        state.selectedUpdates.clear();
        elements.timelineWrapper.querySelectorAll('.release-card').forEach(c => {
            c.classList.remove('selected');
        });
        updateTweetComposer();
        showToast('Cleared all selections', 'info');
    }

    function updateTweetComposer() {
        const count = state.selectedUpdates.size;
        elements.selectedBadge.textContent = `${count} selected`;

        if (count === 0) {
            toggleDrawer(false);
            return;
        }

        toggleDrawer(true);

        const selectedItems = state.updates.filter(u => state.selectedUpdates.has(u.id));
        let composedText = '';

        if (selectedItems.length === 1) {
            composedText = formatSingleTweet(selectedItems[0]);
        } else {
            // Thread/Bullet list consolidation
            const version = selectedItems[0].version;
            composedText = `Spring AI Release Updates (v${version}):\n`;
            
            selectedItems.forEach((u, idx) => {
                const bullet = `\n${idx + 1}. [${u.category}] ${u.title}`;
                composedText += bullet;
            });
            
            // Add a link footer
            composedText += `\n\nRead the full details at docs.spring.io/spring-ai`;
        }

        elements.tweetTextarea.value = composedText;
        updateCharProgressBar();
    }

    function formatSingleTweet(update) {
        const cleanContent = cleanHtml(update.html_content);
        // Truncate cleanly for single tweet to keep under X limit if content is too long
        let text = cleanContent;
        if (text.length > 180) {
            text = text.substring(0, 177) + '...';
        }
        return `Spring AI Update (v${update.version}):\n\n[${update.category}] ${update.title}\n${text}\n\nDocs: ${update.link}`;
    }

    function updateCharProgressBar() {
        const text = elements.tweetTextarea.value;
        const count = text.length;
        elements.charCountText.textContent = `${count} / 280`;

        const percent = Math.min((count / 280) * 100, 100);
        elements.progressFill.style.width = `${percent}%`;

        // Update colors depending on remaining character threshold
        const container = elements.charCountText.parentNode;
        container.className = 'char-details';
        elements.progressFill.style.backgroundColor = 'var(--primary)';

        if (count > 280) {
            container.classList.add('danger');
            elements.progressFill.style.backgroundColor = 'var(--accent-red)';
        } else if (count > 240) {
            container.classList.add('warning');
            elements.progressFill.style.backgroundColor = 'var(--accent-orange)';
        }
    }

    function toggleDrawer(open) {
        if (open) {
            elements.tweetDrawer.classList.remove('collapsed');
        } else {
            elements.tweetDrawer.classList.add('collapsed');
        }
    }

    function postTweetToX() {
        const text = elements.tweetTextarea.value;
        if (text.length > 280) {
            if (!confirm(`Your text contains ${text.length} characters (exceeds 280-char limit). It will be truncated on X/Twitter. Post anyway?`)) {
                return;
            }
        }
        const tweetUrl = `https://x.com/intent/tweet?text=${encodeURIComponent(text)}`;
        window.open(tweetUrl, '_blank');
    }

    function copyTweetToClipboard() {
        copyToClipboard(elements.tweetTextarea.value);
        showToast('Tweet copied to clipboard!', 'success');
    }

    // ==========================================
    // CONTROLS & UTILITIES
    // ==========================================

    function handleSearch(e) {
        state.searchQuery = e.target.value;
        if (state.searchQuery.trim() !== '') {
            elements.clearSearchBtn.style.display = 'block';
        } else {
            elements.clearSearchBtn.style.display = 'none';
        }
        renderTimeline();
    }

    function clearSearch() {
        elements.searchBar.value = '';
        state.searchQuery = '';
        elements.clearSearchBtn.style.display = 'none';
        renderTimeline();
    }

    function copyToClipboard(text) {
        const el = document.createElement('textarea');
        el.value = text;
        document.body.appendChild(el);
        el.select();
        document.execCommand('copy');
        document.body.removeChild(el);
    }

    function cleanHtml(htmlStr) {
        const temp = document.createElement('div');
        temp.innerHTML = htmlStr;
        
        // Strip out pre blocks/code blocks from text to keep tweet concise
        temp.querySelectorAll('.listingblock, pre').forEach(el => el.remove());
        
        // Replace inner codes with backticks to look readable on Twitter
        temp.querySelectorAll('code').forEach(c => {
            c.replaceWith(`\`${c.textContent}\``);
        });

        let text = temp.textContent || temp.innerText || "";
        // Replace consecutive newlines or whitespaces
        return text.replace(/\s+/g, ' ').trim();
    }

    function showState(mode) {
        elements.loadingState.classList.add('hidden');
        elements.errorState.classList.add('hidden');
        elements.emptyState.classList.add('hidden');
        elements.timelineWrapper.classList.add('hidden');

        if (mode === 'loading') elements.loadingState.classList.remove('hidden');
        else if (mode === 'error') elements.errorState.classList.remove('hidden');
        else if (mode === 'empty') elements.emptyState.classList.remove('hidden');
        else if (mode === 'content') elements.timelineWrapper.classList.remove('hidden');
    }

    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        
        let icon = 'fa-circle-info';
        if (type === 'success') icon = 'fa-circle-check';
        if (type === 'error') icon = 'fa-triangle-exclamation';

        toast.innerHTML = `
            <i class="fa-solid ${icon}"></i>
            <span>${message}</span>
        `;
        
        elements.toastContainer.appendChild(toast);
        
        // Exit animation timer
        setTimeout(() => {
            toast.style.transition = 'opacity 0.4s ease, transform 0.4s ease';
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(50px)';
            setTimeout(() => toast.remove(), 400);
        }, 4000);
    }

    function exportToCSV() {
        const filtered = filterUpdates();
        if (filtered.length === 0) {
            showToast('No updates to export', 'error');
            return;
        }

        const headers = ['Version', 'Category', 'Sub-Category', 'Title', 'Description', 'Link'];
        
        const rows = filtered.map(u => [
            u.version,
            u.category,
            u.sub_category,
            u.title,
            cleanHtml(u.html_content),
            u.link
        ]);

        const csvContent = [
            headers.join(','),
            ...rows.map(row => row.map(val => `"${String(val).replace(/"/g, '""')}"`).join(','))
        ].join('\n');

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        
        link.setAttribute('href', url);
        link.setAttribute('download', `spring_ai_release_notes.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        showToast(`Exported ${filtered.length} updates to CSV`, 'success');
    }

    // ==========================================
    // THEME SWITCHER
    // ==========================================

    function initTheme() {
        if (state.theme === 'light') {
            elements.body.classList.remove('dark-theme');
            elements.body.classList.add('light-theme');
            elements.themeToggle.innerHTML = '<i class="fa-solid fa-sun"></i>';
        } else {
            elements.body.classList.add('dark-theme');
            elements.body.classList.remove('light-theme');
            elements.themeToggle.innerHTML = '<i class="fa-solid fa-moon"></i>';
        }
    }

    function toggleTheme() {
        if (elements.body.classList.contains('dark-theme')) {
            elements.body.classList.remove('dark-theme');
            elements.body.classList.add('light-theme');
            elements.themeToggle.innerHTML = '<i class="fa-solid fa-sun"></i>';
            state.theme = 'light';
        } else {
            elements.body.classList.add('dark-theme');
            elements.body.classList.remove('light-theme');
            elements.themeToggle.innerHTML = '<i class="fa-solid fa-moon"></i>';
            state.theme = 'dark';
        }
        localStorage.setItem('theme', state.theme);
        showToast(`Theme switched to ${state.theme} mode`, 'info');
    }
});
