package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Prefix {
    private Ip _ip;
    private int _prefixLength;

    public boolean contains(Ip ip)
    {
        return _ip.asLong() == ip.get_ip();
    }

    public Ip getStartIp() {
        return this._ip;
    }
    public Ip getEndIp() {
        long hostMask = (1L << (32 - _prefixLength)) - 1;
        return new Ip(this._ip.get_ip() | hostMask);
    }
    public static Prefix parse(String str)
    {
        return new Prefix();
    }
}
