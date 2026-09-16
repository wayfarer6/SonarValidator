package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Ip {
    private long _ip;

    public Ip(long l) {
        this._ip = l;
    }

    public long asLong()
    {
         return this._ip;
    }
    public String toString() {
        return Long.toString(this._ip);
    }
}
