/**
 * Copyright (C) 2013 Red Hat, Inc. (nos-devel@redhat.com)
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
package org.commonjava.maven.galley.transport.htcli;

import static org.commonjava.maven.galley.util.UrlUtils.buildUrl;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import com.codahale.metrics.MetricRegistry;
import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.TransferLocationException;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.spi.transport.DownloadJob;
import org.commonjava.maven.galley.spi.transport.ExistenceJob;
import org.commonjava.maven.galley.spi.transport.ListingJob;
import org.commonjava.maven.galley.spi.transport.PublishJob;
import org.commonjava.maven.galley.spi.transport.Transport;
import org.commonjava.maven.galley.config.TransportMetricConfig;
import org.commonjava.maven.galley.transport.htcli.conf.GlobalHttpConfiguration;
import org.commonjava.maven.galley.transport.htcli.internal.HttpDownload;
import org.commonjava.maven.galley.transport.htcli.internal.HttpExistence;
import org.commonjava.maven.galley.transport.htcli.internal.HttpListing;
import org.commonjava.maven.galley.transport.htcli.internal.HttpPublish;
import org.commonjava.maven.galley.transport.htcli.internal.model.WrapperHttpLocation;
import org.commonjava.maven.galley.transport.htcli.model.HttpLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
@Named
public class HttpClientTransport
    implements Transport
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private Http http;

    @Inject
    private GlobalHttpConfiguration globalConfig;

    @Inject
    private ObjectMapper mapper;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private TransportMetricConfig metricConfig;

    protected HttpClientTransport()
    {
    }

    public HttpClientTransport( final Http http )
    {
        this( http, new ObjectMapper(), null, null, null );
    }

    public HttpClientTransport( final Http http, final ObjectMapper mapper, final GlobalHttpConfiguration globalConfig,
                                final MetricRegistry metricRegistry, final TransportMetricConfig metricConfig )
    {
        this.http = http;
        this.mapper = mapper;
        this.globalConfig = globalConfig;
        this.metricRegistry = metricRegistry;
        this.metricConfig = metricConfig;
    }

    @PreDestroy
    public void shutdown()
    {
        try
        {
            http.close();
        }
        catch ( final IOException e )
        {
            logger.error( "Failed to shutdown HTTP manager.", e );
        }
    }

    @Override
    public DownloadJob createDownloadJob( final ConcreteResource resource, final Transfer target,
                                          final Map<Transfer, Long> transferSizes, final int timeoutSeconds,
                                          final EventMetadata eventMetadata )
        throws TransferException
    {
        return new HttpDownload( getUrl( resource ), getHttpLocation( resource.getLocation() ), target, transferSizes, eventMetadata,
                                 http, mapper, metricRegistry, metricConfig );
    }

    @Override
    public PublishJob createPublishJob( final ConcreteResource resource, final InputStream stream, final long length, final String contentType,
                                        final int timeoutSeconds )
        throws TransferException
    {
        return new HttpPublish( getUrl( resource ), getHttpLocation( resource.getLocation() ), stream, length, contentType, http );
    }

    @Override
    public PublishJob createPublishJob( final ConcreteResource resource, final InputStream stream, final long length, final int timeoutSeconds )
        throws TransferException
    {
        return createPublishJob( resource, stream, length, null, timeoutSeconds );
    }

    @Override
    public boolean handles( final Location location )
    {
        final String uri = location.getUri();
        try
        {
            //noinspection ConstantConditions
            return uri != null && uri.startsWith( "http" ) && new URL( uri ) != null; // hack, but just verify that the URL parses.
        }
        catch ( final MalformedURLException e )
        {
            logger.warn( String.format("HTTP transport cannot handle: %s. Error parsing URL: %s", location, e.getMessage()), e );
        }

        return false;
    }

    @Override
    public ListingJob createListingJob( final ConcreteResource resource, final Transfer target, final int timeoutSeconds )
        throws TransferException
    {
        return new HttpListing( getUrl( resource ),
                                new ConcreteResource( getHttpLocation( resource.getLocation() ), resource.getPath() ),
                                http );
    }

    private HttpLocation getHttpLocation( final Location repository )
        throws TransferException
    {
        try
        {
            return ( repository instanceof HttpLocation ) ? (HttpLocation) repository : new WrapperHttpLocation( repository, globalConfig );
        }
        catch ( final MalformedURLException e )
        {
            throw new TransferLocationException( repository, "Failed to parse base-URL for: {}", e, repository.getUri() );
        }
    }

    @Override
    public ExistenceJob createExistenceJob( final ConcreteResource resource, final Transfer target,
                                            final int timeoutSeconds )
        throws TransferException
    {
        return new HttpExistence( getUrl( resource ), getHttpLocation( resource.getLocation() ), target, http, mapper );
    }

    protected String getUrl( final ConcreteResource resource )
        throws TransferException
    {
        try
        {
            return buildUrl( resource );
        }
        catch ( final MalformedURLException e )
        {
            throw new TransferLocationException( resource.getLocation(), "Failed to build URL for resource: {}. Reason: {}", e, resource, e.getMessage() );
        }
    }

    @Override
    public boolean allowsCaching()
    {
        return true;
    }

}
