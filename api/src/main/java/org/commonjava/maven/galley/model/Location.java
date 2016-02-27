/**
 * Copyright (C) 2013 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.maven.galley.model;

import java.util.Map;

public interface Location
{

    String CONNECTION_TIMEOUT_SECONDS = "connection-timeout";

    String CACHE_TIMEOUT_SECONDS = "cache-timeout";

    String ATTR_ALT_STORAGE_LOCATION = "alt-storage-location";

    String ATTR_FAST_STORAGE_LOCATION = "alt-cache-storage-location";

    String MAX_CONNECTIONS = "max-connections";

    int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 30;

    int DEFAULT_CACHE_TIMEOUT_SECONDS = 86400;

    int MIN_CACHE_TIMEOUT_SECONDS = 3600;

    int DEFAULT_MAX_CONNECTIONS = 10;

    boolean allowsDownloading();

    boolean allowsPublishing();

    boolean allowsStoring();

    boolean allowsSnapshots();

    boolean allowsReleases();

    String getUri();

    String getName();

    /**
     * Reminder that equality checks are important here!
     */
    @Override
    boolean equals( Object other );

    /**
     * Reminder that equality checks are important here!
     */
    @Override
    int hashCode();

    Map<String, Object> getAttributes();

    <T> T getAttribute( String key, Class<T> type );

    <T> T getAttribute( String key, Class<T> type, T defaultValue );

    Object removeAttribute( String key );

    Object setAttribute( String key, Object value );

}
