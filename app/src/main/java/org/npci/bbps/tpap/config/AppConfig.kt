package org.npci.bbps.tpap.config

/**
 * Application configuration
 * Centralizes backend URL and other environment-specific settings
 */
object AppConfig {
    
    // Backend URLs for different environments
    // Change these based on your deployment
    
    // Local development (computer's local IP)
    private const val LOCAL_BACKEND_URL = "http://192.168.10.74:8111"
    
    // Emulator (special Android emulator host)
    private const val EMULATOR_BACKEND_URL = "http://10.0.2.2:8111"
    
    // AWS Production (replace with your actual AWS backend URL)
    // For HTTPS: "https://your-api-domain.aws-region.amazonaws.com"
    // For HTTP: "http://your-ec2-ip:8111" or "http://your-alb-domain:8111"
    private const val AWS_BACKEND_URL = "http://ec2-3-109-54-89.ap-south-1.compute.amazonaws.com:8111"

    // Current environment - Change this to switch between environments
    enum class Environment {
        LOCAL,      // Local development on your computer
        EMULATOR,   // Android emulator
        AWS         // AWS production/staging
    }
    
    // Set your current environment here
    private val currentEnvironment = Environment.AWS;
    
    /**
     * Get the backend base URL based on current environment
     */
    val baseUrl: String
        get() = when (currentEnvironment) {
            Environment.LOCAL -> LOCAL_BACKEND_URL
            Environment.EMULATOR -> EMULATOR_BACKEND_URL
            Environment.AWS -> AWS_BACKEND_URL
        }
    
    /**
     * Consumer ID (currently hardcoded, can be made dynamic)
     */
    val consumerId: String = "C12345"
    
    /**
     * Statement ID (currently hardcoded, can be made dynamic)
     */
    val statementId: String = "STMT123"
}
