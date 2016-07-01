package org.commonjava.maven.galley.maven.model.view;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.A;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.G;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.V;

/**
 * Helper class designed to allow {@link MavenPomElementView} descendants consistent access to GAV retrieval functions,
 * even if the cannot inherit from {@link MavenGAView} or {@link MavenGAVView}.<br/>
 * <b>NOTE:</b>This class is <b>NOT</b> designed to be exposed directly as API; it is designed to be used
 * selectively as a delegate from within another view class.
 */
final class MavenGAVHelper
{

    private String groupId;

    private String artifactId;

    private String rawVersion;

    private String managedVersion;

    private boolean versionLookupFinished;

    private MavenPomElementView view;

    MavenGAVHelper( MavenPomElementView view )
    {

        this.view = view;
    }

    synchronized String getGroupId()
    {
        if ( groupId == null )
        {
            groupId = view.getValue( G );
        }

        return groupId;
    }

    void setGroupId( final String groupId )
    {
        this.groupId = groupId;
    }

    synchronized String getArtifactId()
    {
        if ( artifactId == null )
        {
            artifactId = view.getValue( A );
        }

        return artifactId;
    }

    ProjectRef asProjectRef()
            throws GalleyMavenException
    {
        try
        {
            return new SimpleProjectRef( getGroupId(), getArtifactId() );
        }
        catch ( final IllegalArgumentException e )
        {
            throw new GalleyMavenException( "Cannot render ProjectRef: {}:{}. Reason: {}", e, getGroupId(), getArtifactId(), e.getMessage() );
        }
    }

    String gaToString()
    {
        return String.format( "%s [%s:%s]", getClass().getSimpleName(), getGroupId(), getArtifactId() );
    }

    boolean gaIsValid()
    {
        return !view.containsExpression( getGroupId() ) && !view.containsExpression( getArtifactId() );
    }

    int gaHashCode()
    {
        final int prime = 31;
        int result = 1;
        final String artifactId = getArtifactId();
        final String groupId = getGroupId();

        result = prime * result + ( ( artifactId == null ) ? 0 : artifactId.hashCode() );
        result = prime * result + ( ( groupId == null ) ? 0 : groupId.hashCode() );
        return result;
    }

    boolean gaEquals( final Object obj )
    {
        if ( view == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final String artifactId = getArtifactId();
        final String groupId = getGroupId();

        final ProjectRefView other = (ProjectRefView) obj;
        final String oArtifactId = other.getArtifactId();
        final String oGroupId = other.getGroupId();

        if ( artifactId == null )
        {
            if ( oArtifactId != null )
            {
                return false;
            }
        }
        else if ( !artifactId.equals( oArtifactId ) )
        {
            return false;
        }
        if ( groupId == null )
        {
            if ( oGroupId != null )
            {
                return false;
            }
        }
        else if ( !groupId.equals( oGroupId ) )
        {
            return false;
        }
        return true;
    }

    String getVersion()
            throws GalleyMavenException
    {
        lookupVersion();

        return rawVersion == null ? managedVersion : rawVersion;
    }

    boolean isVersionLookupFinished()
    {
        return versionLookupFinished;
    }

    synchronized void lookupVersion()
            throws GalleyMavenException
    {
        if ( !versionLookupFinished && ( rawVersion == null || managedVersion == null ) )
        {
            versionLookupFinished = true;

            //            final Logger logger = LoggerFactory.getLogger( getClass() );
            //            logger.info( "Resolving version for: {}[{}:{}]\nIn: {}", getClass().getSimpleName(), getGroupId(), getArtifactId(), pomView.getRef() );
            rawVersion = view.getValue( V );
            if ( view.getManagementXpathFragment() != null )
            {
                managedVersion = view.getManagedValue( V );
            }
        }
    }

    String getRawVersion()
            throws GalleyMavenException
    {
        return getRawVersion( true );
    }

    String getRawVersion( boolean lookup )
            throws GalleyMavenException
    {
        if ( lookup )
        {
            lookupVersion();
        }

        return rawVersion;
    }

    String getManagedVersion()
            throws GalleyMavenException
    {
        return getManagedVersion( true );
    }

    String getManagedVersion( boolean lookup)
            throws GalleyMavenException
    {

        if ( lookup && view.getManagementXpathFragment() != null )
        {
            lookupVersion();
        }

        return managedVersion;
    }

    ProjectVersionRef asProjectVersionRef()
            throws GalleyMavenException
    {
        try
        {
            return new SimpleProjectVersionRef( getGroupId(), getArtifactId(), getVersion() );
        }
        catch ( final IllegalArgumentException e )
        {
            throw new GalleyMavenException( "Cannot render ProjectVersionRef: {}:{}:{}. Reason: {}", e, getGroupId(),
                                            getArtifactId(), getVersion(), e.getMessage() );
        }
    }

    void setVersion( final String version )
    {
        setRawVersion( version );
    }

    void setRawVersion( String version )
    {
        this.rawVersion = version;
    }

    void setManagedVersion( String version )
    {
        this.managedVersion = version;
    }

    void setVersionLookupDone( boolean done )
    {
        this.versionLookupFinished = done;
    }

    String gavToString()
    {
        String v = rawVersion;
        if ( v == null )
        {
            v = managedVersion;
        }

        return String.format( "%s [%s:%s:%s]%s", getClass().getSimpleName(), getGroupId(), getArtifactId(),
                              v == null ? "unresolved" : v,
                              managedVersion == null ? "" : " (managed from: " + managedVersion + ")" );
    }

    boolean gavIsValid()
    {
        try
        {
            return gaIsValid() && !view.containsExpression( getVersion() );
        }
        catch ( final GalleyMavenException e )
        {
            Logger logger = LoggerFactory.getLogger( getClass() );
            logger.warn( "Failed to lookupVersion management element. Reason: {}", e, e.getMessage() );
        }

        return false;
    }


}
