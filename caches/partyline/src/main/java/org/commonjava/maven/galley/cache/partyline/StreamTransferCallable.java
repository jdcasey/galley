package org.commonjava.maven.galley.cache.partyline;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

/**
 * Created by jdcasey on 2/27/16.
 */
final class StreamTransferCallable
        implements Callable<IOException>
{

    private final InputStream mainStream;

    private final OutputStream fastStream;

    public StreamTransferCallable( InputStream mainStream, OutputStream fastStream )
    {
        this.mainStream = mainStream;
        this.fastStream = fastStream;
    }

    @Override
    public IOException call()
    {
        IOException error = null;
        try
        {
            IOUtils.copy( mainStream, fastStream );
        }
        catch ( IOException e )
        {
            error = e;
        }
        finally
        {
            IOUtils.closeQuietly( fastStream );
        }

        return error;
    }
}
