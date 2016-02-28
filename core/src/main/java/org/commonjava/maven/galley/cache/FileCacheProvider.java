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
        extends AbstractFileBasedCacheProvider
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private FileCacheProviderConfig config;

    private final SimpleLockingSupport lockingSupport = new SimpleLockingSupport();

    protected FileCacheProvider()
    {
    }

    public FileCacheProvider( final File cacheBasedir, final PathGenerator pathGenerator, final FileEventManager fileEventManager,
                              final TransferDecorator transferDecorator, final boolean aliasLinking )
    {
        super( pathGenerator, fileEventManager, transferDecorator );
        this.config = new FileCacheProviderConfig( cacheBasedir ).withAliasLinking( aliasLinking );
        start();
    }

    public FileCacheProvider( final FileCacheProviderConfig config, final PathGenerator pathGenerator, final FileEventManager fileEventManager,
                              final TransferDecorator transferDecorator )
    {
        super( pathGenerator, fileEventManager, transferDecorator );
        this.config = config;
        start();
    }

    public FileCacheProvider( final File cacheBasedir, final PathGenerator pathGenerator, final FileEventManager fileEventManager,
                              final TransferDecorator transferDecorator )
    {
        this( cacheBasedir, pathGenerator, fileEventManager, transferDecorator, true );
    }

    @Override
    public InputStream openInputStream( final ConcreteResource resource )
        throws IOException
    {
        File fast = getHelper().getFastStorageFile( resource );
        File main = getHelper().getMainStorageFile( resource );
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
        File fast = getHelper().getFastStorageFile( resource );
        File main = getHelper().getMainStorageFile( resource );

        getHelper().mkdirs(main);
        if ( fast != null )
        {
            getHelper().mkdirs(fast);
            return new TeeOutputStream( getHelper().wrapperOutputStream( main ), getHelper().wrapperOutputStream( fast ) );
        }

        return getHelper().wrapperOutputStream( main );
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
        return new ResourceFileCacheHelper( fileEventManager, transferDecorator, pathGenerator, lockingSupport, config.getCacheBasedir(), this );
    }
}
