package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.SortedSet;

@Getter
@Setter
public class IpAccessList {
    private String _name;
    private List<IpAccessList> _lines;
    private SortedSet<String> _sourceInterfaces;

}


