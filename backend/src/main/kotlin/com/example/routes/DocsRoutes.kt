package com.example.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.docsRoutes() {
    get("/docs") {
        val html = """
            <!DOCTYPE html>
            <html lang=\"en\">
            <head>
                <meta charset=\"utf-8\" />
                <title>API Docs</title>
                <link rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui.css\" />
            </head>
            <body>
                <div id=\"swagger-ui\"></div>
                <script src=\"https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js\"></script>
                <script>
                    window.onload = () => {
                      window.ui = SwaggerUIBundle({
                        url: '/openapi.yaml',
                        dom_id: '#swagger-ui',
                        presets: [SwaggerUIBundle.presets.apis],
                      });
                    };
                </script>
            </body>
            </html>
        """.trimIndent()

        call.respondText(html, ContentType.Text.Html)
    }

    get("/openapi.yaml") {
        call.respondFile(File("openapi.yaml"))
    }
}