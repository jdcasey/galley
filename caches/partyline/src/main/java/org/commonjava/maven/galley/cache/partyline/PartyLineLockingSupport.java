package org.commonjava.maven.galley.cache.partyline;

import org.commonjava.maven.galley.cache.LockingSupport;
import org.commonjava.maven.galley.cache.ResourceFileCacheHelper;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.util.partyline.JoinableFileManager;

/**
 * Created by jdcasey on 2/27/16.
 */
public class PartyLineLockingSupport
        implements LockingSupport
{
    private JoinableFileManager fileManager;

    private ResourceFileCacheHelper helper;

    public PartyLineLockingSupport( JoinableFileManager fileManager )
    {
        this.fileManager = fileManager;
    }

    public void start( ResourceFileCacheHelper helper )
    {
        this.helper = helper;
    }

    public boolean isReadLocked( final ConcreteResource resource )
    {
        return fileManager.isReadLocked( helper.getFastestExistingFile( resource ) );
    }

    public boolean isWriteLocked( final ConcreteResource resource )
    {
        return fileManager.isWriteLocked( helper.getMainStorageFile( resource ) );
    }

    public void unlockRead( final ConcreteResource resource )
    {
        //        fileManager.unlock( getDetachedFile( resource ) );
    }

    public void unlockWrite( final ConcreteResource resource )
    {
        //        fileManager.unlock( getDetachedFile( resource ) );
    }

    public void lockRead( final ConcreteResource resource )
    {
        //        fileManager.lock( getDetachedFile( resource ) );
    }

    public void lockWrite( final ConcreteResource resource )
    {
        //        fileManager.lock( getDetachedFile( resource ) );
    }

    public void waitForWriteUnlock( final ConcreteResource resource )
    {
        fileManager.waitForWriteUnlock( helper.getMainStorageFile( resource ) );
    }

    public void waitForReadUnlock( final ConcreteResource resource )
    {
        fileManager.waitForReadUnlock( helper.getFastestExistingFile( resource ) );
    }

    public void cleanupCurrentThread()
    {
        fileManager.cleanupCurrentThread();
    }

    public void startReporting()
    {
        fileManager.startReporting();
    }

    public void stopReporting()
    {
        fileManager.stopReporting();
    }
}
