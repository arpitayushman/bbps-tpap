/**
 * Crypto Engine - Decryption using Web Crypto API
 * Implements the same decryption logic as the Kotlin CryptoEngine
 * 
 * Security: All decryption happens in this JavaScript context.
 * The host app (TPAP) cannot access the decrypted data as it stays in WebView's JavaScript context.
 * 
 * SECURE RUNTIME: Only this bundle has access to the device's private key.
 * ECDH key agreement is performed entirely within this JavaScript context.
 */

class CryptoEngine {
    /**
     * Perform ECDH key agreement using device private key and sender public key
     * This is performed entirely within the Secure Runtime Bundle
     * @param {CryptoKey} devicePrivateKey - Device's private key (CryptoKey object)
     * @param {CryptoKey} senderPublicKey - Sender's ephemeral public key (CryptoKey object)
     * @returns {Promise<ArrayBuffer>} Shared secret as ArrayBuffer
     */
    static async deriveSharedSecret(devicePrivateKey, senderPublicKey) {
        try {
            // Perform ECDH key agreement
            const sharedSecret = await crypto.subtle.deriveBits(
                {
                    name: 'ECDH',
                    public: senderPublicKey
                },
                devicePrivateKey,
                256 // Derive 256 bits (32 bytes)
            );
            return sharedSecret;
        } catch (error) {
            console.error('ECDH key agreement failed:', error);
            throw new Error('ECDH key agreement failed: ' + error.message);
        }
    }

    /**
     * Import EC public key from X509/SPKI format (Base64)
     * @param {string} publicKeyBase64 - Base64 encoded X509/SPKI public key
     * @returns {Promise<CryptoKey>} Imported public key
     */
    static async importPublicKey(publicKeyBase64) {
        try {
            const keyBuffer = this.base64ToArrayBuffer(publicKeyBase64);
            const publicKey = await crypto.subtle.importKey(
                'spki',
                keyBuffer,
                {
                    name: 'ECDH',
                    namedCurve: 'P-256'
                },
                false,
                []
            );
            return publicKey;
        } catch (error) {
            console.error('Failed to import public key:', error);
            throw new Error('Failed to import public key: ' + error.message);
        }
    }

    /**
     * Import EC private key from JWK format
     * @param {Object} jwk - JWK object with kty: 'EC', crv: 'P-256', d, x, y
     * @returns {Promise<CryptoKey>} Imported private key
     */
    static async importPrivateKeyFromJwk(jwk) {
        try {
            const privateKey = await crypto.subtle.importKey(
                'jwk',
                jwk,
                {
                    name: 'ECDH',
                    namedCurve: 'P-256'
                },
                false,
                ['deriveBits']
            );
            return privateKey;
        } catch (error) {
            console.error('Failed to import private key from JWK:', error);
            throw new Error('Failed to import private key: ' + error.message);
        }
    }

    /**
     * Decrypt encrypted payload to JSON string using device private key and sender public key
     * Performs ECDH key agreement within the Secure Runtime Bundle
     * @param {string} encryptedPayload - Base64 encoded encrypted payload
     * @param {string} wrappedDek - Base64 encoded wrapped DEK
     * @param {string} iv - Base64 encoded IV
     * @param {CryptoKey} devicePrivateKey - Device's private key (CryptoKey object)
     * @param {string} senderPublicKeyBase64 - Base64 encoded sender's ephemeral public key (X509/SPKI)
     * @returns {Promise<string>} Decrypted JSON string
     */
    static async decryptToJson(
        encryptedPayload,
        wrappedDek,
        iv,
        devicePrivateKey,
        senderPublicKeyBase64
    ) {
        try {
            // Step 1: Import sender's public key
            const senderPublicKey = await this.importPublicKey(senderPublicKeyBase64);

            // Step 2: Perform ECDH key agreement to derive shared secret
            const sharedSecretBuffer = await this.deriveSharedSecret(devicePrivateKey, senderPublicKey);
            const sharedSecretBytes = new Uint8Array(sharedSecretBuffer);
            const wrappingKey = sharedSecretBytes.slice(0, 32);

            // Step 3: Derive deterministic IV for DEK unwrapping (same as Kotlin implementation)
            const wrapIvBuffer = await crypto.subtle.digest(
                'SHA-256',
                sharedSecretBytes
            );
            const wrapIvBytes = new Uint8Array(wrapIvBuffer).slice(0, 12);

            // Step 4: Unwrap the DEK
            const wrappedDekBuffer = this.base64ToArrayBuffer(wrappedDek);
            const wrappingKeyCrypto = await crypto.subtle.importKey(
                'raw',
                wrappingKey,
                {
                    name: 'AES-GCM',
                    length: 256
                },
                false,
                ['decrypt']
            );

            const dekBytes = await crypto.subtle.decrypt(
                {
                    name: 'AES-GCM',
                    iv: wrapIvBytes,
                    tagLength: 128
                },
                wrappingKeyCrypto,
                wrappedDekBuffer
            );

            // Step 5: Decrypt the payload using the unwrapped DEK
            const dek = await crypto.subtle.importKey(
                'raw',
                dekBytes,
                {
                    name: 'AES-GCM',
                    length: 256
                },
                false,
                ['decrypt']
            );

            const encryptedPayloadBuffer = this.base64ToArrayBuffer(encryptedPayload);
            const ivBuffer = this.base64ToArrayBuffer(iv);

            const decryptedPayload = await crypto.subtle.decrypt(
                {
                    name: 'AES-GCM',
                    iv: ivBuffer,
                    tagLength: 128
                },
                dek,
                encryptedPayloadBuffer
            );

            // Convert to string
            const decoder = new TextDecoder();
            return decoder.decode(decryptedPayload);
        } catch (error) {
            console.error('Decryption failed:', error);
            throw new Error('Decryption failed: ' + error.message);
        }
    }

    /**
     * Decrypt encrypted payload to JSON string using shared secret (legacy method)
     * The shared secret is derived from ECDH in Android (hardware-backed key)
     * @param {string} encryptedPayload - Base64 encoded encrypted payload
     * @param {string} wrappedDek - Base64 encoded wrapped DEK
     * @param {string} iv - Base64 encoded IV
     * @param {string} sharedSecretBase64 - Base64 encoded shared secret from ECDH (derived in Android)
     * @returns {Promise<string>} Decrypted JSON string
     * @deprecated Use decryptToJson with private key instead
     */
    static async decryptToJsonWithSharedSecret(
        encryptedPayload,
        wrappedDek,
        iv,
        sharedSecretBase64
    ) {
        try {
            // Decode shared secret
            const sharedSecretArray = this.base64ToArrayBuffer(sharedSecretBase64);
            const sharedSecretBytes = new Uint8Array(sharedSecretArray);
            const wrappingKey = sharedSecretBytes.slice(0, 32);

            // Derive deterministic IV for DEK unwrapping (same as Kotlin implementation)
            const wrapIvBuffer = await crypto.subtle.digest(
                'SHA-256',
                sharedSecretBytes
            );
            const wrapIvBytes = new Uint8Array(wrapIvBuffer).slice(0, 12);

            // Step 1: Unwrap the DEK
            const wrappedDekBuffer = this.base64ToArrayBuffer(wrappedDek);
            const wrappingKeyCrypto = await crypto.subtle.importKey(
                'raw',
                wrappingKey,
                {
                    name: 'AES-GCM',
                    length: 256
                },
                false,
                ['decrypt']
            );

            const dekBytes = await crypto.subtle.decrypt(
                {
                    name: 'AES-GCM',
                    iv: wrapIvBytes,
                    tagLength: 128
                },
                wrappingKeyCrypto,
                wrappedDekBuffer
            );

            // Step 2: Decrypt the payload using the unwrapped DEK
            const dek = await crypto.subtle.importKey(
                'raw',
                dekBytes,
                {
                    name: 'AES-GCM',
                    length: 256
                },
                false,
                ['decrypt']
            );

            const encryptedPayloadBuffer = this.base64ToArrayBuffer(encryptedPayload);
            const ivBuffer = this.base64ToArrayBuffer(iv);

            const decryptedPayload = await crypto.subtle.decrypt(
                {
                    name: 'AES-GCM',
                    iv: ivBuffer,
                    tagLength: 128
                },
                dek,
                encryptedPayloadBuffer
            );

            // Convert to string
            const decoder = new TextDecoder();
            return decoder.decode(decryptedPayload);
        } catch (error) {
            console.error('Decryption failed:', error);
            throw new Error('Decryption failed: ' + error.message);
        }
    }

    /**
     * Decrypt and parse to typed object using device private key
     * Performs ECDH key agreement within the Secure Runtime Bundle
     * @param {string} encryptedPayload
     * @param {string} wrappedDek
     * @param {string} iv
     * @param {CryptoKey} devicePrivateKey - Device's private key (CryptoKey object)
     * @param {string} senderPublicKeyBase64 - Base64 encoded sender's ephemeral public key (X509/SPKI)
     * @param {string} payloadType - 'BILL_STATEMENT' or 'PAYMENT_HISTORY'
     * @returns {Promise<Object>} Parsed decrypted object
     */
    static async decrypt(
        encryptedPayload,
        wrappedDek,
        iv,
        devicePrivateKey,
        senderPublicKeyBase64,
        payloadType
    ) {
        const json = await this.decryptToJson(
            encryptedPayload,
            wrappedDek,
            iv,
            devicePrivateKey,
            senderPublicKeyBase64
        );

        const data = JSON.parse(json);

        if (payloadType === 'PAYMENT_HISTORY') {
            return new PaymentHistoryModel(data);
        } else {
            return new BillStatementModel(data);
        }
    }

    /**
     * Decrypt and parse to typed object using shared secret (legacy method)
     * @param {string} encryptedPayload
     * @param {string} wrappedDek
     * @param {string} iv
     * @param {string} sharedSecretBase64 - Shared secret from ECDH (derived in Android)
     * @param {string} payloadType - 'BILL_STATEMENT' or 'PAYMENT_HISTORY'
     * @returns {Promise<Object>} Parsed decrypted object
     * @deprecated Use decrypt with private key instead
     */
    static async decryptWithSharedSecret(
        encryptedPayload,
        wrappedDek,
        iv,
        sharedSecretBase64,
        payloadType
    ) {
        const json = await this.decryptToJsonWithSharedSecret(
            encryptedPayload,
            wrappedDek,
            iv,
            sharedSecretBase64
        );

        const data = JSON.parse(json);

        if (payloadType === 'PAYMENT_HISTORY') {
            return new PaymentHistoryModel(data);
        } else {
            return new BillStatementModel(data);
        }
    }

    /**
     * Convert base64 string to ArrayBuffer
     * @param {string} base64
     * @returns {ArrayBuffer}
     */
    static base64ToArrayBuffer(base64) {
        // Remove padding if present
        const base64Clean = base64.replace(/[^A-Za-z0-9+/]/g, '');
        const binaryString = atob(base64Clean);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes.buffer;
    }

    /**
     * Convert ArrayBuffer to base64 string
     * @param {ArrayBuffer} buffer
     * @returns {string}
     */
    static arrayBufferToBase64(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }
}
