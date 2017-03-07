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
package org.commonjava.maven.galley.transport.htcli.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.commonjava.maven.galley.BadGatewayException;
import org.commonjava.maven.galley.GalleyException;
import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.TransferLocationException;
import org.commonjava.maven.galley.TransferTimeoutException;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.transport.htcli.Http;
import org.commonjava.maven.galley.transport.htcli.internal.util.TransferResponseUtils;
import org.commonjava.maven.galley.transport.htcli.model.HttpExchangeMetadata;
import org.commonjava.maven.galley.transport.htcli.model.HttpLocation;
import org.commonjava.maven.galley.transport.htcli.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractHttpJob
{
    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    protected final String url;

    protected final HttpLocation location;

    protected final Http http;

    protected TransferException error;

    protected HttpUriRequest request;

    protected CloseableHttpClient client;

    protected CloseableHttpResponse response;

    private final Collection<Integer> successStatuses;

    protected boolean success;

    protected AbstractHttpJob( final String url, final HttpLocation location, final Http http,
                               final Integer... successStatuses )
    {
        this.url = url;
        this.location = location;
        this.http = http;

        if ( successStatuses.length < 1 )
        {
            this.successStatuses = Collections.singleton( HttpStatus.SC_OK );
        }
        else
        {
            this.successStatuses = Arrays.asList( successStatuses );
        }
    }

    public TransferException getError()
    {
        return error;
    }

    public long getTransferSize()
    {
        return response == null ? -1 : HttpUtil.getContentLength( response );
    }

    protected boolean executeHttp()
        throws TransferException
    {
        try
        {
            client = http.createClient( location );
            response = client.execute( request, http.createContext( location ) );

            final StatusLine line = response.getStatusLine();
            final int sc = line.getStatusCode();
            logger.debug( "{} {} : {}", request.getMethod(), line, url );
            if ( !successStatuses.contains( sc ) )
            {
                logger.debug( "Detected failure response: " + sc );
                success = TransferResponseUtils.handleUnsuccessfulResponse( request, response, location, url );
                logger.debug( "Returning non-error failure response for code: " + sc );
                return false;
            }
        }
        catch ( final NoHttpResponseException e )
        {
            throw new TransferTimeoutException( location, url, "Repository remote request failed for: {}. Reason: {}",
                                                e, url, e.getMessage() );
        }
        catch ( final ConnectTimeoutException e )
        {
            throw new TransferTimeoutException( location, url, "Repository remote request failed for: {}. Reason: {}",
                                                e, url, e.getMessage() );
        }
        catch ( final SocketTimeoutException e )
        {
            throw new TransferTimeoutException( location, url, "Repository remote request failed for: {}. Reason: {}",
                                                e, url, e.getMessage() );
        }
        catch ( final ClientProtocolException e )
        {
            throw new TransferLocationException( location, "Repository remote request failed for: {}. Reason: {}", e, url,
                                         e.getMessage() );
        }
        catch ( BadGatewayException e )
        {
            throw e;
        }
        catch ( final GalleyException e )
        {
            throw new TransferException( "Repository remote request failed for: {}. Reason: {}", e, url,
                                         e.getMessage() );
        }
        catch ( final IOException e )
        {
            throw new TransferLocationException( location, "Repository remote request failed for: {}. Reason: {}", e, url,
                                         e.getMessage() );
        }
        finally
        {
            /*
            * we need to integrate the writeMetadata() method into the executeHttp() call in a finally block,
            * and with a condition that it only runs on HEAD or GET. This would allow us to capture metadata on failed requests too,
            * which is critical for responding consistently to the user after a failed request is cached in the NFC.
            */
            String method = request.getMethod();
            if ( "GET".equalsIgnoreCase( method ) || "HEAD".equalsIgnoreCase( method ) )
            {
                Transfer target = getTransfer();
                ObjectMapper mapper = getMetadataObjectMapper();
                if ( target != null && mapper != null )
                {
                    writeMetadata( target, mapper );
                }
            }
        }

        return true;
    }

    /* for GET/HEAD request, need to override below two methods for writeMetadata() */

    protected Transfer getTransfer()
    {
        return null;
    }

    protected ObjectMapper getMetadataObjectMapper()
    {
        return null;
    }

    private void writeMetadata( final Transfer target, final ObjectMapper mapper )
    {
        if ( target == null || request == null || response == null )
        {
            logger.debug( "Cannot write HTTP exchange metadata. Request: {}. Response: {}. Transfer: {}", request, response, target );
            return;
        }

        logger.debug( "Writing HTTP exchange metadata. Request: {}. Response: {}", request, response );
        Transfer metaTxfr = target.getSiblingMeta( HttpExchangeMetadata.FILE_EXTENSION );
        if ( metaTxfr == null )
        {
            if ( target.isDirectory() )
            {
                logger.debug( "DIRECTORY. Using HTTP exchange metadata file INSIDE directory called: {}", HttpExchangeMetadata.FILE_EXTENSION );
                metaTxfr = target.getChild( HttpExchangeMetadata.FILE_EXTENSION );
            }
            else
            {
                logger.debug( "SKIP: Cannot retrieve HTTP exchange metadata Transfer instance for: {}", target );
                return;
            }
        }

        final HttpExchangeMetadata metadata = new HttpExchangeMetadata( request, response );
        OutputStream out = null;
        try
        {
            out = metaTxfr.openOutputStream( TransferOperation.GENERATE, false );
            logger.debug( "Writing HTTP exchange metadata:\n\n{}\n\n", new Object()
            {
                @Override
                public String toString()
                {
                    try
                    {
                        return mapper.writeValueAsString( metadata );
                    }
                    catch ( final JsonProcessingException e )
                    {
                    }

                    return "ERROR RENDERING METADATA";
                }
            } );

            out.write( mapper.writeValueAsBytes( metadata ) );
        }
        catch ( final IOException e )
        {
            if ( logger.isDebugEnabled() )
            {
                logger.debug( String.format( "Failed to write metadata for HTTP exchange to: %s. Reason: %s", metaTxfr,
                                             e.getMessage() ), e );
            }
            else
            {
                logger.warn( "Failed to write metadata for HTTP exchange to: {}. Reason: {}", metaTxfr, e.getMessage() );
            }
        }
        finally
        {
            IOUtils.closeQuietly( out );
        }
    }

    protected void cleanup()
    {
        http.cleanup( client, request, response );
        client = null;
        request = null;
        response = null;
    }

}
