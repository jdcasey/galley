package org.commonjava.maven.galley.cache;

import org.commonjava.maven.galley.model.ConcreteResource;

import java.util.Map;

/**
 * Created by jdcasey on 2/27/16.
 */
public interface LockingSupport
{
    void waitForReadUnlock( ConcreteResource resource );

    void waitForWriteUnlock( ConcreteResource resource );

    boolean isReadLocked( ConcreteResource resource );

    boolean isWriteLocked( ConcreteResource resource );

    void unlockRead( ConcreteResource resource );

    void unlockWrite( ConcreteResource resource );

    void lockRead( ConcreteResource resource );

    void lockWrite( ConcreteResource resource );

    void cleanupCurrentThread();

    void startReporting();

    void stopReporting();
}
