plugins {
    kotlin("jvm") version "2.4.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Force a patched Jackson across all its modules. anthropic-java pulls in
    // jackson-bom:2.18.2 transitively, which flags WS-2026-0003 on jackson-core.
    // Staying on the latest 2.18.x patch keeps the minor line the SDK was built for.
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.18.9"))
    implementation("com.anthropic:anthropic-java:2.34.0")
    implementation("com.openai:openai-java:4.41.0")
    implementation("com.google.genai:google-genai:1.60.0")
    // Anthropic Claude via Amazon Bedrock (_01/_02 in demo.api.aws): the only provider here
    // authenticated with AWS SigV4 request signing rather than a single bearer-token key, so it
    // needs the AWS SDK (v2) instead of a lightweight HTTP call.
    implementation("software.amazon.awssdk:bedrockruntime:2.49.1")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.14.0")
    // Ktor HTTP engine for the MCP Streamable-HTTP (remote) transport used in _19.
    // The SDK brings ktor-client-core (with the SSE plugin) but no engine.
    implementation("io.ktor:ktor-client-cio:3.4.3")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}