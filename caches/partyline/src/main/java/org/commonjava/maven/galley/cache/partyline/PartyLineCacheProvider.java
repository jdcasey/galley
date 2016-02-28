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
package org.commonjava.maven.galley.cache.partyline;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.commonjava.cdi.util.weft.ExecutorConfig;
import org.commonjava.maven.galley.cache.AbstractFileBasedCacheProvider;
import org.commonjava.maven.galley.cache.LockingSupport;
import org.commonjava.maven.galley.cache.ResourceFileCacheHelper;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.event.FileEventManager;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.commonjava.maven.galley.spi.io.TransferDecorator;
import org.commonjava.maven.galley.util.PathUtils;
import org.commonjava.util.partyline.JoinableFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Named( "partyline-galley-cache" )
@Alternative
public class PartyLineCacheProvider
        extends AbstractFileBasedCacheProvider
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final JoinableFileManager fileManager = new JoinableFileManager();

    @Inject
    private PartyLineCacheProviderConfig config;

    @Inject
    @ExecutorConfig( named="fast-storage-transfer", threads=2, daemon=false, priority=4 )
    private ExecutorService fastStorageTransfers;

    private ResourceFileCacheHelper helper;

    protected PartyLineCacheProvider()
    {
    }

    public PartyLineCacheProvider( final File cacheBasedir, final PathGenerator pathGenerator,
                                   final FileEventManager fileEventManager, final TransferDecorator transferDecorator, ExecutorService fastStorageTransfers,
                                   final boolean aliasLinking, final boolean timeoutProcessing,
                                   final int defaultTimeoutSeconds )
    {
        super( pathGenerator, fileEventManager, transferDecorator );
        this.fastStorageTransfers = fastStorageTransfers;
        this.config = new PartyLineCacheProviderConfig( cacheBasedir ).withAliasLinkingEnabled( aliasLinking )
                                                                      .withTimeoutProcessingEnabled( timeoutProcessing )
                                                                      .withDefaultTimeoutSeconds(
                                                                              defaultTimeoutSeconds );
        start();
    }

    public PartyLineCacheProvider( final PartyLineCacheProviderConfig config, final PathGenerator pathGenerator,
                                   final FileEventManager fileEventManager, final TransferDecorator transferDecorator, ExecutorService fastStorageTransfers )
    {
        super( pathGenerator, fileEventManager, transferDecorator );
        this.config = config;
        this.fastStorageTransfers = fastStorageTransfers;
        start();
    }

    public PartyLineCacheProvider( final File cacheBasedir, final PathGenerator pathGenerator,
                                   final FileEventManager fileEventManager, final TransferDecorator transferDecorator, ExecutorService fastStorageTransfers )
    {
        super( pathGenerator, fileEventManager, transferDecorator );
        this.fastStorageTransfers = fastStorageTransfers;
        this.config = new PartyLineCacheProviderConfig( cacheBasedir );
        start();
    }

    @Override
    public InputStream openInputStream( final ConcreteResource resource )
            throws IOException
    {
        File main = helper.getMainStorageFile( resource );
        if ( !main.exists() )
        {
            throw new IOException( "No such file: " + main );
        }

        File fast = helper.getFastStorageFile( resource );
        if ( fast != null )
        {
            if ( fast.exists() )
            {
                return fileManager.openInputStream( fast );
            }
            else
            {
                return new FastTransferInputStream( main, fast, fileManager.openInputStream( main ), fileManager.openOutputStream( fast ), fastStorageTransfers );
            }
        }

        return fileManager.openInputStream( main );
    }

    // TODO: Reimplement to do fast-storage transfers in background.
    // The trick is what file to return (if we're copying things around in the background).
    @Override
    public File getDetachedFile( ConcreteResource resource )
    {
        return super.getDetachedFile( resource );
    }

    @Override
    protected boolean isAliasLinkingEnabled()
    {
        return false;
    }

    @Override
    protected ResourceFileCacheHelper createFileCacheHelper( FileEventManager fileEventManager,
                                                             TransferDecorator transferDecorator,
                                                             PathGenerator pathGenerator )
    {
        PartyLineLockingSupport lockingSupport = new PartyLineLockingSupport( fileManager );

        ResourceFileCacheHelper helper =
                new ResourceFileCacheHelper( fileEventManager, transferDecorator, pathGenerator, lockingSupport,
                                             config.getCacheBasedir(), this );

        lockingSupport.start( this.helper );
        return helper;
    }
}
