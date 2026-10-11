package org.sonar.sonarvalidator_backend.Model;

public class OspfRoute implements AbstractRoute {
    public OspfProtocolSubType protocolSubType;
    public long area;
    public long metric;
    public long cost;
}
