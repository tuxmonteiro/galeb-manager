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
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.galeb.manager.common.LoggerUtils;
import io.galeb.manager.entity.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.HttpDelete;
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

import io.galeb.core.model.Entity;
import io.galeb.manager.common.EmptyStream;
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
        RestTemplate restTemplate = new RestTemplate();

        boolean result = false;

        try {
            URI uri = new URI(uriPath);
            RequestEntity<Void> request = RequestEntity.get(uri).build();
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            result = getResultFromStatusCode(request, response);
        } catch (RuntimeException|URISyntaxException e) {
            LOGGER.error("POST "+uriPath+" ("+e.getMessage()+")");
        }

        return result;
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
        String path = properties.getOrDefault("path", "").toString() + "/" +getIdEncoded(json);
        String uriPath = api + "/" + path;
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpEntityEnclosingRequest delete = new HttpDeleteWithBody("/"+path);

        try {
            delete.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
            String[] apiWithPort = api.split(":");
            String hostName = apiWithPort[0];
            int port =  apiWithPort.length > 1 ? Integer.valueOf(apiWithPort[1]) : 80;
            delete.setEntity(new StringEntity(json));
            HttpResponse response = httpClient.execute(new HttpHost(hostName, port), delete);
            httpClient.close();
            result = getResultFromStatusCode(delete, response);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("DELETE "+uriPath+" ("+e.getMessage()+")");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean reload(Properties properties) throws IOException {
        String api = properties.getOrDefault("api", "NULL").toString();
        Map<String, Map<String, String>> diff =
                (Map<String, Map<String, String>>) properties.getOrDefault("diff", new HashMap<>());
        Set<VirtualHost> virtualHosts = (Set<VirtualHost>)properties.
                getOrDefault("virtualhosts", new HashSet<>());
        Set<Target> targets = (Set<Target>)properties.
                getOrDefault("targets", new HashSet<>());
        Set<Rule> rules = (Set<Rule>)properties.
                getOrDefault("rules", new HashSet<>());

        api = api.startsWith("http") ? api.replaceAll("http.?://", "") : api;
        String[] apiWithPort = api.split(":");
        String hostName = apiWithPort[0];
        int port =  apiWithPort.length > 1 ? Integer.valueOf(apiWithPort[1]) : 80;

        diff.entrySet().stream().forEach(entry -> {
            String key = entry.getKey();
            Map<String, String> attributes = entry.getValue();
            String action = attributes.get("ACTION");
            String entityType = attributes.get("ENTITY_TYPE");
            String id = attributes.get("ID");
            String parentId = attributes.get("parentId");

        });
        // TODO: Fix inconsistencies

        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public StatusFarm status(Properties properties) {
        String api = properties.getOrDefault("api", "localhost:9090").toString();
        api = !api.startsWith("http") ? "http://" + api : api;
        String path = properties.getOrDefault("path", "").toString();
        String name = properties.getOrDefault("name", "UNDEF").toString();
        Stream<? extends AbstractEntity<?>> parents =
                (Stream<? extends AbstractEntity<?>>) properties.getOrDefault("parents", EmptyStream.get());
        int expectedId = properties.getOrDefault("id", -1);
        long expectedNumElements = properties.getOrDefault("numElements", -1L);

        String basePath = api + "/" + path;
        String nameEncoded;
        try {
            nameEncoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e1) {
            LOGGER.error(e1);
            return StatusFarm.FAIL;
        }
        String fullPath = basePath+"/"+nameEncoded;

        AtomicBoolean result = new AtomicBoolean(true);
        try {
            if (!isOkNumElements(basePath, expectedNumElements)) {
                return StatusFarm.FAIL;
            }
            List<AbstractEntity<?>> listOfParents = parents.collect(Collectors.toList());
            if (listOfParents.size()>0) {
                listOfParents.forEach(parent -> {
                    try {
                        if (result.get()) {
                            boolean isSyncronized = isSyncronized(fullPath, name, parent.getName(), expectedId);
                            result.set(isSyncronized);
                        }
                    } catch (Exception e) {
                        result.set(false);
                        LOGGER.error("STATUS FAIL: " + fullPath);
                        LOGGER.error(e);
                    }
                });
            } else {
                result.set(isSyncronized(fullPath, name, null, expectedId));
            }

            if (!result.get()) {
                LOGGER.warn("STATUS FAIL: "+fullPath);
            } else {
                LOGGER.debug("STATUS OK: "+fullPath);
            }
        } catch (RuntimeException | IOException | URISyntaxException e) {
            result.set(false);
            LOGGER.error("STATUS FAIL: "+fullPath);
            LOGGER.error(e);
        }
        return result.get() ? StatusFarm.OK : StatusFarm.FAIL;
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

    private boolean isSyncronized(String fullPath, String name, String parent, int expectedId) throws URISyntaxException, IOException {
        JsonNode json = getJson(fullPath);
        if (json == null) {
            return false;
        }

        final Entity entity = new Entity();

        entity.setVersion(-1);
        if (json.isArray()) {
            StreamSupport.stream(json.spliterator(), false)
                .filter(element -> element.isObject() &&
                        element.get("id") != null &&
                        element.get("id").asText("defaultTextIfAbsent").equals(name))
                .forEach(element -> {
                    if ((parent == null) ||
                        (element.get("parentId") != null &&
                            element.get("parentId").asText("defaultTextIfAbsent").equals(parent)))
                    {
                        entity.setVersion(element.get("version").asInt(-1));
                    } else {
                        LOGGER.debug("CHECK FAIL >>>>  "+fullPath+" : "+" parent="+parent+" expectedId="+expectedId);
                        LOGGER.debug("           >>>>  "+fullPath+" : "+" parent(remote)="+element.get("parentId").asText()+" id(remote)="+element.get("version").asInt());
                    }
            });
        }
        boolean syncronized = expectedId == entity.getVersion();
        if (!syncronized) {
            LOGGER.error(fullPath+" : VERSION NOT MATCH (manager:"+expectedId+" != farm:"+entity.getVersion()+")");
        }

        return syncronized;
    }

    private boolean isOkNumElements(String pathBase, long expectedNumElements) throws URISyntaxException, IOException {
        JsonNode json = getJson(pathBase);
        if (json == null) {
            return false;
        }

        int numElements = 0;

        if (json.isArray()) {
            numElements = json.size();
        }
        boolean resultCount = expectedNumElements == numElements;
        if (!resultCount) {
            LOGGER.error(pathBase+" : COUNT NOT MATCH (manager:"+expectedNumElements+" != farm:"+numElements+")");
        }

        return resultCount;
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
        try {
            String id = new ObjectMapper().readTree(json).get("id").asText();
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
    public Map<String, Map<String, String>> diff(Map<String, Object> properties) {

        final String apiFromProperties = properties.getOrDefault("api", "localhost:9090").toString();
        final String api = !apiFromProperties.startsWith("http") ? "http://" + apiFromProperties : apiFromProperties;
        final Map <String, Set<AbstractEntity<?>>> entitiesMap =
                (Map <String, Set<AbstractEntity<?>>>)properties.getOrDefault("entitiesMap", Collections.emptyMap());

        final Map<String, Map<String, String>> fullMap = extractRemoteMap(api);
        final Map<String, Map<String, String>> diffMap = new HashMap<>();

        entitiesMap.keySet().stream().forEach(path ->
        {
            Set<AbstractEntity<?>> entities = entitiesMap.get(path);

            fullMap.entrySet().stream()
                              .filter(entry ->
                                  entry.getValue().getOrDefault("entity_type", "UNDEF").equals(path))
                              .forEach(entry ->
            {
                final Map<String, String> entityProperties = entry.getValue();
                final String id = entityProperties.getOrDefault("id", "UNDEF");
                final String parentId = entityProperties.getOrDefault("parentId", "UNDEF");
                final String version = entityProperties.getOrDefault("version", "UNDEF");
                final String pk = entityProperties.getOrDefault("pk", "UNDEF");
                AtomicBoolean hasId = new AtomicBoolean(false);

                entities.stream().filter(entity -> entity.getName().equals(id))
                                 .filter(entity -> (!(entity instanceof WithParent) && !(entity instanceof WithParents)) ||
                                         (entity instanceof WithParent) && (
                                                 (((WithParent<AbstractEntity<?>>) entity).getParent() != null &&
                                                         ((WithParent<AbstractEntity<?>>) entity).getParent().getName().equals(parentId)) ||
                                                 !((WithParent<AbstractEntity<?>>) entity).getChildren().isEmpty()) ||
                                         (entity instanceof WithParents) &&
                                                 !((WithParents<AbstractEntity<?>>) entity).getParents().isEmpty() &&
                                                 ((WithParents<AbstractEntity<?>>) entity).getParents().stream()
                                                         .map(AbstractEntity::getName).collect(Collectors.toList()).contains(parentId))
                                 .forEach(entity ->
                                 {
                                     hasId.set(true);
                                     LOGGER.debug("Check if is necessary UPDATE");
                                     if (!version.equals(String.valueOf(entity.getId())) || !pk.equals(String.valueOf(entity.getId()))) {
                                         changeAction(api, path, id, parentId, diffMap);
                                     }
                                 });

                LOGGER.debug("Check if is necessary REMOVE");
                if (!hasId.get()) {
                    delAction(api, path, id, parentId, diffMap);
                }
            });

            entities.stream().forEach(entity -> {
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
            });
        });
        return diffMap;
    }

    private Map<String, Map<String, String>> extractRemoteMap(final String api) {

        final Map<String, Map<String, String>> fullMap = new HashMap<>();
        final List<String> pathList = Arrays.asList("virtualhost", "backendpool", "backend", "rule");

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
        if (requestBody instanceof String && requestBody != null) {
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
