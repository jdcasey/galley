package org.commonjava.maven.galley.cache.infinispan;

import org.commonjava.maven.galley.cache.CacheProviderTCK;
import org.commonjava.maven.galley.cache.partyline.PartyLineCacheProvider;
import org.commonjava.maven.galley.cache.testutil.TestFileEventManager;
import org.commonjava.maven.galley.cache.testutil.TestTransferDecorator;
import org.commonjava.maven.galley.io.HashedLocationPathGenerator;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.spi.event.FileEventManager;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.commonjava.maven.galley.spi.io.TransferDecorator;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractFastLocalCacheBMUnitTest
        extends CacheProviderTCK
{
    protected static EmbeddedCacheManager CACHE_MANAGER;

    protected final PathGenerator pathgen = new HashedLocationPathGenerator();

    protected final FileEventManager events = new TestFileEventManager();

    protected final TransferDecorator decorator = new TestTransferDecorator();

    protected final ExecutorService executor = Executors.newFixedThreadPool( 5 );

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public TestName name = new TestName();

    protected FastLocalCacheProvider provider;

    protected Cache<String, String> cache = CACHE_MANAGER.getCache( NFSOwnerCacheProducer.CACHE_NAME );

    protected String result;

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
        provider =
                new FastLocalCacheProvider( new PartyLineCacheProvider( temp.newFolder(), pathgen, events, decorator ),
                                            cache, pathgen, events, decorator, executor, nfsBasePath );
        provider.init();
    }

    @After
    public void tearDown()
    {
        provider.destroy();
    }

    @Override
    protected FastLocalCacheProvider getCacheProvider()
            throws Exception
    {
        return provider;
    }

    protected void latchWait( CountDownLatch latch )
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
        File file = new File( tempBaseDir + "/mnt/nfs" );
        file.delete();
        file.mkdirs();
        return file.getCanonicalPath();
    }

    protected class WriteLockThread
            implements Runnable
    {
        private ConcreteResource res;

        private CountDownLatch latch;

        public WriteLockThread( ConcreteResource res, CountDownLatch latch )
        {
            this.res = res;
            this.latch = latch;
        }

        @Override
        public void run()
        {
            provider.lockWrite( res );
            provider.waitForWriteUnlock( res );
            latch.countDown();
        }
    }

    protected class ReadLockThread
            implements Runnable
    {
        private ConcreteResource res;

        private CountDownLatch latch;

        public ReadLockThread( ConcreteResource res, CountDownLatch latch )
        {
            this.res = res;
            this.latch = latch;
        }

        @Override
        public void run()
        {
            provider.lockRead( res );
            provider.waitForReadUnlock( res );
            latch.countDown();
        }
    }

    protected class WriteFileThread
            implements Runnable
    {

        private String content;

        private Location loc;

        private String fname;

        private CountDownLatch latch;

        public WriteFileThread( String content, Location loc, String fname, CountDownLatch latch )
        {
            this.content = content;
            this.loc = loc;
            this.fname = fname;
            this.latch = latch;
        }

        @Override
        public void run()
        {
            try
            {
                ConcreteResource resource = new ConcreteResource( loc, fname );
                provider.lockWrite( resource );
                OutputStream out = provider.openOutputStream( resource );
                out.write( content.getBytes( "UTF-8" ) );
                out.flush();
                out.close();
                latch.countDown();
                provider.unlockWrite( resource );
            }
            catch ( IOException e )
            {
                System.out.println( "File outputStream Writing Exception." );
                e.printStackTrace();
            }

        }
    }

    protected class ReadFileThread
            implements Runnable
    {

        private Location loc;

        private String fname;

        private CountDownLatch latch;

        public ReadFileThread( Location loc, String fname, CountDownLatch latch )
        {
            this.loc = loc;
            this.fname = fname;
            this.latch = latch;
        }

        @Override
        public void run()
        {
            try
            {
                InputStream in = provider.openInputStream( new ConcreteResource( loc, fname ) );
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int read = -1;
                byte[] buf = new byte[512];
                while ( ( read = in.read( buf ) ) > -1 )
                {
                    baos.write( buf, 0, read );
                }

                result = new String( baos.toByteArray(), "UTF-8" );

                latch.countDown();
            }
            catch ( IOException e )
            {
                System.out.println( "File inputStream Reading Exception." );
                e.printStackTrace();
            }

        }
    }

    protected class CopyFileThread
            implements Runnable
    {

        private Location loc;

        private Location loc2;

        private String fname;

        private CountDownLatch latch;

        public CopyFileThread( Location loc, Location loc2, String fname, CountDownLatch latch )
        {
            this.loc = loc;
            this.loc2 = loc2;
            this.fname = fname;
            this.latch = latch;
        }

        @Override
        public void run()
        {
            try
            {
                provider.copy( new ConcreteResource( loc, fname ), new ConcreteResource( loc2, fname ) );

                InputStream in = provider.openInputStream( new ConcreteResource( loc2, fname ) );
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int read = -1;
                byte[] buf = new byte[512];
                while ( ( read = in.read( buf ) ) > -1 )
                {
                    baos.write( buf, 0, read );
                }

                result = new String( baos.toByteArray(), "UTF-8" );

                latch.countDown();
            }
            catch ( IOException e )
            {
                System.out.println( "File Copying and inputStream Reading Exception." );
                e.printStackTrace();
            }

        }
    }

    protected class DeleteFileThread
            implements Runnable
    {

        private Location loc;

        private String fname;

        private CountDownLatch latch;

        public DeleteFileThread( Location loc, String fname, CountDownLatch latch )
        {
            this.loc = loc;
            this.fname = fname;
            this.latch = latch;
        }

        @Override
        public void run()
        {
            try
            {
                provider.delete( new ConcreteResource( loc, fname ) );
                latch.countDown();
            }
            catch ( IOException e )
            {
                System.out.println( "File Deleting Exception." );
                e.printStackTrace();
            }

        }
    }
}
