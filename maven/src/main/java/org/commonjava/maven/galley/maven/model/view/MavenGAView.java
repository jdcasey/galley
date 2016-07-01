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
package org.commonjava.maven.galley.maven.model.view;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.A;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.G;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.w3c.dom.Element;

/**
 * Generic view that represents some reference to a Maven project without regard to any given version of it.
 */
public class MavenGAView
    extends MavenPomElementView
    implements ProjectRefView
{

    protected final MavenGAVHelper helper;

    public MavenGAView( final MavenPomView pomView, final Element element, final OriginInfo originInfo, final String managementXpathFragment )
    {
        super( pomView, element, originInfo, managementXpathFragment );
        this.helper = new MavenGAVHelper( this );
    }

    public MavenGAView( final MavenPomView pomView, final Element element, final OriginInfo originInfo )
    {
        super( pomView, element, originInfo, null );
        this.helper = new MavenGAVHelper( this );
    }

    @Override
    public String getGroupId()
    {
        return helper.getGroupId();
    }

    protected void setGroupId( String groupId )
    {
        helper.setGroupId( groupId );
    }

    @Override
    public String getArtifactId()
    {
        return helper.getArtifactId();
    }

    @Override
    public ProjectRef asProjectRef()
            throws GalleyMavenException
    {
        return helper.asProjectRef();
    }

    @Override
    public String toString()
    {
        return helper.gaToString();
    }

    public boolean isValid()
    {
        return helper.gaIsValid();
    }

    @Override
    public int hashCode()
    {
        return helper.gaHashCode();
    }

    @Override
    public boolean equals( final Object obj )
    {
        return helper.gaEquals( obj );
    }

}
