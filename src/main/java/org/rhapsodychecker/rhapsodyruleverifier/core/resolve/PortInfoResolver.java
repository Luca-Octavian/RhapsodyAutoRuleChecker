package org.rhapsodychecker.rhapsodyruleverifier.core.resolve;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

public interface PortInfoResolver {
    PortInfo resolve(ElementRecord element);
}