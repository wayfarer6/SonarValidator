package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaticRoute implements AbstractRoute{
    private boolean drop;
    private boolean reject;
}
