package com.swagger.curl.controller;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/swagger")
public class SwaggerToCurlController {

    @PostMapping(value = "/generate-curl", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> generateCurl(@RequestParam("file") MultipartFile file) {
        try {
            String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);

            // Parse the Swagger (OpenAPI) file
            SwaggerParseResult parseResult = new OpenAPIParser().readContents(fileContent, null, null);
            OpenAPI openAPI = parseResult.getOpenAPI();

            if (openAPI == null || openAPI.getPaths() == null) {
                throw new IllegalArgumentException("Invalid Swagger file.");
            }

            // Generate Postman-compatible cURL commands
            return generateCurlCommands(openAPI);
        } catch (Exception e) {
            return Collections.singletonMap("error", "Failed to process file: " + e.getMessage());
        }
    }

    private Map<String, String> generateCurlCommands(OpenAPI openAPI) {
        Map<String, String> curlCommands = new LinkedHashMap<>();
        String baseUrl = getBaseUrl(openAPI);

        openAPI.getPaths().forEach((path, pathItem) -> {
            pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                String url = baseUrl + path;
                StringBuilder curlCommand = new StringBuilder("curl -X " + httpMethod.toString().toUpperCase() + " \"" + url + "\"");

                // Add query parameters from the operation if parameters exist
                List<Parameter> parameters = operation.getParameters();
                if (parameters != null) {
                    List<String> queryParams = new ArrayList<>();
                    parameters.forEach(param -> {
                        if ("query".equals(param.getIn())) {
                            String paramName = param.getName();
                            String paramValue = param.getSchema() != null && param.getSchema().getDefault() != null
                                    ? param.getSchema().getDefault().toString()
                                    : "undefined";  // Handle undefined default values

                            // If the status enum is available, we can choose one of them or leave it as is
                            if ("status".equals(paramName)) {
                                queryParams.add(paramName + "=" + paramValue);
                            }
                        }
                    });

                    // Add query parameters to the URL if any
                    if (!queryParams.isEmpty()) {
                        url += "?" + String.join("&", queryParams);
                    }
                }

                // Complete the curl command
                curlCommand.append(" -H \"Content-Type: application/json\"");

                // Add Authorization if security is defined
                if (openAPI.getComponents() != null && openAPI.getComponents().getSecuritySchemes() != null) {
                    curlCommand.append(" -H \"Authorization: Bearer <token>\"");
                }

                // Add a dummy JSON body for POST and PUT requests
                if (httpMethod.toString().equalsIgnoreCase("POST") || httpMethod.toString().equalsIgnoreCase("PUT")) {
                    curlCommand.append(" -d '{ \"key\": \"value\" }'");
                }

                curlCommands.put(operation.getOperationId(), curlCommand.toString());
            });
        });

        return curlCommands;
    }



    private String getBaseUrl(OpenAPI openAPI) {
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            return openAPI.getServers().get(0).getUrl(); // Use the first server URL
        }
        return "https://example.com/api"; // Default base URL if not provided
    }
}
