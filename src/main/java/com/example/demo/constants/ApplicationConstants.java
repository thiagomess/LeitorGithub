package com.example.demo.constants;

/**
 * Classe para centralizar constantes da aplicação.
 */
public final class ApplicationConstants {

    private ApplicationConstants() {
        // Construtor privado para evitar instanciação
    }

    // Endpoints de chat
    public static final String CHAT_ENDPOINT_CONTROLLER = "https://genai-inference-app.stackspot.com/v1/agent/01JZ9J6GT997JENKZ9VH77F0TY/chat";

    public static final String CHAT_ENDPOINT_TEST_UNIT = "https://genai-inference-app.stackspot.com/v1/agent/01JY5RF8WWG0V3H32KMNTDGF25/chat";

    // GitHub
    public static final String DEFAULT_GITHUB_REPO_URL = "https://github.com/thiagomess/resource-service/archive/refs/heads/main.zip";

    public static final String DEFAULT_DOWNLOAD_PATH = "repo.zip";

    // OAuth2
    public static final String DEFAULT_TOKEN_URL = "https://idm.stackspot.com/stackspot-freemium/oidc/oauth/token";

    // Upload
    public static final String FILE_UPLOAD_ENDPOINT = "https://data-integration-api.stackspot.com/v2/file-upload/form";

    // Mensagens
    public static final String ENDPOINT_ALREADY_EXISTS = "Já existe o endpoint com o escopo informado.\nClasse: ";

    // Paths
    public static final String DEFAULT_TEST_PATH = "/src/test/java";
}
