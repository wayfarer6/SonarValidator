// 네트워크 관련 주요 구조체등을 정리해둔 헤더 파일입니다.

struct VLan {
public:
    explicit VLan(int vlan_id) : vlan_id(vlan_id) {}
    int getVLANID() const { return vlan_id; }

private:
    int vlan_id;
};

struct Subnet {
public:
    explicit Subnet(int subnet_id);

private:
    int subnet_id;
};

struct NIC {
public:
    explicit NIC(int nic_id);

private:
    int nic_id;
};
