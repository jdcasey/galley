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

import org.commonjava.maven.atlas.ident.DependencyScope;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.galley.maven.parse.GalleyMavenXMLException;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MavenPomViewTest
    extends AbstractMavenViewTest
{

    @Override
    protected String getBaseResource()
    {
        return "view/pom/";
    }

    @Test
    public void resolveExpressionReferencingPropertyWithNumericDottedPart() throws Exception
    {
        final String expr = "${my.1.version}";
        final MavenPomView pom = loadPoms( "pom-with-property.xml" );

        final String value = pom.resolveExpressions( expr );

        assertThat( value, equalTo( "1.0" ) );
    }

    @Test
    public void buildExtension() throws Exception
    {
        MavenPomView pomView = loadPoms( "pom-with-build-ext.xml" );

        List<ExtensionView> extensions = pomView.getBuildExtensions();

        assertThat( extensions, notNullValue() );
        assertThat( extensions.size(), equalTo( 1 ) );
        assertThat( extensions.get( 0 ).asProjectVersionRef(),
                    equalTo( (ProjectVersionRef) new SimpleProjectVersionRef( "ext.group", "ext-artifact", "1.0" ) ) );

    }

    @Test
    public void pluginConfigCalledExtension() throws Exception
    {
        MavenPomView pomView = loadPoms( new String[] { "test" }, "pom-with-plugin-conf-ext.xml" );

        List<ExtensionView> extensions = pomView.getBuildExtensions();

        assertThat( extensions, notNullValue() );
        assertThat( extensions.size(), equalTo( 0 ) );
    }

    @Test
    public void profileBuildExtension() throws Exception
    {
        MavenPomView pomView = loadPoms( new String[] { "test" }, "pom-with-profile-build-ext.xml" );

        List<ExtensionView> extensions = pomView.getBuildExtensions();

        assertThat( extensions, notNullValue() );
        assertThat( extensions.size(), equalTo( 1 ) );
        assertThat( extensions.get( 0 ).asProjectVersionRef(),
                    equalTo( (ProjectVersionRef) new SimpleProjectVersionRef( "ext.group", "ext-artifact", "1.0" ) ) );

    }

    @Test
    public void dependencyManagedByProfile() throws Exception
    {
        MavenPomView pomView = loadPoms( "pom-with-profile.xml" );

        DependencyView dv = pomView.getAllDirectDependencies().get( 0 );

        assertThat( dv.getVersion(), nullValue() );
        assertThat( dv.getScope(), equalTo( DependencyScope.compile ) );

        pomView = loadPoms( new String[] { "test" }, "pom-with-profile.xml" );

        dv = pomView.getAllDirectDependencies().get( 0 );

        assertThat( dv.getVersion(), equalTo( "1.0" ) );
        assertThat( dv.getScope(), equalTo( DependencyScope.test ) );

    }

    /**
     * Checks if plugin version specified as version property in a profile gets
     * resolved correctly. There is also global version property with different
     * value so it also checks if the profile value takes preference.
     */
    @Test
    public void pluginWithVersionPropertyInProfile() throws Exception
    {
        MavenPomView pomView = loadPoms( new String[] { "test" }, "pom-with-plugin-version-property-in-profile.xml" );

        PluginView pv = pomView.getAllBuildPlugins().get( 0 );

        assertThat( pv.getVersion(), equalTo( "2.0" ) );
    }

    @Test
    public void dependencyManagedBySingleBOM() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-bom.xml" );
        final MavenPomView bomView = loadPoms( "simple-bom.xml" );

        pomView.addMixin( new MavenXmlMixin<ProjectVersionRef>( bomView, MavenXmlMixin.DEPENDENCY_MIXIN ) );

        final DependencyView dv = pomView.getAllDirectDependencies().get( 0 );

        assertThat( dv.getVersion(), equalTo( "1.0" ) );
        assertThat( dv.getScope(), equalTo( DependencyScope.test ) );
    }

    @Test
    public void dependencyManagedBySingleBOMWithExpressionToProjectGroupId() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-bom-expr.xml" );
        final MavenPomView bomView = loadPoms( "simple-bom-expr.xml" );

        pomView.addMixin( new MavenXmlMixin<ProjectVersionRef>( bomView, MavenXmlMixin.DEPENDENCY_MIXIN ) );

        final DependencyView dv = pomView.getAllDirectDependencies().get( 0 );

        assertThat( dv.getVersion(), equalTo( "1.0" ) );
        assertThat( dv.getScope(), equalTo( DependencyScope.test ) );
    }

    @Test
    public void directDepsInParentAndMainPOM() throws Exception
    {
        MavenPomView pomView = loadPoms( "pom-with-parent-and-direct-dep.xml", "simple-parent-with-direct-dep.xml" );

        List<DependencyView> deps = pomView.getAllDirectDependencies();
        assertThat( deps.size(), equalTo( 2 ) );
    }

    @Test
    @Ignore( "https://github.com/Commonjava/galley/issues/96" )
    public void managedDepOverlapMergedFromParentToMainPOM() throws Exception
    {
        MavenPomView pomView = loadPoms( "pom-with-parent-and-incomplete-managed-dep.xml",
                                         "simple-parent-with-managed-dep.xml" );

        List<DependencyView> deps = pomView.getAllManagedDependencies();

        assertThat( deps.size(), equalTo( 1 ) );
        DependencyView dep = deps.get( 0 );
        assertThat( dep.getScope(), equalTo( DependencyScope.test ) );
        assertThat( dep.getVersion(), equalTo( "2.5" ) );
    }

    @Test
    @Ignore( "https://github.com/Commonjava/galley/issues/96" )
    public void dependencyOverrideInMainPOM() throws Exception
    {
        MavenPomView pomView = loadPoms( "pom-with-parent-and-redeclared-direct-dep.xml",
                                         "simple-parent-with-direct-dep.xml" );

        List<DependencyView> deps = pomView.getAllDirectDependencies();

        assertThat( deps.size(), equalTo( 1 ) );
        DependencyView dep = deps.get( 0 );
        assertThat( dep.getScope(), equalTo( DependencyScope.compile ) );
    }

    @Test
    public void projectWithBOMContainingBOMs() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-bom-of-boms.xml" );
        final MavenPomView bomView = loadPoms( "bom-with-boms.xml" );

        pomView.addMixin( new MavenXmlMixin<ProjectVersionRef>( bomView, MavenXmlMixin.DEPENDENCY_MIXIN ) );

        final List<DependencyView> boms = pomView.getAllBOMs();

        System.out.printf( "Found {} boms\n\n", boms.size() );
        for ( final DependencyView bom : boms )
        {
            System.out.println( bom.asProjectVersionRef() );
        }
    }

    @Test
    public void retrieveManagedDependencyFromBOMWithExpressionToBOMGroupId() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-bom-expr.xml" );
        final MavenPomView bomView = loadPoms( "simple-bom-expr.xml" );

        pomView.addMixin( new MavenXmlMixin<ProjectVersionRef>( bomView, MavenXmlMixin.DEPENDENCY_MIXIN ) );

        DependencyView dv = pomView.getAllManagedDependencies().get( 0 );

        assertThat( dv.getGroupId(), equalTo( "org.foo" ) );

        dv = pomView.getAllBOMs().get( 0 );

        assertThat( dv.getGroupId(), equalTo( "org.foo" ) );
    }

    @Test
    public void resolveParentVersionExpressionWithoutProjectPrefix() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-parent-expr.xml"/*, "simple-parent-pom.xml"*/ );

        final String value = pomView.resolveExpressions( "${parent.version}" );

        assertThat( value, equalTo( "1.0.0.0" ) );
    }

    @Test
    public void resolveExpressionWithDeprecatedPomDotPrefix() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-parent-expr.xml"/*, "simple-parent-pom.xml"*/ );

        final String value = pomView.resolveExpressions( "${pom.parent.version}" );

        assertThat( value, equalTo( "1.0.0.0" ) );
    }

    @Test
    public void parentPathIsLocalOnly() throws Exception
    {
        final MavenPomView pomView =
                        loadPoms( "pom-with-broken-parent.xml", "pom-with-parent.xml", "simple-parent-pom.xml" );

        final String value = pomView.resolveExpressions( "${parent.version}" );

        assertThat( pomView.containsExpression( value ), equalTo( true ) );
    }

    @Test
    public void groupIdFailOverToParent() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-parent-groupId.xml", "simple-parent-pom.xml" );

        final String gid = pomView.getGroupId();
        final ProjectVersionRef pvr = pomView.asProjectVersionRef();

        assertThat( gid, equalTo( "org.foo" ) );
        assertThat( pvr.getGroupId(), equalTo( "org.foo" ) );
    }

    @Test( expected = GalleyMavenXMLException.class )
    public void artifactId_DOES_NOT_FailOverToParent() throws Exception
    {
        final MavenPomView pomView =
                        loadPoms( "pom-with-broken-artifactId.xml", "pom-with-parent.xml", "simple-parent-pom.xml" );

        final String aid = pomView.getArtifactId();

        assertThat( aid, nullValue() );

        pomView.asProjectVersionRef();
    }

    @Test
    public void retrieveDirectBOMReference() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-bom.xml" );
        final List<DependencyView> boms = pomView.getAllBOMs();

        assertThat( boms, notNullValue() );
        assertThat( boms.size(), equalTo( 1 ) );
    }

    @Test
    public void retrieveBOMReferenceInParent() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-bom-child.xml", "pom-with-bom.xml" );
        final List<DependencyView> boms = pomView.getAllBOMs();

        assertThat( boms, notNullValue() );
        assertThat( boms.size(), equalTo( 1 ) );
    }

    @Test
    public void retrieveImpliedPluginDepsForSurefire() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-surefire.xml" );

        final List<PluginView> plugins = pomView.getAllBuildPlugins();
        assertThat( plugins, notNullValue() );
        assertThat( plugins.size(), equalTo( 1 ) );

        final PluginView pv = plugins.get( 0 );
        assertThat( pv, notNullValue() );

        final Set<PluginDependencyView> ipdvs = pv.getImpliedPluginDependencies();
        assertThat( ipdvs, notNullValue() );
        assertThat( ipdvs.size(), equalTo( 5 ) );

    }

    @Test
    public void artifactIdWithWhitespace() throws Exception
    {
        final MavenPomView pomView = loadPoms( "pom-with-whitespace-artifactId.xml" );

        final String aid = pomView.getArtifactId();
        final ProjectVersionRef pvr = pomView.asProjectVersionRef();

        assertThat( aid, equalTo( "bar-project" ) );
        assertThat( pvr.getArtifactId(), equalTo( "bar-project" ) );
    }

    @Test
    public void pomProperties() throws Exception
    {
        MavenPomView pomView = loadPoms( "pom-with-property.xml", "simple-parent-pom.xml" );

        PropertiesView propertiesView = pomView.getProperties();
        Properties result = propertiesView.getProperties();

        assertThat( result.size(), equalTo( 7 ) );
        assertThat( result.getProperty( "another-property" ), equalTo( "2.1" ) );
        assertThat( result.getProperty( "resolve-second-property" ), equalTo( "2.1" ) );
        assertThat( result.getProperty( "resolve-third-property" ), equalTo( "2.1" ) );
        assertThat( result.getProperty( "resolve-fourth-property" ), equalTo( "2.1" ) );
        assertThat( result.getProperty( "parent-property" ), equalTo( "999999" ) );

        assertThat( result.getProperty( "resolve-no-property" ), equalTo( "${i-dont-exist}" ) );
    }

    @Test
    public void pluginWithManagedPlugins() throws Exception
    {
        MavenPomView pomView = loadPoms( "pom-with-managed-plugin-conf.xml", "pom-plugin-parent.xml" );

        List<PluginView> pvs = pomView.getAllManagedBuildPlugins();

        for ( PluginView pv : pvs )
        {
            System.out.println( "pv " + pv.getGroupId() + " for version " + pv.getVersion() );

            assertTrue( !pv.getVersion().contains( "${" ) );
        }
    }

    @Test
    public void pluginExecutionsIncludedFromParent() throws Exception
    {

    }
}