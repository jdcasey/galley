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

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.enterprise.context.ApplicationScoped;

import org.commonjava.maven.galley.model.ConcreteResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SimpleLockingSupport
        implements LockingSupport
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final Map<ConcreteResource, WeakReference<Thread>> lock =
        new HashMap<ConcreteResource, WeakReference<Thread>>();

    private ReportingTask reporter;

    private final Timer timer = new Timer( true );

    public void waitForUnlock( final ConcreteResource resource )
    {
        synchronized ( lock )
        {
            while ( isLocked( resource ) )
            {
                logger.debug( "{} waiting for unlock of {}", Thread.currentThread()
                                                                   .getName(), resource );
                try
                {
                    lock.wait( 500 );
                }
                catch ( final InterruptedException e )
                {
                    logger.debug( "{} interrupted while waiting for unlock of: {}", Thread.currentThread()
                                                                                          .getName(), resource );
                    break;
                }
            }
        }
    }

    public synchronized boolean isLocked( final ConcreteResource resource )
    {
        final WeakReference<Thread> ref = lock.get( resource );
        if ( ref != null )
        {
            final Thread t = ref.get();
            if ( t != null )
            {
                if ( t == Thread.currentThread() )
                {
                    return false;
                }

                logger.debug( "{} locked by: {}", resource, t.getName() );
                return true;
            }
            else
            {
                lock.remove( resource );
            }
        }

        return false;
    }

    public void unlock( final ConcreteResource resource )
    {
        synchronized ( lock )
        {
            final Thread me = Thread.currentThread();
            final WeakReference<Thread> reference = lock.get( resource );
            if ( reference == null || reference.get() == me )
            {
                logger.debug( "Removing locked: {} by: {}. Returning.", resource, me.getName() );
                lock.remove( resource );
                lock.notifyAll();
            }
            else
            {
                logger.debug( "{} locked by: {}. Returning.", resource, reference.get()
                                                                                 .getName() );
            }
        }
    }

    public void lock( final ConcreteResource resource )
    {
        synchronized ( lock )
        {
            final Thread me = Thread.currentThread();
            final WeakReference<Thread> reference = lock.get( resource );
            if ( reference != null )
            {
                if ( reference.get() == me )
                {
                    logger.debug( "{} already locked by: {}. Returning.", resource, me.getName() );
                    return;
                }
                else
                {
                    logger.debug( "{} already locked by: {}. Waiting.", resource, reference.get()
                                                                                           .getName() );
                    waitForUnlock( resource );
                }
            }

            logger.debug( "Locking: {} in: {}.", resource, me.getName() );
            lock.put( resource, new WeakReference<Thread>( Thread.currentThread() ) );
            lock.notifyAll();
        }
    }

    @Override
    public void waitForReadUnlock( ConcreteResource resource )
    {
        waitForUnlock( resource );
    }

    @Override
    public void waitForWriteUnlock( ConcreteResource resource )
    {
        waitForUnlock( resource );
    }

    @Override
    public boolean isReadLocked( ConcreteResource resource )
    {
        return isLocked( resource );
    }

    @Override
    public boolean isWriteLocked( ConcreteResource resource )
    {
        return isLocked( resource );
    }

    @Override
    public void unlockRead( ConcreteResource resource )
    {
        unlock( resource );
    }

    @Override
    public void unlockWrite( ConcreteResource resource )
    {
        unlock( resource );
    }

    @Override
    public void lockRead( ConcreteResource resource )
    {
        lock( resource );
    }

    @Override
    public void lockWrite( ConcreteResource resource )
    {
        lock( resource );
    }

    @Override
    public synchronized void cleanupCurrentThread()
    {
        final long id = Thread.currentThread()
                              .getId();
        for ( final ConcreteResource res : new HashSet<ConcreteResource>( lock.keySet() ) )
        {
            final WeakReference<Thread> ref = lock.get( res );
            if ( ref != null )
            {
                boolean rm = false;
                if ( ref.get() == null )
                {
                    logger.debug( "Cleaning up lock: {} for thread: {}", res, Thread.currentThread()
                                                                                    .getName() );
                    rm = true;
                }
                else if ( ref.get()
                             .getId() == id )
                {
                    logger.debug( "Cleaning up lock: {} for thread: {}", res, Thread.currentThread()
                                                                                    .getName() );
                    rm = true;
                }

                if ( rm )
                {
                    synchronized ( lock )
                    {
                        lock.remove( res );
                        lock.notifyAll();
                    }
                }
            }
        }
    }

    public Map<ConcreteResource, CharSequence> getActiveLocks()
    {
        final Map<ConcreteResource, CharSequence> active = new HashMap<ConcreteResource, CharSequence>();
        for ( final ConcreteResource f : lock.keySet() )
        {
            final StringBuilder owner = new StringBuilder();
            final WeakReference<Thread> ref = lock.get( f );
            if ( ref == null )
            {
                owner.append( "UNKNOWN OWNER; REF IS NULL." );
            }

            final Thread t = ref.get();
            if ( t == null )
            {
                owner.append( "UNKNOWN OWNER; REF IS EMPTY." );
            }
            else
            {
                owner.append( t.getName() );
                if ( !t.isAlive() )
                {
                    owner.append( " (DEAD)" );
                }
            }

            active.put( f, owner );
        }

        return active;
    }

    public synchronized void startReporting()
    {
        startReporting( 0, 10000 );
    }

    public synchronized void startReporting( final long delay, final long period )
    {
        if ( reporter == null )
        {
            logger.info( "Starting file-lock statistics reporting with initial delay: {}ms and period: {}ms", delay,
                         period );
            reporter = new ReportingTask();
            timer.schedule( reporter, delay, period );
        }
    }

    public synchronized void stopReporting()
    {
        if ( reporter != null )
        {
            logger.info( "Stopping file-lock statistics reporting." );
            reporter.cancel();
        }
    }

    private final class ReportingTask
        extends TimerTask
    {
        @Override
        public void run()
        {
            final Map<ConcreteResource, CharSequence> activeLocks = getActiveLocks();
            if ( activeLocks.isEmpty() )
            {
                logger.debug( "No file locks to report." );
                return;
            }

            final StringBuilder sb = new StringBuilder();
            sb.append( "\n\nThe following file locks are still active:" );
            for ( final ConcreteResource file : activeLocks.keySet() )
            {
                sb.append( "\n" )
                  .append( file )
                  .append( " is owned by " )
                  .append( activeLocks.get( file ) );
            }

            sb.append( "\n\n" );

            logger.info( sb.toString() );
        }
    }

}
