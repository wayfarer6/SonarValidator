package org.sonar.sonarvalidator_backend.Service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;

public final class ManagementServerAddress {
    private ManagementServerAddress() {}
    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String address = value.trim();
        try {
            if (!address.matches("(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")) throw new IllegalArgumentException();
            if (address.matches("[0-9.]+")) PacketVariables.parseCidr(address + "/32");
            return address;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 관리 서버 IPv4 주소 또는 호스트명을 입력하세요.");
        }
    }
}
