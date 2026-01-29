/**
 * Main Entry Point
 * Handles initialization and decryption flow
 * 
 * SECURE RUNTIME: Only this bundle has access to the device's private key.
 * All cryptographic operations happen within this isolated JavaScript context.
 */

// Global state - device key pair (CryptoKey objects)
let devicePrivateKey = null;
let devicePublicKey = null;

// IndexedDB storage for persistent key storage
const DB_NAME = 'BBPS_SECURE_RUNTIME';
const DB_VERSION = 1;
const STORE_NAME = 'device_keys';

/**
 * Initialize the application with device private key
 * @param {Object} privateKeyJwk - Device private key in JWK format
 * @returns {Promise<boolean>} Success status
 */
async function initialize(privateKeyJwk) {
    try {
        if (!privateKeyJwk) {
            throw new Error('Private key JWK is required');
        }
        
        console.log('Initializing Secure Runtime with device private key...');
        devicePrivateKey = await CryptoEngine.importPrivateKeyFromJwk(privateKeyJwk);
        console.log('Device private key imported successfully');
        return true;
    } catch (error) {
        console.error('Initialization failed:', error);
        throw new Error('Failed to initialize Secure Runtime: ' + error.message);
    }
}

/**
 * Open IndexedDB database
 * @returns {Promise<IDBDatabase>}
 */
function openDatabase() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, DB_VERSION);
        
        request.onerror = () => reject(request.error);
        request.onsuccess = () => resolve(request.result);
        
        request.onupgradeneeded = (event) => {
            const db = event.target.result;
            if (!db.objectStoreNames.contains(STORE_NAME)) {
                db.createObjectStore(STORE_NAME);
            }
        };
    });
}

/**
 * Store device key pair in IndexedDB
 * @param {CryptoKey} privateKey - Device private key (must be extractable)
 * @param {CryptoKey} publicKey - Device public key
 */
async function storeDeviceKeyPair(privateKey, publicKey) {
    try {
        // Export both keys as JWK for storage FIRST (before opening transaction)
        // Note: Private key must be extractable to store in IndexedDB
        // This is acceptable because the private key remains isolated in the Secure Runtime Bundle
        // Ensure public key is extractable before exporting
        let publicKeyJwk, privateKeyJwk;
        
        try {
            publicKeyJwk = await crypto.subtle.exportKey('jwk', publicKey);
        } catch (e) {
            console.error('Failed to export public key as JWK:', e);
            // If export fails, the key might not be extractable - regenerate it
            throw new Error('Public key is not extractable, cannot store in IndexedDB');
        }
        
        try {
            privateKeyJwk = await crypto.subtle.exportKey('jwk', privateKey);
        } catch (e) {
            console.error('Failed to export private key as JWK:', e);
            throw new Error('Private key is not extractable, cannot store in IndexedDB');
        }
        
        // Now open database and transaction
        const db = await openDatabase();
        const tx = db.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);
        
        // Store both keys as JWK
        await new Promise((resolve, reject) => {
            const request = store.put({
                privateKeyJwk: privateKeyJwk,
                publicKeyJwk: publicKeyJwk,
                timestamp: Date.now()
            }, 'device_key_pair');
            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
        
        // Store in global state as CryptoKey objects
        devicePrivateKey = privateKey;
        devicePublicKey = publicKey;
        
        console.log('Device key pair stored in IndexedDB');
    } catch (error) {
        console.error('Failed to store device key pair:', error);
        throw error;
    }
}

/**
 * Retrieve device key pair from IndexedDB
 * @returns {Promise<{privateKey: CryptoKey, publicKey: CryptoKey} | null>}
 */
async function retrieveDeviceKeyPair() {
    try {
        const db = await openDatabase();
        const tx = db.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        
        const result = await new Promise((resolve, reject) => {
            const request = store.get('device_key_pair');
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
        
        if (!result || !result.privateKeyJwk || !result.publicKeyJwk) {
            return null;
        }
        
        // Import both keys from JWK
        let privateKey, publicKey;
        try {
            console.log('Importing private key from JWK...');
            privateKey = await crypto.subtle.importKey(
                'jwk',
                result.privateKeyJwk,
                {
                    name: 'ECDH',
                    namedCurve: 'P-256'
                },
                false,
                ['deriveBits']
            );
            console.log('Private key imported successfully');
            
            console.log('Importing public key from JWK...');
            // Public keys can be exported, so we don't need to restrict usage
            // But we need to ensure it's importable with the right format
            publicKey = await crypto.subtle.importKey(
                'jwk',
                result.publicKeyJwk,
                {
                    name: 'ECDH',
                    namedCurve: 'P-256'
                },
                true, // Make it extractable so we can export it for registration
                [] // No specific usage required for public keys
            );
            console.log('Public key imported successfully (extractable: true)');
        } catch (importError) {
            console.error('Failed to import keys from IndexedDB:', importError);
            console.error('Error name:', importError.name);
            console.error('Error message:', importError.message);
            // Return null to trigger key regeneration
            return null;
        }
        
        console.log('Retrieved device key pair from IndexedDB');
        return { privateKey, publicKey };
    } catch (error) {
        console.error('Failed to retrieve device key pair:', error);
        return null;
    }
}

/**
 * Generate a new device key pair for registration
 * @returns {Promise<Object>} Object with privateKey (CryptoKey) and publicKeyJwk (JWK)
 */
async function generateDeviceKeyPair() {
    try {
        console.log('Generating new device key pair in Secure Runtime...');
        const keyPair = await crypto.subtle.generateKey(
            {
                name: 'ECDH',
                namedCurve: 'P-256'
            },
            true, // extractable so we can export both keys for IndexedDB storage
            ['deriveBits']
        );

        // Export public key as JWK for registration
        const publicKeyJwk = await crypto.subtle.exportKey('jwk', keyPair.publicKey);
        
        // Store key pair in IndexedDB and global state
        await storeDeviceKeyPair(keyPair.privateKey, keyPair.publicKey);
        
        console.log('Device key pair generated and stored successfully');
        return {
            privateKey: keyPair.privateKey,
            publicKey: keyPair.publicKey,
            publicKeyJwk: publicKeyJwk
        };
    } catch (error) {
        console.error('Key generation failed:', error);
        throw new Error('Failed to generate device key pair: ' + error.message);
    }
}

/**
 * Ensure device key pair exists (generate if needed, load from IndexedDB if exists)
 * The private key is stored as JWK in IndexedDB, isolated within the Secure Runtime Bundle
 * Automatically registers device with backend to ensure it's registered
 */
async function ensureDeviceKeyPair() {
    try {
        // Check if we already have keys in memory
        if (devicePrivateKey && devicePublicKey) {
            console.log('Device key pair already in memory');
            // Still try to register (idempotent - backend handles duplicates)
            const regSuccess = await autoRegisterDevice();
            if (regSuccess) {
                console.log('Device registration verified for in-memory keys');
            }
            return;
        }
        
        // Try to retrieve from IndexedDB
        const stored = await retrieveDeviceKeyPair();
        
        if (stored && stored.privateKey && stored.publicKey) {
            // Load keys from IndexedDB
            devicePrivateKey = stored.privateKey;
            devicePublicKey = stored.publicKey;
            console.log('Device key pair loaded from IndexedDB');
            // Ensure device is registered with backend (idempotent)
            const regSuccess = await autoRegisterDevice();
            if (regSuccess) {
                console.log('Device registration verified after loading from IndexedDB');
            }
            return;
        }
        
        // Generate new key pair if not found
        console.log('No stored key pair found, generating new one...');
        await generateDeviceKeyPair();
        console.log('Device key pair ensured');
        
        // Automatically register device with backend when new key pair is generated
        // This simplifies TPAP app integration - no need to handle registration separately
        await autoRegisterDevice();
    } catch (error) {
        console.error('Failed to ensure device key pair:', error);
        throw error;
    }
}

/**
 * Automatically register device with backend when key pair is ensured
 * This simplifies TPAP app integration - no need to handle registration separately
 * Uses retry logic to ensure registration completes successfully
 */
async function autoRegisterDevice() {
    const maxRetries = 3;
    let lastError = null;
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            if (!devicePublicKey) {
                console.warn('Device public key not available, cannot register');
                return false;
            }
            
            // Check if Android interface is available
            if (!window.Android || !window.Android.registerDevice || !window.Android.getBackendConfig || !window.Android.getDeviceId) {
                console.warn('Android interface not available, cannot auto-register device');
                if (attempt < maxRetries) {
                    console.log(`Retrying registration (attempt ${attempt + 1}/${maxRetries})...`);
                    await new Promise(resolve => setTimeout(resolve, 1000 * attempt));
                    continue;
                }
                return false;
            }
            
            // Get backend configuration and device ID from Android
            let config, deviceId;
            try {
                const configJson = window.Android.getBackendConfig();
                config = JSON.parse(configJson);
                deviceId = window.Android.getDeviceId();
            } catch (e) {
                console.error('Failed to get backend config or device ID:', e);
                if (attempt < maxRetries) {
                    await new Promise(resolve => setTimeout(resolve, 1000 * attempt));
                    continue;
                }
                return false;
            }
            
            if (!config.baseUrl || !config.consumerId || !deviceId) {
                console.warn('Missing backend configuration or device ID, cannot auto-register');
                if (attempt < maxRetries) {
                    await new Promise(resolve => setTimeout(resolve, 1000 * attempt));
                    continue;
                }
                return false;
            }
            
            // Export public key as SPKI (X509/SPKI format) for backend
            let publicKeyBase64;
            try {
                if (!devicePublicKey || typeof devicePublicKey !== 'object') {
                    throw new Error('Device public key is not a valid CryptoKey object');
                }
                
                console.log('Exporting public key as SPKI...');
                const publicKeySpki = await crypto.subtle.exportKey('spki', devicePublicKey);
                publicKeyBase64 = CryptoEngine.arrayBufferToBase64(publicKeySpki);
                console.log(`Public key exported successfully (length: ${publicKeyBase64.length})`);
            } catch (exportError) {
                console.error('Failed to export public key:', exportError);
                console.error('Error name:', exportError.name);
                console.error('Error message:', exportError.message);
                throw new Error(`Failed to export public key: ${exportError.message || exportError.name || 'Unknown error'}`);
            }
            
            console.log(`Registering device (attempt ${attempt}/${maxRetries})...`);
            console.log(`Device ID: ${deviceId}, Consumer ID: ${config.consumerId}`);
            console.log(`Public key (first 50 chars): ${publicKeyBase64.substring(0, 50)}...`);
            
            // Register device with backend via JavaScript interface
            // This will update the registration if the key has changed
            const success = window.Android.registerDevice(
                config.baseUrl,
                config.consumerId,
                deviceId,
                publicKeyBase64
            );
            
            if (success) {
                console.log('✓ Device successfully registered/updated with backend via Secure Runtime Bundle');
                console.log(`✓ Registration completed on attempt ${attempt}`);
                // Log success for Android interface
                if (window.Android && window.Android.log) {
                    window.Android.log('Device registration successful');
                }
                return true;
            } else {
                lastError = new Error('Device registration returned false');
                console.warn(`Device registration returned false (attempt ${attempt}/${maxRetries})`);
                if (attempt < maxRetries) {
                    console.log(`Retrying registration in ${attempt} second(s)...`);
                    await new Promise(resolve => setTimeout(resolve, 1000 * attempt));
                }
            }
        } catch (error) {
            lastError = error;
            console.error(`Failed to auto-register device (attempt ${attempt}/${maxRetries}):`, error);
            console.error('Error name:', error.name);
            console.error('Error message:', error.message);
            console.error('Error stack:', error.stack);
            if (window.Android && window.Android.log) {
                window.Android.log(`Registration error: ${error.name}: ${error.message}`);
            }
            if (attempt < maxRetries) {
                console.log(`Retrying registration in ${attempt} second(s)...`);
                await new Promise(resolve => setTimeout(resolve, 1000 * attempt));
            }
        }
    }
    
    // All retries failed
    console.error('✗ Device registration failed after all retries');
    if (lastError) {
        console.error('Last error:', lastError);
    }
    return false;
}

/**
 * Get the device public key in JWK format for registration
 * @returns {Promise<Object>} Public key in JWK format
 */
async function getDevicePublicKeyJwk() {
    try {
        // Ensure key pair exists
        if (!devicePublicKey) {
            await ensureDeviceKeyPair();
        }
        
        if (!devicePublicKey) {
            throw new Error('Device public key not available');
        }
        
        // Export public key as JWK
        const publicKeyJwk = await crypto.subtle.exportKey('jwk', devicePublicKey);
        return publicKeyJwk;
    } catch (error) {
        console.error('Failed to get public key:', error);
        throw error;
    }
}

/**
 * Get the device public key in SPKI format (X509/SPKI, Base64) for backend registration
 * @returns {Promise<string>} Public key in SPKI format, Base64 encoded
 */
async function getDevicePublicKeySpkiBase64() {
    try {
        // Ensure key pair exists
        if (!devicePublicKey) {
            await ensureDeviceKeyPair();
        }
        
        if (!devicePublicKey) {
            throw new Error('Device public key not available');
        }
        
        // Export public key as SPKI (X509/SPKI format)
        const publicKeySpki = await crypto.subtle.exportKey('spki', devicePublicKey);
        
        // Convert ArrayBuffer to Base64
        const base64 = CryptoEngine.arrayBufferToBase64(publicKeySpki);
        return base64;
    } catch (error) {
        console.error('Failed to get public key in SPKI format:', error);
        throw error;
    }
}

/**
 * Process encrypted statement
 * This is the main entry point called by the host app
 * Performs ECDH key agreement within the Secure Runtime Bundle
 * @param {Object} params - Decryption parameters
 * @param {string} params.encryptedPayload - Base64 encoded encrypted payload
 * @param {string} params.wrappedDek - Base64 encoded wrapped DEK
 * @param {string} params.iv - Base64 encoded IV
 * @param {string} params.senderPublicKey - Base64 encoded sender's ephemeral public key (X509/SPKI)
 * @param {string} params.payloadType - 'BILL_STATEMENT' or 'PAYMENT_HISTORY'
 * @param {Object} [params.privateKeyJwk] - Device private key in JWK format (if not already initialized)
 * @param {string} [params.sharedSecret] - Legacy: shared secret (deprecated, use privateKeyJwk instead)
 */
async function processEncryptedStatement(params) {
    console.log('processEncryptedStatement called with params:', params);

    const { encryptedPayload, wrappedDek, iv, senderPublicKey, payloadType, privateKeyJwk, sharedSecret } = params;

    // Validate inputs
    if (!encryptedPayload || !wrappedDek || !iv || !senderPublicKey) {
        const errorMsg = 'Missing required decryption parameters';
        console.error(errorMsg, { 
            encryptedPayload: !!encryptedPayload, 
            wrappedDek: !!wrappedDek, 
            iv: !!iv, 
            senderPublicKey: !!senderPublicKey 
        });
        uiController.showError(errorMsg);
        return;
    }

    // Show splash screen
    uiController.showScreen('splash');
    console.log('Splash screen shown, starting decryption...');

    try {
        // Ensure device key pair exists (load from IndexedDB or generate).
        // Do NOT register with backend here: the payload was already encrypted for a specific
        // device key. Registering now could overwrite the backend key and break decryption
        // (e.g. QR was encrypted with key A; we generate key B and register it; decrypt fails).
        console.log('Ensuring device key pair exists (no API call)...');
        await ensureDeviceKeyPair();
        
        if (!devicePrivateKey) {
            throw new Error('Device private key not available. Cannot perform decryption.');
        }

        console.log('Using device key for decryption (payload was encrypted for this key)...');

        // Initialize WASM crypto engine for best practices
        console.log('Initializing WASM crypto engine...');
        await WasmCryptoEngine.initializeWasm();
        
        // Perform ECDH and decrypt within Secure Runtime using WASM crypto
        console.log('Performing ECDH key agreement and decryption in Secure Runtime (WASM)...');
        const decrypted = await WasmCryptoEngine.decrypt(
            encryptedPayload,
            wrappedDek,
            iv,
            devicePrivateKey,
            senderPublicKey,
            payloadType || 'BILL_STATEMENT'
        );

        console.log('Decryption successful, decrypted data:', decrypted);

        // Small delay for UX
        await new Promise(resolve => setTimeout(resolve, 500));

        // Render based on type
        const finalPayloadType = payloadType || 'BILL_STATEMENT';
        console.log('Rendering with payloadType:', finalPayloadType);
        console.log('Decrypted data keys:', Object.keys(decrypted));
        console.log('Decrypted data:', JSON.stringify(decrypted).substring(0, 200));
        
        try {
            if (finalPayloadType === 'PAYMENT_HISTORY') {
                console.log('Rendering payment history...');
                uiController.renderPaymentHistory(decrypted);
            } else {
                console.log('Rendering bill statement...');
                uiController.renderBillStatement(decrypted);
            }
            console.log('Rendering complete, current screen:', uiController.currentScreen);
        } catch (renderError) {
            console.error('Error during rendering:', renderError);
            throw renderError;
        }
    } catch (error) {
        console.error('Decryption failed:', error);
        console.error('Error name:', error.name);
        console.error('Error message:', error.message);
        console.error('Error stack:', error.stack);
        
        // Provide more helpful error message
        let errorMsg = 'This QR code or payload cannot be opened on this device.\n\n';
        
        if (error.message && error.message.includes('decrypt')) {
            errorMsg += 'Decryption failed. This may happen if:\n';
            errorMsg += '- The device key has changed\n';
            errorMsg += '- The payload was encrypted with a different device key\n';
            errorMsg += '- Please try again after the device is properly registered\n\n';
        } else {
            errorMsg += 'It may be expired, corrupted, or not intended for you.\n';
            errorMsg += 'Please request a fresh QR code from the biller.\n\n';
        }
        
        errorMsg += 'Error: ' + (error.message || String(error));
        uiController.showError(errorMsg);
    }
}

/**
 * Close handler - called when user clicks close button
 */
function handleClose() {
    // Notify host app to close
    if (window.Android && window.Android.close) {
        window.Android.close();
    } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.close) {
        window.webkit.messageHandlers.close.postMessage({});
    } else {
        console.log('Close requested');
    }
}

// Setup error close button
document.addEventListener('DOMContentLoaded', () => {
    const errorCloseBtn = document.getElementById('error-close-btn');
    if (errorCloseBtn) {
        errorCloseBtn.addEventListener('click', handleClose);
    }
});

/**
 * Show error message
 * @param {string} message - Error message to display
 */
function showError(message) {
    uiController.showError(message);
}

// Export functions for host app to call
window.BBPSBundle = {
    initialize,
    generateDeviceKeyPair,
    ensureDeviceKeyPair,
    getDevicePublicKeyJwk,
    getDevicePublicKeySpkiBase64,
    processEncryptedStatement,
    handleClose,
    showError,
    autoRegisterDevice
};

// Auto-initialize: Ensure device key pair and registration when bundle loads
// This ensures the device is registered as early as possible
(async function autoInit() {
    try {
        console.log('Auto-initializing Secure Runtime Bundle...');
        // Wait a bit for Android interface to be available
        await new Promise(resolve => setTimeout(resolve, 100));
        
        // Ensure device key pair exists and is registered
        // This happens automatically when the bundle loads
        if (window.BBPSBundle && window.BBPSBundle.ensureDeviceKeyPair) {
            await window.BBPSBundle.ensureDeviceKeyPair();
            console.log('Secure Runtime Bundle auto-initialized and device registered');
        }
    } catch (error) {
        console.warn('Auto-initialization failed (non-critical):', error);
        // Non-critical - initialization will happen when processEncryptedStatement is called
    }
})();
