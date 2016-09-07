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
package org.commonjava.maven.galley.cache.infinispan;

import org.commonjava.maven.galley.cache.partyline.PartyLineCacheProvider;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.SimpleLocation;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith( org.jboss.byteman.contrib.bmunit.BMUnitRunner.class )
@BMUnitConfig( loadDirectory = "target/test-classes/bmunit/common", debug = true, agentPort = "-1")
public class FastLocalCacheProviderTest
        extends AbstractFastLocalCacheBMUnitTest
{
    @Test
    @BMScript( "LockThenWaitForUnLock.btm" )
    public void testLockThenWaitForUnLock()
            throws Exception
    {
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String path = "my/path.txt";
        final ConcreteResource res = new ConcreteResource( loc, path );

        CountDownLatch latch = new CountDownLatch( 2 );

        new Thread( new WriteLockThread( res, latch ) ).start();
        new Thread( new ReadLockThread( res, latch ) ).start();
        latchWait( latch );

        assertThat( provider.isWriteLocked( res ), equalTo( false ) );
        assertThat( provider.isReadLocked( res ), equalTo( false ) );
    }

    @Test
    @BMScript( "SimultaneousWritesResourceExistence.btm" )
    public void testSimultaneousWritesResourceExistence()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String dir = "/path/to/my/";
        final String fname1 = dir + "file1.txt";
        final String fname2 = dir + "file2.txt";

        CountDownLatch latch = new CountDownLatch( 2 );

        new Thread( new WriteFileThread( content, loc, fname1, latch ) ).start();
        new Thread( new WriteFileThread( content, loc, fname2, latch ) ).start();
        latchWait( latch );

        assertThat( provider.exists( new ConcreteResource( loc, fname1 ) ), equalTo( true ) );
        assertThat( provider.exists( new ConcreteResource( loc, fname2 ) ), equalTo( true ) );
        assertThat( provider.isDirectory( new ConcreteResource( loc, dir ) ), equalTo( true ) );

        final Set<String> listing =
                new HashSet<String>( Arrays.asList( provider.list( new ConcreteResource( loc, dir ) ) ) );
        assertThat( listing.size() > 1, equalTo( true ) );
        assertThat( listing.contains( "file1.txt" ), equalTo( true ) );
        assertThat( listing.contains( "file2.txt" ), equalTo( true ) );
    }

    @Test
    @BMScript( "WriteDeleteAndVerifyNonExistence.btm" )
    public void testWriteDeleteAndVerifyNonExistence()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname = "/path/to/my/file.txt";

        CountDownLatch latch = new CountDownLatch( 2 );

        new Thread( new WriteFileThread( content, loc, fname, latch ) ).start();
        new Thread( new DeleteFileThread( loc, fname, latch ) ).start();
        latchWait( latch );

        assertThat( provider.exists( new ConcreteResource( loc, fname ) ), equalTo( false ) );
    }

    @Test
    @BMScript( "ConcurrentWriteAndReadFile.btm" )
    public void ConcurrentWriteAndReadFile()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname = "/path/to/my/file.txt";

        CountDownLatch latch = new CountDownLatch( 2 );

        new Thread( new ReadFileThread( loc, fname, latch ) ).start();
        new Thread( new WriteFileThread( content, loc, fname, latch ) ).start();
        latchWait( latch );

        assertThat( result, equalTo( content ) );
    }

    @Test
    @BMScript( "WriteCopyAndReadNewFile.btm" )
    public void writeCopyAndReadNewFile()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname = "/path/to/my/file.txt";
        final Location loc2 = new SimpleLocation( "http://bar.com" );

        CountDownLatch latch = new CountDownLatch( 2 );

        new Thread( new CopyFileThread( loc, loc2, fname, latch ) ).start();
        new Thread( new WriteFileThread( content, loc, fname, latch ) ).start();
        latchWait( latch );

        assertThat( result, equalTo( content ) );
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testConstructorWitNoNFSSysPath(){
        new FastLocalCacheProvider(  );
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testConstructorWitNoNFSSysPath2()throws IOException{
        final String NON_EXISTS_PATH = "/mnt/nfs/abc/xyz";
        System.setProperty( FastLocalCacheProvider.NFS_BASE_DIR_KEY, NON_EXISTS_PATH );
        new FastLocalCacheProvider(  );
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testConstructorWitNoNFSSysPath3()
            throws IOException
    {
        System.setProperty( FastLocalCacheProvider.NFS_BASE_DIR_KEY, temp.newFile().getCanonicalPath() );
        new FastLocalCacheProvider();
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testConstructorWitNoNFSSysPath4() throws IOException{
        new FastLocalCacheProvider( new PartyLineCacheProvider( temp.newFolder(), pathgen, events, decorator ), cache,
                                    pathgen, events, decorator, executor );
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testConstructorWitNoNFSSysPath5() throws IOException{
        final String NON_EXISTS_PATH = "/mnt/nfs/abc/xyz";
        new FastLocalCacheProvider( new PartyLineCacheProvider( temp.newFolder(), pathgen, events, decorator ), cache,
                                    pathgen, events, decorator, executor, NON_EXISTS_PATH);
    }

    @Test
    public void testConstructorWitNFSSysPath() throws IOException{
        System.setProperty( FastLocalCacheProvider.NFS_BASE_DIR_KEY, temp.newFolder().getCanonicalPath() );
        new FastLocalCacheProvider();
        new FastLocalCacheProvider( new PartyLineCacheProvider( temp.newFolder(), pathgen, events, decorator ), cache,
                                    pathgen, events, decorator, executor );
    }

}
