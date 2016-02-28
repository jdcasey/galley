package org.commonjava.maven.galley.cache.partyline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Created by jdcasey on 2/27/16.
 *
 * Special FilterInputStream implementation that starts a secondary transfer to the faster storage location in the background
 * when the first byte is read from this stream, then blocks the close() method until the background transfer completes
 * (if it was started).
 *
 * This allows subsequent reads from the cache provider to hit the faster storage location.
 */
public class FastTransferInputStream
        extends FilterInputStream
{
    private final File main;

    private final File fast;

    private final InputStream mainStream;

    private final OutputStream fastStream;

    private ExecutorService transfers;

    private Future<IOException> backgroundTransfer;

    public FastTransferInputStream( File main, File fast, InputStream mainStream, OutputStream fastStream,
                                    ExecutorService transfers )
    {
        super( mainStream );
        this.main = main;
        this.fast = fast;
        this.mainStream = mainStream;
        this.fastStream = fastStream;
        this.transfers = transfers;
    }

    @Override
    public int read()
            throws IOException
    {
        startFastTransfer();
        return super.read();
    }

    private synchronized void startFastTransfer()
    {
        if ( backgroundTransfer == null )
        {
            backgroundTransfer = transfers.submit( new StreamTransferCallable( mainStream, fastStream ) );
        }
    }

    @Override
    public int read( byte[] b )
            throws IOException
    {
        startFastTransfer();
        return super.read( b );
    }

    @Override
    public int read( byte[] b, int off, int len )
            throws IOException
    {
        startFastTransfer();
        return super.read( b, off, len );
    }

    @Override
    public synchronized void close()
            throws IOException
    {
        if ( backgroundTransfer != null )
        {
            Logger logger = LoggerFactory.getLogger( getClass() );
            try
            {
                IOException error = backgroundTransfer.get();
                if ( error != null )
                {
                    fast.delete();

                    logger.error( String.format(
                            "Failed to background-transfer main storage file: %s to fast-storage under: %s. Reason: %s",
                            main, fast, error.getMessage() ), error );
                }
            }
            catch ( InterruptedException e )
            {
                fast.delete();

                logger.debug(
                        "Interrupt while background-transferring: {} to: {}. Deleted fast-storage partial transfer.",
                        main, fast );
            }
            catch ( ExecutionException e )
            {
                fast.delete();

                logger.error( String.format(
                        "Error while background-transferring: %s to: %s. Reason: %s. Deleted fast-storage partial transfer.",
                        main, fast, e.getMessage() ), e );
            }
        }

        super.close();
    }

}
