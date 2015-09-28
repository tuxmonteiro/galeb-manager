/*
 *   Galeb - Load Balance as a Service Plataform
 *
 *   Copyright (C) 2014-2015 Globo.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.galeb.manager.engine.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.galeb.core.model.Backend;
import io.galeb.core.model.BackendPool;
import io.galeb.core.model.Rule;
import io.galeb.core.model.VirtualHost;
import io.galeb.manager.common.LoggerUtils;
import io.galeb.manager.entity.AbstractEntity;
import io.galeb.manager.entity.WithParent;
import io.galeb.manager.entity.WithParents;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.galeb.manager.common.Properties;
import io.galeb.manager.engine.Driver;

public class GalebV3Driver implements Driver {

    public static final String DRIVER_NAME = GalebV3Driver.class.getSimpleName()
                                                                .replaceAll("Driver", "");

    private static final Log LOGGER = LogFactory.getLog(GalebV3Driver.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String toString() {
        return DRIVER_NAME;
    }

    @Override
    public boolean exist(Properties properties) {
        String api = properties.getOrDefault("api", "NULL").toString();
        api = !api.startsWith("http") ? "http://" + api : api;
        String json = properties.getOrDefault("json", "{}").toString();
        String path = properties.getOrDefault("path", "").toString() + "/" +getIdEncoded(json);
        String uriPath = api + "/" + path;

        JsonNode jsonNode = null;
        try {
            jsonNode = mapper.readTree(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonNode parentIdObj = jsonNode != null ? jsonNode.get("parentId") : null;
        RestTemplate restTemplate = new RestTemplate();

        boolean result = false;

        try {
            URI uri = new URI(uriPath);
            RequestEntity<Void> request = RequestEntity.get(uri).build();
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            result = getResultFromStatusCode(request, response);
            if (parentIdObj != null) {
                result = result && getResultFromParent(response, parentIdObj.asText());
            }
        } catch (RuntimeException|URISyntaxException e) {
            LOGGER.error("POST "+uriPath+" ("+e.getMessage()+")");
        }

        return result;
    }

    private boolean getResultFromParent(ResponseEntity<String> response, String expectedParent) {
        AtomicBoolean parentFound = new AtomicBoolean(false);

        try {
            JsonNode json = mapper.readTree(response.getBody());
            if (json.isArray()) {
                json.forEach(jsonNode -> {
                    JsonNode parentIdObj = jsonNode.get("parentId");
                    if (parentIdObj != null && parentIdObj.isTextual() && parentIdObj.asText().equals(expectedParent)) {
                        parentFound.set(true);
                    }
                });
            }
        } catch (IOException e) {
            LOGGER.error(e);
            return false;
        }

        return parentFound.get();
    }

    @Override
    public boolean create(Properties properties) {
        String api = properties.getOrDefault("api", "NULL").toString();
        api = !api.startsWith("http") ? "http://" + api : api;
        String json = properties.getOrDefault("json", "{}").toString();
        String path = properties.getOrDefault("path", "").toString();
        String uriPath = api + "/" + path;
        RestTemplate restTemplate = new RestTemplate();
        boolean result = false;

        try {
            URI uri = new URI(uriPath);
            RequestEntity<String> request = RequestEntity.post(uri).contentType(MediaType.APPLICATION_JSON).body(json);
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            result = getResultFromStatusCode(request, response);
        } catch (RuntimeException|URISyntaxException e) {
            LOGGER.error("POST "+uriPath+" ("+e.getMessage()+")");
        }
        return result;
    }

    @Override
    public boolean update(Properties properties) {
        String api = properties.getOrDefault("api", "NULL").toString();
        api = !api.startsWith("http") ? "http://" + api : api;
        String json = properties.getOrDefault("json", "{}").toString();
        String path = properties.getOrDefault("path", "").toString() + "/" +getIdEncoded(json);
        String uriPath = api + "/" + path;
        RestTemplate restTemplate = new RestTemplate();
        boolean result = false;

        try {
            URI uri = new URI(uriPath);
            RequestEntity<String> request = RequestEntity.put(uri).contentType(MediaType.APPLICATION_JSON).body(json);
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            result = getResultFromStatusCode(request, response);
        } catch (RuntimeException|URISyntaxException e) {
            LOGGER.error("PUT "+uriPath+" ("+e.getMessage()+")");
        }
        return result;
    }

    @Override
    public boolean remove(Properties properties) {
        boolean result = false;
        String api = properties.getOrDefault("api", "NULL").toString();
        api = !api.startsWith("http") ? "http://" + api : api;
        String json = properties.getOrDefault("json", "{}").toString();
        String path = properties.getOrDefault("path", "").toString();
        String id = getIdEncoded(json);
        path = !"".equals(id) ? path + "/" + id : path;
        String uriPath = api + "/" + path;
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpEntityEnclosingRequest delete = new HttpDeleteWithBody("/"+path);

        try {
            delete.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
            api = api.startsWith("http") ? api.replaceAll("http.?://", "") : api;
            String[] apiWithPort = api.split(":");
            String hostName = apiWithPort[0];
            int port =  apiWithPort.length > 1 ? Integer.valueOf(apiWithPort[1]) : 80;
            delete.setEntity(new StringEntity(!"".equals(id) ? json : "{\"id\":\"\",\"version\":0}"));
            HttpResponse response = httpClient.execute(new HttpHost(hostName, port), delete);
            httpClient.close();
            result = getResultFromStatusCode(delete, response);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("DELETE "+uriPath+" ("+e.getMessage()+")");
        }
        return result;
    }

    private JsonNode getJson(String path) throws URISyntaxException, IOException {
        JsonNode json = null;
        RestTemplate restTemplate = new RestTemplate();
        URI uri = new URI(path);
        RequestEntity<Void> request = RequestEntity.get(uri).build();
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        boolean result = response.getStatusCode().value() < 400;

        if (result) {
            json = mapper.readTree(response.getBody());
        }
        return json;
    }

    @NotThreadSafe
    private static class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {

        @Override
        public String getMethod() {
            return "DELETE";
        }

        public HttpDeleteWithBody(String uri) {
            super();
            setURI(URI.create(uri));
        }
    }

    private String getIdEncoded(String json) {
        if (json == null) {
            return "";
        }
        try {
            JsonNode idObj = mapper.readTree(json).get("id");
            if (idObj == null) {
                return "";
            }
            String id = idObj.asText();
            if (id!=null) {
                id = URLEncoder.encode(id, StandardCharsets.UTF_8.toString());
            } else {
                id = "";
            }
            return id;
        } catch (IOException e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Map<String, String>> diff(Properties properties) {

        final String apiFromProperties = properties.getOrDefault("api", "localhost:9090").toString();
        final String api = !apiFromProperties.startsWith("http") ? "http://" + apiFromProperties : apiFromProperties;
        final Map <String, Set<AbstractEntity<?>>> entitiesMap =
                (Map <String, Set<AbstractEntity<?>>>)properties.getOrDefault("entitiesMap", Collections.emptyMap());

        final Map<String, Map<String, String>> fullMap = extractRemoteMap(api);
        final Map<String, Map<String, String>> diffMap = new HashMap<>();

        entitiesMap.keySet().stream().forEach(path -> makeDiffMap(api, path, entitiesMap, fullMap, diffMap));
        return diffMap;
    }

    private void makeDiffMap(String api,
                             String path,
                             final Map<String, Set<AbstractEntity<?>>> entitiesMap,
                             final Map<String, Map<String, String>> fullMap,
                             final Map<String, Map<String, String>> diffMap) {

        Set<AbstractEntity<?>> entities = entitiesMap.get(path);

        fullMap.entrySet().stream()
                          .filter(entry ->
                                  entry.getValue().getOrDefault("entity_type", "UNDEF").equals(path))
                          .forEach(entry ->
                          {
                              final Map<String, String> entityProperties = entry.getValue();
                              final String id = entityProperties.getOrDefault("id", "UNDEF");
                              final String parentId = entityProperties.getOrDefault("parentId", "UNDEF");
                              AtomicBoolean hasId = new AtomicBoolean(false);

                              entities.stream().filter(entity -> entity.getName().equals(id))
                                      .filter(getAbstractEntityPredicate(parentId))
                                      .forEach(entity ->
                                      {
                                          hasId.set(true);
                                          updateIfNecessary(api, path, id, parentId, entity, entityProperties, diffMap);
                                      });
                              removeEntityIfNecessary(api, path, id, parentId, hasId, diffMap);
                          });

        entities.stream().forEach(entity -> createEntityIfNecessary(api, path, entity, fullMap, diffMap));
    }

    private void updateIfNecessary(String api,
                                   String path,
                                   String id,
                                   String parentId,
                                   final AbstractEntity<?> entity,
                                   final Map<String, String> entityProperties,
                                   final Map<String, Map<String, String>> diffMap) {
        final String version = entityProperties.getOrDefault("version", "UNDEF");
        final String pk = entityProperties.getOrDefault("pk", "UNDEF");
        LOGGER.debug("Check if is necessary UPDATE");
        if (!version.equals(String.valueOf(entity.getId())) || !pk.equals(String.valueOf(entity.getId()))) {
            changeAction(api, path, id, parentId, diffMap);
        }
    }

    private void removeEntityIfNecessary(String api,
                                         String path,
                                         String id,
                                         String parentId,
                                         final AtomicBoolean hasId,
                                         final Map<String, Map<String, String>> diffMap) {
        LOGGER.debug("Check if is necessary REMOVE");
        if (!hasId.get()) {
            delAction(api, path, id, parentId, diffMap);
        }
    }

    @SuppressWarnings("unchecked")
    private void createEntityIfNecessary(String api,
                                         String path,
                                         final AbstractEntity<?> entity,
                                         final Map<String, Map<String, String>> fullMap,
                                         final Map<String, Map<String, String>> diffMap) {
        String id = entity.getName();
        LOGGER.debug("Check if is necessary CREATE");
        if (!(entity instanceof WithParent) && !(entity instanceof WithParents)) {
            addAction(api, path, id, "", fullMap.keySet(), diffMap);
        }
        if (entity instanceof WithParent) {
            AbstractEntity<?> parentInstance = ((WithParent<AbstractEntity<?>>) entity).getParent();
            String parentId = parentInstance != null ? parentInstance.getName() : "";
            addAction(api, path, id, parentId, fullMap.keySet(), diffMap);
        }
        if (entity instanceof WithParents) {
            ((WithParents<AbstractEntity<?>>) entity).getParents().forEach(aParent ->
            {
                String parentId = aParent.getName();
                addAction(api, path, id, parentId, fullMap.keySet(), diffMap);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private Predicate<AbstractEntity<?>> getAbstractEntityPredicate(String parentId) {
        return entity -> (!(entity instanceof WithParent) && !(entity instanceof WithParents)) ||
                (entity instanceof WithParent) && (
                        ((WithParent<AbstractEntity<?>>) entity).getParent() != null &&
                                ((WithParent<AbstractEntity<?>>) entity).getParent().getName().equals(parentId)) ||
                (entity instanceof WithParents) &&
                        !((WithParents<AbstractEntity<?>>) entity).getParents().isEmpty() &&
                        ((WithParents<AbstractEntity<?>>) entity).getParents().stream()
                                .map(AbstractEntity::getName).collect(Collectors.toList()).contains(parentId);
    }

    private Map<String, Map<String, String>> extractRemoteMap(final String api) {

        final Map<String, Map<String, String>> fullMap = new HashMap<>();
        final List<String> pathList = Arrays.asList(
                VirtualHost.class.getSimpleName().toLowerCase(),
                BackendPool.class.getSimpleName().toLowerCase(),
                Backend.class.getSimpleName().toLowerCase(),
                Rule.class.getSimpleName().toLowerCase());

        pathList.stream().map(path -> api + "/" + path).forEach(fullPath ->
        {
            try {
                JsonNode json = getJson(fullPath);
                if (json.isArray()) {
                    json.forEach(element -> {
                        Map<String, String> entityProperties = new HashMap<>();
                        String id = element.get("id").asText();
                        JsonNode parentIdObj = element.get("parentId");
                        String parentId = parentIdObj != null ? parentIdObj.asText() : "";
                        String pk = element.get("pk").asText();
                        String version = element.get("version").asText();
                        String entityType = element.get("_entity_type").asText();
                        String etag = element.get("_etag").asText();

                        entityProperties.put("id", id);
                        entityProperties.put("pk", pk);
                        entityProperties.put("version", version);
                        entityProperties.put("parentId", parentId);
                        entityProperties.put("entity_type", entityType);
                        entityProperties.put("etag", etag);
                        fullMap.put(fullPath + "/" + id + "@" + parentId, entityProperties);
                    });
                }
            } catch (Exception e) {
                LOGGER.error(e);
            }
        });
        return fullMap;
    }

    private void addAction(final String api,
                           final String path,
                           final String id,
                           final String parentId,
                           final Set<String> setOfKeys,
                           final Map<String, Map<String, String>> diffMap) {
        String key = api + "/" + path + "/" + id + "@" + parentId;
        if (!setOfKeys.contains(key)) {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("ACTION", "CREATE");
            attributes.put("ID", id);
            attributes.put("PARENT_ID", parentId);
            attributes.put("ENTITY_TYPE", path);
            diffMap.put(key, attributes);
        }
    }

    private void changeAction(final String api,
                              final String path,
                              final String id,
                              final String parentId,
                              final Map<String, Map<String, String>> diffMap) {
        Map<String, String> attributes = new HashMap<>();
        String key = api + "/" + path + "/" + id + "@" + parentId;
        attributes.put("ACTION", "UPDATE");
        attributes.put("ID", id);
        attributes.put("PARENT_ID", parentId);
        attributes.put("ENTITY_TYPE", path);
        diffMap.put(key, attributes);
    }

    private void delAction(final String api,
                           final String path,
                           final String id,
                           final String parentId,
                           final Map<String, Map<String, String>> diffMap) {
        Map<String, String> attributes = new HashMap<>();
        String key = api + "/" + path + "/" + id + "@" + parentId;
        attributes.put("ACTION", "REMOVE");
        attributes.put("ID", id);
        attributes.put("PARENT_ID", parentId);
        attributes.put("ENTITY_TYPE", path);
        diffMap.put(key, attributes);
    }

    private boolean getResultFromStatusCode(HttpEntityEnclosingRequest request, HttpResponse response) {
        InputStream content = null;
        try {
            content = request.getEntity().getContent();
        } catch (IOException e) {
            LOGGER.error(e);
        }
        BufferedReader bufferedReader = null;
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        try {
            if (content != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8.toString()));
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
            } else {
                LOGGER.warn("Content is null.");
            }
        } catch (IOException e) {
            LOGGER.error(e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    LOGGER.error(e);
                }
            }
        }
        String body = stringBuilder.toString();

        MultiValueMap<String, String> headers = new org.springframework.http.HttpHeaders();
        Map<String, List<String>> newMapOfHeaders =
                Arrays.asList(request.getAllHeaders()).stream().collect(
                        Collectors.toMap(Header::getName, header -> Arrays.asList(header.getValue().split(","))));
        headers.putAll(newMapOfHeaders);
        HttpMethod httpMethod = EnumSet.allOf(HttpMethod.class).stream()
                .filter(method -> method.toString().equals(request.getRequestLine().getMethod())).findFirst().get();

        RequestEntity<String> newRequest = new RequestEntity<>(body,
                headers,
                httpMethod,
                URI.create(request.getRequestLine().getUri()));


        InputStream responseContent = null;
        try {
            responseContent = response.getEntity().getContent();
        } catch (IOException e) {
            LOGGER.error(e);
        }
        bufferedReader = null;
        stringBuilder = new StringBuilder();
        try {
            if (responseContent != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(responseContent));
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
            } else {
                LOGGER.warn("ResponseContent is null");
            }
        } catch (IOException e) {
            LOGGER.error(e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    LOGGER.error(e);
                }
            }
        }
        String responseBody = stringBuilder.toString();

        MultiValueMap<String, String> responseHeaders = new org.springframework.http.HttpHeaders();
        Map<String, List<String>> newResponseMapOfHeaders =
                Arrays.asList(request.getAllHeaders()).stream().collect(
                        Collectors.toMap(Header::getName, header -> Arrays.asList(header.getValue().split(","))));
        responseHeaders.putAll(newResponseMapOfHeaders);
        HttpStatus responseStatusCode = EnumSet.allOf(HttpStatus.class).stream()
                .filter(status -> status.value() == response.getStatusLine().getStatusCode()).findFirst().get();

        ResponseEntity<String> newResponse = new ResponseEntity<>(responseBody,
                responseHeaders,
                responseStatusCode);

        return getResultFromStatusCode(newRequest, newResponse);
    }

    private boolean getResultFromStatusCode(RequestEntity<?> request, ResponseEntity<String> response) {
        if (response.getStatusCode().value() < 400) {
            logRequestResponse(request, response, LogLevel.INFO);
            return true;
        } else {
            logRequestResponse(request, response, LogLevel.ERROR);
            return false;
        }
    }

    private void logRequestResponse(RequestEntity<?> request, ResponseEntity<String> response, LogLevel logLevel) {
        HttpStatus statusCode = response.getStatusCode();
        String status = "HTTP/1.? " + statusCode.value()+" " + statusCode.getReasonPhrase();

        LoggerUtils.logger(LOGGER, logLevel, request.getMethod().toString() + " " + request.getUrl().toString());
        request.getHeaders().entrySet().forEach(entry ->
                LoggerUtils.logger(LOGGER, logLevel, entry.getKey() + ": " + entry.getValue().stream().collect(Collectors.joining(","))));
        Object requestBody = request.getBody();
        if (requestBody instanceof String) {
            LoggerUtils.logger(LOGGER, logLevel, requestBody);
        }
        LoggerUtils.logger(LOGGER, logLevel, "---");
        LoggerUtils.logger(LOGGER, logLevel, status);
        response.getHeaders().entrySet().forEach(entry ->
                LoggerUtils.logger(LOGGER, logLevel, entry.getKey() + ": " + entry.getValue().stream().collect(Collectors.joining(","))));
        String responseBody = response.getBody();
        if (responseBody != null) {
            LoggerUtils.logger(LOGGER, logLevel, response.getBody());
        }
    }


}
