package org.commonjava.maven.galley.cache;

import org.apache.commons.io.output.TeeOutputStream;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.event.FileEventManager;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.commonjava.maven.galley.spi.io.TransferDecorator;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by jdcasey on 2/27/16.
 */
public abstract class AbstractFileBasedCacheProvider
        implements CacheProvider
{
    @Inject
    private PathGenerator pathGenerator;

    @Inject
    private FileEventManager fileEventManager;

    @Inject
    private TransferDecorator transferDecorator;

    private ResourceFileCacheHelper helper;

    protected AbstractFileBasedCacheProvider(){}

    protected AbstractFileBasedCacheProvider( PathGenerator pathGenerator, FileEventManager fileEventManager,
                                           TransferDecorator transferDecorator )
    {
        this.pathGenerator = pathGenerator;
        this.fileEventManager = fileEventManager;
        this.transferDecorator = transferDecorator;
    }

    protected abstract boolean isAliasLinkingEnabled();

    protected abstract ResourceFileCacheHelper createFileCacheHelper( FileEventManager fileEventManager,
                                                                      TransferDecorator transferDecorator, PathGenerator pathGenerator );

    protected PathGenerator getPathGenerator()
    {
        return pathGenerator;
    }

    protected FileEventManager getFileEventManager()
    {
        return fileEventManager;
    }

    protected TransferDecorator getTransferDecorator()
    {
        return transferDecorator;
    }

    protected ResourceFileCacheHelper getHelper()
    {
        return helper;
    }

    @Override
    public OutputStream openOutputStream( final ConcreteResource resource )
            throws IOException
    {
        // copied directly from FileCacheProvider...
        File fast = helper.getFastStorageFile( resource );
        File main = helper.getMainStorageFile( resource );

        helper.mkdirs(main);
        if ( fast != null )
        {
            helper.mkdirs(fast);
            return new TeeOutputStream( helper.wrapperOutputStream( main ), helper.wrapperOutputStream( fast ) );
        }

        return helper.wrapperOutputStream( main );
    }

    @PostConstruct
    public void start()
    {
        this.helper = createFileCacheHelper( fileEventManager, transferDecorator, pathGenerator );
    }

    @PreDestroy
    public void stop()
    {
        stopReporting();
    }

    @Override
    public File getDetachedFile( ConcreteResource resource )
    {
        return helper.getDetachedFile( resource );
    }

    @Override
    public boolean isDirectory( final ConcreteResource resource )
    {
        return helper.isDirectory( resource );
    }

    @Override
    public boolean isFile( final ConcreteResource resource )
    {
        return helper.isFile( resource );
    }

    @Override
    public boolean exists( final ConcreteResource resource )
    {
        return helper.exists( resource );
    }

    @Override
    public void copy( final ConcreteResource from, final ConcreteResource to )
            throws IOException
    {
        helper.copy( from, to );
    }

    @Override
    public boolean delete( final ConcreteResource resource )
        throws IOException
    {
        return helper.delete( resource );
    }

    @Override
    public String[] list( final ConcreteResource resource )
    {
        return helper.list( resource );
    }

    @Override
    public void mkdirs( final ConcreteResource resource )
        throws IOException
    {
        helper.mkdirs( resource );
    }

    @Override
    public void createFile( final ConcreteResource resource )
        throws IOException
    {
        helper.createFile( resource );
    }

    @Override
    public void createAlias( final ConcreteResource from, final ConcreteResource to )
        throws IOException
    {
        helper.createAlias( from, to, isAliasLinkingEnabled() );
    }

    @Override
    public String getFilePath( final ConcreteResource resource )
    {
        return helper.getFilePath( resource );
    }

    @Override
    public synchronized Transfer getTransfer( final ConcreteResource resource )
    {
        return helper.getTransfer( resource );
    }

    @Override
    public void clearTransferCache()
    {
        helper.clearTransferCache();
    }

    @Override
    public long length( final ConcreteResource resource )
    {
        return helper.length( resource );
    }

    @Override
    public long lastModified( final ConcreteResource resource )
    {
        return helper.lastModified( resource );
    }

    @Override
    public boolean isReadLocked( ConcreteResource resource )
    {
        return helper.isReadLocked( resource );
    }

    @Override
    public boolean isWriteLocked( ConcreteResource resource )
    {
        return helper.isWriteLocked( resource );
    }

    @Override
    public void unlockRead( ConcreteResource resource )
    {
        helper.unlockRead( resource );
    }

    @Override
    public void unlockWrite( ConcreteResource resource )
    {
        helper.unlockWrite( resource );
    }

    @Override
    public void lockRead( ConcreteResource resource )
    {
        helper.lockRead( resource );
    }

    @Override
    public void lockWrite( ConcreteResource resource )
    {
        helper.lockWrite( resource );
    }

    @Override
    public void waitForWriteUnlock( ConcreteResource resource )
    {
        helper.waitForWriteUnlock( resource );
    }

    @Override
    public void waitForReadUnlock( ConcreteResource resource )
    {
        helper.waitForReadUnlock( resource );
    }

    @Override
    public void cleanupCurrentThread()
    {
        helper.cleanupCurrentThread();
    }

    @Override
    public void startReporting()
    {
        helper.startReporting();
    }

    @Override
    public void stopReporting()
    {
        helper.stopReporting();
    }
}
