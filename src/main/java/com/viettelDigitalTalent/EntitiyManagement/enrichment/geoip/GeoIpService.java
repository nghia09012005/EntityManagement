package com.viettelDigitalTalent.EntitiyManagement.enrichment.geoip;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.core.EnrichmentProvider;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

@Service
@Slf4j // Dùng Lombok để log
public class GeoIpService {

    private DatabaseReader cityReader;
    private DatabaseReader asnReader;

    @PostConstruct
    public void init() {
        cityReader = loadReader("GeoLite2-City.mmdb");
        asnReader = loadReader("GeoLite2-ASN.mmdb");
        log.info("GeoIP databases đã tải xong: city={}, asn={}", cityReader != null, asnReader != null);
    }

    private DatabaseReader loadReader(String resourceName) {
        try (InputStream database = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (database == null) {
                log.warn("Không tìm thấy file {} trong resources", resourceName);
                return null;
            }
            return new DatabaseReader.Builder(database).build();
        } catch (IOException e) {
            log.error("Lỗi khi load GeoIP database {}", resourceName, e);
            return null;
        }
    }

    public GeoInfo lookup(String ipAddress) {
        try {
            InetAddress ip = InetAddress.getByName(ipAddress);
            GeoInfo info = new GeoInfo();

            if (cityReader != null) {
                CityResponse response = cityReader.city(ip);
                info.setCountry(response.getCountry().getName());
                info.setCity(response.getCity().getName());
                info.setIsoCode(response.getCountry().getIsoCode());
            }

            if (asnReader != null) {
                AsnResponse asnResponse = asnReader.asn(ip);
                String organization = asnResponse.getAutonomousSystemOrganization();
                Long asnNumber = asnResponse.getAutonomousSystemNumber();
                info.setAsn(organization == null || organization.isBlank()
                    ? "AS" + asnNumber
                    : "AS" + asnNumber + " - " + organization);
            }

            return info;
        } catch (Exception e) {
            log.warn("Không thể tra cứu IP: {}. Lỗi: {}", ipAddress, e.getMessage());
            return null; // Trả về null nếu không tìm thấy
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (cityReader != null) cityReader.close();
            if (asnReader != null) asnReader.close();
        } catch (IOException e) {
            log.error("Lỗi khi đóng GeoIP reader", e);
        }
    }
}