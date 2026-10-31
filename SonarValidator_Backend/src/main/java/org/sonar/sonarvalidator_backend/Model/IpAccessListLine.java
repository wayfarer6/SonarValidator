package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IpAccessListLine implements AclLineMatchExpr{
    private String _name;
    private LineAction _action;
    private AclLineMatchExpr _matchCondition;

    @Override
    public boolean match() {
        return false;
    }
}
