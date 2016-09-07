package org.commonjava.maven.galley.cache.infinispan;

import org.apache.commons.io.IOUtils;
import org.commonjava.maven.galley.cache.partyline.PartyLineCacheProvider;
import org.commonjava.maven.galley.cache.testutil.TestFileEventManager;
import org.commonjava.maven.galley.cache.testutil.TestTransferDecorator;
import org.commonjava.maven.galley.io.HashedLocationPathGenerator;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.SimpleLocation;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.event.FileEventManager;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.commonjava.maven.galley.spi.io.TransferDecorator;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

@RunWith( org.jboss.byteman.contrib.bmunit.BMUnitRunner.class )
@BMUnitConfig( loadDirectory = "target/test-classes/bmunit", debug = true, agentPort = "-1" )
public class FastLocalCacheProviderConcurrentTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static EmbeddedCacheManager CACHE_MANAGER;

    private final String content = "This is a bmunit test";

    private final String diffContent = "This is different from content";

    private final CountDownLatch latch = new CountDownLatch( 2 );

    private FastLocalCacheProvider provider;

    private final PathGenerator pathgen = new HashedLocationPathGenerator();

    private final FileEventManager events = new TestFileEventManager();

    private final TransferDecorator decorator = new TestTransferDecorator();

    private final Cache<String, String> cache = CACHE_MANAGER.getCache( NFSOwnerCacheProducer.CACHE_NAME );

    private PartyLineCacheProvider plProvider;

    private final ExecutorService executor = Executors.newFixedThreadPool( 4 );

    private final ExecutorService testPool = Executors.newFixedThreadPool( 2 );

    @BeforeClass
    public static void setupClass()
    {
        CACHE_MANAGER = new NFSOwnerCacheProducer().getCacheMgr();
    }

    @Before
    public void setup()
            throws Exception
    {
        final String nfsBasePath = createNFSBaseDir( temp.newFolder().getCanonicalPath() );
        plProvider = new PartyLineCacheProvider( temp.newFolder(), pathgen, events, decorator );
        provider =
                new FastLocalCacheProvider( plProvider,
                                            cache, pathgen, events, decorator, executor, nfsBasePath );
        provider.init();
    }

    @Test
    @BMScript( "TryToReadWhileWriting.btm" )
    public void testReadWrite()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_read_write.txt" );
        testPool.execute( new WriteTask( provider, content, resource, latch, 1000 ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );

        latchWait( latch );

        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( content ) );
        assertThat( provider.exists( resource ), equalTo( true ));
        assertThat( readLocalResource( resource ), equalTo( content ));
        assertThat( readNFSResource( resource ), equalTo( content ));
    }

    @Test
    @BMScript( "TryToWriteWhileReading.btm" )
    public void tesWriteReadWithLocal()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_write_read.txt" );
        prepareBothResource( resource, content );
        testPool.execute( new WriteTask( provider, diffContent, resource, latch, 1000 ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );

        latchWait( latch );

        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( content ) );
        final String changedResult = readLocalResource( resource );
        assertThat( changedResult, equalTo( diffContent ));
    }

    @Test
    @BMScript( "TryToWriteWhileReading.btm" )
    public void testWriteReadWithNFS() throws Exception{
        final ConcreteResource resource = createTestResource( "file_write_read_has_only_NFS.txt" );
        prepareNFSResource( resource, content );
        testPool.execute( new WriteTask( provider, diffContent, resource, latch ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );

        latchWait( latch );

        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( content ) );
        final String changedResult = readLocalResource( resource );
        assertThat( changedResult, equalTo( diffContent ) );
    }

    @Test
    @BMScript( "TryToWriteThenWaitReadingStreamOpen.btm" )
    public void testWriteWhenReadOpenWithNFS()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_write_wait_read_has_only_NFS.txt" );
        prepareNFSResource( resource, content );
        testPool.execute( new WriteTask( provider, diffContent, resource, latch ) );
        //make sure write task execute first
        Thread.sleep( 500 );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch, 500 ) );

        latchWait( latch );

        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( diffContent ) );
        final String changedResult = readLocalResource( resource );
        assertThat( changedResult, equalTo( diffContent ));
    }

    @Test
    @BMScript( "TryToWriteWhileReading.btm" )
    public void testWriteReadWithNoResource() throws Exception{
        final ConcreteResource resource = createTestResource( "file_write_read_no_both_resource.txt" );
        testPool.execute( new WriteTask( provider, content, resource, latch ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );

        latchWait( latch );

        final String readingResult = readingFuture.get();
        assertNull(readingResult);
        final String changedResult = readLocalResource( resource );
        assertThat( changedResult, equalTo( content ) );
    }

    @Test
    public void testBothWrite() throws Exception{
        final ConcreteResource resource = createTestResource( "file_both_write.txt" );
        testPool.execute( new WriteTask( provider, content, resource, latch ) );
        testPool.execute( new WriteTask( provider, diffContent, resource, latch ) );

        latchWait( latch );

        final String localResult = readLocalResource( resource );
        final String nfsResult = readNFSResource( resource );
        assertThat( localResult, equalTo( nfsResult ) );
    }

    @Test
    public void testBothReadWithNFS() throws Exception{
        final ConcreteResource resource = createTestResource( "file_both_read_has_only_NFS.txt" );
        prepareNFSResource( resource, content );
        final Future<String> readingFuture1 = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );
        final Future<String> readingFuture2 = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );

        latchWait( latch );

        final String readingResult1 = readingFuture1.get();
        assertThat( readingResult1, equalTo( content ) );
        final String readingResult2 = readingFuture2.get();
        assertThat( readingResult2, equalTo( content ) );
    }

    @Test
    @BMScript( "TryToDeleteWhileReadingCompleted.btm" )
    public void testDeleteWhenReadCompleted()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_delete_read.txt" );
        prepareBothResource( resource, content );
        final Future<Boolean> deleteFuture =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );

        latchWait( latch );

        final Boolean deleted = deleteFuture.get();
        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( content ) );
        assertThat( deleted, equalTo( true ) );
        assertThat( provider.exists( resource ), equalTo( false ) );
    }

    @Test
    @BMScript( "TryToDeleteWhileReadingNotCompleted.btm" )
    public void testDeleteWhenReadNotCompleted()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_delete_read_not_completed.txt" );
        prepareBothResource( resource, content );
        final Future<Boolean> deleteFuture =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch, 1000 ) );

        latchWait( latch );

        final Boolean deleted = deleteFuture.get();
        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( content ) );
        assertThat( deleted, equalTo( false ) );
        assertThat( provider.exists( resource ), equalTo( true ) );
    }

    @Test
    @BMScript( "TryToDeleteWhileReadingNotCompleted.btm" )
    public void testDeleteWhenReadNotCompletedWithNFS()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_delete_read_not_completed_nfs_only.txt" );
        prepareNFSResource( resource, content );
        final Future<Boolean> deleteFuture =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch, 1000 ) );

        latchWait( latch );

        final Boolean deleted = deleteFuture.get();
        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( content ) );
        assertThat( deleted, equalTo( false ) );
        assertThat( provider.exists( resource ), equalTo( true ) );
    }

    @Test
    @BMScript( "TryToReadWhileDeleteCompleted.btm" )
    public void testReadWhileDeleteCompleted()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_read_delete_completed.txt" );
        prepareBothResource( resource, content );
        final Future<Boolean> deleteFuture =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        final Future<String> readingFuture = testPool.submit((Callable<String>) new ReadTask( provider, content, resource, latch ) );

        latchWait( latch );

        final Boolean deleted = deleteFuture.get();
        final String readingResult = readingFuture.get();
        assertThat( readingResult, equalTo( null ) );
        assertThat( deleted, equalTo( true ) );
        assertThat( provider.exists( resource ), equalTo( false ) );
    }

    @Test
    @BMScript( "TryToDeleteWhileWritingCompleted.btm" )
    public void testDeleteWhenWriteCompleted()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_delete_write_completed.txt" );
        final Future<Boolean> deleteFuture =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        testPool.execute( new WriteTask( provider, content, resource, latch ) );

        latchWait( latch );

        final Boolean deleted = deleteFuture.get();
        assertThat( deleted, equalTo( true ) );
        assertThat( provider.exists( resource ), equalTo( false ) );
    }

    @Test
    @BMScript( "TryToDeleteWhileWritingNotCompleted.btm" )
    public void testDeleteWhenWriteNotCompleted()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_delete_write_not_completed.txt" );
        final Future<Boolean> deleteFuture =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        testPool.execute( new WriteTask( provider, content, resource, latch, 1000 ) );

        latchWait( latch );

        final Boolean deleted = deleteFuture.get();
        assertThat( deleted, equalTo( false ) );
        assertThat( provider.exists( resource ), equalTo( true ) );
    }

    @Test
    @BMScript( "TryToWriteWhileDeleteCompleted.btm" )
    public void testWriteWhenDeleteCompleted()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_write_delete_completed.txt" );
        prepareBothResource( resource, content );
        final Future<Boolean> deleteFuture =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        testPool.execute( new WriteTask( provider, content, resource, latch ) );

        latchWait( latch );

        final Boolean deleted = deleteFuture.get();
        assertThat( deleted, equalTo( true ) );
        assertThat( provider.exists( resource ), equalTo( true ) );
    }

    @Test
    public void testBothDelete()
            throws Exception
    {
        final ConcreteResource resource = createTestResource( "file_both_delete.txt" );
        prepareBothResource( resource, content );
        final Future<Boolean> deleteFurture1 =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );
        final Future<Boolean> deleteFurture2 =
                testPool.submit( (Callable<Boolean>) new DeleteTask( provider, content, resource, latch ) );

        latchWait( latch );

        final Boolean deleted1 = deleteFurture1.get();
        final Boolean deleted2 = deleteFurture2.get();
        assertThat( deleted1 && deleted2, equalTo( false ) );
        assertThat( deleted1 || deleted2, equalTo( true ) );
        assertThat( provider.exists( resource ), equalTo( false ) );
    }

    private void latchWait( CountDownLatch latch )
    {
        try
        {
            latch.await();
        }
        catch ( InterruptedException e )
        {
            System.out.println( "Threads await Exception." );
        }
    }

    private String createNFSBaseDir( String tempBaseDir )
            throws IOException
    {
        final File file = new File( tempBaseDir + "/mnt/nfs" );
        file.delete();
        file.mkdirs();
        return file.getCanonicalPath();
    }

    private void prepareBothResource( ConcreteResource resource, String content )
            throws IOException
    {
        prepareLocalResource( resource, content );
        prepareNFSResource( resource, content );
    }

    private void prepareLocalResource( ConcreteResource resource, String content )
            throws IOException
    {
        OutputStream stream = plProvider.openOutputStream( resource );
        IOUtils.write( content.getBytes(), stream );
        stream.close();
    }

    private void prepareNFSResource( ConcreteResource resource, String content )
            throws IOException
    {
        final File file = provider.getNFSDetachedFile( resource );
        if ( !file.exists() )
        {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        OutputStream stream = new FileOutputStream( file );
        IOUtils.write( content.getBytes(), stream );
        stream.close();
    }

    private String readLocalResource( ConcreteResource resource )
            throws IOException
    {
        return readFromStream( plProvider.openInputStream( resource ) );
    }

    private String readNFSResource(ConcreteResource resource ) throws IOException{
        return readFromStream( new FileInputStream( provider.getNFSDetachedFile( resource ) ) );
    }

    private String readFromStream(InputStream in) throws IOException{
        if ( in == null )
        {
            System.out.println( "Can not read content as the input stream is null." );
            return null;
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read;
        final byte[] buf = new byte[512];
        while ( ( read = in.read( buf ) ) > -1 )
        {
            baos.write( buf, 0, read );
        }

        String readingResult = new String( baos.toByteArray(), "UTF-8" );
        baos.close();
        in.close();

        return readingResult;
    }

    private ConcreteResource createTestResource( String fname )
    {
        final Location loc = new SimpleLocation( "http://foo.com" );
        return new ConcreteResource( loc, String.format( "/path/to/my/%s", fname ) );
    }

    private abstract class CacheProviderWorkingTask
            implements Runnable
    {
        protected CacheProvider provider;

        protected String content;

        protected ConcreteResource resource;

        protected CountDownLatch controlLatch;

        protected long waiting;

        protected CacheProviderWorkingTask( CacheProvider provider, String content, ConcreteResource resource,
                                            CountDownLatch controlLatch, long waiting )
        {
            this.provider = provider;
            this.content = content;
            this.resource = resource;
            this.controlLatch = controlLatch;
            this.waiting = waiting;
        }
    }

    private final class WriteTask
            extends CacheProviderWorkingTask
    {
        public WriteTask( CacheProvider provider, String content, ConcreteResource resource,
                          CountDownLatch controlLatch, long waiting )
        {
            super( provider, content, resource, controlLatch, waiting );
        }

        public WriteTask( CacheProvider provider, String content, ConcreteResource resource,
                          CountDownLatch controlLatch )
        {
            super( provider, content, resource, controlLatch, -1 );
        }

        @Override
        public void run()
        {
            try
            {
                final OutputStream out = provider.openOutputStream( resource );
                final ByteArrayInputStream bais = new ByteArrayInputStream( content.getBytes() );
                int read = -1;
                final byte[] buf = new byte[512];
                System.out.println(
                        String.format( "<<<WriteTask>>> start to write to the resource with outputStream %s",
                                       out.getClass().getName() ) );
                while ( ( read = bais.read( buf ) ) > -1 )
                {
                    if ( waiting > 0 )
                    {
                        Thread.sleep( waiting );
                    }
                    out.write( buf, 0, read );
                }
                out.close();
                System.out.println(
                        String.format( "<<<WriteTask>>> writing to the resource done with outputStream %s",
                                       out.getClass().getName() ) );
                controlLatch.countDown();
            }
            catch ( Exception e )
            {
                System.out.println( "Write Task Runtime Exception." );
                e.printStackTrace();
            }
        }
    }

    private final class ReadTask
            extends CacheProviderWorkingTask implements Callable<String>
    {
        private String readingResult;

        public ReadTask( CacheProvider provider, String content, ConcreteResource resource,
                         CountDownLatch controlLatch )
        {
            super( provider, content, resource, controlLatch, -1 );
        }

        public ReadTask( CacheProvider provider, String content, ConcreteResource resource, CountDownLatch controlLatch,
                         long waiting )
        {
            super( provider, content, resource, controlLatch, waiting );
        }

        @Override
        public void run()
        {
            try
            {
                final InputStream in = provider.openInputStream( resource );
                if ( in == null )
                {
                    System.out.println( "Can not read content as the input stream is null." );
                    controlLatch.countDown();
                    return;
                }
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int read = -1;
                final byte[] buf = new byte[512];
                System.out.println(
                        String.format( "<<<ReadTask>>> will start to read from the resource with inputStream %s",
                                       in.getClass().getName() ) );
                while ( ( read = in.read( buf ) ) > -1 )
                {
                    if ( waiting > 0 )
                    {
                        Thread.sleep( waiting );
                    }
                    baos.write( buf, 0, read );
                }
                baos.close();
                in.close();
                System.out.println(
                        String.format( "<<<ReadTask>>> reading from the resource done with inputStream %s",
                                       in.getClass().getName() ) );
                readingResult = new String( baos.toByteArray(), "UTF-8" );
                controlLatch.countDown();
            }
            catch ( Exception e )
            {
                System.out.println( "Read Task Runtime Exception." );
                e.printStackTrace();
            }
        }

        @Override
        public String call(){
            this.run();
            return readingResult;
        }
    }

    private final class DeleteTask
            extends CacheProviderWorkingTask
            implements Callable<Boolean>
    {
        private Boolean callResult;

        public DeleteTask( CacheProvider provider, String content, ConcreteResource resource,
                           CountDownLatch controlLatch )
        {
            super( provider, content, resource, controlLatch, -1 );
        }

        public DeleteTask( CacheProvider provider, String content, ConcreteResource resource,
                           CountDownLatch controlLatch, long waiting )
        {
            super( provider, content, resource, controlLatch, waiting );
        }

        @Override
        public void run()
        {
            try
            {
                if ( waiting > 0 )
                {
                    Thread.sleep( waiting );
                }
                System.out.println("<<<DeleteTask>>> start to delete resource");
                callResult = provider.delete( resource );
            }
            catch ( Exception e )
            {
                System.out.println( "Delete Task Runtime Exception." );
                e.printStackTrace();
            }
            finally
            {
                controlLatch.countDown();
            }
        }

        @Override
        public Boolean call()
        {
            this.run();
            return callResult;
        }
    }
}
