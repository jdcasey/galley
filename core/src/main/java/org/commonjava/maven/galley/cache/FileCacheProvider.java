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
package org.commonjava.maven.galley.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.event.FileEventManager;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.commonjava.maven.galley.spi.io.TransferDecorator;
import org.commonjava.maven.galley.util.AtomicFileOutputStreamWrapper;
import org.commonjava.maven.galley.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named( "file-galley-cache" )
@Alternative
public class FileCacheProvider
    implements CacheProvider
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private FileCacheProviderConfig config;

    @Inject
    private PathGenerator pathGenerator;

    @Inject
    private FileEventManager fileEventManager;

    @Inject
    private TransferDecorator transferDecorator;

    private ResourceFileCacheHelper helper;

    private final SimpleLockingSupport lockingSupport = new SimpleLockingSupport();

    protected FileCacheProvider()
    {
    }

    public FileCacheProvider( final File cacheBasedir, final PathGenerator pathGenerator, final FileEventManager fileEventManager,
                              final TransferDecorator transferDecorator, final boolean aliasLinking )
    {
        this.pathGenerator = pathGenerator;
        this.fileEventManager = fileEventManager;
        this.transferDecorator = transferDecorator;
        this.config = new FileCacheProviderConfig( cacheBasedir ).withAliasLinking( aliasLinking );
        start();
    }

    public FileCacheProvider( final FileCacheProviderConfig config, final PathGenerator pathGenerator, final FileEventManager fileEventManager,
                              final TransferDecorator transferDecorator )
    {
        this.config = config;
        this.pathGenerator = pathGenerator;
        this.fileEventManager = fileEventManager;
        this.transferDecorator = transferDecorator;
        start();
    }

    public FileCacheProvider( final File cacheBasedir, final PathGenerator pathGenerator, final FileEventManager fileEventManager,
                              final TransferDecorator transferDecorator )
    {
        this( cacheBasedir, pathGenerator, fileEventManager, transferDecorator, true );
    }

    @PostConstruct
    public void start()
    {
        this.helper = new ResourceFileCacheHelper( fileEventManager, transferDecorator, pathGenerator, lockingSupport, config.getCacheBasedir(), this );
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
    public InputStream openInputStream( final ConcreteResource resource )
        throws IOException
    {
        File fast = helper.getFastStorageFile( resource );
        File main = helper.getMainStorageFile( resource );
        if ( fast != null )
        {
            if ( !fast.exists() )
            {
                FileUtils.copyFile( main, fast );
            }

            return new FileInputStream( fast );
        }

        return new FileInputStream( main );
    }

    @Override
    public OutputStream openOutputStream( final ConcreteResource resource )
        throws IOException
    {
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
        helper.createAlias( from, to, config.isAliasLinking() );
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
