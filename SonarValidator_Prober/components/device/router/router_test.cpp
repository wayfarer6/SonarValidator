#include "components/device/router/router.hpp"

#include <cassert>
#include <string>

int main()
{
    Router router("edge-router");
    router.getRoutingTable().SetConnectionId("link-1");
    router.getRoutingTable().AddRoute("10.0.0.0/24", "10.0.0.1", "static", "10", "eth0");

    assert(router.getName() == "edge-router");
    assert(router.getRoutingTable().AddConnection());
    assert(router.getRoutingTable().GetRouteCount() == 1);

    router.setName("core-router");
    assert(router.getName() == "core-router");

    const Router& const_router = router;
    assert(const_router.getRoutingTable().GetRoutes().front().prefix == "10.0.0.0/24");
    return 0;
}
