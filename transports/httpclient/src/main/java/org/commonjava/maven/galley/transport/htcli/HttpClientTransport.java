package org.commonjava.maven.galley.transport.htcli;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Resource;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.spi.transport.DownloadJob;
import org.commonjava.maven.galley.spi.transport.ExistenceJob;
import org.commonjava.maven.galley.spi.transport.ListingJob;
import org.commonjava.maven.galley.spi.transport.PublishJob;
import org.commonjava.maven.galley.spi.transport.Transport;
import org.commonjava.maven.galley.transport.htcli.conf.GlobalHttpConfiguration;
import org.commonjava.maven.galley.transport.htcli.internal.HttpDownload;
import org.commonjava.maven.galley.transport.htcli.internal.HttpExistence;
import org.commonjava.maven.galley.transport.htcli.internal.HttpListing;
import org.commonjava.maven.galley.transport.htcli.internal.HttpPublish;
import org.commonjava.maven.galley.transport.htcli.internal.model.WrapperHttpLocation;
import org.commonjava.maven.galley.transport.htcli.model.HttpLocation;

@ApplicationScoped
@Named( "httpclient-galley-transport" )
public class HttpClientTransport
    implements Transport
{

    @Inject
    private Http http;

    @Inject
    private GlobalHttpConfiguration globalConfig;

    protected HttpClientTransport()
    {
    }

    public HttpClientTransport( final Http http )
    {
        this( http, null );
    }

    public HttpClientTransport( final Http http, final GlobalHttpConfiguration globalConfig )
    {
        this.http = http;
        this.globalConfig = globalConfig;
    }

    @Override
    public DownloadJob createDownloadJob( final String url, final Resource resource, final Transfer target, final int timeoutSeconds )
        throws TransferException
    {
        return new HttpDownload( url, getHttpLocation( resource.getLocation() ), target, http );
    }

    @Override
    public PublishJob createPublishJob( final String url, final Resource resource, final InputStream stream, final long length,
                                        final String contentType, final int timeoutSeconds )
        throws TransferException
    {
        return new HttpPublish( url, getHttpLocation( resource.getLocation() ), stream, length, contentType, http );
    }

    @Override
    public PublishJob createPublishJob( final String url, final Resource resource, final InputStream stream, final long length,
                                        final int timeoutSeconds )
        throws TransferException
    {
        return createPublishJob( url, resource, stream, length, null, timeoutSeconds );
    }

    @Override
    public boolean handles( final Location location )
    {
        final String uri = location.getUri();
        try
        {
            return uri != null && uri.startsWith( "http" ) && new URL( location.getUri() ) != null; // hack, but just verify that the URL parses.
        }
        catch ( final MalformedURLException e )
        {
        }

        return false;
    }

    @Override
    public ListingJob createListingJob( final String url, final Resource resource, final int timeoutSeconds )
        throws TransferException
    {
        return new HttpListing( url, new Resource( getHttpLocation( resource.getLocation() ), resource.getPath() ), timeoutSeconds, http );
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
            throw new TransferException( "Failed to parse base-URL for: %s", e, repository.getUri() );
        }
    }

    @Override
    public ExistenceJob createExistenceJob( final String url, final Resource resource, final int timeoutSeconds )
        throws TransferException
    {
        return new HttpExistence( url, getHttpLocation( resource.getLocation() ), http );
    }

}
