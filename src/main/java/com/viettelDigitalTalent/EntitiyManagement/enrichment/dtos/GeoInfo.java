package com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos;

import lombok.Data;

@Data
public class GeoInfo {
    private String country;
    private String city;
    private String isoCode;
    private String asn; // Cần dùng thư viện GeoIP2-ASN nếu muốn lấy ASN
}