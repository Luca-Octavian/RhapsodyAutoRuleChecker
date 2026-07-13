package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ModelRef;

public final class RhapsodyModelRef implements ModelRef {
	  private final String guid;
	  public RhapsodyModelRef(String guid) { this.guid = guid; }
	  public String tool() { return "Rhapsody"; }
	  public String id() { return guid; }
	}