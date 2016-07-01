package org.commonjava.maven.galley.maven.model.view;

import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.w3c.dom.Element;

import static org.commonjava.maven.galley.maven.model.view.XPathManager.A;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.AND;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.END_PAREN;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.EQQUOTE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.G;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.QUOTE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.RESOLVE;
import static org.commonjava.maven.galley.maven.model.view.XPathManager.TEXT;

/**
 * Abstract super-class for Maven plugin and plugin-execution sections of the POM. Both sections contain a configuration
 * element, and can be specified in pluginManagement as well as in the main build plugins section. This base class also
 * allows things like {@link PluginConfigurationView} to hold its parent value in a single field with relatively tight
 * type constraints (i.e. not of type {@link Object}). The type parameter in this class should allow us to always return
 * the appropriately-typed {@link PluginConfigurationView} (though I need to find out if this is really required, or if
 * the compiler can figure it out).
 *
 * Created by jdcasey on 6/30/16.
 */
public class AbstractPluginBaseView<T extends AbstractPluginBaseView<T>>
        extends MavenPomElementView
{
    private PluginConfigurationView configuration;

    public AbstractPluginBaseView( MavenPomView pomView, Element element, OriginInfo originInfo,
                                   String managementXpathFragment )
    {
        super( pomView, element, originInfo, managementXpathFragment );
    }

    public boolean isManaged()
            throws GalleyMavenException
    {
        return xmlView.resolveXPathToNodeFrom( elementContext, "ancestor::pluginManagement", true ) != null;
    }

    public PluginConfigurationView<T> getConfiguration()
    {
        Element config = getElement( "configuration" );
        OriginInfo originInfo = getOriginInfo();
        if ( config == null )
        {
            config = getManagedElement( "configuration" );
            originInfo.setInherited( true );
        }

        return new PluginConfigurationView( getPomView(), this, getElement(), originInfo );
    }
}
