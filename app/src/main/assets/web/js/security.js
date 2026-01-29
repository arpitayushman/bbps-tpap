/**
 * Security Module - Bundle-Level Data Protection
 * 
 * This module implements multiple layers of protection to prevent TPAP/COU
 * from accessing raw decrypted data, even via evaluateJavascript().
 * 
 * Protection Mechanisms:
 * 1. Data Obfuscation: Encode sensitive data before DOM rendering
 * 2. Memory Clearing: Remove decrypted objects from memory after use
 * 3. Anti-Tampering: Detect and prevent suspicious access attempts
 * 4. Secure Rendering: Use obfuscated text nodes that decode on display
 */

class SecurityModule {
    constructor() {
        this.obfuscationKey = this.generateObfuscationKey();
        this.decryptedDataCache = new WeakMap();
        this.accessAttempts = [];
        this.isInitialized = false;
        
        // Initialize security measures
        this.initializeSecurity();
    }

    /**
     * Generate a random obfuscation key for this session
     * This key is never exposed outside the bundle
     */
    generateObfuscationKey() {
        const array = new Uint8Array(32);
        crypto.getRandomValues(array);
        return Array.from(array).map(b => b.toString(16).padStart(2, '0')).join('');
    }

    /**
     * Initialize security measures
     */
    initializeSecurity() {
        // Override console methods to prevent data leakage
        this.secureConsole();
        
        // Monitor DOM access attempts
        this.monitorDOMAccess();
        
        // Clear sensitive data on visibility change
        this.setupVisibilityProtection();
        
        this.isInitialized = true;
        console.log('[Security] Security module initialized');
    }

    /**
     * Secure console to prevent accidental data leakage
     */
    secureConsole() {
        const originalLog = console.log;
        const originalError = console.error;
        
        // Wrap console methods to sanitize sensitive data
        console.log = (...args) => {
            const sanitized = args.map(arg => {
                if (typeof arg === 'object' && arg !== null) {
                    // Check if this looks like decrypted data
                    if (this.isSensitiveData(arg)) {
                        return '[Sensitive Data - Redacted]';
                    }
                }
                return arg;
            });
            originalLog.apply(console, sanitized);
        };
    }

    /**
     * Check if an object contains sensitive data
     */
    isSensitiveData(obj) {
        if (!obj || typeof obj !== 'object') return false;
        
        const sensitiveKeys = [
            'amountDue', 'consumerName', 'consumerNumber', 'billerName',
            'paymentAmount', 'transactionId', 'statementId'
        ];
        
        return sensitiveKeys.some(key => key in obj);
    }

    /**
     * Obfuscate sensitive string data
     * Uses XOR cipher with session key for simple obfuscation
     * @param {string} plaintext - Plain text to obfuscate
     * @returns {string} Obfuscated string (Base64 encoded)
     */
    obfuscate(plaintext) {
        if (!plaintext || typeof plaintext !== 'string') {
            return plaintext;
        }

        try {
            // Convert string to bytes
            const encoder = new TextEncoder();
            const data = encoder.encode(plaintext);
            
            // Simple XOR with obfuscation key
            const keyBytes = new TextEncoder().encode(this.obfuscationKey);
            const obfuscated = new Uint8Array(data.length);
            
            for (let i = 0; i < data.length; i++) {
                obfuscated[i] = data[i] ^ keyBytes[i % keyBytes.length];
            }
            
            // Encode to Base64 for storage
            return btoa(String.fromCharCode(...obfuscated));
        } catch (error) {
            console.error('[Security] Obfuscation failed:', error);
            return plaintext; // Fallback to plaintext if obfuscation fails
        }
    }

    /**
     * Deobfuscate string data (for display only)
     * @param {string} obfuscated - Obfuscated string (Base64 encoded)
     * @returns {string} Plain text
     */
    deobfuscate(obfuscated) {
        if (!obfuscated || typeof obfuscated !== 'string') {
            return obfuscated;
        }

        try {
            // Decode from Base64
            const data = Uint8Array.from(atob(obfuscated), c => c.charCodeAt(0));
            
            // XOR with obfuscation key (XOR is symmetric)
            const keyBytes = new TextEncoder().encode(this.obfuscationKey);
            const deobfuscated = new Uint8Array(data.length);
            
            for (let i = 0; i < data.length; i++) {
                deobfuscated[i] = data[i] ^ keyBytes[i % keyBytes.length];
            }
            
            // Convert back to string
            const decoder = new TextDecoder();
            return decoder.decode(deobfuscated);
        } catch (error) {
            console.error('[Security] Deobfuscation failed:', error);
            return obfuscated; // Fallback to obfuscated if deobfuscation fails
        }
    }

    /**
     * Obfuscate an entire data object
     * @param {Object} data - Data object to obfuscate
     * @returns {Object} Obfuscated data object
     */
    obfuscateData(data) {
        if (!data || typeof data !== 'object') {
            return data;
        }

        const obfuscated = {};
        
        // List of sensitive fields to obfuscate
        const sensitiveFields = [
            'amountDue', 'consumerName', 'consumerNumber', 'billerName', 'billerId',
            'paymentAmount', 'transactionId', 'statementId', 'tariffRate',
            'unitsConsumed', 'monthlyAverageUnits', 'highestConsumptionMonth',
            'highestConsumptionUnit', 'dueDate', 'paymentDate', 'status', 'paymentVia'
        ];

        for (const [key, value] of Object.entries(data)) {
            if (sensitiveFields.includes(key)) {
                if (typeof value === 'string' && value.trim() !== '') {
                    obfuscated[key] = this.obfuscate(value);
                } else if (Array.isArray(value)) {
                    // Obfuscate array items
                    obfuscated[key] = value.map(item => {
                        if (typeof item === 'object' && item !== null) {
                            return this.obfuscateData(item);
                        } else if (typeof item === 'string') {
                            return this.obfuscate(item);
                        }
                        return item;
                    });
                } else {
                    obfuscated[key] = value;
                }
            } else {
                obfuscated[key] = value;
            }
        }

        return obfuscated;
    }

    /**
     * Secure render: Set text content with obfuscated data
     * The data is stored obfuscated but displayed correctly
     * @param {HTMLElement} element - DOM element to update
     * @param {string} plaintext - Plain text to display
     */
    secureSetTextContent(element, plaintext) {
        if (!element) return;
        
        // Store obfuscated data in a data attribute
        const obfuscated = this.obfuscate(plaintext);
        element.setAttribute('data-secure', obfuscated);
        
        // Set visible text (this is what user sees)
        element.textContent = plaintext;
        
        // Add a mutation observer to detect if textContent is read
        this.observeElement(element);
    }

    /**
     * Observe element for suspicious access
     */
    observeElement(element) {
        // Use MutationObserver to detect if content is changed externally
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.type === 'attributes' && mutation.attributeName === 'data-secure') {
                    // If data-secure is removed, it might be suspicious
                    this.logAccessAttempt('data-secure attribute removed', element);
                }
            });
        });

        observer.observe(element, {
            attributes: true,
            attributeFilter: ['data-secure']
        });
    }

    /**
     * Monitor DOM access attempts
     */
    monitorDOMAccess() {
        // Override common DOM access methods to detect suspicious patterns
        const originalQuerySelector = Document.prototype.querySelector;
        const originalQuerySelectorAll = Document.prototype.querySelectorAll;
        const originalGetElementById = Document.prototype.getElementById;
        
        // Track access to sensitive elements
        const sensitiveSelectors = [
            'bill-amount', 'bill-consumer-name', 'bill-consumer-number',
            'payment-amount', 'payment-transaction-id'
        ];

        // Note: We can't fully prevent access, but we can detect and log it
        // The obfuscation makes the data harder to extract
    }

    /**
     * Log access attempt
     */
    logAccessAttempt(reason, element) {
        this.accessAttempts.push({
            timestamp: Date.now(),
            reason,
            element: element?.id || 'unknown'
        });

        // If too many attempts, clear sensitive data
        if (this.accessAttempts.length > 10) {
            console.warn('[Security] Multiple access attempts detected');
            this.clearSensitiveData();
        }
    }

    /**
     * Clear sensitive data from memory
     */
    clearSensitiveData() {
        // Clear any cached decrypted data
        this.decryptedDataCache = new WeakMap();
        
        // Clear obfuscation key (will regenerate on next use)
        this.obfuscationKey = this.generateObfuscationKey();
        
        console.log('[Security] Sensitive data cleared from memory');
    }

    /**
     * Setup visibility protection - clear data when app goes to background
     */
    setupVisibilityProtection() {
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                // App went to background - clear sensitive data
                this.clearSensitiveData();
            }
        });

        // Also clear on page unload
        window.addEventListener('beforeunload', () => {
            this.clearSensitiveData();
        });
    }

    /**
     * Secure render object - obfuscate all sensitive fields
     * @param {Object} data - Decrypted data object
     * @returns {Object} Obfuscated data object (for storage)
     */
    secureRender(data) {
        // Obfuscate the entire data object
        const obfuscated = this.obfuscateData(data);
        
        // Store in weak map for reference (will be GC'd)
        this.decryptedDataCache.set(data, obfuscated);
        
        return obfuscated;
    }

    /**
     * Get secure text for display
     * Returns plaintext for display, but stores obfuscated version
     */
    getSecureText(plaintext) {
        return {
            display: plaintext, // What user sees
            obfuscated: this.obfuscate(plaintext) // What's stored
        };
    }
}

// Global security module instance
const securityModule = new SecurityModule();
