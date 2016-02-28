package org.commonjava.maven.galley.cache;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.event.FileEventManager;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.commonjava.maven.galley.spi.io.TransferDecorator;
import org.commonjava.maven.galley.util.AtomicFileOutputStreamWrapper;
import org.commonjava.maven.galley.util.LocationUtils;
import org.commonjava.maven.galley.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by jdcasey on 2/27/16.
 *
 * Helper class that consolidates common file operations for file-based CacheProvider implementations. This class is
 * fast-storage aware, and will try to return results from that faster storage location before the main (slower) storage.
 */
public class ResourceFileCacheHelper
{
    private final Map<ConcreteResource, Transfer> transferCache =
            new ConcurrentHashMap<ConcreteResource, Transfer>( 10000 );

    private final FileEventManager fileEventManager;

    private final TransferDecorator transferDecorator;

    private final PathGenerator pathGenerator;

    private final LockingSupport lockingSupport;

    private File cacheBasedir;

    private CacheProvider provider;

    public ResourceFileCacheHelper( FileEventManager fileEventManager, TransferDecorator transferDecorator,
                                    PathGenerator pathGenerator, LockingSupport lockingSupport, File cacheBasedir,
                                    CacheProvider provider )
    {
        this.fileEventManager = fileEventManager;
        this.transferDecorator = transferDecorator;
        this.pathGenerator = pathGenerator;
        this.lockingSupport = lockingSupport;
        this.cacheBasedir = cacheBasedir;
        this.provider = provider;
    }

    public synchronized Transfer getTransfer( final ConcreteResource resource )
    {
        Transfer t = transferCache.get( resource );
        if ( t == null )
        {
            t = new Transfer( resource, provider, fileEventManager, transferDecorator );
            transferCache.put( resource, t );
        }

        return t;
    }

    public File getDetachedFile( final ConcreteResource resource )
    {
        // TODO: this might be a bit heavy-handed, but we need to be sure.
        // Maybe I can improve it later.
        final Transfer txfr = getTransfer( resource );
        synchronized ( txfr )
        {
            File main = getMainStorageFile( resource );
            File result = main;
            File fast = getFastStorageFile( resource );

            if ( resource.isRoot() && !main.isDirectory() )
            {
                main.mkdirs();
                return main;
            }

            Logger logger = LoggerFactory.getLogger( getClass() );
            if ( fast != null && main.exists() )
            {
                try
                {
                    if ( main.isDirectory() )
                    {
                        if ( !fast.isDirectory() && !fast.mkdirs() )
                        {
                            logger.error( "Cannot create directory in fast storage: {}", fast );
                        }
                        else
                        {
                            FileUtils.copyDirectoryToDirectory( main, fast );
                        }
                    }
                    else
                    {
                        FileUtils.copyFile( main, fast );
                    }

                    result = fast;
                }
                catch ( IOException e )
                {
                    logger.error( String.format( "Failed to copy %s to fast storage under %s. Reason: %s", main, fast,
                                                 e.getMessage() ), e );
                }
            }

            if ( resource.isRoot() && !result.isDirectory() )
            {
                result.mkdirs();
            }

            return result;
        }
    }

    public File getFastestExistingFile( ConcreteResource resource )
    {
        File f = getFastStorageFile( resource );
        if ( f == null )
        {
            f = getMainStorageFile( resource );
        }

        return f;
    }

    public File getFastStorageFileTemp( ConcreteResource resource )
    {
        final String fastDir = LocationUtils.getFastStoragePath( resource.getLocation() );

        File f = null;
        if ( fastDir != null )
        {
            f = new File( fastDir, resource.getPath() + CacheProvider.SUFFIX_TO_WRITE );
        }

        return f;
    }

    public File getFastStorageFile( ConcreteResource resource )
    {
        final String fastDir = LocationUtils.getFastStoragePath( resource.getLocation() );

        File f = null;
        if ( fastDir != null )
        {
            f = new File( fastDir, resource.getPath() );
        }

        processTimeouts( resource, f );

        return f;
    }

    public File getMainStorageFile( ConcreteResource resource )
    {
        final String altDir = LocationUtils.getAltStoragePath( resource.getLocation() );

        File f = null;
        if ( altDir == null )
        {
            f = new File( getFilePath( resource ) );
        }
        else
        {
            f = new File( altDir, resource.getPath() );
        }

        processTimeouts( resource, f );

        return f;
    }

    public String getFilePath( final ConcreteResource resource )
    {
        return PathUtils.normalize( cacheBasedir.getPath(), pathGenerator.getFilePath( resource ) );
    }

    public void processTimeouts( ConcreteResource resource, File f )
    {
        if ( f == null )
        {
            return;
        }

        // TODO: configurable default timeout
        int timeoutSeconds = LocationUtils.getTimeoutSeconds( resource );
        if ( !resource.isRoot() && f.exists() && !f.isDirectory() && timeoutSeconds > 0 )
        {
            final long current = System.currentTimeMillis();
            final long lastModified = f.lastModified();
            final int tos = timeoutSeconds < Location.MIN_CACHE_TIMEOUT_SECONDS ?
                    Location.MIN_CACHE_TIMEOUT_SECONDS :
                    timeoutSeconds;

            final long timeout = TimeUnit.MILLISECONDS.convert( tos, TimeUnit.SECONDS );

            if ( current - lastModified > timeout )
            {
                final File mved = new File( f.getPath() + CacheProvider.SUFFIX_TO_DELETE );
                f.renameTo( mved );

                Logger logger = LoggerFactory.getLogger( getClass() );
                try
                {
                    logger.info(
                            "Deleting cached file: {} (moved to: {})\n  due to timeout after: {}\n  elapsed: {}\n  original timeout in seconds: {}",
                            f, mved, timeout, ( current - lastModified ), tos );

                    if ( mved.exists() )
                    {
                        FileUtils.forceDelete( mved );
                    }
                }
                catch ( final IOException e )
                {
                    logger.error( String.format( "Failed to delete: %s.", f ), e );
                }
            }
        }
    }

    public boolean exists( ConcreteResource resource )
    {
        File f = getFastStorageFile( resource );
        if ( f != null && f.exists() )
        {
            return true;
        }

        f = getMainStorageFile( resource );

        //        logger.info( "Checking for existence of cache file: {}", f );
        return f.exists();
    }

    public void mkdirs( File f )
            throws IOException
    {
        final File dir = f.getParentFile();
        if ( !dir.isDirectory() && !dir.mkdirs() )
        {
            throw new IOException( "Cannot create directory: " + dir );
        }
    }

    public void copy( ConcreteResource from, ConcreteResource to )
            throws IOException
    {
        File mf = getMainStorageFile( from );
        File mt = getMainStorageFile( to );

        if ( mf.exists() )
        {
            FileUtils.copyFile( mf, mt );

            File ff = getFastStorageFile( from );
            File ft = getFastStorageFile( to );
            if ( ff != null && ff.exists() && ft != null )
            {
                FileUtils.copyFile( ff, ft );
            }
        }
        else
        {
            throw new IOException( "Cannot copy. Source file: " + mf + " does not exist!" );
        }
    }

    public boolean delete( ConcreteResource resource )
            throws IOException
    {
        File fast = getFastStorageFile( resource );
        if ( fast != null )
        {
            if ( !fast.delete() )
            {
                throw new IOException( "Cannot delete fast-storage file: " + fast );
            }
        }

        return getMainStorageFile( resource ).delete();
    }

    public String[] list( ConcreteResource resource )
    {
        final String[] listing = getMainStorageFile( resource ).list();
        if ( listing == null )
        {
            return null;
        }

        final List<String> list = new ArrayList<String>( Arrays.asList( listing ) );
        for ( final Iterator<String> it = list.iterator(); it.hasNext(); )
        {
            final String fname = it.next();
            if ( fname.charAt( 0 ) == '.' )
            {
                it.remove();
                continue;
            }

            for ( final String suffix : CacheProvider.HIDDEN_SUFFIXES )
            {
                if ( fname.endsWith( suffix ) )
                {
                    it.remove();
                }
            }
        }

        return list.toArray( new String[list.size()] );
    }

    public boolean isDirectory( ConcreteResource resource )
    {
        File f = getFastStorageFile( resource );
        if ( f != null && f.exists() )
        {
            return f.isDirectory();
        }

        f = getMainStorageFile( resource );
        return f.isDirectory();
    }

    public boolean isFile( ConcreteResource resource )
    {
        File f = getFastStorageFile( resource );
        if ( f != null && f.exists() )
        {
            return f.isDirectory();
        }

        f = getMainStorageFile( resource );
        return f.isFile();
    }

    public OutputStream wrapperOutputStream( File f )
            throws FileNotFoundException
    {
        final File downloadFile = new File( f.getPath() + CacheProvider.SUFFIX_TO_WRITE );
        final FileOutputStream stream = new FileOutputStream( downloadFile );

        return new AtomicFileOutputStreamWrapper( f, downloadFile, stream );
    }

    public void mkdirs( ConcreteResource resource )
            throws IOException
    {
        getMainStorageFile( resource ).mkdirs();
        getFastStorageFile( resource ).mkdirs();
    }

    public void createFile( ConcreteResource resource )
            throws IOException
    {
        getMainStorageFile( resource ).createNewFile();
        getFastStorageFile( resource ).createNewFile();
    }

    public void createAlias( ConcreteResource from, ConcreteResource to, boolean link )
            throws IOException
    {
        // if the download landed in a different repository, copy it to the current one for
        // completeness...
        final Location fromKey = from.getLocation();
        final Location toKey = to.getLocation();
        final String fromPath = from.getPath();
        final String toPath = to.getPath();

        if ( fromKey != null && toKey != null && !fromKey.equals( toKey ) && fromPath != null && toPath != null
                && !fromPath.equals( toPath ) )
        {
            if ( link )
            {
                // TODO: Why is linking disabled?? Is it because it'd couple DELETEs, etc? Maybe a way to do a file pool instead?
                copy( from, to );
                //                Files.createLink( Paths.get( fromFile.toURI() ), Paths.get( toFile.toURI() ) );
            }
            else
            {
                copy( from, to );
            }
        }
    }

    public void clearTransferCache()
    {
        transferCache.clear();
    }

    public long length( ConcreteResource resource )
    {
        return getFastestExistingFile( resource ).length();
    }

    public long lastModified( ConcreteResource resource )
    {
        return getFastestExistingFile( resource ).lastModified();
    }

    public boolean isReadLocked( final ConcreteResource resource )
    {
        return lockingSupport.isReadLocked( resource );
    }

    public boolean isWriteLocked( final ConcreteResource resource )
    {
        return lockingSupport.isWriteLocked( resource );
    }

    public void unlockRead( final ConcreteResource resource )
    {
        lockingSupport.unlockRead( resource );
    }

    public void unlockWrite( final ConcreteResource resource )
    {
        lockingSupport.unlockWrite( resource );
    }

    public void lockRead( final ConcreteResource resource )
    {
        lockingSupport.lockRead( resource );
    }

    public void lockWrite( final ConcreteResource resource )
    {
        lockingSupport.lockWrite( resource );
    }

    public void waitForWriteUnlock( final ConcreteResource resource )
    {
        lockingSupport.waitForWriteUnlock( resource );
    }

    public void waitForReadUnlock( final ConcreteResource resource )
    {
        lockingSupport.waitForReadUnlock( resource );
    }

    public void cleanupCurrentThread()
    {
        lockingSupport.cleanupCurrentThread();
    }

    public void startReporting()
    {
        lockingSupport.startReporting();
    }

    public void stopReporting()
    {
        lockingSupport.stopReporting();
    }

}
