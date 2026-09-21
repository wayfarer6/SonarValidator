#include <iostream>

class CiscoRouterCommand {
  public:
    bool setRoutingProtocol();
    bool setNICDisable();
    bool setNICEnable(Interface& nic);
    bool showConfig();
}
