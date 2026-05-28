package com.viettelDigitalTalent.EntitiyManagement.enrichment.geoip;

import com.maxmind.geoip2.DatabaseReader;
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

    private DatabaseReader reader;

    @PostConstruct
    public void init() {
        try {
            // Đọc file từ classpath
            InputStream database = getClass().getClassLoader().getResourceAsStream("GeoLite2-City.mmdb");
            if (database == null) {
                throw new FileNotFoundException("Không tìm thấy file GeoLite2-City.mmdb trong resources");
            }
            reader = new DatabaseReader.Builder(database).build();
            log.info("GeoIP Database đã tải thành công!");
        } catch (IOException e) {
            log.error("Lỗi khi load GeoIP database", e);
        }
    }

    public GeoInfo lookup(String ipAddress) {
        try {
            InetAddress ip = InetAddress.getByName(ipAddress);
            CityResponse response = reader.city(ip);

            GeoInfo info = new GeoInfo();
            info.setCountry(response.getCountry().getName());
            info.setCity(response.getCity().getName());
            info.setIsoCode(response.getCountry().getIsoCode());
            return info;
        } catch (Exception e) {
            log.warn("Không thể tra cứu IP: {}. Lỗi: {}", ipAddress, e.getMessage());
            return null; // Trả về null nếu không tìm thấy
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (reader != null) reader.close();
        } catch (IOException e) {
            log.error("Lỗi khi đóng GeoIP reader", e);
        }
    }
}