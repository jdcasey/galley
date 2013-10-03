package org.commonjava.maven.galley.maven.type;

import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.TypeAndClassifier;
import org.commonjava.maven.galley.model.TypeMapping;

public interface TypeMapper
{

    TypeMapping lookup( TypeAndClassifier tc );

    Map<String, TypeMapping> getMappings();

}